// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

import io.github.velosako3.blossom.feature.lyrics.providers.LyricsRepository

/**
 * BitChord's lookup, as BitChord runs it: every source asked at once, answers taken in priority order (the first
 * timed one wins), Genius only when nothing timed exists, and the recording settled by ISRC first.
 */
fun bitChordLyrics(): OnlineLyrics = OnlineLyrics { q ->
    LyricsRepository.lyrics(
        videoId = q.videoId.orEmpty(),
        title = q.title,
        artist = q.artists.map(::lyricsArtist).distinct().joinToString(", "),
        durationMs = q.durationMs,
        album = q.album,
    )?.let { lyricsOf(it.lines, it.source.label) }
}

/**
 * The artist name the lyrics sites know. Unreleased Ye songs mostly come from uploaders named after him ("YeBreak",
 * "Ye Leaks", "KanyeLeaks"), and the sites file him as "Ye": every such name becomes "Ye". Other artists starting
 * with "Ye" (Yeat, Yelawolf) are left alone. ponytail: one artist; a table of uploader patterns if others need it.
 */
fun lyricsArtist(name: String): String =
    if (YE_UPLOADER.matches(name.trim()) || KANYE.containsMatchIn(name)) "Ye" else name

private val YE_UPLOADER = Regex(
    """(?i)^(?:kanye(?:\s*west)?|ye)(?:[\s._-]*(?:break|leaks?|archive|vault|hub|news|updates?|daily|world|central|music|unreleased|snippets?|tv|files))*$""",
)
private val KANYE = Regex("""(?i)kanye""")
