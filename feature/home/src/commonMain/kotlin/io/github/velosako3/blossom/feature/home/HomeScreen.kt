// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.home

import io.github.velosako3.blossom.core.designsystem.components.MediaRow
import io.github.velosako3.blossom.core.designsystem.components.GlassListItem
import io.github.velosako3.blossom.core.designsystem.components.Artwork
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import io.github.velosako3.blossom.core.platform.NetworkMonitor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.components.GlassButton
import io.github.velosako3.blossom.core.designsystem.components.MediaCard
import io.github.velosako3.blossom.core.designsystem.glass.GlassSurface
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.feature.home.resources.*
import io.github.velosako3.blossom.source.youtubemusic.HomeItem
import io.github.velosako3.blossom.source.youtubemusic.HomeSection
import io.github.velosako3.blossom.source.youtubemusic.YtmBrowse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** A saved playlist on the offline home. */
data class OfflinePlaylistCard(val id: String, val name: String, val artworkUrls: List<String>)

sealed interface HomeState {
    data object Loading : HomeState
    data class Loaded(val sections: List<HomeSection>) : HomeState
    data class Failed(val message: String?) : HomeState
    /** No connection: saved playlists and recently played saved songs. */
    data class OfflineHome(val playlists: List<OfflinePlaylistCard>, val recent: List<Track>) : HomeState
}

/** The YouTube Music home feed; while [network] is offline, [offline] (saved music) instead. */
class HomeViewModel(
    private val browse: YtmBrowse,
    private val scope: CoroutineScope,
    private val network: NetworkMonitor? = null,
    private val offline: Flow<HomeState.OfflineHome> = emptyFlow(),
) {
    private val _state = MutableStateFlow<HomeState>(HomeState.Loading)
    val state: StateFlow<HomeState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        if (network == null) refresh()
        else scope.launch {
            network.status.map { it.online }.distinctUntilChanged().collect { online ->
                if (online) refresh() else { job?.cancel(); job = scope.launch { offline.collect { _state.value = it } } }
            }
        }
    }

    fun refresh() {
        job?.cancel()
        _state.value = HomeState.Loading
        job = scope.launch {
            _state.value = try {
                HomeState.Loaded(browse.home())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                HomeState.Failed(e.message)
            }
        }
    }
}

@Composable
fun HomeScreen(
    vm: HomeViewModel,
    onPlay: (tracks: List<Track>, index: Int) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
    banner: (@Composable () -> Unit)? = null,
    onOpenSavedPlaylist: (String) -> Unit = {},
) {
    val state by vm.state.collectAsState()
    Box(modifier.fillMaxSize()) {
        when (val s = state) {
            HomeState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = BlossomTheme.colors.accent)
            is HomeState.Failed -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.home_failed), color = BlossomTheme.colors.onSurfaceVariant)
                GlassButton(vm::refresh) { Text(stringResource(Res.string.home_retry)) }
            }
            is HomeState.Loaded -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                        Text(stringResource(Res.string.home_title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 20.dp, top = 12.dp))
                        banner?.invoke()
                    }
                }
                items(s.sections, key = { it.title }) { section ->
                    SectionCard(section, onPlay, onOpenAlbum, onOpenArtist, onOpenPlaylist)
                }
            }
            is HomeState.OfflineHome -> OfflineHomeList(s, onPlay, onOpenSavedPlaylist, banner)
        }
    }
}

@Composable
private fun OfflineHomeList(
    s: HomeState.OfflineHome,
    onPlay: (List<Track>, Int) -> Unit,
    onOpenSavedPlaylist: (String) -> Unit,
    banner: (@Composable () -> Unit)?,
) {
    val muted = BlossomTheme.colors.onSurfaceVariant
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp)) {
        item {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                Text(stringResource(Res.string.home_title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 12.dp, top = 12.dp))
                banner?.invoke()
            }
        }
        if (s.playlists.isEmpty() && s.recent.isEmpty()) {
            item { Text(stringResource(Res.string.home_offline_empty), color = muted, modifier = Modifier.padding(16.dp)) }
        }
        if (s.playlists.isNotEmpty()) {
            item { Text(stringResource(Res.string.home_saved_playlists), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(12.dp)) }
            items(s.playlists, key = { "p" + it.id }) { p ->
                GlassListItem(
                    headline = p.name,
                    leading = { Artwork(p.artworkUrls.firstOrNull(), null, Modifier.size(48.dp)) },
                    onClick = { onOpenSavedPlaylist(p.id) },
                )
            }
        }
        if (s.recent.isNotEmpty()) {
            item { Text(stringResource(Res.string.home_recent_saved), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(12.dp)) }
            itemsIndexed(s.recent, key = { _, t -> "t" + t.id.value }) { i, t ->
                MediaRow(t.title, t.artistNames, t.artworkUrl, onClick = { onPlay(s.recent, i) })
            }
        }
    }
}

@Composable
private fun SectionCard(
    section: HomeSection,
    onPlay: (List<Track>, Int) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    val songs = section.items.filterIsInstance<HomeItem.Song>().map { it.track }
    GlassSurface(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Column(Modifier.padding(vertical = 16.dp)) {
            section.strapline?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelSmall, color = BlossomTheme.colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp)) }
            Text(section.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(section.items) { item ->
                    when (item) {
                        is HomeItem.Song -> MediaCard(item.track.title, item.track.artistNames, item.track.artworkUrl, { onPlay(songs, songs.indexOf(item.track)) })
                        is HomeItem.Album -> MediaCard(item.album.title, item.album.artists.joinToString(", ") { it.name }, item.album.artworkUrl, { onOpenAlbum(item.album.id) })
                        is HomeItem.Artist -> MediaCard(item.artist.name, null, item.artist.artworkUrl, { onOpenArtist(item.artist.id) }, circle = true)
                        is HomeItem.Playlist -> MediaCard(item.playlist.title, item.playlist.author, item.playlist.artworkUrl, { onOpenPlaylist(item.playlist.id) })
                    }
                }
            }
        }
    }
}
