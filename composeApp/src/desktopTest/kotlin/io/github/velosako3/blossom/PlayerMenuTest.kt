// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.core.player.SleepState
import io.github.velosako3.blossom.navigation.Destination
import io.github.velosako3.blossom.navigation.Navigator
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class PlayerMenuTest {
    /** The player menu's "Sleep timer" opens its dialog, and a choice starts the timer. */
    @Test fun sleepTimerFromThePlayerMenu() = runDesktopComposeUiTest(412, 915) {
        val t = TestGraph()
        setContent { App(navigator = Navigator(Destination.NowPlaying), graph = t.graph, glassQuality = GlassQuality.FULL, reduceMotion = true) }
        waitForIdle()
        onAllNodesWithContentDescription("More")[0].performClick(); waitForIdle()
        onNodeWithText("Sleep timer").performClick(); waitForIdle()
        onNodeWithText("End of song").performClick(); waitForIdle()
        assertEquals(SleepState.EndOfTrack, t.graph.sleepTimer.state.value)
    }
}
