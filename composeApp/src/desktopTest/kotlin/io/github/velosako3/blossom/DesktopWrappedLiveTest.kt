// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.takahirom.roborazzi.captureRoboImage
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.core.model.PlayEvent
import io.github.velosako3.blossom.feature.stats.Period
import io.github.velosako3.blossom.navigation.Destination
import io.github.velosako3.blossom.navigation.Navigator
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** Wrapped in the real app with this week's plays: the top song plays from ~30 %, and closing restores playback. Live: -PliveTests=true. */
@OptIn(ExperimentalTestApi::class)
class DesktopWrappedLiveTest {
    @Test fun wrappedPlaysTopSongAndRestores() = runDesktopComposeUiTest(1440, 900, testTimeout = 5.minutes) {
        resetTestDataDir()
        val graph = createDesktopGraph()
        val songs = runBlocking { graph.ytm.search("Daft Punk Around the World").songs.take(3) }
        val now = Clock.System.now()
        runBlocking {
            repeat(12) { i ->
                graph.commands.recordPlay(PlayEvent("w$i", songs[if (i < 7) 0 else 1 + i % 2], now - (i + 1).hours, 200_000, true, graph.deviceId))
            }
            // Something already playing, to be restored afterwards.
            graph.player.setQueue(listOf(songs[2]), 0, startPositionMs = 10_000)
        }
        val navigator = Navigator()
        setContent { App(navigator = navigator, graph = graph, glassQuality = GlassQuality.FULL, reduceMotion = true) }
        fun pump(ms: Long) { repeat((ms / 250).toInt().coerceAtLeast(1)) { mainClock.advanceTimeByFrame(); Thread.sleep(250) } }
        mainClock.autoAdvance = false
        pump(1000)
        navigator.navigate(Destination.Wrapped(Period.weekContaining(now, graph.stats.zoneId).key))
        val end = System.currentTimeMillis() + 60_000
        while (graph.player.state.value.current?.id != songs[0].id || graph.player.state.value.positionMs < songs[0].durationMs * 0.25) {
            check(System.currentTimeMillis() < end) { "top song did not start: ${graph.player.state.value.current?.title} at ${graph.player.state.value.positionMs}" }
            pump(250)
        }
        val at = graph.player.state.value.positionMs
        println("wrapped live: top song '${songs[0].title}' playing at $at ms of ${songs[0].durationMs} (${at * 100 / songs[0].durationMs}%)")
        pump(2000)
        onRoot().captureRoboImage("src/desktopTest/snapshots/e2e/13_wrapped.png")
        navigator.back()
        val end2 = System.currentTimeMillis() + 30_000
        while (graph.player.state.value.current?.id != songs[2].id) { check(System.currentTimeMillis() < end2) { "not restored" }; pump(250) }
        println("wrapped live: restored '${graph.player.state.value.current?.title}' at ${graph.player.state.value.positionMs} ms, playing=${graph.player.state.value.isPlaying}")
        assertEquals(songs[2].id, graph.player.state.value.current?.id)
        assertTrue(at >= songs[0].durationMs * 0.25)
    }
}
