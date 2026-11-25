// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.database

import kotlinx.coroutines.IO
import io.github.velosako3.blossom.core.model.PlaylistOrigin
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.sync.OpEnvelope
import io.github.velosako3.blossom.core.model.sync.Operation
import io.github.velosako3.blossom.core.model.sync.OperationJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Projects operations into the library tables. Idempotent and order-independent: every field remembers the HLC of
 * the write that set it and only a later HLC replaces it (last writer wins); a playlist deletion is final; a removed
 * entry stays removed whatever moves arrive; plays are a union by ID.
 */
class LibraryProjector(private val db: BlossomDatabase, private val settingAllowed: (String) -> Boolean = { true }) {
    private val q get() = db.blossomQueries

    suspend fun apply(ops: List<OpEnvelope>) = withContext(Dispatchers.IO) { applyBlocking(ops) }

    fun applyBlocking(ops: List<OpEnvelope>) = db.transaction { applyInTransaction(ops) }

    fun applyInTransaction(ops: List<OpEnvelope>) = ops.forEach(::applyOne)

    private fun applyOne(env: OpEnvelope) {
        val h = env.hlc.encode()
        val at = env.hlc.wallMs
        when (val op = env.op) {
            is Operation.PlaylistCreated -> {
                val id = op.playlistId.value
                q.ensurePlaylist(id)
                q.setPlaylistCreated(h, op.origin?.let { OperationJson.encodeToString(PlaylistOrigin.serializer(), it) }, at, id)
                q.setPlaylistName(op.name, h, at, id)
                q.setPlaylistDescription(op.description, h, at, id)
            }
            is Operation.PlaylistRenamed -> { q.ensurePlaylist(op.playlistId.value); q.setPlaylistName(op.name, h, at, op.playlistId.value) }
            is Operation.PlaylistDescriptionSet -> { q.ensurePlaylist(op.playlistId.value); q.setPlaylistDescription(op.description, h, at, op.playlistId.value) }
            is Operation.PlaylistDeleted -> { q.ensurePlaylist(op.playlistId.value); q.markPlaylistDeleted(h, op.playlistId.value) }
            is Operation.EntryAdded -> {
                val p = op.playlistId.value
                q.ensurePlaylist(p)
                q.ensureEntry(p, op.entryId)
                q.setEntryAdded(op.sourceTitle, op.sourceArtist, op.matchConfidence?.toDouble(), at, h, p, op.entryId)
                q.setEntryPosition(op.position, h, p, op.entryId)
                q.setEntryTrack(op.track?.let(::trackJson), op.status.name, h, p, op.entryId)
                q.touchPlaylist(at, p)
            }
            is Operation.EntryMoved -> {
                q.ensurePlaylist(op.playlistId.value); q.ensureEntry(op.playlistId.value, op.entryId)
                q.setEntryPosition(op.position, h, op.playlistId.value, op.entryId)
            }
            is Operation.EntryRemoved -> {
                q.ensurePlaylist(op.playlistId.value); q.ensureEntry(op.playlistId.value, op.entryId)
                q.markEntryRemoved(h, op.playlistId.value, op.entryId)
                q.touchPlaylist(at, op.playlistId.value)
            }
            is Operation.EntryTrackReplaced -> {
                q.ensurePlaylist(op.playlistId.value); q.ensureEntry(op.playlistId.value, op.entryId)
                q.setEntryTrack(trackJson(op.track), op.status.name, h, op.playlistId.value, op.entryId)
            }
            is Operation.TrackLiked -> {
                q.ensureLike(op.track.id.value, trackJson(op.track))
                if (q.setLiked(1, h, at, op.track.id.value).value > 0) q.setLikeTrack(trackJson(op.track), op.track.id.value)
            }
            is Operation.TrackUnliked -> { q.ensureLike(op.trackId.value, ""); q.setLiked(0, h, at, op.trackId.value) }
            is Operation.PlayRecorded -> with(op.event) {
                q.insertPlay(id, track.id.value, trackJson(track), startedAt.toEpochMilliseconds(), playedMs, if (counted) 1 else 0, deviceId)
            }
            is Operation.SettingChanged -> if (settingAllowed(op.key)) { q.ensureSetting(op.key); q.setSetting(op.jsonValue, h, op.key) }
        }
    }

    private fun trackJson(t: Track) = OperationJson.encodeToString(Track.serializer(), t)
}
