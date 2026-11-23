// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics.ui

import io.github.velosako3.blossom.feature.lyrics.providers.LyricLine
import io.github.velosako3.blossom.feature.lyrics.providers.LyricWord
import io.github.velosako3.blossom.feature.lyrics.ui.panel.PlaybackPosition

internal val sentences = listOf(
    "City lights are fading slow", "Every window burning gold", "I keep walking through the night",
    "Counting stars inside your eyes", "Hold me close and let it go", "We were young and never old",
    "Running down the empty streets", "Singing songs nobody hears", "Morning comes and finds us here", "Still awake and still in love",
)

/** Line k starts at 2 s + 4 s·k; each of its words lasts 0.8 s. */
internal val wordSyncedLines: List<LyricLine> = sentences.mapIndexed { k, s ->
    val start = 2_000L + 4_000L * k
    var t = start
    val words = s.split(" ").mapIndexed { i, w -> LyricWord(t, t + 800, if (i == 0) w else " $w").also { t += 800 } }
    LyricLine(start, s, words)
}

internal val lineSyncedLines: List<LyricLine> = wordSyncedLines.map { LyricLine(it.timeMs, it.text, sungUntilMs = it.timeMs + 4_000) }

internal val plainLines: List<LyricLine> = sentences.map { LyricLine(0, it) }

internal fun positionAt(ms: Long) = PlaybackPosition().apply { report(ms, isPlaying = false) }
