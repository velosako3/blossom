// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.takahirom.roborazzi.captureRoboImage
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.feature.importer.ui.ImportUi
import io.github.velosako3.blossom.navigation.Destination
import io.github.velosako3.blossom.navigation.Navigator
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** A public Spotify album through the real Import screen (no sign-in), matched on YouTube Music. Live: -PliveTests=true. */
@OptIn(ExperimentalTestApi::class)
class DesktopImportLiveTest {
    @Test fun importSpotifyAlbum() = runDesktopComposeUiTest(1440, 900, testTimeout = 5.minutes) {
        resetTestDataDir()
        val graph = createDesktopGraph()
        val navigator = Navigator()
        setContent { App(navigator = navigator, graph = graph, glassQuality = GlassQuality.FULL, reduceMotion = true) }
        fun pump(ms: Long) { repeat((ms / 250).toInt().coerceAtLeast(1)) { mainClock.advanceTimeByFrame(); Thread.sleep(250) } }
        mainClock.autoAdvance = false
        navigator.navigate(Destination.Import("https://open.spotify.com/album/4LH4d3cOWNNsVw41Gqt2kv?si=share"))
        pump(1500)
        onAllNodes(hasText("Save as new playlist"))[0].performClick()
        val end = System.currentTimeMillis() + 180_000
        while (graph.importVm.state.value !is ImportUi.Done && graph.importVm.state.value !is ImportUi.Failed) {
            check(System.currentTimeMillis() < end) { "import timed out: ${graph.importVm.state.value}" }
            pump(500)
        }
        pump(1500)
        onRoot().captureRoboImage("src/desktopTest/snapshots/e2e/12_import_done.png")
        val done = graph.importVm.state.value as ImportUi.Done
        println("import live: ${done.progress.total} tracks, ${done.progress.matched} matched, ${done.progress.uncertain} uncertain, ${done.progress.unavailable} not found")
        assertTrue(done.progress.matched > 0)
    }
}
