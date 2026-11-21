// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.takahirom.roborazzi.captureRoboImage
import io.github.velosako3.blossom.core.designsystem.background.BlossomBackground
import io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.core.designsystem.glass.glassBackdropSource
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.feature.lyrics.LyricLine
import io.github.velosako3.blossom.feature.lyrics.LyricWord
import io.github.velosako3.blossom.feature.lyrics.Lyrics
import kotlin.test.Test

private val palette = ArtworkPalette.Default
private val sentences = listOf(
    "City lights are fading slow", "Every window burning gold", "I keep walking through the night",
    "Counting stars inside your eyes", "Hold me close and let it go", "We were young and never old",
    "Running down the empty streets", "Singing songs nobody hears", "Morning comes and finds us here", "Still awake and still in love",
)

/** Line k starts at 2 s + 4 s·k; each of its words lasts 0.8 s. */
private val wordSynced = Lyrics.WordSynced(sentences.mapIndexed { k, s ->
    val start = 2_000L + 4_000L * k
    var t = start
    val words = s.split(" ").mapIndexed { i, w -> LyricWord(if (i == 0) w else " $w", t, t + 800).also { t += 800 } }
    LyricLine(start, start + 4_000, s, words)
}, "test")

private val lineSynced = Lyrics.LineSynced(wordSynced.lines.map { it.copy(words = emptyList()) }, "test")

private val withGap = Lyrics.LineSynced(
    lineSynced.lines.take(3) + LyricLine(14_000, 26_000, "", emptyList()) + lineSynced.lines.drop(3).map { it.copy(startMs = it.startMs + 12_000, endMs = it.endMs + 12_000) },
    "test",
)

@OptIn(ExperimentalTestApi::class)
private fun shot(name: String, reduceMotion: Boolean = false, content: @Composable () -> Unit) = runDesktopComposeUiTest(412, 760) {
    mainClock.autoAdvance = false
    setContent {
        BlossomTheme(true, palette, glassQuality = GlassQuality.FULL, reduceMotion = reduceMotion) {
            Box(Modifier.fillMaxSize()) {
                BlossomBackground(palette, Modifier.fillMaxSize().glassBackdropSource())
                content()
            }
        }
    }
    repeat(12) { mainClock.advanceTimeBy(100) }
    onRoot().captureRoboImage("src/desktopTest/snapshots/$name.png")
}

class LyricsSnapshotTest {
    // Line 4 starts at 18 s; its second word runs 18.8–19.6 s.
    @Test fun lyrics_wordSynced_midWord() = shot("lyrics_wordSynced_midWord") { LyricsView(wordSynced, 19_200, {}) }
    @Test fun lyrics_lineSynced() = shot("lyrics_lineSynced") { LyricsView(lineSynced, 19_200, {}) }
    @Test fun lyrics_instrumentalGap() = shot("lyrics_instrumentalGap") { LyricsView(withGap, 20_000, {}) }
    @Test fun lyrics_reduceMotion() = shot("lyrics_reduceMotion", reduceMotion = true) { LyricsView(wordSynced, 19_200, {}) }
    @Test fun lyrics_none() = shot("lyrics_none") { LyricsView(null, 0, {}) }
}
