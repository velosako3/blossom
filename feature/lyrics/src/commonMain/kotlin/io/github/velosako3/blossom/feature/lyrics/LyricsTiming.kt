// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

/** The line playing at [positionMs]: -1 before the first line, the last line after the end. Binary search. */
fun activeLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
    var lo = 0
    var hi = lines.lastIndex
    var found = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (lines[mid].startMs <= positionMs) { found = mid; lo = mid + 1 } else hi = mid - 1
    }
    return found
}

/** How much of [word] has been sung, 0..1. */
fun wordProgress(word: LyricWord, positionMs: Long): Float {
    val span = word.endMs - word.startMs
    if (span <= 0) return if (positionMs >= word.startMs) 1f else 0f
    return ((positionMs - word.startMs).toFloat() / span).coerceIn(0f, 1f)
}
