// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.offline

import kotlinx.coroutines.IO
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
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
import kotlin.concurrent.atomics.AtomicReference


/** [repo] is only needed for saved playlists. */
class SqlOfflineStore(
    private val db: BlossomDatabase,
    val manager: DownloadManager,
    private val repo: LibraryRepository?,
    private val scope: CoroutineScope,
) : OfflineStore {
    private val q get() = db.blossomQueries
    private val io = Dispatchers.IO
    private val json = Json { ignoreUnknownKeys = true }
    // Swapped by compare-and-set (no JVM-only concurrent map): playlist id -> its watcher.
    private val watchers = AtomicReference(emptyMap<String, Job>())

    /** Sets (or with null removes) [key]'s watcher and returns the one it replaced. */
    private fun swapWatcher(key: String, job: Job?): Job? {
        while (true) {
            val old = watchers.load()
            val next = if (job == null) old - key else old + (key to job)
            if (watchers.compareAndSet(old, next)) return old[key]
        }
    }
    @kotlin.concurrent.Volatile private var savedIds: Set<String> = emptySet()

    init {
        scope.launch(io) { q.offlineSavedIds().asFlow().mapToList(io).collect { savedIds = it.toHashSet() } }
        scope.launch(io) {
            manager.vault.offlineKey() // load the key now, so openReader never waits on the keyring
            sweepUnreferencedFiles()
            q.offlinePlaylists().executeAsList().forEach { watch(PlaylistId(it)) }
            manager.kick(retryPaused = true)
        }
    }

    /** Files left by an unsave or clear that was interrupted, before any download starts. */
    private fun sweepUnreferencedFiles() {
        val known = q.offlineAllIds().executeAsList().mapTo(HashSet()) { manager.fileFor(it).name }
        manager.dir.listFiles().orEmpty().filter { it.isFile && it.name !in known }.forEach { it.delete() }
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

    override suspend fun saveTrack(track: Track, ref: OfflineRef) = saveTracks(listOf(track), ref)

    /** One transaction and one queue check for any number of tracks (a whole playlist). */
    private suspend fun saveTracks(tracks: List<Track>, ref: OfflineRef) {
        val streamed = tracks.filter { it.id.kind != SourceKind.LOCAL } // local files are already on the device
        if (streamed.isEmpty()) return
        withContext(io) {
            db.transaction {
                for (track in streamed) {
                    q.offlineInsert(track.id.value, json.encodeToString(Track.serializer(), track))
                    q.offlineAddRef(track.id.value, ref.key)
                    // Saving again retries a failed download.
                    if (q.offlineItem(track.id.value).executeAsOne().state == OfflineState.FAILED.name) {
                        q.offlineSetState(OfflineState.QUEUED.name, track.id.value)
                    }
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
        swapWatcher(id.value, null)?.cancel()
        withContext(io) { q.offlineRemovePlaylist(id.value) }
        unsave(OfflineRef.FromPlaylist(id))
    }

    private fun watch(id: PlaylistId) {
        val repo = repo ?: return
        swapWatcher(id.value, scope.launch(io) { repo.playlist(id).filterNotNull().collect { sync(id, it) } })?.cancel()
    }

    /** Saves the playlist's tracks and drops the ones no longer in it. */
    private suspend fun sync(id: PlaylistId, p: io.github.velosako3.blossom.core.model.Playlist) {
        val ref = OfflineRef.FromPlaylist(id)
        val tracks = p.entries.mapNotNull { it.track }.filter { it.id.kind != SourceKind.LOCAL }
        val referenced = withContext(io) { q.offlineTracksWithRef(ref.key).executeAsList().toHashSet() }
        saveTracks(tracks.filter { it.id.value !in referenced }, ref)
        val keep = tracks.mapTo(HashSet()) { it.id.value }
        val gone = withContext(io) { q.offlineTracksWithRef(ref.key).executeAsList().filter { it !in keep } }
        if (gone.isNotEmpty()) {
            withContext(io) { gone.forEach { q.offlineRemoveRef(it, ref.key) } }
            collectOrphans()
        }
    }

    /** Deletes items nothing refers to any more (in one transaction, so a concurrent save can't lose its row), then their files. */
    private suspend fun collectOrphans() {
        val orphans = withContext(io) { db.transactionWithResult { q.offlineOrphans().executeAsList().onEach { q.offlineDelete(it) } } }
        if (orphans.isEmpty()) return
        savedIds = savedIds - orphans.toSet() // the player must stream them from now on
        for (id in orphans) {
            manager.cancel(id) // waits until the download stopped writing
            withContext(io) { manager.fileFor(id).delete() }
        }
        manager.kick(retryPaused = true) // freed space
    }

    fun openReader(id: TrackId): DecryptingReader? {
        val row = q.offlineItem(id.value).executeAsOneOrNull() ?: return null
        if (row.state != OfflineState.SAVED.name) return null
        val file = manager.fileFor(id.value).takeIf { it.isFile } ?: return heal(id)
        // Loaded at startup; blocking only if playback starts before that finished.
        val key = manager.vault.cachedKey() ?: runBlocking { manager.vault.offlineKey() }
        return try {
            DecryptingReader(file, key).also { r ->
                if (r.plainLength > 0) r.read(0, ByteArray(1), 0, 1) // fails at once on a wrong key or a damaged file
            }
        } catch (_: CacheCorruptedException) {
            heal(id)
        }
    }

    /** The saved copy is missing or can't be decrypted (e.g. the key was reset): download it again. */
    private fun heal(id: TrackId): DecryptingReader? {
        savedIds = savedIds - id.value
        manager.fileFor(id.value).delete()
        q.offlineSetProgress(0, id.value)
        q.offlineSetState(OfflineState.QUEUED.name, id.value)
        manager.kick()
        return null
    }

    override fun isSaved(id: TrackId): Boolean = id.value in savedIds

    override fun usageBytes(): Flow<Long> = q.offlineUsage().asFlow().mapToOne(io)

    override fun savedTracks(): Flow<List<Track>> = q.offlineSavedTracks().asFlow().mapToList(io).map { rows ->
        rows.mapNotNull { runCatching { json.decodeFromString(Track.serializer(), it) }.getOrNull() }
    }

    override fun pausedForStorage(): Flow<Boolean> = q.offlinePausedForStorage().asFlow().mapToOne(io).map { it > 0 }.distinctUntilChanged()

    override fun savedPlaylists(): Flow<Set<PlaylistId>> = q.offlinePlaylists().asFlow().mapToList(io).map { ids -> ids.mapTo(HashSet()) { PlaylistId(it) } }

    override suspend fun clearAll() {
        watchers.exchange(emptyMap()).values.forEach { it.cancel() }
        withContext(io) { q.offlineClear() }
        savedIds = emptySet()
        manager.cancelAll() // waits for running downloads to stop writing
        withContext(io) { manager.dir.listFiles().orEmpty().forEach { it.delete() } }
    }
}
