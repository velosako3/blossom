// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.importer.ui

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
import io.github.velosako3.blossom.core.database.SqlOpLog
import io.github.velosako3.blossom.core.database.inMemoryDatabase
import io.github.velosako3.blossom.core.designsystem.background.BlossomBackground
import io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.core.designsystem.glass.glassBackdropSource
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.EntryStatus
import io.github.velosako3.blossom.core.model.PlaylistEntry
import io.github.velosako3.blossom.core.model.PlaylistId
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.core.model.sync.HybridLogicalClock
import io.github.velosako3.blossom.feature.importer.DetectedLink
import io.github.velosako3.blossom.feature.importer.FindReplacement
import io.github.velosako3.blossom.feature.importer.ImportJobId
import io.github.velosako3.blossom.feature.importer.ImportJobState
import io.github.velosako3.blossom.feature.importer.ImportProgress
import io.github.velosako3.blossom.feature.importer.ImportService
import io.github.velosako3.blossom.feature.importer.LinkKind
import io.github.velosako3.blossom.feature.importer.MatchFixSheet
import io.github.velosako3.blossom.source.api.AudioQuality
import io.github.velosako3.blossom.source.api.MusicSource
import io.github.velosako3.blossom.source.api.SearchFilter
import io.github.velosako3.blossom.source.api.SearchResults
import io.github.velosako3.blossom.source.api.StreamInfo
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant

private val palette = ArtworkPalette.Default
private fun song(id: String, title: String, artist: String, kind: SourceKind = SourceKind.YOUTUBE_MUSIC) =
    Track(TrackId.of(kind, id), title, listOf(ArtistRef(null, artist)), null, 200_000, null)
private val at = Instant.fromEpochSeconds(1_800_000_000)
private fun entry(i: Int, title: String, artist: String, status: EntryStatus) =
    PlaylistEntry("e$i", if (status == EntryStatus.UNAVAILABLE) null else song("v$i", title, artist), status, title, artist, "V$i", at, null)

private val entries = listOf(
    entry(1, "Blinding Lights", "The Weeknd", EntryStatus.MATCHED),
    entry(2, "Levitating", "Dua Lipa", EntryStatus.MATCHED),
    entry(3, "Heat Waves", "Glass Animals", EntryStatus.UNCERTAIN),
    entry(4, "Stay", "The Kid LAROI, Justin Bieber", EntryStatus.MATCHED),
    entry(5, "Unreleased Demo", "Some Artist", EntryStatus.UNAVAILABLE),
    entry(6, "Good 4 U", "Olivia Rodrigo", EntryStatus.MATCHED),
)

private val noActions = ImportActions({}, {}, {}, {}, { _, _, _ -> })

@OptIn(ExperimentalTestApi::class)
private fun shot(name: String, content: @Composable () -> Unit) = runDesktopComposeUiTest(412, 915) {
    setContent {
        BlossomTheme(true, palette, glassQuality = GlassQuality.FULL, reduceMotion = true) {
            Box(Modifier.fillMaxSize()) {
                BlossomBackground(palette, Modifier.fillMaxSize().glassBackdropSource())
                content()
            }
        }
    }
    waitForIdle()
    onRoot().captureRoboImage("src/desktopTest/snapshots/$name.png")
}

class ImportSnapshotTest {
    private val link = DetectedLink(ImportService.SPOTIFY, LinkKind.PLAYLIST, "https://open.spotify.com/playlist/x")

    @Test fun import_inProgress() = shot("import_inProgress") {
        val progress = ImportProgress(ImportJobState.MATCHING, 300, 142, 133, 6, 3, PlaylistId("p"))
        ImportContent(ImportUi.InProgress(link, ImportJobId("j"), progress, at), "", null, entries, emptyList(),
            {}, { _, _ -> }, {}, {}, {}, {}, noActions)
    }

    @Test fun import_matchFix() = shot("import_matchFix") {
        val db = inMemoryDatabase()
        val commands = LibraryCommands(SqlOpLog(db, "A", HybridLogicalClock("A", Clock.System)), LibraryProjector(db), db)
        val source = object : MusicSource {
            override val kind = SourceKind.YOUTUBE_MUSIC
            override suspend fun search(query: String, filter: SearchFilter) = SearchResults(listOf(
                song("a", "Heat Waves", "Glass Animals"), song("b", "Heat Waves (Slowed)", "Glass Animals"), song("c", "Heat Waves (Live)", "Glass Animals"),
            ))
            override suspend fun track(id: TrackId) = null
            override suspend fun resolveStream(id: TrackId, quality: AudioQuality): StreamInfo = error("unused")
        }
        ImportContent(ImportUi.InProgress(link, ImportJobId("j"), ImportProgress(ImportJobState.MATCHING, 300, 142, 133, 6, 3, PlaylistId("p")), at),
            "", null, entries, emptyList(), {}, { _, _ -> }, {}, {}, {}, {}, noActions)
        MatchFixSheet(entries[2], FindReplacement(listOf(source), commands), {}, {})
    }
}
