// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.importer.ui

import io.github.velosako3.blossom.core.database.LibraryCommands
import io.github.velosako3.blossom.core.database.LibraryProjector
import io.github.velosako3.blossom.core.database.SqlLibraryRepository
import io.github.velosako3.blossom.core.database.SqlOpLog
import io.github.velosako3.blossom.core.database.fileDatabase
import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.PlaylistOrigin
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.core.model.sync.HybridLogicalClock
import io.github.velosako3.blossom.feature.importer.ImportJobRunner
import io.github.velosako3.blossom.feature.importer.ImportService
import io.github.velosako3.blossom.feature.importer.ImportTarget
import io.github.velosako3.blossom.feature.importer.LinkKind
import io.github.velosako3.blossom.source.api.ImportedPlaylist
import io.github.velosako3.blossom.source.api.ImportedTrack
import io.github.velosako3.blossom.source.api.PlaylistImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock

class ImportViewModelTest {
    private val root = kotlin.io.path.createTempDirectory("blossom-importvm").toFile()
    private val db = fileDatabase(File(root, "blossom.db"))
    private val commands = LibraryCommands(SqlOpLog(db, "A", HybridLogicalClock("A", Clock.System)), LibraryProjector(db), db)
    private val repo = SqlLibraryRepository(db)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val url = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"
    private val targets = mutableListOf<ImportTarget>()
    private val importer = object : PlaylistImporter {
        override fun canHandle(url: String) = true
        override suspend fun read(url: String, onProgress: (Int, Int?) -> Unit) = ImportedPlaylist("Today's Hits", null, null,
            PlaylistOrigin(null, "Spotify", url),
            (0 until 30).map { i -> ImportedTrack("Song $i", listOf("A"), 180_000, null, Track(TrackId.of(SourceKind.YOUTUBE_MUSIC, "v$i"), "Song $i", listOf(ArtistRef(null, "A")), null, 180_000, null)) })
    }
    private val runner = ImportJobRunner(db, commands, repo, mapOf(ImportService.SPOTIFY to importer), search = { emptyList() }, scope = scope, batchSize = 5)
    private val vm = ImportViewModel(runner, repo, scope) { targets += it }

    @AfterTest fun cleanup() { scope.cancel(); root.deleteRecursively() }

    @Test fun pastingASpotifyLinkIsDetected() {
        vm.onTextChange("  $url?si=abc ")
        val s = assertIs<ImportUi.Detected>(vm.state.value)
        assertEquals(ImportService.SPOTIFY, s.link.service)
        assertEquals(LinkKind.PLAYLIST, s.link.kind)
        vm.onTextChange("not a link")
        assertIs<ImportUi.NotALink>(vm.state.value)
        assertEquals(url, vm.clipboardOffer("$url?si=x")?.canonicalUrl)
    }

    @Test fun progressStreamsThroughToDone(): Unit = runBlocking {
        vm.onTextChange(url)
        vm.start()
        val seen = mutableListOf<Int>()
        val done = withTimeout(20_000) {
            vm.state.first { s ->
                if (s is ImportUi.InProgress) s.progress?.done?.let { seen += it }
                s is ImportUi.Done
            }
        } as ImportUi.Done
        assertEquals(30, repo.playlist(done.playlistId).first()!!.entries.size)
        assertEquals("Today's Hits", repo.playlist(done.playlistId).first()!!.name)
        assertTrue(seen.isNotEmpty(), "in-progress states were seen")
    }

    @Test fun duplicateOriginAsksAndUpdates(): Unit = runBlocking {
        val existing = commands.createPlaylist("Already imported", origin = PlaylistOrigin(null, "Spotify", url))
        vm.onTextChange(url)
        vm.start()
        val dup = withTimeout(5_000) { vm.state.first { it is ImportUi.DuplicateFound } } as ImportUi.DuplicateFound
        assertEquals(existing, dup.existing)
        assertEquals("Already imported", dup.existingName)
        vm.updateExisting()
        withTimeout(20_000) { vm.state.first { it is ImportUi.Done } }
        assertEquals(listOf<ImportTarget>(ImportTarget.UpdateExisting(existing)), targets)
        assertEquals(30, repo.playlist(existing).first()!!.entries.size)
    }
}
