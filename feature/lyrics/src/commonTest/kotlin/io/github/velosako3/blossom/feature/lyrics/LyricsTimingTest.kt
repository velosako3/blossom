// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals

class LyricsTimingTest {
    private val lines = listOf(LyricLine(12_500, 15_000, "a", emptyList()), LyricLine(15_000, 20_000, "b", emptyList()))

    @Test fun activeLine() {
        assertEquals(-1, activeLineIndex(lines, 500))
        assertEquals(0, activeLineIndex(lines, 12_500))
        assertEquals(0, activeLineIndex(lines, 14_999))
        assertEquals(1, activeLineIndex(lines, 15_000))
        assertEquals(1, activeLineIndex(lines, 99_000))
    }

    @Test fun progress() {
        val w = LyricWord("x", 1000, 1400)
        assertEquals(0.5f, wordProgress(w, 1200))
        assertEquals(0f, wordProgress(w, 900))
        assertEquals(1f, wordProgress(w, 2000))
    }
}
