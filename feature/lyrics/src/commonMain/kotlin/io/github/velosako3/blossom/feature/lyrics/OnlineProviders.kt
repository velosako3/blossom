// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

import io.github.velosako3.blossom.feature.lyrics.providers.BetterLyrics
import io.github.velosako3.blossom.feature.lyrics.providers.BiniLyrics
import io.github.velosako3.blossom.feature.lyrics.providers.Genius
import io.github.velosako3.blossom.feature.lyrics.providers.KuGou
import io.github.velosako3.blossom.feature.lyrics.providers.LrcLib
import io.github.velosako3.blossom.feature.lyrics.providers.LrcRed
import io.github.velosako3.blossom.feature.lyrics.providers.LyricsPlus
import io.github.velosako3.blossom.feature.lyrics.providers.Musixmatch
import io.github.velosako3.blossom.feature.lyrics.providers.PaxSenix
import io.github.velosako3.blossom.feature.lyrics.providers.Unison
import io.github.velosako3.blossom.feature.lyrics.providers.YouTubeMusicLyrics
import io.github.velosako3.blossom.feature.lyrics.providers.YouTubeTranscriptLyrics
import io.github.velosako3.blossom.feature.lyrics.providers.artistForLyricsSearch
import io.github.velosako3.blossom.feature.lyrics.providers.forLyricsSearch
import io.github.velosako3.blossom.feature.lyrics.providers.LyricLine as BcLine

/** BitChord's providers, in BitChord's default priority (key-only PaxSenix variants and hidden hosts left out). */
fun onlineLyricsProviders(): List<LyricsProvider> {
    fun p(name: String, priority: Int, fetch: suspend (LyricsQuery, String, String) -> List<BcLine>?) = object : LyricsProvider {
        override val name = name
        override val priority = priority
        override suspend fun fetch(q: LyricsQuery): Lyrics? =
            fetch(q, q.title.forLyricsSearch(), q.artists.firstOrNull().orEmpty().artistForLyricsSearch())?.toLyrics(name, q.durationMs)
    }
    return listOf(
        p("lrc.red", 1) { q, t, a -> LrcRed.lyrics(t, a, q.durationMs) },
        p("BiniLyrics", 2) { q, t, a -> BiniLyrics.lyrics(t, a, q.durationMs, q.album)?.lines },
        p("BetterLyrics", 3) { q, t, a -> BetterLyrics.lyrics(t, a, q.durationMs, q.album) },
        p("BetterLyrics Portato", 4) { q, t, a -> BetterLyrics.portato(t, a, q.durationMs, q.album) },
        p("PaxSenix", 5) { q, t, a -> PaxSenix.lyrics(t, a, q.durationMs, q.album) },
        p("LyricsPlus", 6) { q, t, a -> LyricsPlus.lyrics(t, a, q.durationMs, q.album) },
        p("Unison", 7) { q, t, a -> Unison.lyrics(t, a, q.durationMs, q.album) },
        p("YouTube captions", 8) { q, _, _ -> q.videoId?.let { YouTubeTranscriptLyrics.lyrics(it) } },
        p("YouTube Music", 9) { q, _, _ -> q.videoId?.let { YouTubeMusicLyrics.lyrics(it) } },
        p("KuGou", 10) { q, t, a -> KuGou.lyrics(t, a, q.durationMs, q.album) },
        p("LRCLIB", 11) { q, t, a -> LrcLib.lyrics(t, a, q.durationMs) },
        p("Musixmatch", 12) { q, t, a -> Musixmatch.lyrics(t, a, q.durationMs) },
        p("Genius", 13) { _, t, a -> Genius.lyrics(t, a) },
    )
}

/** BitChord lines → blossom lyrics. Gap lines (empty text) only end the line before them. */
fun List<BcLine>.toLyrics(provider: String, durationMs: Long): Lyrics? {
    val sorted = sortedBy { it.timeMs }
    val texts = sorted.filter { it.text.isNotBlank() }
    if (texts.isEmpty()) return null
    val timed = texts.any { it.timeMs > 0 } || texts.any { it.words.isNotEmpty() }
    if (!timed) return Lyrics.Plain(texts.joinToString("\n") { it.text }, provider)
    val lines = texts.map { line ->
        val next = sorted.firstOrNull { it.timeMs > line.timeMs }?.timeMs
        val end = line.sungUntilMs?.takeIf { it > line.timeMs } ?: next ?: durationMs.takeIf { it > line.timeMs } ?: (line.timeMs + 5_000)
        LyricLine(line.timeMs, maxOf(end, next ?: end), line.text, line.words.map { LyricWord(it.text, it.startMs, it.endMs) })
    }
    return if (lines.any { it.words.isNotEmpty() }) Lyrics.WordSynced(lines, provider) else Lyrics.LineSynced(lines, provider)
}
