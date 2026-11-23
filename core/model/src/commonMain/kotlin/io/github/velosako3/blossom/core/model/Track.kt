// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ArtistRef(val id: String?, val name: String)

@Serializable
data class AlbumRef(val id: String?, val title: String)

/** A playable song. Snapshots of it are stored with playlist entries, likes and plays. */
@Serializable
data class Track(
    val id: TrackId,
    val title: String,
    val artists: List<ArtistRef>,
    val album: AlbumRef?,
    val durationMs: Long,
    val artworkUrl: String?,
    val explicit: Boolean = false,
) {
    val artistNames: String get() = artists.joinToString(", ") { it.name }
}
