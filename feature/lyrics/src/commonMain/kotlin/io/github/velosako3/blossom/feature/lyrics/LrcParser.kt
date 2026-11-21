// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

/**
 * LRC and enhanced LRC. `[mm:ss.xx]` / `[mm:ss.xxx]` line stamps (several per line allowed), `<mm:ss.xx>` word
 * stamps, `[offset:±ms]` (positive = lyrics earlier), metadata tags ignored. A line ends where the next begins; the
 * last ends at the track's end, or 5 s later when the duration is unknown.
 */
object LrcParser {
    private val LINE_STAMP = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val WORD_STAMP = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val META = Regex("""^\[([a-zA-Z#]+):(.*)]\s*$""")
    private const val LAST_LINE_MS = 5_000L

    private fun ms(m: MatchResult): Long {
        val (min, sec, frac) = m.destructured
        val f = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
        return min.toLong() * 60_000 + sec.toLong() * 1000 + f
    }

    private data class Raw(val start: Long, val text: String, val words: List<Pair<Long, String>>, val closing: Long?)

    fun parse(text: String, provider: String, trackDurationMs: Long?): Lyrics? {
        if (text.isBlank()) return null
        var offset = 0L
        val raws = mutableListOf<Raw>()
        var stamped = false
        for (rawLine in text.lines()) {
            val line = rawLine.trim()
            META.matchEntire(line)?.let { m ->
                if (m.groupValues[1].lowercase() == "offset") offset = m.groupValues[2].trim().removePrefix("+").toLongOrNull() ?: 0
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
                wordMatches.forEachIndexed { i, m ->
                    val end = wordMatches.getOrNull(i + 1)?.range?.first ?: rest.length
                    val w = rest.substring(m.range.last + 1, end)
                    if (w.isEmpty() && i == wordMatches.lastIndex) closing = ms(m) else words += ms(m) to w
                }
                WORD_STAMP.replace(rest, "").trim()
            }
            stamps.forEach { s -> raws += Raw(s, plain, words, closing) }
        }
        if (!stamped) {
            val plain = text.lines().filterNot { META.matches(it.trim()) }.joinToString("\n").trim()
            return if (plain.isEmpty()) null else Lyrics.Plain(plain, provider)
        }
        // [offset] may appear anywhere in the file; it applies to every stamp.
        val sorted = raws.map { r -> r.copy(start = r.start - offset, words = r.words.map { (t, w) -> (t - offset) to w }, closing = r.closing?.minus(offset)) }
            .sortedBy { it.start }
        val lines = sorted.mapIndexed { i, r ->
            val end = sorted.getOrNull(i + 1)?.start ?: trackDurationMs?.takeIf { it > r.start } ?: (r.start + LAST_LINE_MS)
            val words = r.words.mapIndexed { j, (t, w) ->
                LyricWord(w, t, r.words.getOrNull(j + 1)?.first ?: r.closing ?: end)
            }
            LyricLine(r.start.coerceAtLeast(0), end, r.text, words)
        }
        return if (lines.any { it.words.isNotEmpty() }) Lyrics.WordSynced(lines, provider) else Lyrics.LineSynced(lines, provider)
    }
}
