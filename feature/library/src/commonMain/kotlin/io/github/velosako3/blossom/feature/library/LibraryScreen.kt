// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.library

import io.github.velosako3.blossom.core.designsystem.components.ScrollbarLazyColumn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.components.GlassButton
import io.github.velosako3.blossom.core.designsystem.components.GlassChip
import io.github.velosako3.blossom.core.designsystem.components.GlassIconButton
import io.github.velosako3.blossom.core.designsystem.components.GlassListItem
import io.github.velosako3.blossom.core.designsystem.components.GlassSheet
import io.github.velosako3.blossom.core.designsystem.components.MediaRow
import io.github.velosako3.blossom.core.designsystem.components.formatDuration
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.model.PlaylistId
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.feature.library.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

enum class LibrarySegment { PLAYLISTS, LIKED, LOCAL, SAVED }

/** Playlists, Liked Songs and Local files, with "+" for a new playlist or an import. */
@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    onOpenPlaylist: (PlaylistId) -> Unit,
    onPlay: (tracks: List<Track>, index: Int, shuffle: Boolean) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
    onSettings: (() -> Unit)? = null,
    localFiles: (@Composable () -> Unit)? = null,
    filterChips: (@Composable () -> Unit)? = null,
    /** Songs saved for offline; adds the "Saved" chip. */
    saved: (@Composable () -> Unit)? = null,
) {
    var segment by rememberSaveable { mutableStateOf(LibrarySegment.PLAYLISTS) }
    var menu by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val playlists by vm.playlists.collectAsState()
    val liked by vm.liked.collectAsState()
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.lib_title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                GlassIconButton({ menu = true }, Icons.Rounded.Add, stringResource(Res.string.lib_add))
                if (onSettings != null) GlassIconButton(onSettings, Icons.Rounded.Settings, stringResource(Res.string.lib_settings), modifier = Modifier.padding(start = 8.dp))
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassChip(segment == LibrarySegment.PLAYLISTS, { segment = LibrarySegment.PLAYLISTS }, stringResource(Res.string.lib_playlists))
                GlassChip(segment == LibrarySegment.LIKED, { segment = LibrarySegment.LIKED }, stringResource(Res.string.lib_liked))
                GlassChip(segment == LibrarySegment.LOCAL, { segment = LibrarySegment.LOCAL }, stringResource(Res.string.lib_local))
                if (saved != null) GlassChip(segment == LibrarySegment.SAVED, { segment = LibrarySegment.SAVED }, stringResource(Res.string.lib_saved))
                filterChips?.invoke()
            }
            when (segment) {
                LibrarySegment.PLAYLISTS -> {
                    val list = playlists
                    if (list != null && list.isEmpty()) EmptyLibrary({ creating = true }, onImport)
                    else ScrollbarLazyColumn(contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp + io.github.velosako3.blossom.core.designsystem.glass.LocalBarsPadding.current)) {
                        items(list.orEmpty(), key = { it.id.value }) { p ->
                            GlassListItem(
                                headline = p.name,
                                supporting = pluralStringResource(Res.plurals.lib_songs, p.entryCount, p.entryCount),
                                leading = { PlaylistArtwork(p.artworkUrls) },
                                onClick = { onOpenPlaylist(p.id) },
                            )
                        }
                    }
                }
                LibrarySegment.LIKED -> LikedList(liked.orEmpty(), onPlay)
                LibrarySegment.LOCAL -> localFiles?.invoke()
                LibrarySegment.SAVED -> saved?.invoke()
            }
        }
        GlassSheet(menu, { menu = false }) {
            GlassListItem(stringResource(Res.string.lib_new_playlist), onClick = { menu = false; creating = true }, leading = { Icon(Icons.Rounded.Add, null) })
            GlassListItem(stringResource(Res.string.lib_import), onClick = { menu = false; onImport() }, leading = { Icon(Icons.Rounded.Link, null) })
        }
        if (creating) CreatePlaylistDialog({ creating = false }) { name -> creating = false; vm.createPlaylist(name) { onOpenPlaylist(it) } }
    }
}

@Composable
private fun EmptyLibrary(onCreate: () -> Unit, onImport: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
        GlassButton(onCreate, prominent = true) { Icon(Icons.Rounded.Add, null); Text(stringResource(Res.string.lib_empty_title)) }
        GlassButton(onImport) { Icon(Icons.Rounded.Link, null); Text(stringResource(Res.string.lib_empty_import)) }
    }
}

/** Liked Songs as a list (also used by the Liked destination). */
@Composable
fun LikedList(
    tracks: List<Track>,
    onPlay: (List<Track>, Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isAvailable: (Track) -> Boolean = { true },
    badge: (@Composable (Track) -> Unit)? = null,
    emptyText: String? = null,
) {
    if (tracks.isEmpty()) {
        Text(emptyText ?: stringResource(Res.string.lib_liked_empty), color = BlossomTheme.colors.onSurfaceVariant, modifier = modifier.padding(24.dp))
        return
    }
    ScrollbarLazyColumn(modifier, contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp + io.github.velosako3.blossom.core.designsystem.glass.LocalBarsPadding.current)) {
        item {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassButton({ onPlay(tracks, 0, false) }, prominent = true) { Text(stringResource(Res.string.pl_play)) }
                GlassButton({ onPlay(tracks, 0, true) }) { Text(stringResource(Res.string.pl_shuffle)) }
            }
        }
        itemsIndexed(tracks, key = { _, t -> t.id.value }) { i, t ->
            val available = isAvailable(t)
            MediaRow(
                t.title, t.artistNames, t.artworkUrl,
                trailingText = formatDuration(t.durationMs).takeIf { t.durationMs > 0 },
                onClick = if (available) ({ onPlay(tracks, i, false) }) else null,
                dimmed = !available,
                badge = badge?.let { b -> { b(t) } },
            )
        }
    }
}
