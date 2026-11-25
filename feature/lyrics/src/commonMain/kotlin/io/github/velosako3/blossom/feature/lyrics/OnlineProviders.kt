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

/**
 * What the lyrics sites would call the song. Uploads (untitled, SoundCloud, YouTube videos) often carry the artist in
 * the title ("Kanye West – Nitrous (prod. X) [v2]") and the uploader as the artist; that's split out ([uploaded], or
 * when the left side is one of the artists or Ye). Labels the sites never use are dropped from any title: producer,
 * featured artists, versions, snippet/leak/CDQ tags, "Official Video" and the like.
 */
fun tidyLyricsQuery(q: LyricsQuery, uploaded: Boolean): LyricsQuery {
    var title = q.title.replace(SYMBOLS, " ")
    var artists = q.artists
    ARTIST_TITLE.find(title)?.let { m ->
        val left = m.groupValues[1].trim()
        val right = m.groupValues[2].trim()
        val known = artists.any { it.isNotBlank() && (left.contains(it, ignoreCase = true) || it.contains(left, ignoreCase = true)) }
        if (right.isNotEmpty() && (uploaded || known || lyricsArtist(left) == "Ye")) {
            title = right
            artists = left.split(ARTIST_JOIN).map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { artists }
        }
    }
    repeat(3) { title = title.replace(NOISE_GROUP, " ") } // nested or repeated groups
    title = title.replace(TRAILING_NOISE, "").replace(Regex("""\s+"""), " ").trim(' ', '-', '–', '—', '|', ':', '.')
    return q.copy(title = title.ifEmpty { q.title }, artists = artists)
}

private val SYMBOLS = Regex("""[✦★☆♪♫🔥💯🎵🎶✨⭐]""")
private val ARTIST_TITLE = Regex("""^\s*(.+?)\s+[-–—]\s+(.+)$""")
private val ARTIST_JOIN = Regex("""(?i)\s*(?:,|&|\bx\b|\band\b|\bfeat\.?|\bft\.?)\s*""")
private const val NOISE_WORDS =
    """prod|produced|feat|ft|with|v\s*\d+|version|snippet|leak(?:ed)?|cdq|hq|lq|og|demo|unreleased|yedit|edit|official|video|audio|lyrics?|visuali[sz]er|music\s*video|mv|hd|4k|remaster(?:ed)?|explicit|clean|extended|full|session|reference|ref|mastered|unmastered|mixed|unmixed|alt(?:ernate)?|early|final|solo|mono|stereo|filtered|instrumental\s*version"""
/** A bracketed group that holds one of [NOISE_WORDS]: `(prod. X)`, `[v2]`, `(Official Video)`, `【Leak】`. */
private val NOISE_GROUP = Regex("""(?i)\s*[(\[{【][^)\]}】]*\b(?:$NOISE_WORDS)\b[^)\]}】]*[)\]}】]""")
/** Unbracketed tails: ` prod. X`, ` ft. X`, ` | Kanye West`, ` - Remastered 2011`. */
private val TRAILING_NOISE = Regex("""(?i)(?:\s+(?:prod\.?|produced by|feat\.?|ft\.?)\s+.*$|\s+\|.*$|\s+[-–—]\s+(?:remaster(?:ed)?|official|lyrics?|audio|video|snippet|leak(?:ed)?|cdq|v\s*\d+)\b.*$)""")
