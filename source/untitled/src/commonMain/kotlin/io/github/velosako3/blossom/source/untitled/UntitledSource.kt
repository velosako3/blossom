// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.untitled

import io.github.velosako3.blossom.core.model.AlbumRef
import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.source.api.AudioQuality
import io.github.velosako3.blossom.source.api.MusicSource
import io.github.velosako3.blossom.source.api.SearchFilter
import io.github.velosako3.blossom.source.api.SearchResults
import io.github.velosako3.blossom.source.api.StreamInfo
import io.github.velosako3.blossom.source.api.TrackUnavailableException
import kotlinx.serialization.json.JsonElement
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/** untitled.stream tracks, streamed straight from untitled. Titles, uploader names and artwork are kept verbatim. */
class UntitledSource(private val client: UntitledClient, private val clock: Clock = Clock.System) : MusicSource {
    override val kind = SourceKind.UNTITLED

    /** untitled has no public search; songs come in through links. */
    override suspend fun search(query: String, filter: SearchFilter): SearchResults = SearchResults.Empty

    override suspend fun track(id: TrackId): Track? = try {
        val (project, data) = trackData(id)
        if (project == null) data.toTrack(null, null, null)
        else client.project(project).let { p -> data.toTrack(project, p.obj("project")?.str("title"), p.obj("project")?.str("artwork_signed_url")) }
    } catch (_: UntitledClient.Gone) {
        null
    }

    /**
     * A track's data. Tracks inside a project have no page of their own (it answers 204), so their ID is
     * `<project slug>/<track slug>` and the data comes from the project.
     */
    private suspend fun trackData(id: TrackId): Pair<String?, kotlinx.serialization.json.JsonObject> {
        val project = id.sourceId.substringBefore('/', "").ifEmpty { null }
        if (project == null) return null to client.track(id.sourceId)
        val slug = id.sourceId.substringAfter('/')
        val tracks = client.project(project)["tracks"] as? kotlinx.serialization.json.JsonArray
        val t = tracks.orEmpty().firstOrNull { (it as? kotlinx.serialization.json.JsonObject)?.str("slug") == slug } as? kotlinx.serialization.json.JsonObject
        return project to (t ?: throw UntitledClient.Gone(slug))
    }

    override suspend fun resolveStream(id: TrackId, quality: AudioQuality): StreamInfo {
        val t = try { trackData(id).second } catch (e: UntitledClient.Gone) { throw TrackUnavailableException("untitled link is private or was removed", e) }
        // The mp3 transcode when there is one (smaller, always mp3), else the original upload.
        val storage = t.str("audio_fallback_url").takeIf { t.str("transcoding_state") == "transcoded" } ?: t.str("audio_url")
            ?: throw TrackUnavailableException("no audio")
        val (url, lifetimeMs) = try { client.signedUrl(storage) } catch (e: UntitledClient.Gone) { throw TrackUnavailableException("untitled audio was removed", e) }
        val mime = if (storage.endsWith(".mp3", ignoreCase = true)) "audio/mpeg" else "audio/" + storage.substringAfterLast('.').lowercase()
        return StreamInfo(url, mapOf("User-Agent" to UntitledClient.USER_AGENT), mime, null, clock.now() + lifetimeMs.milliseconds)
    }
}

/**
 * An untitled track as a blossom [Track]: `untitled:<slug>` on its own, `untitled:<project slug>/<slug>` inside a
 * project; uploader as artist, project as album.
 */
internal fun JsonElement.toTrack(projectSlug: String?, project: String?, projectArtwork: String?): Track? {
    val slug = str("slug") ?: return null
    return Track(
        id = TrackId.of(SourceKind.UNTITLED, if (projectSlug != null) "$projectSlug/$slug" else slug),
        title = str("title") ?: return null,
        artists = listOfNotNull(str("username")).map { ArtistRef(null, it) },
        album = project?.let { AlbumRef(null, it) },
        durationMs = ((double("duration") ?: 0.0) * 1000).toLong(),
        artworkUrl = str("artwork_signed_url") ?: projectArtwork,
    )
}
