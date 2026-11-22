// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

/**
 * LRC and enhanced LRC. `[mm:ss.xx]` / `[mm:ss.xxx]` line stamps (several per line allowed), `<mm:ss.xx>` word
 * stamps, `[offset:±ms]` (positive = lyrics earlier), standard tags (`[ar:…]`, `[ti:…]`, …) ignored. A line ends
 * where the next begins; the last ends at the track's end, or 5 s later when the duration is unknown.
 */
object LrcParser {
    private val LINE_STAMP = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val WORD_STAMP = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val TAG = Regex("""^\[(ar|ti|al|au|by|re|ve|la|id|length|offset|tool|#)\s*:(.*)]\s*$""", RegexOption.IGNORE_CASE)
    private const val LAST_LINE_MS = 5_000L
    /** A last word with no closing stamp is sung for at most this long. */
    private const val LAST_WORD_MS = 2_000L
    /** Marks text before the first word stamp: it starts with the line. */
    private const val AT_LINE_START = Long.MIN_VALUE

    private fun ms(m: MatchResult): Long {
        val (min, sec, frac) = m.destructured
        val f = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
        return min.toLong() * 60_000 + sec.toLong() * 1000 + f
    }

    private data class Raw(val start: Long, val text: String, val words: List<Pair<Long, String>>, val closing: Long?)

    fun parse(text: String, provider: String, trackDurationMs: Long?): Lyrics? {
        val source = text.removePrefix("﻿")
        if (source.isBlank()) return null
        var offset = 0L
        val raws = mutableListOf<Raw>()
        var stamped = false
        for (rawLine in source.lines()) {
            val line = rawLine.trim()
            TAG.matchEntire(line)?.let { m ->
                if (m.groupValues[1].equals("offset", ignoreCase = true)) offset = m.groupValues[2].trim().removePrefix("+").toLongOrNull() ?: 0
                continue
            }
            val stamps = mutableListOf<Long>()
            var rest = line
            while (true) {
                val m = LINE_STAMP.matchAt(rest, 0) ?: break
                stamps += ms(m); rest = rest.substring(m.range.last + 1)
            }
            if (stamps.isEmpty()) continue
            stamped = true
            val words = mutableListOf<Pair<Long, String>>()
            var closing: Long? = null
            val wordMatches = WORD_STAMP.findAll(rest).toList()
            val plain = if (wordMatches.isEmpty()) rest.trim() else {
                rest.substring(0, wordMatches[0].range.first).takeIf { it.isNotBlank() }?.let { words += AT_LINE_START to it.trimStart() }
                wordMatches.forEachIndexed { i, m ->
                    val end = wordMatches.getOrNull(i + 1)?.range?.first ?: rest.length
                    val w = rest.substring(m.range.last + 1, end)
                    if (w.isEmpty() && i == wordMatches.lastIndex) closing = ms(m) else words += ms(m) to w
                }
                WORD_STAMP.replace(rest, "").trim()
            }
            stamps.forEach { s -> raws += Raw(s, plain, words.map { (t, w) -> (if (t == AT_LINE_START) s else t) to w }, closing) }
        }
        if (!stamped) {
            val plain = source.lines().filterNot { TAG.matches(it.trim()) }.joinToString("\n").trim()
            return if (plain.isEmpty()) null else Lyrics.Plain(plain, provider)
        }
        // [offset] may appear anywhere in the file; it applies to every stamp. Times never go below 0.
        fun shift(t: Long) = (t - offset).coerceAtLeast(0)
        val sorted = raws.map { r -> r.copy(start = shift(r.start), words = r.words.map { (t, w) -> shift(t) to w }, closing = r.closing?.let(::shift)) }
            .sortedBy { it.start }
        val lines = sorted.mapIndexed { i, r ->
            val end = maxOf(r.start, sorted.getOrNull(i + 1)?.start ?: trackDurationMs?.takeIf { it > r.start } ?: (r.start + LAST_LINE_MS))
            val words = r.words.mapIndexed { j, (t, w) ->
                val wordEnd = r.words.getOrNull(j + 1)?.first ?: r.closing ?: minOf(end, t + LAST_WORD_MS)
                LyricWord(w, t, maxOf(t, wordEnd))
            }
            LyricLine(r.start, end, r.text, words)
        }
        return if (lines.any { it.words.isNotEmpty() }) Lyrics.WordSynced(lines, provider) else Lyrics.LineSynced(lines, provider)
    }
}
