// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.artist

import io.github.velosako3.blossom.core.designsystem.components.ScrollbarLazyColumn
import io.github.velosako3.blossom.core.designsystem.components.ScrollbarLazyRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import io.github.velosako3.blossom.core.designsystem.components.MediaCard
import io.github.velosako3.blossom.core.designsystem.components.MediaRow
import io.github.velosako3.blossom.core.designsystem.components.formatDuration
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.feature.artist.resources.*
import io.github.velosako3.blossom.source.youtubemusic.ArtistPage
import io.github.velosako3.blossom.source.youtubemusic.HomeItem
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.stringResource

@Composable
fun ArtistScreen(
    artistId: String,
    load: suspend () -> ArtistPage,
    onPlay: (tracks: List<Track>, index: Int, shuffle: Boolean) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
) {
    var retry by remember { mutableIntStateOf(0) }
    var result by remember(artistId, retry) { mutableStateOf<Result<ArtistPage>?>(null) }
    LaunchedEffect(artistId, retry) {
        result = try { Result.success(load()) } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
    }
    Box(modifier.fillMaxSize()) {
        val r = result
        when {
            r == null -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = BlossomTheme.colors.accent)
            r.isFailure -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.artist_failed), color = BlossomTheme.colors.onSurfaceVariant)
                GlassButton({ retry++ }) { Text(stringResource(Res.string.artist_retry)) }
            }
            else -> Content(r.getOrThrow(), onPlay, onOpenAlbum, onOpenArtist, onOpenPlaylist, topBar)
        }
    }
}

@Composable
private fun Content(
    page: ArtistPage,
    onPlay: (List<Track>, Int, Boolean) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    topBar: @Composable () -> Unit,
) {
    val top = page.topSongs.take(5)
    ScrollbarLazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp + io.github.velosako3.blossom.core.designsystem.glass.LocalBarsPadding.current)) {
        item {
            Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars), horizontalAlignment = Alignment.CenterHorizontally) {
                topBar()
                Artwork(page.artworkUrl, page.name, Modifier.padding(top = 8.dp).size(200.dp).dropShadow(CircleShape, Shadow(32.dp, Color.Black.copy(alpha = 0.35f), offset = DpOffset(0.dp, 12.dp))), shape = CircleShape, iconSize = 56.dp)
                Text(page.name, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 20.dp, start = 24.dp, end = 24.dp))
                Row(Modifier.padding(vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassButton({ onPlay(page.topSongs, 0, false) }, prominent = true, enabled = page.topSongs.isNotEmpty()) { Icon(Icons.Rounded.PlayArrow, null); Text(stringResource(Res.string.artist_play)) }
                    GlassButton({ onPlay(page.topSongs, 0, true) }, enabled = page.topSongs.isNotEmpty()) { Icon(Icons.Rounded.Shuffle, null); Text(stringResource(Res.string.artist_shuffle)) }
                }
            }
        }
        if (top.isNotEmpty()) {
            item { Text(stringResource(Res.string.artist_top_songs), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, bottom = 4.dp)) }
            itemsIndexed(top, key = { _, t -> t.id.value }) { i, t ->
                MediaRow(t.title, t.album?.title ?: t.artistNames, t.artworkUrl, Modifier.padding(horizontal = 8.dp), trailingText = formatDuration(t.durationMs).takeIf { t.durationMs > 0 }, onClick = { onPlay(page.topSongs, i, false) })
            }
        }
        items(page.sections, key = { it.title }) { section ->
            Column(Modifier.padding(top = 20.dp)) {
                Text(section.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp))
                ScrollbarLazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(section.items) { item ->
                        when (item) {
                            is HomeItem.Song -> MediaCard(item.track.title, item.track.artistNames, item.track.artworkUrl, { onPlay(listOf(item.track), 0, false) })
                            is HomeItem.Album -> MediaCard(item.album.title, item.album.year?.toString(), item.album.artworkUrl, { onOpenAlbum(item.album.id) })
                            is HomeItem.Artist -> MediaCard(item.artist.name, null, item.artist.artworkUrl, { onOpenArtist(item.artist.id) }, circle = true)
                            is HomeItem.Playlist -> MediaCard(item.playlist.title, item.playlist.author, item.playlist.artworkUrl, { onOpenPlaylist(item.playlist.id) })
                        }
                    }
                }
            }
        }
    }
}
