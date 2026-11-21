// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
enum class EntryStatus { MATCHED, UNCERTAIN, UNAVAILABLE }

@Serializable
data class PlaylistEntry(
    val entryId: String,
    val track: Track?,
    val status: EntryStatus,
    val sourceTitle: String,
    val sourceArtist: String,
    /** Fractional index; entries sort by it. */
    val position: String,
    val addedAt: Instant,
    val matchConfidence: Float?,
)

/** Where an imported playlist came from; powers "Refresh from source" and duplicate detection. */
@Serializable
data class PlaylistOrigin(val source: SourceKind?, val service: String, val url: String)

data class Playlist(
    val id: PlaylistId,
    val name: String,
    val description: String?,
    val entries: List<PlaylistEntry>,
    val origin: PlaylistOrigin?,
    val artworkUrl: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
