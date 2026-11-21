// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.components.GlassButton
import io.github.velosako3.blossom.core.designsystem.components.GlassIconButton
import io.github.velosako3.blossom.core.designsystem.components.GlassListItem
import io.github.velosako3.blossom.core.designsystem.components.MediaRow
import io.github.velosako3.blossom.core.designsystem.components.formatDuration
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.feature.library.resources.*
import org.jetbrains.compose.resources.stringResource

/** Local files: the scanned songs, the folders being watched, and the Android permission rationale when needed. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalFilesScreen(
    tracks: List<Track>,
    folders: List<String>,
    onPlay: (List<Track>, Int, Boolean) -> Unit,
    onAddToPlaylist: (List<Track>) -> Unit,
    onAddFolder: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    modifier: Modifier = Modifier,
    needsPermission: Boolean = false,
    onRequestPermission: () -> Unit = {},
) {
    val colors = BlossomTheme.colors
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp)) {
        if (needsPermission) item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.local_permission_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(Res.string.local_permission_text), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                GlassButton(onRequestPermission, prominent = true) { Text(stringResource(Res.string.local_permission_allow)) }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassButton({ onPlay(tracks, 0, false) }, prominent = true, enabled = tracks.isNotEmpty()) { Text(stringResource(Res.string.pl_play)) }
                GlassButton({ onPlay(tracks, 0, true) }, enabled = tracks.isNotEmpty()) { Text(stringResource(Res.string.pl_shuffle)) }
                GlassButton(onAddFolder) { Icon(Icons.Rounded.CreateNewFolder, null); Text(stringResource(Res.string.local_add_folder)) }
            }
        }
        if (folders.isNotEmpty()) {
            item { Text(stringResource(Res.string.local_folders), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
            items(folders) { f ->
                GlassListItem(f, leading = { Icon(Icons.Rounded.Folder, null) },
                    trailing = { GlassIconButton({ onRemoveFolder(f) }, Icons.Rounded.Close, stringResource(Res.string.local_remove_folder), size = 36.dp, iconSize = 18.dp, glass = false) })
            }
        }
        if (tracks.isEmpty()) item { Text(stringResource(Res.string.local_empty), color = colors.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
        itemsIndexed(tracks, key = { _, t -> t.id.value }) { i, t ->
            MediaRow(
                t.title, listOfNotNull(t.artistNames, t.album?.title).joinToString(" · "), t.artworkUrl,
                modifier = Modifier.combinedClickable(onClick = { onPlay(tracks, i, false) }, onLongClick = { onAddToPlaylist(listOf(t)) }),
                trailingText = formatDuration(t.durationMs).takeIf { t.durationMs > 0 },
                trailing = { GlassIconButton({ onAddToPlaylist(listOf(t)) }, Icons.Rounded.PlaylistAdd, stringResource(Res.string.local_add_to_playlist), size = 36.dp, iconSize = 20.dp, glass = false) },
            )
        }
    }
}
