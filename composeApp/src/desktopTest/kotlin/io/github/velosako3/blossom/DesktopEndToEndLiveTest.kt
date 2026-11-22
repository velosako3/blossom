// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.takahirom.roborazzi.captureRoboImage
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.navigation.Navigator
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * The real desktop app (live YouTube Music, bundled libVLC, real audio), driven off-screen: search → play → seek →
 * next. Screenshots land in src/desktopTest/snapshots/e2e/. Live: runs only with -PliveTests=true.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopEndToEndLiveTest {
    @Test fun searchPlaySeekNext() = runDesktopComposeUiTest(1440, 900, testTimeout = 5.minutes) {
        resetTestDataDir()
        val graph = createDesktopGraph()
        val navigator = Navigator()
        setContent { App(navigator = navigator, graph = graph, glassQuality = GlassQuality.FULL, reduceMotion = true) }
        fun shot(name: String) = onRoot().captureRoboImage("src/desktopTest/snapshots/e2e/$name.png")
        // Spinners animate forever, so never wait for idle: advance the clock by hand while real I/O runs.
        fun pump(ms: Long) { repeat((ms / 250).toInt().coerceAtLeast(1)) { mainClock.advanceTimeByFrame(); Thread.sleep(250) } }
        fun waitFor(timeoutMs: Long = 45_000, what: String, cond: () -> Boolean) {
            val end = System.currentTimeMillis() + timeoutMs
            while (!runCatching(cond).getOrDefault(false)) { check(System.currentTimeMillis() < end) { "timed out waiting for $what" }; pump(200) }
        }
        mainClock.autoAdvance = false
        waitFor(what = "home feed") { graph.home.state.value is io.github.velosako3.blossom.feature.home.HomeState.Loaded }
        pump(4000); shot("01_home")

        onAllNodesWithText("Search")[0].performClick()
        pump(800)
        onNode(hasSetTextAction()).performTextInput("Daft Punk One More Time")
        waitFor(what = "results") { onAllNodes(hasText("One More Time", substring = true)).fetchSemanticsNodes().size > 2 }
        pump(2000); shot("02_search")

        onAllNodes(hasText("One More Time", substring = true))[1].performClick()
        waitFor(what = "playback") { graph.player.progress.value.positionMs > 3_000 }
        val playing = graph.player.state.value
        println("e2e playing: ${playing.current?.title} – ${playing.current?.artistNames} at ${graph.player.progress.value.positionMs} ms")
        pump(1000); shot("03_playing")

        graph.player.seekTo(120_000)
        waitFor(what = "seek") { graph.player.progress.value.positionMs in 120_000..140_000 }
        println("e2e after seek: ${graph.player.progress.value.positionMs} ms")

        val before = graph.player.state.value.currentIndex
        graph.player.next()
        waitFor(what = "next track") { graph.player.state.value.currentIndex == before + 1 && graph.player.progress.value.positionMs in 2_000..20_000 }
        println("e2e next: ${graph.player.state.value.current?.title} at ${graph.player.progress.value.positionMs} ms")
        navigator.navigate(io.github.velosako3.blossom.navigation.Destination.NowPlaying)
        pump(2000); shot("04_now_playing")
        graph.player.pause()
        assertTrue(graph.player.state.value.current != null)

        // Library: like the song and put it in a new playlist.
        val song = graph.player.state.value.current!!
        kotlinx.coroutines.runBlocking { graph.commands.like(song) }
        graph.library.createWith("blossom e2e", listOf(song))
        waitFor(what = "playlist") { graph.library.playlists.value?.any { it.name == "blossom e2e" && it.entryCount == 1 } == true }
        val liked = kotlinx.coroutines.runBlocking { graph.repo.liked().first() }
        println("e2e library: liked=${liked.map { it.title }} playlists=${graph.library.playlists.value?.map { it.name to it.entryCount }}")
        navigator.navigate(io.github.velosako3.blossom.navigation.Destination.Library)
        pump(2000); shot("05_library")
        val pid = graph.library.playlists.value!!.first { it.name == "blossom e2e" }.id
        navigator.navigate(io.github.velosako3.blossom.navigation.Destination.Playlist(pid.value))
        pump(2000); shot("06_playlist")
    }
}
