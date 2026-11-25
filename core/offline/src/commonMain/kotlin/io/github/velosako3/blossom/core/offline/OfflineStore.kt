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

enum class OfflineState { NOT_SAVED, QUEUED, DOWNLOADING, SAVED, PAUSED_STORAGE, PAUSED_NETWORK, FAILED }

/** Why a track is saved. A track stays saved while any reference points at it. */
sealed interface OfflineRef {
    data class Single(val trackId: TrackId) : OfflineRef
    data class FromPlaylist(val playlistId: PlaylistId) : OfflineRef
    data object UntitledAuto : OfflineRef
}

val OfflineRef.key: String
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
    /** Non-blocking (kept in memory), so the player can ask from any thread. */
    fun isSaved(id: TrackId): Boolean
    fun usageBytes(): Flow<Long>
    /** Saved songs, newest first. */
    fun savedTracks(): Flow<List<Track>>
    /** Playlists saved for offline (their future entries are saved too). */
    fun savedPlaylists(): Flow<Set<PlaylistId>>
    /** True while downloads wait because the storage limit is reached. */
    fun pausedForStorage(): Flow<Boolean>
    suspend fun clearAll()
}
