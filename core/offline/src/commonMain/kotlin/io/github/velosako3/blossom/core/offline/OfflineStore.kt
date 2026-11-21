// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.offline

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import app.cash.sqldelight.coroutines.mapToOneOrNull
import io.github.velosako3.blossom.core.database.BlossomDatabase
import io.github.velosako3.blossom.core.database.LibraryRepository
import io.github.velosako3.blossom.core.model.PlaylistId
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

enum class OfflineState { NOT_SAVED, QUEUED, DOWNLOADING, SAVED, PAUSED_STORAGE, PAUSED_NETWORK, FAILED }

/** Why a track is saved. A track stays saved while any reference points at it. */
sealed interface OfflineRef {
    data class Single(val trackId: TrackId) : OfflineRef
    data class FromPlaylist(val playlistId: PlaylistId) : OfflineRef
    data object UntitledAuto : OfflineRef
}

internal val OfflineRef.key: String
    get() = when (this) {
        is OfflineRef.Single -> "single"
        is OfflineRef.FromPlaylist -> "playlist:${playlistId.value}"
        OfflineRef.UntitledAuto -> "untitled"
    }

interface OfflineStore {
    fun state(id: TrackId): Flow<OfflineState>
    fun progress(id: TrackId): Flow<Float>
    suspend fun saveTrack(track: Track, ref: OfflineRef)
    suspend fun unsave(ref: OfflineRef)
    /** Also saves entries added to the playlist later, and drops entries removed from it. */
    suspend fun savePlaylist(id: PlaylistId)
    suspend fun unsavePlaylist(id: PlaylistId)
    fun openReader(id: TrackId): DecryptingReader?
    fun usageBytes(): Flow<Long>
    suspend fun clearAll()
}

/** [repo] is only needed for saved playlists. */
class SqlOfflineStore(
    private val db: BlossomDatabase,
    private val manager: DownloadManager,
    private val repo: LibraryRepository?,
    private val scope: CoroutineScope,
) : OfflineStore {
    private val q get() = db.blossomQueries
    private val io = Dispatchers.IO
    private val json = Json { ignoreUnknownKeys = true }
    private val watchers = ConcurrentHashMap<String, Job>()

    init {
        scope.launch(io) {
            q.offlinePlaylists().executeAsList().forEach { watch(PlaylistId(it)) }
            manager.kick(retryPaused = true)
        }
    }

    override fun state(id: TrackId): Flow<OfflineState> =
        q.offlineItem(id.value).asFlow().mapToOneOrNull(io).map { row -> row?.let { OfflineState.valueOf(it.state) } ?: OfflineState.NOT_SAVED }
            .distinctUntilChanged()

    override fun progress(id: TrackId): Flow<Float> = q.offlineItem(id.value).asFlow().mapToOneOrNull(io).map { row ->
        when {
            row == null -> 0f
            row.state == OfflineState.SAVED.name -> 1f
            row.bytes > 0 -> (row.downloaded.toFloat() / row.bytes).coerceIn(0f, 1f)
            else -> 0f
        }
    }.distinctUntilChanged()

    override suspend fun saveTrack(track: Track, ref: OfflineRef) {
        if (track.id.kind == SourceKind.LOCAL) return // already on the device
        withContext(io) {
            db.transaction {
                q.offlineInsert(track.id.value, json.encodeToString(Track.serializer(), track))
                q.offlineAddRef(track.id.value, ref.key)
                // Saving again retries a failed download.
                if (q.offlineItem(track.id.value).executeAsOne().state == OfflineState.FAILED.name) {
                    q.offlineSetState(OfflineState.QUEUED.name, track.id.value)
                }
            }
        }
        manager.kick()
    }

    override suspend fun unsave(ref: OfflineRef) {
        withContext(io) {
            if (ref is OfflineRef.Single) q.offlineRemoveRef(ref.trackId.value, ref.key) else q.offlineRemoveRefEverywhere(ref.key)
        }
        collectOrphans()
    }

    override suspend fun savePlaylist(id: PlaylistId) {
        withContext(io) { q.offlineAddPlaylist(id.value) }
        // Reference today's entries before returning, so an unsave right after can't miss them.
        repo?.playlist(id)?.first()?.let { sync(id, it) }
        watch(id)
    }

    override suspend fun unsavePlaylist(id: PlaylistId) {
        watchers.remove(id.value)?.cancel()
        withContext(io) { q.offlineRemovePlaylist(id.value) }
        unsave(OfflineRef.FromPlaylist(id))
    }

    private fun watch(id: PlaylistId) {
        val repo = repo ?: return
        watchers.put(id.value, scope.launch(io) { repo.playlist(id).filterNotNull().collect { sync(id, it) } })?.cancel()
    }

    /** Saves the playlist's tracks and drops the ones no longer in it. */
    private suspend fun sync(id: PlaylistId, p: io.github.velosako3.blossom.core.model.Playlist) {
        val ref = OfflineRef.FromPlaylist(id)
        val tracks = p.entries.mapNotNull { it.track }.filter { it.id.kind != SourceKind.LOCAL }
        tracks.forEach { saveTrack(it, ref) }
        val keep = tracks.mapTo(HashSet()) { it.id.value }
        val gone = withContext(io) { q.offlineTracksWithRef(ref.key).executeAsList().filter { it !in keep } }
        if (gone.isNotEmpty()) {
            withContext(io) { gone.forEach { q.offlineRemoveRef(it, ref.key) } }
            collectOrphans()
        }
    }

    /** Deletes items nothing refers to any more, and their files. */
    private suspend fun collectOrphans() {
        val orphans = withContext(io) { q.offlineOrphans().executeAsList() }
        for (id in orphans) {
            manager.cancel(id)
            withContext(io) {
                q.offlineDelete(id)
                manager.fileFor(id).delete()
            }
        }
        if (orphans.isNotEmpty()) manager.kick(retryPaused = true) // freed space
    }

    override fun openReader(id: TrackId): DecryptingReader? {
        val row = q.offlineItem(id.value).executeAsOneOrNull() ?: return null
        if (row.state != OfflineState.SAVED.name) return null
        val file = manager.fileFor(id.value).takeIf { it.isFile } ?: return null
        // ponytail: blocks only on the very first call (KeyVault caches the key afterwards).
        val key = runBlocking { manager.vault.offlineKey() }
        return DecryptingReader(file, key)
    }

    override fun usageBytes(): Flow<Long> = q.offlineUsage().asFlow().mapToOne(io)

    override suspend fun clearAll() {
        watchers.values.forEach { it.cancel() }; watchers.clear()
        manager.cancelAll()
        withContext(io) {
            q.offlineClear()
            manager.dir.listFiles().orEmpty().forEach { it.delete() }
        }
    }
}
