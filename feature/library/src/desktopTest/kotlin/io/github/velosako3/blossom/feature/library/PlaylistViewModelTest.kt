// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.library

import io.github.velosako3.blossom.core.database.LibraryCommands
import io.github.velosako3.blossom.core.database.LibraryProjector
import io.github.velosako3.blossom.core.database.NewEntry
import io.github.velosako3.blossom.core.database.SqlLibraryRepository
import io.github.velosako3.blossom.core.database.SqlOpLog
import io.github.velosako3.blossom.core.database.inMemoryDatabase
import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.EntryStatus
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.core.model.sync.HybridLogicalClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock

class PlaylistViewModelTest {
    private val db = inMemoryDatabase()
    private val commands = LibraryCommands(SqlOpLog(db, "A", HybridLogicalClock("A", Clock.System)), LibraryProjector(db), db)
    private val repo = SqlLibraryRepository(db)
    private fun t(n: Int) = Track(TrackId.of(SourceKind.YOUTUBE_MUSIC, "v$n"), "Song $n", listOf(ArtistRef(null, "A")), null, 1000, null)

    private suspend fun <T> eventually(block: suspend () -> T?): T {
        repeat(100) { block()?.let { return it }; delay(20) }
        error("condition not met")
    }

    @Test fun dragFourToOneMovesThatEntry() = runBlocking {
        val id = commands.createPlaylist("P")
        val ids = commands.addEntries(id, (0..5).map { NewEntry(t(it), EntryStatus.MATCHED, "Song $it", "A", null) })
        val vm = PlaylistViewModel(id, repo, commands, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        eventually { vm.state.value.playlist?.takeIf { it.entries.size == 6 } }
        vm.onMove(4, 1)
        val order = eventually { repo.playlist(id).first()!!.entries.map { it.entryId }.takeIf { it[1] == ids[4] } }
        assertEquals(listOf(ids[0], ids[4], ids[1], ids[2], ids[3], ids[5]), order)
    }

    @Test fun deleteNeedsConfirmation() = runBlocking {
        val id = commands.createPlaylist("P")
        val vm = PlaylistViewModel(id, repo, commands, CoroutineScope(SupervisorJob() + Dispatchers.Default))
        vm.onDelete()
        delay(200)
        assertNotNull(repo.playlist(id).first())
        assertEquals(true, vm.state.value.confirmingDelete)
        vm.onDeleteConfirmed()
        eventually { repo.playlist(id).first().let { if (it == null) Unit else null } }
        assertNull(repo.playlist(id).first())
    }
}
