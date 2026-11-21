// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.database

import io.github.velosako3.blossom.core.model.EntryStatus
import io.github.velosako3.blossom.core.model.PlayEvent
import io.github.velosako3.blossom.core.model.PlaylistId
import io.github.velosako3.blossom.core.model.PlaylistOrigin
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.core.model.sync.FractionalIndex
import io.github.velosako3.blossom.core.model.sync.OpEnvelope
import io.github.velosako3.blossom.core.model.sync.Operation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** An entry to add; [track] is null when an import found no playable match. */
data class NewEntry(
    val track: Track?,
    val status: EntryStatus,
    val sourceTitle: String,
    val sourceArtist: String,
    val matchConfidence: Float?,
)

/**
 * The only way the library changes: each command appends its operation(s) to this device's log and projects them,
 * in one transaction. [localOps] emits every appended operation (sync and offline auto-save listen to it).
 */
@OptIn(ExperimentalUuidApi::class)
class LibraryCommands(
    private val opLog: SqlOpLog,
    private val projector: LibraryProjector,
    private val db: BlossomDatabase,
    @Suppress("unused") private val clock: Clock = Clock.System,
) {
    private val _localOps = MutableSharedFlow<OpEnvelope>(extraBufferCapacity = 256)
    val localOps: SharedFlow<OpEnvelope> = _localOps

    private suspend fun run(ops: List<Operation>): List<OpEnvelope> = withContext(Dispatchers.IO) {
        db.transactionWithResult {
            ops.map { opLog.appendInTransaction(it) }.also { projector.applyInTransaction(it) }
        }.onEach { _localOps.tryEmit(it) }
    }

    suspend fun createPlaylist(name: String, description: String? = null, origin: PlaylistOrigin? = null): PlaylistId {
        val id = PlaylistId(Uuid.random().toString())
        run(listOf(Operation.PlaylistCreated(id, name, description, origin)))
        return id
    }

    suspend fun renamePlaylist(id: PlaylistId, name: String) { run(listOf(Operation.PlaylistRenamed(id, name))) }
    suspend fun setDescription(id: PlaylistId, description: String?) { run(listOf(Operation.PlaylistDescriptionSet(id, description))) }
    suspend fun deletePlaylist(id: PlaylistId) { run(listOf(Operation.PlaylistDeleted(id))) }

    /** Adds [entries] in order, appended or before the entry now at [atIndex]. Returns the new entry IDs. */
    suspend fun addEntries(id: PlaylistId, entries: List<NewEntry>, atIndex: Int? = null): List<String> {
        if (entries.isEmpty()) return emptyList()
        val positions = withContext(Dispatchers.IO) { db.blossomQueries.liveEntries(id.value).executeAsList().map { it.position } }
        val index = (atIndex ?: positions.size).coerceIn(0, positions.size)
        val keys = FractionalIndex.nBetween(positions.getOrNull(index - 1), positions.getOrNull(index), entries.size)
        val ids = entries.map { Uuid.random().toString() }
        run(entries.mapIndexed { i, e -> Operation.EntryAdded(id, ids[i], e.track, e.status, e.sourceTitle, e.sourceArtist, keys[i], e.matchConfidence) })
        return ids
    }

    suspend fun moveEntry(id: PlaylistId, entryId: String, toIndex: Int) {
        val others = withContext(Dispatchers.IO) { db.blossomQueries.liveEntries(id.value).executeAsList() }.filter { it.entry_id != entryId }
        val i = toIndex.coerceIn(0, others.size)
        val key = FractionalIndex.between(others.getOrNull(i - 1)?.position, others.getOrNull(i)?.position)
        run(listOf(Operation.EntryMoved(id, entryId, key)))
    }

    suspend fun removeEntry(id: PlaylistId, entryId: String) { run(listOf(Operation.EntryRemoved(id, entryId))) }

    suspend fun replaceEntryTrack(id: PlaylistId, entryId: String, track: Track, status: EntryStatus) {
        run(listOf(Operation.EntryTrackReplaced(id, entryId, track, status)))
    }

    suspend fun like(track: Track) { run(listOf(Operation.TrackLiked(track))) }
    suspend fun unlike(trackId: TrackId) { run(listOf(Operation.TrackUnliked(trackId))) }
    suspend fun recordPlay(event: PlayEvent) { run(listOf(Operation.PlayRecorded(event))) }
    suspend fun changeSetting(key: String, jsonValue: String) { run(listOf(Operation.SettingChanged(key, jsonValue))) }
}
