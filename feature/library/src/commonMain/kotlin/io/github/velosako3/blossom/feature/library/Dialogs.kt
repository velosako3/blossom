// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.database.LibraryPlaylistSummary
import io.github.velosako3.blossom.core.designsystem.components.Artwork
import io.github.velosako3.blossom.core.designsystem.components.GlassButton
import io.github.velosako3.blossom.core.designsystem.components.GlassDialog
import io.github.velosako3.blossom.core.designsystem.components.GlassListItem
import io.github.velosako3.blossom.core.designsystem.components.GlassSheet
import io.github.velosako3.blossom.core.designsystem.components.GlassTextField
import io.github.velosako3.blossom.core.designsystem.components.MediaRow
import io.github.velosako3.blossom.feature.library.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Playlist artwork: the playlist's own image, else a 2×2 mosaic of its first four songs. */
@Composable
fun PlaylistArtwork(urls: List<String>, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val shape = RoundedCornerShape(if (size > 100.dp) 20.dp else 8.dp)
    if (urls.size < 4) { Artwork(urls.firstOrNull(), null, modifier.size(size), shape = shape, iconSize = size / 3); return }
    Column(modifier.size(size).clip(shape)) {
        repeat(2) { r ->
            Row(Modifier.weight(1f)) {
                repeat(2) { c -> Artwork(urls[r * 2 + c], null, Modifier.weight(1f).aspectRatio(1f), shape = RoundedCornerShape(0.dp), iconSize = 0.dp) }
            }
        }
    }
}

/** Asks for a playlist name. */
@Composable
fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    val default = stringResource(Res.string.untitled_playlist)
    var name by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    GlassDialog(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.create_title),
        content = {
            GlassTextField(
                name, { name = it }, placeholder = stringResource(Res.string.create_hint),
                modifier = Modifier.focusRequester(focus),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onCreate(name.ifBlank { default }) }),
            )
        },
        confirmButton = { GlassButton({ onCreate(name.ifBlank { default }) }, prominent = true) { Text(stringResource(Res.string.create_ok)) } },
        dismissButton = { GlassButton(onDismiss) { Text(stringResource(Res.string.pl_cancel)) } },
    )
}

/** Picks a playlist to add songs to (or makes a new one). */
@Composable
fun AddToPlaylistSheet(
    visible: Boolean,
    playlists: List<LibraryPlaylistSummary>,
    onDismiss: () -> Unit,
    onPick: (LibraryPlaylistSummary) -> Unit,
    onNew: () -> Unit,
) {
    GlassSheet(visible, onDismiss) {
        Text(stringResource(Res.string.add_sheet_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        GlassListItem(stringResource(Res.string.add_sheet_new), onClick = onNew, leading = { Icon(Icons.Rounded.Add, null) }, modifier = Modifier.padding(horizontal = 8.dp))
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(playlists, key = { it.id.value }) { p ->
                MediaRow(p.name, pluralStringResource(Res.plurals.lib_songs, p.entryCount, p.entryCount), null, onClick = { onPick(p) })
            }
        }
    }
}

