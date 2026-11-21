// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.api

import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.Track

enum class SearchFilter { SONGS, ALBUMS, ARTISTS, PLAYLISTS, VIDEOS }

data class AlbumSummary(val id: String, val title: String, val artists: List<ArtistRef>, val year: Int?, val artworkUrl: String?)
data class ArtistSummary(val id: String, val name: String, val artworkUrl: String?)
data class PlaylistSummary(val id: String, val title: String, val author: String?, val artworkUrl: String?, val trackCount: Int?)

data class SearchResults(
    val songs: List<Track>,
    val albums: List<AlbumSummary> = emptyList(),
    val artists: List<ArtistSummary> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList(),
) {
    companion object {
        val Empty = SearchResults(emptyList())
    }
}
