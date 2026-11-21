// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.model.sync

import io.github.velosako3.blossom.core.model.EntryStatus
import io.github.velosako3.blossom.core.model.PlayEvent
import io.github.velosako3.blossom.core.model.PlaylistId
import io.github.velosako3.blossom.core.model.PlaylistOrigin
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Every library change. Operations are appended to the device's log and synced; the library is their projection. */
@Serializable
sealed interface Operation {
    @Serializable @SerialName("PlaylistCreated")
    data class PlaylistCreated(val playlistId: PlaylistId, val name: String, val description: String?, val origin: PlaylistOrigin?) : Operation
    @Serializable @SerialName("PlaylistRenamed")
    data class PlaylistRenamed(val playlistId: PlaylistId, val name: String) : Operation
    @Serializable @SerialName("PlaylistDescriptionSet")
    data class PlaylistDescriptionSet(val playlistId: PlaylistId, val description: String?) : Operation
    @Serializable @SerialName("PlaylistDeleted")
    data class PlaylistDeleted(val playlistId: PlaylistId) : Operation
    @Serializable @SerialName("EntryAdded")
    data class EntryAdded(
        val playlistId: PlaylistId, val entryId: String, val track: Track?, val status: EntryStatus,
        val sourceTitle: String, val sourceArtist: String, val position: String, val matchConfidence: Float?,
    ) : Operation
    @Serializable @SerialName("EntryMoved")
    data class EntryMoved(val playlistId: PlaylistId, val entryId: String, val position: String) : Operation
    @Serializable @SerialName("EntryRemoved")
    data class EntryRemoved(val playlistId: PlaylistId, val entryId: String) : Operation
    @Serializable @SerialName("EntryTrackReplaced")
    data class EntryTrackReplaced(val playlistId: PlaylistId, val entryId: String, val track: Track, val status: EntryStatus) : Operation
    @Serializable @SerialName("TrackLiked")
    data class TrackLiked(val track: Track) : Operation
    @Serializable @SerialName("TrackUnliked")
    data class TrackUnliked(val trackId: TrackId) : Operation
    @Serializable @SerialName("PlayRecorded")
    data class PlayRecorded(val event: PlayEvent) : Operation
    /** Allowlisted keys only (see the sync feature's SyncedSettings). */
    @Serializable @SerialName("SettingChanged")
    data class SettingChanged(val key: String, val jsonValue: String) : Operation
}

/**
 * An operation as stored and synced. [seq] counts the originating device's operations from 1, so a peer can ask
 * "everything from device X after seq N".
 */
@Serializable
data class OpEnvelope(val opId: String, val hlc: Hlc, val deviceId: String, val seq: Long, val op: Operation)

/** The JSON used for operation payloads everywhere (database and sync files). Unknown keys are tolerated. */
val OperationJson = Json { ignoreUnknownKeys = true; classDiscriminator = "type"; encodeDefaults = true }
