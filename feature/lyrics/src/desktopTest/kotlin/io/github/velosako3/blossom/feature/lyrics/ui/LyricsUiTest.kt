// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.feature.lyrics.LyricLine
import io.github.velosako3.blossom.feature.lyrics.Lyrics
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class LyricsUiTest {
    @Test fun tappingALineSeeksToIt() = runComposeUiTest {
        val lines = (0 until 8).map { LyricLine(it * 4_000L + 1_000, (it + 1) * 4_000L + 1_000, "Line number $it", emptyList()) }
        var seek = -1L
        setContent {
            BlossomTheme(true, ArtworkPalette.Default, glassQuality = GlassQuality.FULL, reduceMotion = true) {
                LyricsView(Lyrics.LineSynced(lines, "t"), positionMs = 1_500, onSeek = { seek = it })
            }
        }
        onNodeWithTag(lyricLineTag(3)).assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Button))
        onNodeWithTag(lyricLineTag(3)).performClick()
        assertEquals(lines[3].startMs, seek)
    }
}
