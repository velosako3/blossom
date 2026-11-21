// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.library

import io.github.velosako3.blossom.core.database.LibraryCommands
import io.github.velosako3.blossom.core.database.LibraryPlaylistSummary
import io.github.velosako3.blossom.core.database.LibraryRepository
import io.github.velosako3.blossom.core.database.NewEntry
import io.github.velosako3.blossom.core.model.EntryStatus
import io.github.velosako3.blossom.core.model.Playlist
import io.github.velosako3.blossom.core.model.PlaylistId
import io.github.velosako3.blossom.core.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A track as a new playlist entry (matched, title/artist from the track itself). */
fun Track.asNewEntry() = NewEntry(this, EntryStatus.MATCHED, title, artistNames, null)

class LibraryViewModel(private val repo: LibraryRepository, private val commands: LibraryCommands, private val scope: CoroutineScope) {
    val playlists: StateFlow<List<LibraryPlaylistSummary>?> = repo.playlists().stateIn(scope, SharingStarted.Eagerly, null)
    val liked: StateFlow<List<Track>?> = repo.liked().stateIn(scope, SharingStarted.Eagerly, null)

    fun createPlaylist(name: String, then: (PlaylistId) -> Unit = {}) = scope.launch { then(commands.createPlaylist(name.trim())) }

    fun addTo(playlist: PlaylistId, tracks: List<Track>) = scope.launch { commands.addEntries(playlist, tracks.map { it.asNewEntry() }) }

    fun createWith(name: String, tracks: List<Track>) = scope.launch {
        val id = commands.createPlaylist(name.trim())
        commands.addEntries(id, tracks.map { it.asNewEntry() })
    }

    fun toggleLike(track: Track) = scope.launch { commands.toggleLike(track) }
}

data class PlaylistUiState(
    val playlist: Playlist? = null,
    val loaded: Boolean = false,
    val confirmingDelete: Boolean = false,
    val selected: Set<String> = emptySet(),
)

class PlaylistViewModel(
    val id: PlaylistId,
    repo: LibraryRepository,
    private val commands: LibraryCommands,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(PlaylistUiState())
    val state: StateFlow<PlaylistUiState> = _state.asStateFlow()

    init {
        scope.launch { repo.playlist(id).collect { p -> _state.update { it.copy(playlist = p, loaded = true) } } }
    }

    /** Called once when a drag ends: the entry that was at [from] now sits at [to]. */
    fun onMove(from: Int, to: Int) {
        val entries = _state.value.playlist?.entries ?: return
        if (from == to || from !in entries.indices) return
        scope.launch { commands.moveEntry(id, entries[from].entryId, to) }
    }

    fun onRemove(entryId: String) = scope.launch { commands.removeEntry(id, entryId) }
    fun onRename(name: String) { if (name.isNotBlank() && name != _state.value.playlist?.name) scope.launch { commands.renamePlaylist(id, name.trim()) } }
    fun onDescription(description: String) {
        val d = description.trim().ifEmpty { null }
        if (d != _state.value.playlist?.description) scope.launch { commands.setDescription(id, d) }
    }

    fun onDelete() = _state.update { it.copy(confirmingDelete = true) }
    fun onDeleteDismissed() = _state.update { it.copy(confirmingDelete = false) }
    fun onDeleteConfirmed() {
        _state.update { it.copy(confirmingDelete = false) }
        scope.launch { commands.deletePlaylist(id) }
    }

    fun toggleSelected(entryId: String) = _state.update { s -> s.copy(selected = if (entryId in s.selected) s.selected - entryId else s.selected + entryId) }
    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    fun selectedTracks(): List<Track> {
        val s = _state.value
        return s.playlist?.entries.orEmpty().filter { it.entryId in s.selected }.mapNotNull { it.track }
    }
}
