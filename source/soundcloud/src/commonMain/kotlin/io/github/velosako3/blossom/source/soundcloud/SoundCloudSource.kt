// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.soundcloud

import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.source.api.AudioQuality
import io.github.velosako3.blossom.source.api.MusicSource
import io.github.velosako3.blossom.source.api.SearchFilter
import io.github.velosako3.blossom.source.api.SearchResults
import io.github.velosako3.blossom.source.api.StreamInfo
import io.github.velosako3.blossom.source.api.TrackUnavailableException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** SoundCloud tracks: search (used by "Find replacement"), lookup and streams. No account needed. */
class SoundCloudSource(private val client: SoundCloudClient, private val clock: Clock = Clock.System) : MusicSource {
    override val kind = SourceKind.SOUNDCLOUD

    override suspend fun search(query: String, filter: SearchFilter): SearchResults {
        if (filter != SearchFilter.SONGS) return SearchResults.Empty
        val r = client.api("/search/tracks", mapOf("q" to query, "limit" to "30"))
        return SearchResults(r.arr("collection").orEmpty().filterNot { it.previewOnly }.mapNotNull { it.toTrack() })
    }

    /** Null only when the track is gone; network trouble throws, so a dead-link check can't mistake it for removal. */
    override suspend fun track(id: TrackId): Track? =
        try { client.api("/tracks/${id.sourceId}").toTrack() } catch (_: SoundCloudClient.NotFound) { null }

    override suspend fun resolveStream(id: TrackId, quality: AudioQuality): StreamInfo {
        val json = try { client.api("/tracks/${id.sourceId}") } catch (e: SoundCloudClient.NotFound) {
            throw TrackUnavailableException("removed from SoundCloud", e)
        }
        if (json.previewOnly) throw TrackUnavailableException("Go+ preview only")
        val t = selectTranscoding(json.transcodings()) ?: throw TrackUnavailableException("no stream")
        val url = client.streamUrl(t.url, json.str("track_authorization"))
        return StreamInfo(
            url = url,
            headers = emptyMap(),
            mimeType = if (t.protocol == "hls") "application/vnd.apple.mpegurl" else t.mimeType.substringBefore(';'),
            bitrateKbps = Regex("""(\d+)k""").find(t.preset)?.groupValues?.get(1)?.toIntOrNull(),
            // Signed media URLs last a while; re-resolving before the hour is cheap.
            expiresAt = clock.now() + 50.minutes,
        )
    }
}
