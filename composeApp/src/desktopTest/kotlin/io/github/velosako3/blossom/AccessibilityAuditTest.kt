// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.navigation.Destination
import io.github.velosako3.blossom.navigation.Navigator
import kotlin.test.Test
import kotlin.test.assertTrue

/** Every clickable thing on every screen says what it is: visible text or a content description. */
@OptIn(ExperimentalTestApi::class)
class AccessibilityAuditTest {
    private fun audit(width: Int, height: Int) = runDesktopComposeUiTest(width, height) {
        val t = TestGraph(withSync = true)
        val navigator = Navigator(Destination.Home)
        setContent { App(navigator = navigator, graph = t.graph, glassQuality = GlassQuality.FULL, reduceMotion = true) }
        val screens = listOf(
            Destination.Home, Destination.Search, Destination.Library, Destination.Playlist(t.playlist.value), Destination.Liked,
            Destination.NowPlaying, Destination.Stats, Destination.Settings, Destination.Import(), Destination.LocalFiles,
            Destination.Sync, Destination.Equalizer, Destination.Licenses, Destination.PairDevice,
        )
        val problems = mutableListOf<String>()
        val unlabeled = SemanticsMatcher("clickable without a label") { node ->
            val c = node.config
            c.getOrNull(SemanticsActions.OnClick) != null &&
                c.getOrNull(SemanticsProperties.Text).isNullOrEmpty() &&
                c.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() &&
                c.getOrNull(SemanticsProperties.EditableText) == null
        }
        for (screen in screens) {
            runOnIdle { navigator.navigate(screen) }
            waitForIdle()
            onAllNodes(unlabeled).fetchSemanticsNodes().forEach { n ->
                problems += "$screen: ${n.config.getOrNull(SemanticsProperties.TestTag) ?: "-"} role=${n.config.getOrNull(SemanticsProperties.Role)} at ${n.boundsInRoot}"
            }
        }
        assertTrue(problems.isEmpty(), "unlabeled clickable nodes (${width}x$height):\n" + problems.joinToString("\n"))
    }

    @Test fun compact() = audit(412, 915)
    @Test fun expanded() = audit(1440, 900)
}
