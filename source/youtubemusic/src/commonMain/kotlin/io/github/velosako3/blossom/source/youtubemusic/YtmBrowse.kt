// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.youtubemusic

import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.source.api.AlbumSummary
import io.github.velosako3.blossom.source.api.ArtistSummary
import io.github.velosako3.blossom.source.api.PlaylistSummary

/** One card in a home or artist carousel. */
sealed interface HomeItem {
    data class Song(val track: Track) : HomeItem
    data class Album(val album: AlbumSummary) : HomeItem
    data class Artist(val artist: ArtistSummary) : HomeItem
    data class Playlist(val playlist: PlaylistSummary) : HomeItem
}

data class HomeSection(val title: String, val items: List<HomeItem>, val strapline: String? = null)

data class AlbumPage(
    val id: String,
    val title: String,
    val artists: List<ArtistRef>,
    val year: Int?,
    val artworkUrl: String?,
    val tracks: List<Track>,
    val playlistId: String?,
)

data class ArtistPage(
    val id: String,
    val name: String,
    val artworkUrl: String?,
    val description: String?,
    val topSongs: List<Track>,
    val sections: List<HomeSection>,
)

data class PlaylistPage(
    val id: String,
    val title: String,
    val author: String?,
    val artworkUrl: String?,
    val tracks: List<Track>,
    val continuation: String?,
)

/** YouTube Music browsing beyond plain search. */
interface YtmBrowse {
    suspend fun home(): List<HomeSection>
    suspend fun album(browseId: String): AlbumPage
    suspend fun artist(browseId: String): ArtistPage
    suspend fun playlist(id: String): PlaylistPage
    suspend fun radio(seed: TrackId): List<Track>
}
