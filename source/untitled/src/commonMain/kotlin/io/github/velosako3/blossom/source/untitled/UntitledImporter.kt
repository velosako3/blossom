// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.untitled

import io.github.velosako3.blossom.core.model.PlaylistOrigin
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.source.api.ImportError
import io.github.velosako3.blossom.source.api.ImportedPlaylist
import io.github.velosako3.blossom.source.api.ImportedTrack
import io.github.velosako3.blossom.source.api.PlaylistImporter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** untitled projects and tracks. Everything streams natively from untitled. */
class UntitledImporter(private val client: UntitledClient) : PlaylistImporter {
    override fun canHandle(url: String) = UntitledLinks.parse(url) != null

    override suspend fun read(url: String, onProgress: (loaded: Int, total: Int?) -> Unit): ImportedPlaylist = try {
        when (val link = UntitledLinks.parse(url) ?: throw ImportError.NotFound(url)) {
            is UntitledLink.Invite -> read(client.followInvite(link.code), onProgress)
            is UntitledLink.Project -> {
                val data = client.project(link.slug)
                val meta = data.obj("project") ?: throw ImportError.NotFound(url)
                val title = meta.str("title").orEmpty()
                val art = meta.str("artwork_signed_url")
                val tracks = (data["tracks"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.toTrack(link.slug, title, art) }
                onProgress(tracks.size, tracks.size)
                ImportedPlaylist(title, meta.str("username"), art, origin("https://untitled.stream/library/project/${link.slug}"), tracks.map(::imported))
            }
            is UntitledLink.Track -> {
                val t = UntitledSource(client).track(TrackId.of(SourceKind.UNTITLED, link.project?.let { "$it/${link.slug}" } ?: link.slug))
                    ?: throw ImportError.NotFound(url)
                val canonical = link.project?.let { "https://untitled.stream/library/project/$it/track/${link.slug}" } ?: "https://untitled.stream/library/track/${link.slug}"
                ImportedPlaylist(t.title, null, t.artworkUrl, origin(canonical), listOf(imported(t)))
            }
        }
    } catch (e: UntitledClient.Gone) {
        throw ImportError.NotFound(url)
    }

    private fun imported(t: Track) = ImportedTrack(t.title, t.artists.map { it.name }, t.durationMs, null, t, t.album?.title, sourceKey = t.id.value)

    private fun origin(url: String) = PlaylistOrigin(SourceKind.UNTITLED, "untitled", url)
}
