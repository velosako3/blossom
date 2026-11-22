// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.stats.wrapped

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.input.key.Key
import io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class WrappedScreenTest {
    private fun fills(test: androidx.compose.ui.test.ComposeUiTest) =
        test.onAllNodesWithTag(STORY_SEGMENT_TAG).fetchSemanticsNodes().map { it.config[SemanticsProperties.ProgressBarRangeInfo].current }

    @Test fun lastCardStaysFullAfterNext() = runDesktopComposeUiTest(360, 640) {
        mainClock.autoAdvance = false
        setContent { BlossomTheme(true, ArtworkPalette.Default, glassQuality = GlassQuality.FULL, reduceMotion = true) { WrappedScreen(listOf(WrappedCard.Streak(3), WrappedCard.Streak(4)), {}) } }
        mainClock.advanceTimeBy(13_000)
        assertEquals(listOf(1f, 1f), fills(this))
        onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        mainClock.advanceTimeBy(500)
        assertEquals(listOf(1f, 1f), fills(this))
    }

    @Test fun backThenNextShowsEachCardFully() = runDesktopComposeUiTest(360, 640) {
        mainClock.autoAdvance = false
        setContent { BlossomTheme(true, ArtworkPalette.Default, glassQuality = GlassQuality.FULL, reduceMotion = true) { WrappedScreen(listOf(WrappedCard.Streak(3), WrappedCard.Streak(4), WrappedCard.Streak(5)), {}) } }
        mainClock.advanceTimeBy(7_000) // on card 2
        onRoot().performKeyInput { pressKey(Key.DirectionRight) } // card 3
        mainClock.advanceTimeBy(1_000)
        val f = fills(this)
        assertEquals(1f, f[0]); assertEquals(1f, f[1]); assertEquals(true, f[2] in 0.1f..0.3f, "$f")
    }
}
