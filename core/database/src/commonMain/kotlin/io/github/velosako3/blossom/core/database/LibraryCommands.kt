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

    private suspend fun run(ops: List<Operation>): List<OpEnvelope> = runComputed { ops }

    /** Builds the operations from the database inside the same transaction that appends and projects them. */
    private suspend fun runComputed(build: () -> List<Operation>): List<OpEnvelope> {
        val envs = withContext(Dispatchers.IO) {
            db.transactionWithResult { build().map { opLog.appendInTransaction(it) }.also { projector.applyInTransaction(it) } }
        }
        envs.forEach { _localOps.emit(it) } // suspends rather than dropping when listeners are slow
        return envs
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
    /** [inTransaction] runs in the same transaction (an import marks its items written, so a crash can't duplicate). */
    suspend fun addEntries(id: PlaylistId, entries: List<NewEntry>, atIndex: Int? = null, inTransaction: () -> Unit = {}): List<String> {
        if (entries.isEmpty()) return emptyList()
        val ids = entries.map { Uuid.random().toString() }
        runComputed {
            inTransaction()
            val positions = db.blossomQueries.liveEntries(id.value).executeAsList().map { it.position }
            val (before, after) = neighbors(positions, (atIndex ?: positions.size).coerceIn(0, positions.size))
            val keys = FractionalIndex.nBetween(before, after, entries.size)
            entries.mapIndexed { i, e -> Operation.EntryAdded(id, ids[i], e.track, e.status, e.sourceTitle, e.sourceArtist, keys[i], e.matchConfidence) }
        }
        return ids
    }

    suspend fun moveEntry(id: PlaylistId, entryId: String, toIndex: Int) {
        runComputed {
            val others = db.blossomQueries.liveEntries(id.value).executeAsList().filter { it.entry_id != entryId }.map { it.position }
            val (before, after) = neighbors(others, toIndex.coerceIn(0, others.size))
            listOf(Operation.EntryMoved(id, entryId, FractionalIndex.between(before, after)))
        }
    }

    /**
     * Keys around [index]. Concurrent inserts on two devices can leave equal keys (ordered by entry ID); the "after"
     * neighbour skips keys equal to "before" so there is always room, at the cost of landing after the whole tie.
     */
    private fun neighbors(sorted: List<String>, index: Int): Pair<String?, String?> {
        val before = sorted.getOrNull(index - 1)
        val after = sorted.drop(index).firstOrNull { before == null || it > before }
        return before to after
    }

    suspend fun removeEntry(id: PlaylistId, entryId: String) { run(listOf(Operation.EntryRemoved(id, entryId))) }

    suspend fun replaceEntryTrack(id: PlaylistId, entryId: String, track: Track, status: EntryStatus) {
        run(listOf(Operation.EntryTrackReplaced(id, entryId, track, status)))
    }

    suspend fun like(track: Track) { run(listOf(Operation.TrackLiked(track))) }

    /** Likes or unlikes based on the stored state, read in the same transaction (no stale UI flag). */
    suspend fun toggleLike(track: Track) {
        runComputed { if (db.blossomQueries.isLiked(track.id.value).executeAsOneOrNull() == 1L) listOf(Operation.TrackUnliked(track.id)) else listOf(Operation.TrackLiked(track)) }
    }
    suspend fun unlike(trackId: TrackId) { run(listOf(Operation.TrackUnliked(trackId))) }
    suspend fun recordPlay(event: PlayEvent) { run(listOf(Operation.PlayRecorded(event))) }
    suspend fun changeSetting(key: String, jsonValue: String) { run(listOf(Operation.SettingChanged(key, jsonValue))) }
}
