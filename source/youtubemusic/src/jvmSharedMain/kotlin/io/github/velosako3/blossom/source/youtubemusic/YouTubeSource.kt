// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.youtubemusic

import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.source.api.AudioQuality
import io.github.velosako3.blossom.source.api.MusicSource
import io.github.velosako3.blossom.source.api.SearchFilter
import io.github.velosako3.blossom.source.api.SearchResults
import io.github.velosako3.blossom.source.api.StreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Plain YouTube uploads (not YouTube Music) — where re-uploads of leaks end up. Used by "Find replacement".
 * Search goes through NewPipe's YouTube search; playback uses the same stream pipeline as YouTube Music.
 */
class YouTubeSource(private val streams: YouTubeStreams) : MusicSource {
    override val kind = SourceKind.YOUTUBE

    override suspend fun search(query: String, filter: SearchFilter): SearchResults = withContext(Dispatchers.IO) {
        streams.ensureNewPipe()
        val extractor = ServiceList.YouTube.getSearchExtractor(query, listOf("videos"), "")
        extractor.fetchPage()
        SearchResults(extractor.initialPage.items.filterIsInstance<StreamInfoItem>().mapNotNull { it.toTrack() })
    }

    override suspend fun track(id: TrackId): Track? = null

    override suspend fun resolveStream(id: TrackId, quality: AudioQuality): StreamInfo = streams.resolve(id.sourceId, quality)

    private fun StreamInfoItem.toTrack(): Track? {
        val videoId = url.substringAfter("v=", "").substringBefore('&').ifBlank { return null }
        return Track(
            id = TrackId.of(SourceKind.YOUTUBE, videoId),
            title = name,
            artists = listOf(ArtistRef(null, uploaderName ?: YtmParser.UNKNOWN)),
            album = null,
            durationMs = duration.coerceAtLeast(0) * 1000,
            artworkUrl = thumbnails.maxByOrNull { it.width }?.url,
        )
    }
}
