// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.takahirom.roborazzi.captureRoboImage
import io.github.velosako3.blossom.core.database.LibraryCommands
import io.github.velosako3.blossom.core.database.LibraryProjector
import io.github.velosako3.blossom.core.database.NewEntry
import io.github.velosako3.blossom.core.database.SqlLibraryRepository
import io.github.velosako3.blossom.core.database.SqlOpLog
import io.github.velosako3.blossom.core.database.inMemoryDatabase
import io.github.velosako3.blossom.core.designsystem.background.BlossomBackground
import io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.core.designsystem.glass.glassBackdropSource
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.EntryStatus
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.core.model.sync.HybridLogicalClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.time.Clock

@OptIn(ExperimentalTestApi::class)
class LibrarySnapshotTest {
    private val db = inMemoryDatabase()
    private val commands = LibraryCommands(SqlOpLog(db, "A", HybridLogicalClock("A", Clock.System)), LibraryProjector(db), db)
    private val repo = SqlLibraryRepository(db)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun shot(name: String, w: Int, h: Int, content: @Composable () -> Unit) = runDesktopComposeUiTest(w, h) {
        setContent {
            BlossomTheme(true, ArtworkPalette.Default, glassQuality = GlassQuality.FULL, reduceMotion = true) {
                Box(Modifier.fillMaxSize()) { BlossomBackground(ArtworkPalette.Default, Modifier.fillMaxSize().glassBackdropSource()); content() }
            }
        }
        Thread.sleep(600); waitForIdle()
        onRoot().captureRoboImage("src/desktopTest/snapshots/$name.png")
    }

    private fun seeded() = runBlocking {
        val id = commands.createPlaylist("Late night drive", "Synths for empty roads")
        val names = listOf("Nightcall" to "Kavinsky", "A Real Hero" to "College", "Midnight City" to "M83", "Tenebre Rosso Sangue" to "KEYGEN CHURCH", "Resonance" to "HOME", "Odd Look" to "Kavinsky")
        commands.addEntries(id, names.mapIndexed { i, (t, a) ->
            val status = when (i) { 3 -> EntryStatus.UNCERTAIN; 5 -> EntryStatus.UNAVAILABLE; else -> EntryStatus.MATCHED }
            NewEntry(if (status == EntryStatus.UNAVAILABLE) null else Track(TrackId.of(SourceKind.YOUTUBE_MUSIC, "v$i"), t, listOf(ArtistRef(null, a)), null, 200_000L + i * 13_000, null), status, t, a, null)
        })
        id
    }

    @Test fun library_empty() = shot("library_empty", 412, 915) {
        LibraryScreen(LibraryViewModel(repo, commands, scope), {}, { _, _, _ -> }, {}, onSettings = {})
    }

    @Test fun playlist_compact() {
        val id = seeded()
        shot("playlist_compact", 412, 915) { PlaylistScreen(PlaylistViewModel(id, repo, commands, scope), { _, _, _ -> }, {}, {}) }
    }

    @Test fun playlist_expanded() {
        val id = seeded()
        shot("playlist_expanded", 1100, 800) { PlaylistScreen(PlaylistViewModel(id, repo, commands, scope), { _, _, _ -> }, {}, {}) }
    }
}
