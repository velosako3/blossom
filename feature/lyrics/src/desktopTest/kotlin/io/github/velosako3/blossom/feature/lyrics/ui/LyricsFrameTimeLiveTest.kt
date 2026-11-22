// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.feature.lyrics.providers.LyricLine
import io.github.velosako3.blossom.feature.lyrics.ui.panel.SyncedLyricsPanel
import io.github.velosako3.blossom.feature.lyrics.providers.LyricWord
import kotlin.test.Test

/**
 * Render cost of word-synced lyrics in full motion: 30 s of a 60 Hz song, 1440×900, CPU raster (no GPU off-screen,
 * so this is an upper bound). Timing-sensitive, so opt-in with the live tests: -PliveTests=true.
 */
class LyricsFrameTimeLiveTest {
    @Test fun thirtySecondsAt60Hz() {
        val lines = (0 until 80).map { k ->
            val start = 1_000L + 3_000L * k
            var t = start
            val words = "Every window burning gold tonight".split(" ").mapIndexed { i, w -> LyricWord(t, t + 500, if (i == 0) w else " $w").also { t += 500 } }
            LyricLine(start, "Every window burning gold tonight", words)
        }
        for ((label, reduce, show) in listOf(Triple("empty", false, false), Triple("reduceMotion", true, true), Triple("motionReducedGlass", false, true), Triple("full", false, true))) {
            val times = measure(1440, 900, 300) { pos ->
                BlossomTheme(true, ArtworkPalette.Default, glassQuality = if (label == "motionReducedGlass") GlassQuality.REDUCED else GlassQuality.FULL, reduceMotion = reduce) {
                    Box(Modifier.fillMaxSize().background(Color(0xFF2A1B3D))) { if (show) SyncedLyricsPanel(lines, "bench", positionAt(pos), looking = false, isPlaying = false, onSeekToLine = {}) }
                }
            }
            fun p(q: Double) = times[((times.size - 1) * q).toInt()]
            println("lyrics render [$label]: n=${times.size} p50=${"%.2f".format(p(0.5))} p90=${"%.2f".format(p(0.9))} p99=${"%.2f".format(p(0.99))} max=${"%.2f".format(times.last())} ms")
        }
    }

    private fun measure(w: Int, h: Int, frames: Int, content: @androidx.compose.runtime.Composable (Long) -> Unit): List<Double> {
        val pos = mutableLongStateOf(0L)
        val scene = ImageComposeScene(w, h, Density(1.5f)) { content(pos.longValue) }
        val frameNs = 16_666_667L
        var now = 0L
        val times = ArrayList<Double>()
        repeat(frames + 120) { i ->
            pos.longValue = 30_000 + now / 1_000_000
            val t0 = System.nanoTime()
            scene.render(now).close()
            if (i >= 120) times += (System.nanoTime() - t0) / 1e6
            now += frameNs
        }
        scene.close()
        return times.sorted()
    }
}
