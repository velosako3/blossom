// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics.ui

import io.github.velosako3.blossom.feature.lyrics.LyricLine

/** What the synced view lays out: a sung line, or an instrumental gap of more than 5 s. */
internal sealed interface LyricsItem {
    val startMs: Long
    data class Line(val index: Int, val line: LyricLine, val wordRanges: List<IntRange>) : LyricsItem { override val startMs get() = line.startMs }
    data class Gap(override val startMs: Long, val endMs: Long) : LyricsItem
}

internal const val GAP_MS = 5_000L

internal fun lyricsItems(lines: List<LyricLine>): List<LyricsItem> {
    val out = mutableListOf<LyricsItem>()
    val first = lines.firstOrNull { it.text.isNotBlank() } ?: return out
    if (first.startMs > GAP_MS) out += LyricsItem.Gap(0, first.startMs)
    lines.forEachIndexed { i, line ->
        val next = lines.getOrNull(i + 1)?.startMs
        if (line.text.isBlank()) {
            if (line.endMs - line.startMs > GAP_MS) out += LyricsItem.Gap(line.startMs, line.endMs)
            return@forEachIndexed
        }
        out += LyricsItem.Line(i, line, wordRanges(line))
        // Word-synced lines say when singing stops; a long silence after that is a gap of its own.
        val sungUntil = line.words.lastOrNull()?.endMs
        if (sungUntil != null && next != null && next - sungUntil > GAP_MS) out += LyricsItem.Gap(sungUntil, next)
    }
    return out
}

/** Index of the item playing at [positionMs]; -1 before everything. */
internal fun activeItemIndex(items: List<LyricsItem>, positionMs: Long): Int {
    var lo = 0
    var hi = items.lastIndex
    var found = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (items[mid].startMs <= positionMs) { found = mid; lo = mid + 1 } else hi = mid - 1
    }
    return found
}

/** Where each word sits in the line's text. Words not found verbatim take the next characters. */
internal fun wordRanges(line: LyricLine): List<IntRange> {
    var cursor = 0
    return line.words.map { w ->
        val t = w.text.trim()
        val at = if (t.isEmpty()) -1 else line.text.indexOf(t, cursor)
        val start = if (at >= 0) at else cursor.coerceAtMost(line.text.length)
        val end = (start + t.length).coerceAtMost(line.text.length)
        cursor = end
        start until end
    }
}
