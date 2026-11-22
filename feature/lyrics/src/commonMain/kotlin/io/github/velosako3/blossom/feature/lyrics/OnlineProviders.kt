// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

import io.github.velosako3.blossom.feature.lyrics.providers.LyricsRepository
import io.github.velosako3.blossom.feature.lyrics.providers.LyricLine as BcLine

/**
 * BitChord's lookup, as BitChord runs it: every source asked at once, answers taken in priority order (the first
 * timed one wins), Genius only when nothing timed exists, and the recording settled by ISRC first.
 */
fun bitChordLyrics(): OnlineLyrics = OnlineLyrics { q ->
    LyricsRepository.lyrics(
        videoId = q.videoId.orEmpty(),
        title = q.title,
        artist = q.artists.joinToString(", "),
        durationMs = q.durationMs,
        album = q.album,
    )?.let { it.lines.toLyrics(it.source.label, q.durationMs) }
}

/** BitChord lines → blossom lyrics. Gap lines (empty text) stay as empty lines: the view shows long ones as dots. */
fun List<BcLine>.toLyrics(provider: String, durationMs: Long): Lyrics? {
    val sorted = sortedBy { it.timeMs }
    val texts = sorted.filter { it.text.isNotBlank() }
    if (texts.isEmpty()) return null
    val timed = texts.any { it.timeMs > 0 } || texts.any { it.words.isNotEmpty() }
    if (!timed) return Lyrics.Plain(texts.joinToString("\n") { it.text }, provider)
    val lines = sorted.map { line ->
        val next = sorted.firstOrNull { it.timeMs > line.timeMs }?.timeMs
        val end = next ?: durationMs.takeIf { it > line.timeMs } ?: (line.timeMs + 5_000)
        LyricLine(line.timeMs, end, line.text.trim(), line.words.map { LyricWord(it.text, it.startMs, it.endMs) })
    }
    return if (lines.any { it.words.isNotEmpty() }) Lyrics.WordSynced(lines, provider) else Lyrics.LineSynced(lines, provider)
}
