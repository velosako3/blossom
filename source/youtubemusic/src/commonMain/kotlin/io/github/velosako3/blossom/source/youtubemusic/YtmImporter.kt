// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.youtubemusic

import io.ktor.http.decodeURLQueryComponent
import io.github.velosako3.blossom.core.model.PlaylistOrigin
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.source.api.ImportError
import io.github.velosako3.blossom.source.api.ImportedPlaylist
import io.github.velosako3.blossom.source.api.ImportedTrack
import io.github.velosako3.blossom.source.api.PlaylistImporter

/**
 * YouTube Music and YouTube links: playlists (every continuation page), albums and single videos. Everything plays
 * natively, so each track carries its `ytm:` track and skips matching.
 */
class YtmImporter(private val ytm: YtmSource) : PlaylistImporter {
    override fun canHandle(url: String): Boolean = target(url) != null

    override suspend fun read(url: String, onProgress: (loaded: Int, total: Int?) -> Unit): ImportedPlaylist {
        val t = target(url) ?: throw ImportError.NotFound(url)
        val result = when (t) {
            is Target.Playlist -> ytm.playlist(t.id).let { p ->
                ImportedPlaylist(p.title, p.author, p.artworkUrl, origin("https://music.youtube.com/playlist?list=${t.id}"), p.tracks.map(::imported))
            }
            is Target.Album -> ytm.album(t.browseId).let { a ->
                ImportedPlaylist(a.title, a.artists.joinToString(", ") { it.name }.ifBlank { null }, a.artworkUrl,
                    origin("https://music.youtube.com/browse/${t.browseId}"), a.tracks.map(::imported))
            }
            is Target.Video -> {
                val track = ytm.track(TrackId.of(SourceKind.YOUTUBE_MUSIC, t.videoId)) ?: throw ImportError.NotFound(url)
                ImportedPlaylist(track.title, null, track.artworkUrl, origin("https://music.youtube.com/watch?v=${t.videoId}"), listOf(imported(track)))
            }
        }
        onProgress(result.tracks.size, result.tracks.size)
        return result
    }

    private fun imported(t: Track) = ImportedTrack(
        title = t.title,
        artists = t.artists.map { it.name },
        durationMs = t.durationMs.takeIf { it > 0 },
        isrc = null,
        nativeTrack = t,
        album = t.album?.title,
        sourceKey = t.id.value,
    )

    private fun origin(url: String) = PlaylistOrigin(SourceKind.YOUTUBE_MUSIC, "YouTube Music", url)

    private sealed interface Target {
        data class Playlist(val id: String) : Target
        data class Album(val browseId: String) : Target
        data class Video(val videoId: String) : Target
    }

    private val ID = Regex("""[A-Za-z0-9_-]{1,64}""")

    private fun target(url: String): Target? {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) return null
        val uri = runCatching { io.ktor.http.Url(trimmed) }.getOrNull() ?: return null
        val host = uri.host.lowercase().removePrefix("www.").removePrefix("m.").ifEmpty { return null }
        val path = uri.encodedPath
        // A malformed escape drops only its own parameter; IDs must look like YouTube IDs.
        val q = uri.encodedQuery.split('&').filter { '=' in it }.mapNotNull {
            val (k, v) = it.split('=', limit = 2)
            runCatching { k to v.decodeURLQueryComponent() }.getOrNull()?.takeIf { (_, value) -> ID.matches(value) }
        }.toMap()
        return when (host) {
            "music.youtube.com", "youtube.com" -> when {
                q["list"] != null && (path == "/playlist" || path == "/watch") -> Target.Playlist(q.getValue("list"))
                path.startsWith("/browse/MPRE") -> Target.Album(path.removePrefix("/browse/").trimEnd('/'))
                path == "/watch" && q["v"] != null -> Target.Video(q.getValue("v"))
                path.startsWith("/shorts/") -> Target.Video(path.removePrefix("/shorts/").trim('/'))
                else -> null
            }
            "youtu.be" -> path.trim('/').takeIf { it.isNotEmpty() }?.let { v -> q["list"]?.let(Target::Playlist) ?: Target.Video(v) }
            else -> null
        }
    }
}
