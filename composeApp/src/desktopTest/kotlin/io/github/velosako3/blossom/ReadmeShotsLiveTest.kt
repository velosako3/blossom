// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.takahirom.roborazzi.captureRoboImage
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.navigation.Destination
import io.github.velosako3.blossom.navigation.Navigator
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

/**
 * The README's desktop pictures: the real app (live services, libVLC) off-screen with full glass, playing a song with
 * word-synced lyrics. Writes docs/screenshots/readme/desktop_*.png, which go into the README as 1200 px JPEGs (the Android
 * pictures are emulator screenshots, 540 px wide). Run with -PliveTests=true -Proborazzi.test.record=true.
 */
@OptIn(ExperimentalTestApi::class)
class ReadmeShotsLiveTest {
    @Test fun readmePictures() = runDesktopComposeUiTest(1440, 900, testTimeout = 4.minutes) {
        resetTestDataDir()
        val graph = createDesktopGraph()
        graph.preferences.onboarded.set(true)
        val navigator = Navigator()
        setContent { App(navigator = navigator, graph = graph, glassQuality = GlassQuality.FULL) }
        fun shot(name: String) = onRoot().captureRoboImage("../docs/screenshots/readme/desktop_$name.png")
        fun pump(ms: Long) { repeat((ms / 250).toInt().coerceAtLeast(1)) { mainClock.advanceTimeByFrame(); Thread.sleep(250) } }
        fun waitFor(timeoutMs: Long = 45_000, what: String, cond: () -> Boolean) {
            val end = System.currentTimeMillis() + timeoutMs
            while (!runCatching(cond).getOrDefault(false)) { check(System.currentTimeMillis() < end) { "timed out waiting for $what" }; pump(200) }
        }
        mainClock.autoAdvance = false

        waitFor(what = "home feed") { graph.home.state.value is io.github.velosako3.blossom.feature.home.HomeState.Loaded }
        val songs = kotlinx.coroutines.runBlocking { graph.ytm.search(System.getProperty("blossom.readmeSong") ?: "The Weeknd Blinding Lights").songs }
        println("readme: ${songs.size} songs, first ${songs.firstOrNull()?.title}")
        kotlinx.coroutines.runBlocking { graph.player.setQueue(songs.take(5), 0); graph.player.play() }
        pump(8000); println("readme: state ${graph.player.state.value.let { "playing=${it.isPlaying} buffering=${it.isBuffering} error=${it.error}" }} at ${graph.player.progress.value.positionMs} ms")
        waitFor(what = "playback") { graph.player.progress.value.positionMs > 2_000 }
        graph.player.seekTo(58_000)
        waitFor(what = "the seek") { graph.player.progress.value.positionMs in 59_000..70_000 }
        println("readme playing: ${graph.player.state.value.current?.title} – ${graph.player.state.value.current?.artistNames}")
        pump(5000); shot("home")
        navigator.navigate(Destination.NowPlaying)
        pump(9000); shot("now_playing")
        graph.player.pause()
    }
}
