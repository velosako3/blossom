// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.takahirom.roborazzi.captureRoboImage
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.navigation.Destination
import io.github.velosako3.blossom.navigation.Navigator
import io.github.velosako3.blossom.shell.NAV_ITEM_TAG
import io.github.velosako3.blossom.shell.SIDEBAR_TAG
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ShellSnapshotTest {
    @Test fun shell_compact_412x915() = runDesktopComposeUiTest(412, 915) {
        setContent { App(reduceMotion = true, glassQuality = GlassQuality.FULL) }
        waitForIdle()
        onRoot().captureRoboImage("src/desktopTest/snapshots/shell_compact_412x915.png")
    }

    @Test fun shell_expanded_1440x900() = runDesktopComposeUiTest(1440, 900) {
        setContent { App(reduceMotion = true, glassQuality = GlassQuality.FULL) }
        waitForIdle()
        onRoot().captureRoboImage("src/desktopTest/snapshots/shell_expanded_1440x900.png")
    }

    @Test fun compactShellHasFourLabelledTabs() = runDesktopComposeUiTest(412, 915) {
        setContent { App(reduceMotion = true, glassQuality = GlassQuality.FULL) }
        val tabs = onAllNodesWithTag(NAV_ITEM_TAG)
        tabs.assertCountEquals(4)
        listOf("Home", "Search", "Library", "Stats").forEachIndexed { i, label ->
            assertEquals(true, hasText(label).matches(tabs[i].fetchSemanticsNode()), "tab $i should be $label")
        }
    }

    @Test fun expandedSidebarNavigates() = runDesktopComposeUiTest(1440, 900) {
        val navigator = Navigator()
        setContent { App(navigator = navigator, reduceMotion = true, glassQuality = GlassQuality.FULL) }
        onNodeWithTag(SIDEBAR_TAG).assertIsDisplayed()
        onNodeWithText("Library", useUnmergedTree = true).performClick()
        waitForIdle()
        assertEquals(Destination.Library, navigator.current)
    }
}
