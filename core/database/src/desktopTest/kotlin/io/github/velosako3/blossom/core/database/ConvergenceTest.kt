// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.database

import io.github.velosako3.blossom.core.model.PlayEvent
import io.github.velosako3.blossom.core.model.PlaylistId
import io.github.velosako3.blossom.core.model.sync.Hlc
import io.github.velosako3.blossom.core.model.sync.OpEnvelope
import io.github.velosako3.blossom.core.model.sync.Operation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Review focus 4: concurrent offline edits with skewed clocks merge identically in any delivery order. */
class ConvergenceTest {
    private fun project(ops: List<OpEnvelope>): LibrarySnapshot {
        val db = inMemoryDatabase()
        LibraryProjector(db).applyBlocking(ops)
        return snapshot(db)
    }

    @Test fun threeDevicesConverge() = runTest {
        val clocks = listOf(OffsetClock(-(2.hours)), OffsetClock(0.hours), OffsetClock(5.minutes))
        val devices = listOf(Device("A", clocks[0]), Device("B", clocks[1]), Device("C", clocks[2]))
        // Shared starting point: A creates three playlists with a few songs, delivered to everyone.
        val shared = List(3) { devices[0].commands.createPlaylist("Shared $it") }
        shared.forEach { devices[0].commands.addEntries(it, (1..4).map(::newEntry)) }
        val seed = devices[0].log.all()
        devices.drop(1).forEach { it.projector.applyBlocking(seed); it.log.insertRemote(seed) }

        val rnd = Random(42)
        var n = 100
        repeat(200) { round ->
            devices.forEachIndexed { i, d ->
                clocks[i].tick(rnd.nextLong(1, 50))
                val pl = shared[rnd.nextInt(shared.size)]
                val entries = d.repo.playlist(pl).first()?.entries.orEmpty()
                when (rnd.nextInt(9)) {
                    0 -> d.commands.renamePlaylist(pl, "${d.id}-$round")
                    1 -> d.commands.setDescription(pl, if (rnd.nextBoolean()) null else "d${d.id}$round")
                    2, 3 -> d.commands.addEntries(pl, listOf(newEntry(n++)), atIndex = rnd.nextInt(entries.size + 1))
                    4 -> entries.randomOrNull(rnd)?.let { d.commands.moveEntry(pl, it.entryId, rnd.nextInt(entries.size)) }
                    5 -> entries.randomOrNull(rnd)?.let { d.commands.removeEntry(pl, it.entryId) }
                    6 -> d.commands.like(sampleTrack(rnd.nextInt(20)))
                    7 -> d.commands.unlike(sampleTrack(rnd.nextInt(20)).id)
                    8 -> d.commands.recordPlay(PlayEvent("${d.id}-play-$round", sampleTrack(rnd.nextInt(20)), Instant.fromEpochMilliseconds(1_800_000_000_000 + round), 31_000, true, d.id))
                }
                if (round == 150 && i == 2) d.commands.deletePlaylist(shared[2])
            }
        }
        val all = devices.flatMap { it.log.all() }.distinctBy { it.opId }
        val inOrder = project(all.sortedBy { it.hlc })
        val reversed = project(all.sortedByDescending { it.hlc } + all.take(50))
        val shuffledTwice = project((all + all).shuffled(Random(7)))
        assertEquals(inOrder, reversed)
        assertEquals(inOrder, shuffledTwice)
        assertTrue(inOrder.playlists.none { it.first == shared[2].value }, "deleted playlist stays deleted")
        assertTrue(inOrder.entries.values.sumOf { it.size } > 20)
    }

    private val p = PlaylistId("p1")
    private fun env(id: String, wall: Long, device: String, op: Operation) = OpEnvelope(id, Hlc(wall, 0, device), device, wall, op)

    @Test fun deleteWins() {
        val ops = listOf(
            env("1", 1, "A", Operation.PlaylistCreated(p, "P", null, null)),
            env("2", 10, "A", Operation.PlaylistDeleted(p)),
            env("3", 12, "B", Operation.EntryAdded(p, "e1", sampleTrack(1), io.github.velosako3.blossom.core.model.EntryStatus.MATCHED, "t", "a", "V", null)),
        )
        assertTrue(project(ops).playlists.isEmpty())
        assertTrue(project(ops.reversed()).playlists.isEmpty())
    }

    @Test fun concurrentRenameLastWriterWins() {
        val ops = listOf(
            env("1", 1, "A", Operation.PlaylistCreated(p, "P", null, null)),
            env("2", 5, "A", Operation.PlaylistRenamed(p, "Gym")),
            env("3", 7, "B", Operation.PlaylistRenamed(p, "Gym 2")),
        )
        assertEquals("Gym 2", project(ops).playlists.single().second)
        assertEquals("Gym 2", project(ops.reversed()).playlists.single().second)
    }

    @Test fun concurrentInsertSameIndexKeepsBoth() {
        val status = io.github.velosako3.blossom.core.model.EntryStatus.MATCHED
        val ops = listOf(
            env("1", 1, "A", Operation.PlaylistCreated(p, "P", null, null)),
            env("2", 5, "A", Operation.EntryAdded(p, "ea", sampleTrack(1), status, "t", "a", "V", null)),
            env("3", 5, "B", Operation.EntryAdded(p, "eb", sampleTrack(2), status, "t", "a", "V", null)),
        )
        val a = project(ops).entries.getValue("p1")
        assertEquals(setOf("ea", "eb"), a.toSet())
        assertEquals(a, project(ops.reversed()).entries.getValue("p1"))
    }
}
