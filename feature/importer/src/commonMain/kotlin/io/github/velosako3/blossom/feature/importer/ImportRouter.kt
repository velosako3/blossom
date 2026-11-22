// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.importer

import java.net.URI
import java.net.URLDecoder

enum class ImportService { SPOTIFY, YOUTUBE_MUSIC, SOUNDCLOUD, UNTITLED }

enum class LinkKind { TRACK, PLAYLIST, ALBUM }

/** A pasted link, cleaned: no tracking parameters, one canonical form per item. */
data class DetectedLink(val service: ImportService, val kind: LinkKind, val canonicalUrl: String)

/**
 * Recognizes Spotify, YouTube / YouTube Music, SoundCloud and untitled.stream links (web URLs and `spotify:` URIs).
 * Short links (`on.soundcloud.com`, untitled invites) only reveal their kind after a redirect; they're reported as
 * playlists and the importer resolves them.
 */
object ImportRouter {
    private val SPOTIFY_URI = Regex("""^spotify:(track|album|playlist):([A-Za-z0-9]+)$""")
    private val SPOTIFY_PATH = Regex("""^/(?:intl-[a-z]{2}(?:-[a-z]{2})?/)?(track|album|playlist)/([A-Za-z0-9]+)/?$""", RegexOption.IGNORE_CASE)
    private val UNTITLED_PROJECT_TRACK = Regex("""^/library/project/([A-Za-z0-9]{10,40})/track/([A-Za-z0-9]{10,40})(?:/embed)?/?$""")
    private val UNTITLED_PROJECT = Regex("""^/library/project/([A-Za-z0-9]{10,40})(?:/embed)?/?$""")
    private val UNTITLED_TRACK = Regex("""^/library/track/([A-Za-z0-9]{10,40})(?:/embed)?/?$""")
    private val UNTITLED_EMBED = Regex("""^/embed/([A-Za-z0-9]{10,40})/?$""")
    private val UNTITLED_INVITE = Regex("""^/invite/([A-Za-z0-9_-]+)/?$""")

    fun detect(raw: String): DetectedLink? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        SPOTIFY_URI.matchEntire(text)?.let { m ->
            return DetectedLink(ImportService.SPOTIFY, kindOf(m.groupValues[1]), "https://open.spotify.com/${m.groupValues[1]}/${m.groupValues[2]}")
        }
        val uri = runCatching { URI(if ("://" in text) text else "https://$text") }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null
        val path = uri.rawPath.orEmpty()
        val query = params(uri.rawQuery)
        return when (host) {
            "open.spotify.com", "play.spotify.com" -> SPOTIFY_PATH.matchEntire(path)?.let { m ->
                val type = m.groupValues[1].lowercase()
                DetectedLink(ImportService.SPOTIFY, kindOf(type), "https://open.spotify.com/$type/${m.groupValues[2]}")
            }
            "music.youtube.com", "youtube.com" -> youtube(path, query)
            "youtu.be" -> path.trim('/').takeIf { it.isNotEmpty() && '/' !in it }?.let { v ->
                query["list"]?.let { DetectedLink(ImportService.YOUTUBE_MUSIC, LinkKind.PLAYLIST, ytPlaylist(it)) }
                    ?: DetectedLink(ImportService.YOUTUBE_MUSIC, LinkKind.TRACK, "https://music.youtube.com/watch?v=$v")
            }
            "soundcloud.com" -> {
                val parts = path.trim('/').split('/').filter { it.isNotEmpty() }
                when {
                    parts.size == 2 && parts[0] !in SC_RESERVED -> DetectedLink(ImportService.SOUNDCLOUD, LinkKind.TRACK, "https://soundcloud.com/${parts[0]}/${parts[1]}")
                    parts.size == 3 && parts[1] == "sets" -> DetectedLink(ImportService.SOUNDCLOUD, LinkKind.PLAYLIST, "https://soundcloud.com/${parts[0]}/sets/${parts[2]}")
                    else -> null
                }
            }
            "on.soundcloud.com" -> path.trim('/').takeIf { it.isNotEmpty() }?.let {
                DetectedLink(ImportService.SOUNDCLOUD, LinkKind.PLAYLIST, "https://on.soundcloud.com/$it")
            }
            "untitled.stream" -> untitled(path)
            else -> null
        }
    }

    private fun youtube(path: String, query: Map<String, String>): DetectedLink? {
        val list = query["list"]
        return when {
            path == "/playlist" && list != null -> DetectedLink(ImportService.YOUTUBE_MUSIC, LinkKind.PLAYLIST, ytPlaylist(list))
            path == "/watch" && list != null -> DetectedLink(ImportService.YOUTUBE_MUSIC, LinkKind.PLAYLIST, ytPlaylist(list))
            path == "/watch" && query["v"] != null -> DetectedLink(ImportService.YOUTUBE_MUSIC, LinkKind.TRACK, "https://music.youtube.com/watch?v=${query["v"]}")
            path.startsWith("/browse/MPRE") -> DetectedLink(ImportService.YOUTUBE_MUSIC, LinkKind.ALBUM, "https://music.youtube.com${path.trimEnd('/')}")
            path.startsWith("/shorts/") -> DetectedLink(ImportService.YOUTUBE_MUSIC, LinkKind.TRACK, "https://music.youtube.com/watch?v=${path.removePrefix("/shorts/").trim('/')}")
            else -> null
        }
    }

    private fun untitled(path: String): DetectedLink? {
        val base = "https://untitled.stream"
        UNTITLED_PROJECT_TRACK.matchEntire(path)?.let { m ->
            return DetectedLink(ImportService.UNTITLED, LinkKind.TRACK, "$base/library/project/${m.groupValues[1]}/track/${m.groupValues[2]}")
        }
        UNTITLED_PROJECT.matchEntire(path)?.let { return DetectedLink(ImportService.UNTITLED, LinkKind.ALBUM, "$base/library/project/${it.groupValues[1]}") }
        UNTITLED_TRACK.matchEntire(path)?.let { return DetectedLink(ImportService.UNTITLED, LinkKind.TRACK, "$base/library/track/${it.groupValues[1]}") }
        UNTITLED_EMBED.matchEntire(path)?.let { return DetectedLink(ImportService.UNTITLED, LinkKind.TRACK, "$base/library/track/${it.groupValues[1]}") }
        UNTITLED_INVITE.matchEntire(path)?.let { return DetectedLink(ImportService.UNTITLED, LinkKind.PLAYLIST, "$base/invite/${it.groupValues[1]}") }
        return null
    }

    private fun ytPlaylist(list: String) = "https://music.youtube.com/playlist?list=$list"

    private fun kindOf(type: String) = when (type) { "track" -> LinkKind.TRACK; "album" -> LinkKind.ALBUM; else -> LinkKind.PLAYLIST }

    /** Query parameters (tracking ones like `si`, `utm_*`, `feature` never reach the canonical URL anyway). */
    private fun params(raw: String?): Map<String, String> = raw.orEmpty().split('&').filter { '=' in it }.associate {
        val (k, v) = it.split('=', limit = 2)
        URLDecoder.decode(k, Charsets.UTF_8) to URLDecoder.decode(v, Charsets.UTF_8)
    }

    private val SC_RESERVED = setOf("discover", "search", "you", "stream", "upload", "charts", "pages", "settings")
}
