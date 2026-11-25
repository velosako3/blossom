// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.nowplaying

import io.github.velosako3.blossom.core.designsystem.components.ScrollbarLazyColumn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.components.GlassIconButton
import io.github.velosako3.blossom.core.designsystem.components.MediaRow
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.player.BlossomPlayer
import io.github.velosako3.blossom.feature.nowplaying.resources.*
import org.jetbrains.compose.resources.stringResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** The play queue: tap to jump, drag the handle to reorder, × to remove. */
@Composable
fun QueueView(player: BlossomPlayer, modifier: Modifier = Modifier) {
    val state by player.state.collectAsState()
    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to -> player.moveInQueue(from.index, to.index) }
    if (state.queue.isEmpty()) {
        Text(stringResource(Res.string.np_queue_empty), color = BlossomTheme.colors.onSurfaceVariant, modifier = modifier.padding(16.dp))
        return
    }
    Column(modifier) {
        Text(stringResource(Res.string.np_up_next), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        ScrollbarLazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 16.dp)) {
            itemsIndexed(state.queue, key = { i, t -> "${t.id.value}#$i" }) { i, t ->
                ReorderableItem(reorder, key = "${t.id.value}#$i") {
                    MediaRow(
                        t.title, t.artistNames, t.artworkUrl,
                        onClick = { player.skipTo(i) },
                        badge = { if (i == state.currentIndex) Icon(Icons.Rounded.GraphicEq, null, tint = BlossomTheme.colors.accent) },
                        trailing = {
                            GlassIconButton({ player.removeFromQueue(i) }, Icons.Rounded.Close, stringResource(Res.string.np_remove), size = 36.dp, iconSize = 18.dp, glass = false)
                            Icon(Icons.Rounded.DragHandle, stringResource(Res.string.np_reorder), Modifier.draggableHandle().padding(8.dp), tint = BlossomTheme.colors.onSurfaceVariant)
                        },
                    )
                }
            }
        }
    }
}
