// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.album

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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.components.Artwork
import io.github.velosako3.blossom.core.designsystem.components.GlassButton
import io.github.velosako3.blossom.core.designsystem.components.MediaRow
import io.github.velosako3.blossom.core.designsystem.components.formatDuration
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.feature.album.resources.*
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.stringResource

/** What an album or a remote playlist page shows. */
data class Collection(val title: String, val subtitle: String?, val artworkUrl: String?, val tracks: List<Track>)

/** Loads [load] once and shows the collection, a spinner, or an error with retry. */
@Composable
fun CollectionScreen(
    key: Any,
    load: suspend () -> Collection,
    onPlay: (tracks: List<Track>, index: Int, shuffle: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    rowDecoration: @Composable (Track) -> Unit = {},
) {
    var retry by remember { mutableIntStateOf(0) }
    var result by remember(key, retry) { mutableStateOf<Result<Collection>?>(null) }
    LaunchedEffect(key, retry) {
        result = try { Result.success(load()) } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
    }
    Box(modifier.fillMaxSize()) {
        val r = result
        when {
            r == null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = BlossomTheme.colors.accent)
            r.isFailure -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.album_failed), color = BlossomTheme.colors.onSurfaceVariant)
                GlassButton({ retry++ }) { Text(stringResource(Res.string.album_retry)) }
            }
            else -> CollectionContent(r.getOrThrow(), onPlay, topBar, rowDecoration)
        }
    }
}

@Composable
private fun CollectionContent(c: Collection, onPlay: (List<Track>, Int, Boolean) -> Unit, topBar: @Composable () -> Unit, rowDecoration: @Composable (Track) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars), horizontalAlignment = Alignment.CenterHorizontally) {
                topBar()
                val shape = RoundedCornerShape(20.dp)
                Artwork(
                    c.artworkUrl, c.title,
                    Modifier.padding(top = 8.dp).size(240.dp).dropShadow(shape, Shadow(32.dp, Color.Black.copy(alpha = 0.35f), offset = DpOffset(0.dp, 12.dp))),
                    shape = shape, iconSize = 64.dp,
                )
                Text(c.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 20.dp, start = 24.dp, end = 24.dp))
                c.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = BlossomTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp)) }
                Text(stringResource(Res.string.album_tracks, c.tracks.size), style = MaterialTheme.typography.labelMedium, color = BlossomTheme.colors.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassButton({ onPlay(c.tracks, 0, false) }, prominent = true, enabled = c.tracks.isNotEmpty()) { Icon(Icons.Rounded.PlayArrow, null); Text(stringResource(Res.string.album_play)) }
                    GlassButton({ onPlay(c.tracks, 0, true) }, enabled = c.tracks.isNotEmpty()) { Icon(Icons.Rounded.Shuffle, null); Text(stringResource(Res.string.album_shuffle)) }
                }
            }
        }
        itemsIndexed(c.tracks, key = { i, t -> "$i-${t.id.value}" }) { i, t ->
            MediaRow(
                t.title, t.artistNames, t.artworkUrl,
                modifier = Modifier.padding(horizontal = 8.dp),
                trailingText = formatDuration(t.durationMs).takeIf { t.durationMs > 0 },
                onClick = { onPlay(c.tracks, i, false) },
                badge = { rowDecoration(t) },
            )
        }
    }
}

