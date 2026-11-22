// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync

import io.github.velosako3.blossom.core.database.BlossomDatabase
import io.github.velosako3.blossom.core.database.LibraryCommands
import io.github.velosako3.blossom.core.database.LibraryProjector
import io.github.velosako3.blossom.core.database.NewEntry
import io.github.velosako3.blossom.core.database.SqlLibraryRepository
import io.github.velosako3.blossom.core.database.SqlOpLog
import io.github.velosako3.blossom.core.database.inMemoryDatabase
import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.EntryStatus
import io.github.velosako3.blossom.core.model.PlayEvent
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.core.model.sync.HybridLogicalClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** A shared folder in memory; records every write path. */
class InMemoryBackend(override val id: String = "memory") : SyncBackend {
    val files = ConcurrentHashMap<String, ByteArray>()
    val writes = mutableListOf<String>()
    override suspend fun list(prefix: String) = files.filterKeys { it.startsWith(prefix) }.map { (p, b) -> RemoteFile(p, b.size.toLong(), null, null) }
    override suspend fun read(path: String) = files[path] ?: throw java.io.FileNotFoundException(path)
    override suspend fun write(path: String, bytes: ByteArray) { synchronized(writes) { writes += path }; files[path] = bytes }
}

private class MovableClock(var now: Instant = Instant.fromEpochMilliseconds(1_800_000_000_000)) : Clock {
    override fun now() = now
    fun advance(d: Duration) { now += d }
}

private class Peer(val id: String, backend: SyncBackend, val clock: MovableClock = MovableClock()) {
    val db: BlossomDatabase = inMemoryDatabase()
    val log = SqlOpLog(db, id, HybridLogicalClock(id, clock))
    val projector = LibraryProjector(db)
    val commands = LibraryCommands(log, projector, db, clock)
    val repo = SqlLibraryRepository(db)
    val engine = SyncEngine(backend, log, projector, db, id, "Device $id")
}

private fun track(n: Int) = Track(TrackId.of(SourceKind.YOUTUBE_MUSIC, "v$n"), "Song $n", listOf(ArtistRef(null, "Artist $n")), null, 180_000, null)

/** Everything that must converge, as plain values. */
private fun snapshot(db: BlossomDatabase): List<Any> {
    val q = db.blossomQueries
    val playlists = q.livePlaylists().executeAsList().map { Triple(it.id, it.name, it.description) }.sortedBy { it.first }
    return listOf(
        playlists,
        playlists.associate { (id) -> id to q.liveEntries(id).executeAsList().map { it.entry_id } },
        q.likedTracks().executeAsList().map { it.track_id }.sorted(),
        q.allPlayIds().executeAsList().sorted(),
    )
}

private suspend fun randomEdits(p: Peer, rnd: Random, n: Int) {
    repeat(n) {
        p.clock.advance(Duration.parse("${rnd.nextInt(1, 5000)}ms"))
        val lists = p.repo.playlists().first()
        when (rnd.nextInt(7)) {
            0 -> p.commands.createPlaylist("L${rnd.nextInt(1000)}")
            1 -> lists.randomOrNull(rnd)?.let { p.commands.renamePlaylist(it.id, "R${rnd.nextInt(1000)}") }
            2, 3 -> lists.randomOrNull(rnd)?.let { p.commands.addEntries(it.id, listOf(NewEntry(track(rnd.nextInt(50)), EntryStatus.MATCHED, "t", "a", null))) }
            4 -> lists.randomOrNull(rnd)?.let { l -> p.repo.playlist(l.id).first()?.entries?.randomOrNull(rnd)?.let { e -> p.commands.moveEntry(l.id, e.entryId, 0) } }
            5 -> p.commands.toggleLike(track(rnd.nextInt(20)))
            else -> p.commands.recordPlay(PlayEvent("pe-${p.id}-$it-${rnd.nextInt()}", track(rnd.nextInt(20)), p.clock.now(), 40_000, true, p.id))
        }
    }
}

class SyncEngineTest {
    @Test fun threeDevicesConverge(): Unit = runBlocking {
        val backend = InMemoryBackend()
        val peers = listOf("A", "B", "C").map { Peer(it, backend) }
        val rnd = Random(7)
        peers.forEach { randomEdits(it, rnd, 60) }
        repeat(2) { peers.forEach { it.engine.syncOnce() } }
        val snaps = peers.map { snapshot(it.db) }
        assertEquals(snaps[0], snaps[1]); assertEquals(snaps[0], snaps[2])
        assertTrue((snaps[0][0] as List<*>).isNotEmpty())
    }

    @Test fun twoWeeksOfflineConvergesInOneRound(): Unit = runBlocking {
        val backend = InMemoryBackend()
        val (a, b, c) = listOf("A", "B", "C").map { Peer(it, backend) }
        val rnd = Random(11)
        randomEdits(a, rnd, 30); randomEdits(b, rnd, 30)
        a.engine.syncOnce(); b.engine.syncOnce(); a.engine.syncOnce()
        c.clock.advance(14.days)
        randomEdits(c, rnd, 500)
        c.engine.syncOnce(); a.engine.syncOnce(); b.engine.syncOnce()
        assertEquals(snapshot(a.db), snapshot(c.db)); assertEquals(snapshot(b.db), snapshot(c.db))
    }

    @Test fun anUnreadableSegmentIsFetchedAgainLater(): Unit = runBlocking {
        val backend = InMemoryBackend()
        val b = Peer("B", backend)
        val bEngine = SyncEngine(backend, b.log, b.projector, b.db, "B", "Device B", segmentMaxBytes = 600)
        repeat(12) { b.commands.toggleLike(track(it)) }
        bEngine.syncOnce()
        assertTrue(backend.files.keys.count { it.startsWith("blossom/v1/devices/B/ops-") } >= 3)
        var failFirst = true
        val flaky = object : SyncBackend by backend {
            override suspend fun read(path: String): ByteArray {
                if (path == SyncLayout.segment("B", 0) && failFirst) { failFirst = false; throw java.io.IOException("429 Too Many Requests") }
                return backend.read(path)
            }
        }
        val a = Peer("A", backend)
        val aEngine = SyncEngine(flaky, a.log, a.projector, a.db, "A", "Device A")
        val first = aEngine.syncOnce()
        assertTrue(first.errors.isNotEmpty())
        aEngine.syncOnce()
        assertEquals(12, a.db.blossomQueries.likedTracks().executeAsList().size)
    }

    @Test fun corruptLineIsSkippedAndReported(): Unit = runBlocking {
        val backend = InMemoryBackend()
        val a = Peer("A", backend); val b = Peer("B", backend)
        val pid = b.commands.createPlaylist("From B")
        b.commands.addEntries(pid, (1..5).map { NewEntry(track(it), EntryStatus.MATCHED, "t", "a", null) })
        b.engine.syncOnce()
        val seg = SyncLayout.segment("B", 0)
        val lines = String(backend.files.getValue(seg)).trimEnd('\n').split('\n').toMutableList()
        lines.add(3, "{\"v\":1,\"opId\":\"broken\",") // a torn write in the middle
        backend.files[seg] = (lines.joinToString("\n") + "\n").toByteArray()
        val report = a.engine.syncOnce()
        assertEquals(1, report.errors.size, report.errors.toString())
        assertEquals(5, a.repo.playlist(pid).first()!!.entries.size)
        assertEquals(6, report.pulled)
    }

    @Test fun writesOnlyInOwnFolder(): Unit = runBlocking {
        val backend = InMemoryBackend()
        val peers = listOf("A", "B").map { Peer(it, backend) }
        val rnd = Random(3)
        peers.forEach { randomEdits(it, rnd, 40) }
        repeat(2) { peers.forEach { it.engine.syncOnce() } }
        val a = peers[0]
        val before = backend.writes.size
        randomEdits(a, rnd, 10); a.engine.syncOnce()
        assertTrue(backend.writes.drop(before).all { it.startsWith("blossom/v1/devices/A/") }, backend.writes.drop(before).toString())
        assertTrue(backend.writes.all { it.startsWith("blossom/v1/devices/A/") || it.startsWith("blossom/v1/devices/B/") })
    }

    @Test fun unsyncedSettingsAreIgnored(): Unit = runBlocking {
        val backend = InMemoryBackend()
        val a = Peer("A", backend); val b = Peer("B", backend)
        b.commands.changeSetting("theme", "\"light\"")
        b.commands.changeSetting("local.folders", "[\"/home/b/Music\"]")
        b.engine.syncOnce(); a.engine.syncOnce()
        assertEquals("\"light\"", a.db.blossomQueries.settingValue("theme").executeAsOneOrNull())
        assertEquals(null, a.db.blossomQueries.settingValue("local.folders").executeAsOneOrNull()?.takeIf { it.isNotEmpty() })
    }

    @Test fun segmentsRollOver(): Unit = runBlocking {
        val backend = InMemoryBackend()
        val a = Peer("A", backend)
        val engine = SyncEngine(backend, a.log, a.projector, a.db, "A", "A", segmentMaxBytes = 4_000)
        repeat(60) { a.commands.toggleLike(track(it)) }
        engine.syncOnce()
        val m = DeviceManifest.decode(String(backend.files.getValue(SyncLayout.manifest("A"))))
        assertTrue(m.segments.size > 1, "${m.segments}")
        assertEquals(60, m.lastSeq)
        val b = Peer("B", backend); b.engine.syncOnce()
        assertEquals(60, b.db.blossomQueries.likedTracks().executeAsList().size)
    }
}
