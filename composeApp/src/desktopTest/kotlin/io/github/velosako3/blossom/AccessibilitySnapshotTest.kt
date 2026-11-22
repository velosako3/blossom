// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.takahirom.roborazzi.captureRoboImage
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.navigation.Destination
import io.github.velosako3.blossom.navigation.Navigator
import kotlin.test.Test

/** The main screens with text at 200 %: nothing may be clipped or overlap (reviewed by eye). */
@OptIn(ExperimentalTestApi::class)
class AccessibilitySnapshotTest {
    private fun shots(width: Int, height: Int, name: String) = runDesktopComposeUiTest(width, height) {
        val t = TestGraph()
        val navigator = Navigator(Destination.Home)
        setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale = 2f)) {
                App(navigator = navigator, graph = t.graph, glassQuality = GlassQuality.FULL, reduceMotion = true)
            }
        }
        mainClock.advanceTimeBy(7_000) // past the offline notice
        for ((screen, file) in listOf(Destination.Home to "home", Destination.Playlist(t.playlist.value) to "playlist", Destination.NowPlaying to "player", Destination.Stats to "stats")) {
            runOnIdle { navigator.navigate(screen) }
            waitForIdle()
            if (screen == Destination.NowPlaying && width >= 1000) { // only the wide player shows lyrics
                waitUntil(timeoutMillis = 10_000) { t.graph.lyrics.state.value is io.github.velosako3.blossom.feature.lyrics.LyricsUi.Ready }
                waitForIdle()
            }
            onRoot().captureRoboImage("src/desktopTest/snapshots/a11y/${file}_${name}_font2.png")
        }
    }

    @Test fun compact() = shots(412, 915, "compact")
    @Test fun expanded() = shots(1440, 900, "expanded")
}
