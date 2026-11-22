// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.soundcloud

import io.github.velosako3.blossom.core.model.PlaylistOrigin
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.source.api.ImportError
import io.github.velosako3.blossom.source.api.ImportedPlaylist
import io.github.velosako3.blossom.source.api.ImportedTrack
import io.github.velosako3.blossom.source.api.PlaylistImporter
import kotlinx.serialization.json.JsonElement
import java.net.URI

/**
 * SoundCloud track and set links (and `on.soundcloud.com` short links). Tracks play natively; Go+ preview-only tracks
 * are flagged so the matcher finds them on YouTube Music instead.
 */
class SoundCloudImporter(private val client: SoundCloudClient) : PlaylistImporter {
    override fun canHandle(url: String): Boolean {
        val host = runCatching { URI(url.trim()).host?.lowercase() }.getOrNull() ?: return false
        return host == "soundcloud.com" || host.endsWith(".soundcloud.com")
    }

    override suspend fun read(url: String, onProgress: (loaded: Int, total: Int?) -> Unit): ImportedPlaylist {
        val canonical = canonical(url)
        val item = try { client.api("/resolve", mapOf("url" to canonical)) } catch (e: SoundCloudClient.NotFound) {
            throw ImportError.NotFound(canonical)
        }
        val origin = PlaylistOrigin(SourceKind.SOUNDCLOUD, "SoundCloud", canonical)
        return when (item.str("kind")) {
            "track" -> ImportedPlaylist(item.str("title").orEmpty(), null, item.str("artwork_url"), origin, listOf(imported(item)))
            "playlist", "system-playlist" -> {
                val entries = item.arr("tracks").orEmpty()
                // Sets list their first few tracks in full and the rest as IDs only; fetch those 50 at a time.
                val stubs = entries.filter { it.str("title") == null }.mapNotNull { it.long("id") }
                val fetched = stubs.chunked(50).flatMap { ids ->
                    client.api("/tracks", mapOf("ids" to ids.joinToString(","))).let { it as? kotlinx.serialization.json.JsonArray }.orEmpty()
                }.associateBy { it.long("id") }
                val tracks = entries.mapNotNull { e -> (if (e.str("title") != null) e else fetched[e.long("id")])?.let(::imported) }
                onProgress(tracks.size, tracks.size)
                ImportedPlaylist(item.str("title").orEmpty(), item.obj("user")?.str("username"), item.str("artwork_url"), origin, tracks)
            }
            else -> throw ImportError.NotFound(canonical)
        }
    }

    private fun imported(t: JsonElement): ImportedTrack {
        val preview = t.previewOnly
        return ImportedTrack(
            title = t.str("title").orEmpty(),
            artists = listOf(t.artistName()).filter { it.isNotBlank() },
            durationMs = t.long("full_duration") ?: t.long("duration"),
            isrc = t.obj("publisher_metadata")?.str("isrc"),
            nativeTrack = if (preview) null else t.toTrack(),
            album = t.obj("publisher_metadata")?.str("album_title"),
            sourceKey = t.long("id")?.let { "sc:$it" },
            unplayableReason = if (preview) "previewOnly" else null,
        )
    }

    /** Follows short links; drops query strings (`si`, `utm_*`) and fragments. */
    private suspend fun canonical(url: String): String {
        val u = URI(url.trim())
        val full = if (u.host.equals("on.soundcloud.com", ignoreCase = true)) URI(client.followShortLink(u.path.trim('/'))) else u
        return "https://soundcloud.com" + full.path.trimEnd('/')
    }
}
