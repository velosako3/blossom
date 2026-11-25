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

