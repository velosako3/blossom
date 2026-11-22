// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.importer

import io.github.velosako3.blossom.core.model.EntryStatus
import io.github.velosako3.blossom.core.model.PlaylistId
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.source.api.ImportedTrack
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@JvmInline
value class ImportJobId(val value: String)

@Serializable
sealed interface ImportTarget {
    /** A new playlist named [name], or after the source when null. */
    @Serializable @SerialName("new") data class NewPlaylist(val name: String?) : ImportTarget
    @Serializable @SerialName("existing") data class Existing(val id: PlaylistId) : ImportTarget
    /** "Refresh from source": adds only tracks not already in the playlist, never removes the user's edits. */
    @Serializable @SerialName("update") data class UpdateExisting(val id: PlaylistId) : ImportTarget
}

enum class ImportJobState { READING, MATCHING, DONE, FAILED }

/** Why a job FAILED. Network trouble never fails a job: it waits and resumes. */
enum class ImportFailure { NEEDS_LOGIN, NOT_FOUND, UNSUPPORTED }

data class ImportProgress(
    val state: ImportJobState,
    val total: Int?,
    val done: Int,
    val matched: Int,
    val uncertain: Int,
    val unavailable: Int,
    val playlistId: PlaylistId?,
    val failure: ImportFailure? = null,
)

/** [ImportedTrack] as stored in `import_job_item`. */
@Serializable
internal data class StoredImport(
    val title: String,
    val artists: List<String>,
    val durationMs: Long?,
    val isrc: String?,
    val nativeTrack: Track?,
    val album: String?,
    val sourceKey: String?,
) {
    fun toImported() = ImportedTrack(title, artists, durationMs, isrc, nativeTrack, album, sourceKey)

    companion object {
        fun of(t: ImportedTrack) = StoredImport(t.title, t.artists, t.durationMs, t.isrc, t.nativeTrack, t.album, t.sourceKey)
    }
}

@Serializable
internal data class StoredMatch(val track: Track?, val confidence: Float, val status: EntryStatus)
