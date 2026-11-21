// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.api

import io.github.velosako3.blossom.core.model.PlaylistOrigin
import io.github.velosako3.blossom.core.model.Track

data class ImportedTrack(
    val title: String,
    val artists: List<String>,
    val durationMs: Long?,
    val isrc: String?,
    /** Non-null when the track plays natively (ytm, sc, untitled) and needs no matching. */
    val nativeTrack: Track?,
    val album: String? = null,
    /** The service's own key for this track, used by "Refresh from source" to skip tracks already imported. */
    val sourceKey: String? = null,
    /** Why the track can't play natively, e.g. `previewOnly` for SoundCloud Go+ snippets. */
    val unplayableReason: String? = null,
)

data class ImportedPlaylist(
    val name: String,
    val description: String?,
    val artworkUrl: String?,
    val origin: PlaylistOrigin,
    val tracks: List<ImportedTrack>,
)

interface PlaylistImporter {
    fun canHandle(url: String): Boolean
    suspend fun read(url: String, onProgress: (loaded: Int, total: Int?) -> Unit = { _, _ -> }): ImportedPlaylist
}
