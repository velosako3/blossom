// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.navigation.Destination
import io.github.velosako3.blossom.navigation.Navigator
import kotlin.test.Test
import kotlin.test.assertTrue

/** Tab from launch walks the window in reading order and reaches every part of it; Shift+Tab walks back. */
@OptIn(ExperimentalTestApi::class)
class KeyboardNavigationTest {
    private fun SemanticsNode.label(): String = listOfNotNull(
        config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(),
        config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text },
        config.getOrNull(SemanticsProperties.EditableText)?.let { "field:$it" },
        config.getOrNull(SemanticsProperties.TestTag),
    ).joinToString(" | ").ifEmpty { "?" }

    @Test fun tabReachesSearchNavAndPlayer() = runDesktopComposeUiTest(1440, 900) {
        val t = TestGraph()
        setContent { App(navigator = Navigator(Destination.Home), graph = t.graph, glassQuality = GlassQuality.FULL, reduceMotion = true) }
        waitForIdle()
        val seen = mutableListOf<String>()
        repeat(80) {
            onRoot().performKeyInput { pressKey(Key.Tab) }
            waitForIdle()
            onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull()?.let { seen += it.label() }
        }
        println("tab order: " + seen.distinct().joinToString(" → "))
        fun first(what: String, match: (String) -> Boolean) = seen.indexOfFirst(match).also { assertTrue(it >= 0, "Tab never reached $what. Order: ${seen.distinct()}") }
        val search = first("the search field") { it.contains("field:") || it.contains("Search", true) }
        val nav = first("the nav items") { it.contains("Library") }
        val player = first("the player controls") { it.contains("Pause") || it.contains("Play") }
        // Reading order: navigation and search before the player bar at the bottom.
        assertTrue(nav < player && search < player, "order: ${seen.distinct()}")
        // Shift+Tab goes back to where it came from.
        val before = onAllNodes(isFocused()).fetchSemanticsNodes().first().label()
        onRoot().performKeyInput { pressKey(Key.Tab) }; waitForIdle()
        onRoot().performKeyInput { keyDown(Key.ShiftLeft); pressKey(Key.Tab); keyUp(Key.ShiftLeft) }; waitForIdle()
        assertTrue(onAllNodes(isFocused()).fetchSemanticsNodes().first().label() == before, "Shift+Tab didn't go back")
    }
}
