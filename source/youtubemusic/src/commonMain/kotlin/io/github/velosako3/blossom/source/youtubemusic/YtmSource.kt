// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.youtubemusic

import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.core.platform.SecureStore
import io.github.velosako3.blossom.source.api.AudioQuality
import io.github.velosako3.blossom.source.api.MusicSource
import io.github.velosako3.blossom.source.api.SearchFilter
import io.github.velosako3.blossom.source.api.SearchResults
import io.github.velosako3.blossom.source.api.StreamInfo
import java.io.File

/** Search params YouTube Music's own filter chips send. */
internal object YtmSearchParams {
    const val SONGS = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
    const val ALBUMS = "EgWKAQIYAWoKEAkQBRAKEAMQBA%3D%3D"
    const val ARTISTS = "EgWKAQIgAWoKEAkQBRAKEAMQBA%3D%3D"
    const val PLAYLISTS = "EgeKAQQoAEABagoQCRAFEAoQAxAE"
    const val VIDEOS = "EgWKAQIQAWoKEAkQBRAKEAMQBA%3D%3D"
}

/**
 * YouTube Music as a guest by default. Signing in is optional: a cookie stored under [COOKIE_KEY] in [SecureStore]
 * personalises the home feed and unlocks the account's own playlists, exactly like BitChord's sign-in.
 */
class YtmSource(
    private val secureStore: SecureStore,
    cacheDir: File,
    val client: InnertubeClient = InnertubeClient(cookie = { secureStore.get(COOKIE_KEY) }),
    private val streams: YouTubeStreams = YouTubeStreams({ secureStore.get(COOKIE_KEY) }, cacheDir),
) : MusicSource, YtmBrowse {
    override val kind = SourceKind.YOUTUBE_MUSIC

    override suspend fun search(query: String, filter: SearchFilter): SearchResults = when (filter) {
        SearchFilter.SONGS -> SearchResults(YtmParser.searchSongs(client.search(query, YtmSearchParams.SONGS)))
        SearchFilter.ALBUMS -> YtmParser.search(client.search(query, YtmSearchParams.ALBUMS)).copy(songs = emptyList())
        SearchFilter.ARTISTS -> YtmParser.search(client.search(query, YtmSearchParams.ARTISTS)).copy(songs = emptyList())
        SearchFilter.PLAYLISTS -> YtmParser.search(client.search(query, YtmSearchParams.PLAYLISTS)).copy(songs = emptyList())
        SearchFilter.VIDEOS -> SearchResults(emptyList())
    }

    /** All four kinds at once (the unfiltered search page). */
    suspend fun searchAll(query: String): SearchResults = YtmParser.search(client.search(query))

    override suspend fun track(id: TrackId): Track? =
        YtmParser.watchQueue(client.next(id.sourceId)).firstOrNull { it.id == id }

    override suspend fun resolveStream(id: TrackId, quality: AudioQuality): StreamInfo = streams.resolve(id.sourceId, quality)

    override suspend fun home(): List<HomeSection> = YtmParser.home(client.browse("FEmusic_home"))
    override suspend fun album(browseId: String): AlbumPage = YtmParser.album(browseId, client.browse(browseId))
    override suspend fun artist(browseId: String): ArtistPage = YtmParser.artist(browseId, client.browse(browseId))

    override suspend fun playlist(id: String): PlaylistPage {
        val browseId = if (id.startsWith("VL") || id.startsWith("MPRE")) id else "VL$id"
        var page = YtmParser.playlist(id, client.browse(browseId))
        val all = page.tracks.toMutableList()
        var token = page.continuation
        var guard = 0
        while (token != null && guard++ < MAX_PAGES) {
            val (more, next) = YtmParser.continuationTracks(client.browseContinuation(token))
            if (more.isEmpty()) break
            all += more
            token = next
        }
        page = page.copy(tracks = all.distinctBy { it.id }, continuation = null)
        return page
    }

    override suspend fun radio(seed: TrackId): List<Track> =
        YtmParser.watchQueue(client.next(seed.sourceId)).filterNot { it.id == seed }

    companion object {
        const val COOKIE_KEY = "ytm.cookie"
        private const val MAX_PAGES = 200
    }
}
