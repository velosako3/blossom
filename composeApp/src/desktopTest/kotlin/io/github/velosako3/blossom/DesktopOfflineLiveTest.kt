// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.takahirom.roborazzi.captureRoboImage
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.core.offline.OfflineRef
import io.github.velosako3.blossom.core.offline.OfflineState
import io.github.velosako3.blossom.navigation.Destination
import io.github.velosako3.blossom.navigation.Navigator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/**
 * Save a song while online, then start a second app with `blossom.forceOffline=true` (inside this JVM only; the
 * machine's network is untouched): the offline home lists it, and it plays from the encrypted cache.
 * Screenshots: snapshots/e2e/09_offline_home, 10_offline_library_saved. Live: -PliveTests=true.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopOfflineLiveTest {
    @Test fun playsSavedSongOffline() = runDesktopComposeUiTest(1440, 900, testTimeout = 5.minutes) {
        resetTestDataDir()
        val online = createDesktopGraph()
        val song = runBlocking {
            val song = online.ytm.search("Daft Punk One More Time").songs.first()
            online.offline!!.saveTrack(song, OfflineRef.Single(song.id))
            withTimeout(120_000) { online.offline!!.state(song.id).first { it == OfflineState.SAVED || it == OfflineState.FAILED } }
            assertEquals(OfflineState.SAVED, online.offline!!.state(song.id).first())
            println("offline saved: ${song.title} – ${song.artistNames}, ${online.offline!!.usageBytes().first()} bytes")
            song
        }
        val dir = java.io.File(System.getProperty("blossom.dataDir"), "offline")
        assertTrue(dir.listFiles().orEmpty().all { it.name.endsWith(".blsm") })

        System.setProperty("blossom.forceOffline", "true")
        try {
            val graph = createDesktopGraph()
            assertFalse(graph.network.status.value.online)
            val navigator = Navigator()
            setContent { App(navigator = navigator, graph = graph, glassQuality = GlassQuality.FULL, reduceMotion = true) }
            fun pump(ms: Long) { repeat((ms / 250).toInt().coerceAtLeast(1)) { mainClock.advanceTimeByFrame(); Thread.sleep(250) } }
            mainClock.autoAdvance = false
            pump(3000)
            onRoot().captureRoboImage("src/desktopTest/snapshots/e2e/09_offline_home.png")
            assertTrue(onAllNodes(hasText(song.title)).fetchSemanticsNodes().isNotEmpty(), "offline home lists the saved song")

            navigator.navigate(Destination.Library)
            pump(1000)
            onAllNodes(hasText("Saved"))[0].performClick()
            pump(1500)
            onRoot().captureRoboImage("src/desktopTest/snapshots/e2e/10_offline_library_saved.png")

            graph.player.setQueue(listOf(song), 0)
            graph.player.play()
            val end = System.currentTimeMillis() + 60_000
            while (graph.player.progress.value.positionMs <= 3_000) { check(System.currentTimeMillis() < end) { "offline playback" }; pump(250) }
            println("offline playing: ${graph.player.state.value.current?.title} at ${graph.player.progress.value.positionMs} ms, online=${graph.network.status.value.online}")
            graph.player.pause()
        } finally {
            System.clearProperty("blossom.forceOffline")
        }
    }
}
