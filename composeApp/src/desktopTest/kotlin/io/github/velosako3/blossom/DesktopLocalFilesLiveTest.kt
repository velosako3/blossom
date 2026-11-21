// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.takahirom.roborazzi.captureRoboImage
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.navigation.Destination
import io.github.velosako3.blossom.navigation.Navigator
import java.io.File
import kotlin.test.Test
import kotlinx.coroutines.flow.first
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** Local files with the real app graph: a watched folder, the Local files tab, and libVLC playing a local file. */
@OptIn(ExperimentalTestApi::class)
class DesktopLocalFilesLiveTest {
    @Test fun scanShowAndPlayLocalFile() = runDesktopComposeUiTest(1440, 900, testTimeout = 3.minutes) {
        val data = File(System.getProperty("blossom.dataDir")).apply { deleteRecursively(); mkdirs() }
        val music = File(data, "music").apply { mkdirs() }
        listOf("tagged.mp3", "tagged.flac").forEach { n -> javaClass.getResourceAsStream("/fixtures/$n")!!.use { i -> File(music, n).outputStream().use { i.copyTo(it) } } }
        File(data, "preferences.properties").writeText("local.folders=[\"${music.absolutePath}\"]\n")
        val graph = createDesktopGraph()
        val navigator = Navigator(Destination.Library)
        setContent { App(navigator = navigator, graph = graph, glassQuality = GlassQuality.FULL, reduceMotion = true) }
        fun pump(ms: Long) { repeat((ms / 250).toInt().coerceAtLeast(1)) { mainClock.advanceTimeByFrame(); Thread.sleep(250) } }
        fun waitFor(what: String, cond: () -> Boolean) { val end = System.currentTimeMillis() + 30_000; while (!runCatching(cond).getOrDefault(false)) { check(System.currentTimeMillis() < end) { "timed out: $what" }; pump(250) } }
        mainClock.autoAdvance = false
        val tracks = mutableListOf<io.github.velosako3.blossom.core.model.Track>()
        kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeout(20_000) { tracks += graph.local.library.tracks().first { it.size == 2 } } }
        println("local: ${tracks.map { "${it.title} – ${it.artistNames} (${it.id.kind})" }}")
        assertTrue(tracks.all { it.id.kind == SourceKind.LOCAL })
        pump(500)
        onAllNodesWithText("Local files")[0].performClick()
        pump(1500)
        kotlinx.coroutines.runBlocking { graph.player.setQueue(tracks, 0); graph.player.play() }
        waitFor("local playback") { graph.player.state.value.positionMs > 300 }
        println("local playing: ${graph.player.state.value.current?.title} at ${graph.player.state.value.positionMs} ms")
        pump(500)
        onRoot().captureRoboImage("src/desktopTest/snapshots/e2e/07_local_files.png")
    }
}
