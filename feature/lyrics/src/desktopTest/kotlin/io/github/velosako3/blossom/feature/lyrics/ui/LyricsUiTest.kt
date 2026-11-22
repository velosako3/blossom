// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics.ui

import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.feature.lyrics.ui.panel.SyncedLyricsPanel
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class LyricsUiTest {
    @Test fun tappingALineSeeksToIt() = runComposeUiTest {
        var seek = -1L
        setContent {
            BlossomTheme(true, ArtworkPalette.Default, glassQuality = GlassQuality.FULL, reduceMotion = true) {
                SyncedLyricsPanel(lineSyncedLines, "t", positionAt(2_500), looking = false, isPlaying = false, onSeekToLine = { seek = it }, modifier = Modifier.fillMaxSize())
            }
        }
        onNodeWithText(sentences[2]).performClick()
        assertEquals(lineSyncedLines[2].timeMs, seek)
    }
}
