// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.library

import io.github.velosako3.blossom.core.designsystem.components.typingFocus
import io.github.velosako3.blossom.core.designsystem.components.ScrollbarLazyColumn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.components.GlassButton
import io.github.velosako3.blossom.core.designsystem.components.GlassDialog
import io.github.velosako3.blossom.core.designsystem.components.GlassIconButton
import io.github.velosako3.blossom.core.designsystem.components.GlassListItem
import io.github.velosako3.blossom.core.designsystem.components.GlassSheet
import io.github.velosako3.blossom.core.designsystem.components.MediaRow
import io.github.velosako3.blossom.core.designsystem.components.formatDuration
import io.github.velosako3.blossom.core.designsystem.glass.GlassSurface
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.model.EntryStatus
import io.github.velosako3.blossom.core.model.PlaylistEntry
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.feature.library.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** A menu entry another feature adds to the playlist "…" menu (Refresh from source, …). */
data class PlaylistMenuAction(val label: String, val onClick: () -> Unit)

/**
 * One playlist: mosaic header, inline-editable name and description, Play/Shuffle, reorderable entries (handle on
 * desktop, long-press on Android), swipe or × or Delete key to remove, long-press to multi-select.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    vm: PlaylistViewModel,
    onPlay: (tracks: List<Track>, index: Int, shuffle: Boolean) -> Unit,
    onAddToPlaylist: (List<Track>) -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    isAvailable: (Track) -> Boolean = { true },
    /** Saved on this device: plays even when its link has died. */
    isSavedOffline: (Track) -> Boolean = { false },
    menuActions: List<PlaylistMenuAction> = emptyList(),
    headerExtras: @Composable () -> Unit = {},
    rowBadge: @Composable (PlaylistEntry) -> Unit = {},
    /** Tapping an unavailable entry ("Find replacement"). */
    onUnavailableClick: ((PlaylistEntry) -> Unit)? = null,
) {
    val state by vm.state.collectAsState()
    val playlist = state.playlist
    if (state.loaded && playlist == null) { LaunchedEffect(Unit) { onDeleted() }; return }
    if (playlist == null) return
    var showMenu by remember { mutableStateOf(false) }
    // Local copy so dragging moves rows instantly; committed once when the drag ends.
    var items by remember(playlist.entries) { mutableStateOf(playlist.entries) }
    var dragFrom by remember { mutableIntStateOf(-1) }
    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val f = from.index - 1; val t = to.index - 1 // index 0 is the header
        if (f in items.indices && t in items.indices) items = items.toMutableList().apply { add(t, removeAt(f)) }
    }
    val playable = playableTracks(items, isAvailable, isSavedOffline)
    fun playFrom(entry: PlaylistEntry?, shuffle: Boolean) = onPlay(playable, entry?.track?.let { playable.indexOf(it) }?.coerceAtLeast(0) ?: 0, shuffle)
    val colors = BlossomTheme.colors

    Box(modifier.fillMaxSize()) {
        ScrollbarLazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp + io.github.velosako3.blossom.core.designsystem.glass.LocalBarsPadding.current)) {
            item(key = "header") {
                Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars), horizontalAlignment = Alignment.CenterHorizontally) {
                    topBar()
                    PlaylistArtwork(playlist.entries.mapNotNull { it.track?.artworkUrl }.take(4), Modifier.padding(top = 8.dp), size = 200.dp)
                    var name by remember(playlist.name) { mutableStateOf(playlist.name) }
                    BasicTextField(
                        name, { name = it },
                        textStyle = MaterialTheme.typography.headlineMedium.copy(color = colors.onSurface, textAlign = TextAlign.Center),
                        cursorBrush = SolidColor(colors.accent), singleLine = true,
                        modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(top = 20.dp, start = 24.dp, end = 24.dp)
                            .onFocusChanged { if (!it.isFocused) vm.onRename(name) }.typingFocus(),
                    )
                    var description by remember(playlist.description) { mutableStateOf(playlist.description.orEmpty()) }
                    Box(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                        if (description.isEmpty()) Text(stringResource(Res.string.pl_description_hint), color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        BasicTextField(
                            description, { description = it },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurfaceVariant, textAlign = TextAlign.Center),
                            cursorBrush = SolidColor(colors.accent),
                            modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused) vm.onDescription(description) }.typingFocus(),
                        )
                    }
                    Text(pluralStringResource(Res.plurals.lib_songs, items.size, items.size), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.padding(vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlassButton({ playFrom(null, false) }, prominent = true, enabled = playable.isNotEmpty()) { Icon(Icons.Rounded.PlayArrow, null); Text(stringResource(Res.string.pl_play)) }
                        GlassButton({ playFrom(null, true) }, enabled = playable.isNotEmpty()) { Icon(Icons.Rounded.Shuffle, null); Text(stringResource(Res.string.pl_shuffle)) }
                        headerExtras()
                        GlassIconButton({ showMenu = true }, Icons.Rounded.MoreHoriz, stringResource(Res.string.pl_more))
                    }
                    if (items.isEmpty()) Text(stringResource(Res.string.pl_empty), color = colors.onSurfaceVariant, modifier = Modifier.padding(24.dp))
                }
            }
            itemsIndexed(items, key = { _, e -> e.entryId }) { _, entry ->
                ReorderableItem(reorder, key = entry.entryId) {
                    val dismiss = rememberSwipeToDismissBoxState()
                    LaunchedEffect(dismiss.currentValue) { if (dismiss.currentValue != SwipeToDismissBoxValue.Settled) vm.onRemove(entry.entryId) }
                    SwipeToDismissBox(dismiss, backgroundContent = {}, modifier = Modifier.padding(horizontal = 8.dp)) {
                        val track = entry.track
                        val unavailable = track == null || entry.status == EntryStatus.UNAVAILABLE && !isSavedOffline(track)
                        val missing = !unavailable && !isAvailable(track!!)
                        val selected = entry.entryId in state.selected
                        MediaRow(
                            title = track?.title ?: entry.sourceTitle,
                            subtitle = when {
                                unavailable -> "${entry.sourceArtist} · " + stringResource(Res.string.pl_unavailable)
                                missing -> "${track!!.artistNames} · " + stringResource(Res.string.pl_not_here)
                                else -> track!!.artistNames
                            },
                            artworkUrl = track?.artworkUrl,
                            dimmed = unavailable || missing,
                            trailingText = track?.durationMs?.takeIf { it > 0 }?.let(::formatDuration),
                            modifier = Modifier
                                .background(if (selected) colors.accent.copy(alpha = 0.18f) else androidx.compose.ui.graphics.Color.Transparent, CircleShape.let { androidx.compose.foundation.shape.RoundedCornerShape(12.dp) })
                                .combinedClickable(
                                    onClick = {
                                        when {
                                            state.selected.isNotEmpty() -> vm.toggleSelected(entry.entryId)
                                            unavailable -> onUnavailableClick?.invoke(entry)
                                            !missing -> playFrom(entry, false)
                                        }
                                    },
                                    onLongClick = { vm.toggleSelected(entry.entryId) },
                                )
                                .focusable()
                                .onKeyEvent { e -> if (e.type == KeyEventType.KeyDown && (e.key == Key.Delete || e.key == Key.Backspace)) { vm.onRemove(entry.entryId); true } else false },
                            badge = {
                                if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = colors.accent, modifier = Modifier.padding(end = 6.dp).size(20.dp))
                                if (entry.status == EntryStatus.UNCERTAIN) Icon(Icons.Rounded.HelpOutline, stringResource(Res.string.pl_uncertain), tint = colors.onSurfaceVariant, modifier = Modifier.padding(end = 6.dp).size(18.dp))
                                rowBadge(entry)
                            },
                            trailing = {
                                GlassIconButton({ vm.onRemove(entry.entryId) }, Icons.Rounded.Close, stringResource(Res.string.pl_remove), size = 36.dp, iconSize = 18.dp, glass = false)
                                Icon(
                                    Icons.Rounded.DragHandle, stringResource(Res.string.pl_reorder),
                                    Modifier.draggableHandle(
                                        onDragStarted = { dragFrom = playlist.entries.indexOfFirst { it.entryId == entry.entryId } },
                                        onDragStopped = { vm.onMove(dragFrom, items.indexOfFirst { it.entryId == entry.entryId }) },
                                    ).padding(8.dp),
                                    tint = colors.onSurfaceVariant,
                                )
                            },
                        )
                    }
                }
            }
        }
        if (state.selected.isNotEmpty()) {
            GlassSurface(Modifier.align(Alignment.BottomCenter).padding(16.dp), shape = CircleShape) {
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassIconButton(vm::clearSelection, Icons.Rounded.Close, stringResource(Res.string.pl_clear_selection), glass = false)
                    Text(stringResource(Res.string.pl_selected, state.selected.size), style = MaterialTheme.typography.labelLarge)
                    GlassButton({ onAddToPlaylist(vm.selectedTracks()); vm.clearSelection() }, prominent = true) { Text(stringResource(Res.string.pl_add_to)) }
                }
            }
        }
        GlassSheet(showMenu, { showMenu = false }) {
            menuActions.forEach { a -> GlassListItem(a.label, onClick = { showMenu = false; a.onClick() }) }
            GlassListItem(stringResource(Res.string.pl_delete), onClick = { showMenu = false; vm.onDelete() })
        }
        if (state.confirmingDelete) {
            GlassDialog(
                onDismissRequest = vm::onDeleteDismissed,
                title = stringResource(Res.string.pl_delete_title),
                text = stringResource(Res.string.pl_delete_text),
                confirmButton = { GlassButton(vm::onDeleteConfirmed, prominent = true) { Text(stringResource(Res.string.pl_delete)) } },
                dismissButton = { GlassButton(vm::onDeleteDismissed) { Text(stringResource(Res.string.pl_cancel)) } },
            )
        }
    }
}

/**
 * The entries that can play now, in order. An UNAVAILABLE entry (its link died) still plays when this device saved it
 * before: the encrypted copy is what saving was for.
 */
fun playableTracks(entries: List<PlaylistEntry>, isAvailable: (Track) -> Boolean, isSaved: (Track) -> Boolean): List<Track> =
    entries.mapNotNull { e -> e.track?.takeIf { t -> (e.status != EntryStatus.UNAVAILABLE || isSaved(t)) && isAvailable(t) } }
