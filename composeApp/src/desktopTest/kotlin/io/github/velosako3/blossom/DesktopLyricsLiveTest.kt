// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.takahirom.roborazzi.captureRoboImage
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.feature.lyrics.Lyrics
import io.github.velosako3.blossom.feature.lyrics.LyricsUi
import io.github.velosako3.blossom.navigation.Navigator
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Real app, off-screen: play a song, open the lyrics panel (full motion) and check the lyrics arrive and follow the
 * song. Frame cost is measured separately in feature/lyrics' LyricsFrameTimeLiveTest. Live: -PliveTests=true.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopLyricsLiveTest {
    @Test fun lyricsPanelFollowsPlayback() = runDesktopComposeUiTest(1440, 900, testTimeout = 5.minutes) {
        System.getProperty("blossom.dataDir")?.let { java.io.File(it).deleteRecursively() }
        val graph = createDesktopGraph()
        setContent { App(navigator = Navigator(), graph = graph, glassQuality = GlassQuality.FULL, reduceMotion = false) }
        fun shot(name: String) = onRoot().captureRoboImage("src/desktopTest/snapshots/e2e/$name.png")
        fun pump(ms: Long) { repeat((ms / 250).toInt().coerceAtLeast(1)) { mainClock.advanceTimeByFrame(); Thread.sleep(250) } }
        fun waitFor(timeoutMs: Long = 60_000, what: String, cond: () -> Boolean) {
            val end = System.currentTimeMillis() + timeoutMs
            while (!runCatching(cond).getOrDefault(false)) { check(System.currentTimeMillis() < end) { "timed out waiting for $what" }; pump(200) }
        }
        mainClock.autoAdvance = false
        pump(2000)
        onAllNodesWithText("Search")[0].performClick()
        pump(800)
        onNode(hasSetTextAction()).performTextInput("Kanye West Runaway")
        waitFor(what = "results") { onAllNodes(hasText("Runaway", substring = true)).fetchSemanticsNodes().size > 2 }
        onAllNodes(hasText("Runaway", substring = true))[1].performClick()
        waitFor(what = "playback") { graph.player.state.value.positionMs > 3_000 }
        onAllNodesWithContentDescription("Lyrics")[0].performClick()
        waitFor(what = "lyrics") { (graph.lyrics.state.value as? LyricsUi.Ready)?.lyrics != null }
        val lyrics = (graph.lyrics.state.value as LyricsUi.Ready).lyrics!!
        println("lyrics: ${lyrics::class.simpleName} from ${lyrics.provider}")
        graph.player.seekTo(60_000)
        waitFor(what = "seek") { graph.player.state.value.positionMs in 60_000..80_000 }

        // Test frames take ~0.7 s of wall time each while the song plays in real time, so line changes arrive faster
        // than the virtual clock can animate them. Pause, then give the springs ~1 s of frames to settle.
        pump(3000)
        graph.player.pause()
        repeat(60) { mainClock.advanceTimeByFrame() }
        shot("08_lyrics")
        assertTrue(lyrics is Lyrics.WordSynced || lyrics is Lyrics.LineSynced)
    }
}
