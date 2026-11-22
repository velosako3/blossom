// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.offline

import io.github.velosako3.blossom.core.database.LibraryCommands
import io.github.velosako3.blossom.core.database.LibraryProjector
import io.github.velosako3.blossom.core.database.NewEntry
import io.github.velosako3.blossom.core.database.SqlLibraryRepository
import io.github.velosako3.blossom.core.database.SqlOpLog
import io.github.velosako3.blossom.core.model.EntryStatus
import io.github.velosako3.blossom.core.model.sync.HybridLogicalClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

class OfflineStoreTest {
    private val h = OfflineHarness(mapOf("a" to 200_000, "b" to 150_000, "c" to 120_000))
    private val hlc = HybridLogicalClock("A", Clock.System)
    private val commands = LibraryCommands(SqlOpLog(h.db, "A", hlc), LibraryProjector(h.db), h.db)
    private val store = SqlOfflineStore(h.db, h.manager, SqlLibraryRepository(h.db), h.scope)

    @AfterTest fun close() = h.close()

    private fun entry(id: String) = NewEntry(track(id), EntryStatus.MATCHED, "Song $id", "Artist", null)
    private fun files() = h.dir.listFiles().orEmpty().filter(File::isFile)

    @Test fun refCountedAcrossPlaylists(): Unit = runBlocking {
        val p1 = commands.createPlaylist("one"); val p2 = commands.createPlaylist("two")
        commands.addEntries(p1, listOf(entry("a"))); commands.addEntries(p2, listOf(entry("a")))
        store.savePlaylist(p1); store.savePlaylist(p2)
        val id = track("a").id
        withTimeout(30_000) { store.state(id).first { it == OfflineState.SAVED } }
        store.unsavePlaylist(p1)
        assertEquals(OfflineState.SAVED, store.state(id).first())
        assertEquals(1, files().size)
        store.unsavePlaylist(p2)
        withTimeout(10_000) { store.state(id).first { it == OfflineState.NOT_SAVED } }
        assertEquals(0, files().size)
    }

    @Test fun savedPlaylistPicksUpNewEntries(): Unit = runBlocking {
        val p = commands.createPlaylist("auto")
        commands.addEntries(p, listOf(entry("b")))
        store.savePlaylist(p)
        withTimeout(30_000) { store.state(track("b").id).first { it == OfflineState.SAVED } }
        commands.addEntries(p, listOf(entry("c")))
        withTimeout(30_000) { store.state(track("c").id).first { it == OfflineState.SAVED } }
    }

    @Test fun unreferencedFilesAreSweptAtStartup(): Unit = runBlocking {
        java.io.File(h.dir, "leftover.blsm").writeBytes(ByteArray(10))
        SqlOfflineStore(h.db, h.manager, null, h.scope)
        withTimeout(5_000) { while (java.io.File(h.dir, "leftover.blsm").exists()) kotlinx.coroutines.delay(20) }
    }

    @Test fun clearAllRemovesEverything(): Unit = runBlocking {
        val t = track("a")
        store.saveTrack(t, OfflineRef.Single(t.id))
        withTimeout(30_000) { store.state(t.id).first { it == OfflineState.SAVED } }
        store.clearAll()
        assertEquals(OfflineState.NOT_SAVED, store.state(t.id).first())
        assertEquals(0L, store.usageBytes().first())
        assertEquals(0, files().size)
    }
}
