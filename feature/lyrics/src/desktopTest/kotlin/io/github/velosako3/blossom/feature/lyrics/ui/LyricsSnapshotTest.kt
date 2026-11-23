// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.takahirom.roborazzi.captureRoboImage
import io.github.velosako3.blossom.core.designsystem.background.BlossomBackground
import io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.core.designsystem.glass.glassBackdropSource
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.feature.lyrics.providers.LyricLine
import io.github.velosako3.blossom.feature.lyrics.ui.panel.PLAYER_GUTTER
import io.github.velosako3.blossom.feature.lyrics.ui.panel.PlayerSettings
import io.github.velosako3.blossom.feature.lyrics.ui.panel.SyncedLyricsPanel
import kotlin.test.Test

private val palette = ArtworkPalette.Default

@OptIn(ExperimentalTestApi::class)
private fun shot(name: String, reduceMotion: Boolean = false, content: @Composable () -> Unit) = runDesktopComposeUiTest(412, 760) {
    PlayerSettings.reduceAnimation.value = reduceMotion
    mainClock.autoAdvance = false
    setContent {
        BlossomTheme(true, palette, glassQuality = GlassQuality.FULL, reduceMotion = reduceMotion) {
            Box(Modifier.fillMaxSize()) {
                BlossomBackground(palette, Modifier.fillMaxSize().glassBackdropSource())
                content()
            }
        }
    }
    repeat(20) { mainClock.advanceTimeBy(100) }
    onRoot().captureRoboImage("src/desktopTest/snapshots/$name.png")
    PlayerSettings.reduceAnimation.value = false
}

@Composable
private fun panel(lines: List<LyricLine>, at: Long, looking: Boolean = false) =
    SyncedLyricsPanel(lines, "t", positionAt(at), looking = looking, isPlaying = false, onSeekToLine = {}, modifier = Modifier.fillMaxSize().padding(horizontal = PLAYER_GUTTER))

/** BitChord's panel, paused (its clock holds the reported position, so frames are deterministic). */
class LyricsSnapshotTest {
    // Line 4 starts at 18 s; its second word runs 18.8–19.6 s.
    @Test fun lyrics_wordSynced_midWord() = shot("lyrics_wordSynced_midWord") { panel(wordSyncedLines, 19_200) }
    @Test fun lyrics_lineSynced() = shot("lyrics_lineSynced") { panel(lineSyncedLines, 19_200) }
    @Test fun lyrics_plain() = shot("lyrics_plain") { panel(plainLines, 0) }
    @Test fun lyrics_reduceMotion() = shot("lyrics_reduceMotion", reduceMotion = true) { panel(wordSyncedLines, 19_200) }
    @Test fun lyrics_looking() = shot("lyrics_looking") { panel(emptyList(), 0, looking = true) }
    @Test fun lyrics_none() = shot("lyrics_none") { panel(emptyList(), 0) }
}
