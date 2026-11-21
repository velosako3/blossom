// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.feature.album.Collection
import io.github.velosako3.blossom.feature.album.CollectionScreen
import io.github.velosako3.blossom.feature.artist.ArtistScreen
import io.github.velosako3.blossom.feature.home.HomeScreen
import io.github.velosako3.blossom.feature.nowplaying.FullPlayer
import io.github.velosako3.blossom.feature.nowplaying.MiniPlayer
import io.github.velosako3.blossom.feature.nowplaying.PlayerBar
import io.github.velosako3.blossom.feature.nowplaying.QueueView
import io.github.velosako3.blossom.feature.search.SearchScreen
import io.github.velosako3.blossom.navigation.Destination
import io.github.velosako3.blossom.navigation.Navigator
import io.github.velosako3.blossom.navigation.ScreenTopBar
import io.github.velosako3.blossom.shell.RightPanel
import io.github.velosako3.blossom.shell.ShellSlots
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.composeApp.resources.Res
import io.github.velosako3.blossom.composeApp.resources.dest_liked
import io.github.velosako3.blossom.composeApp.resources.menu_add_to_playlist
import io.github.velosako3.blossom.core.designsystem.components.GlassSidebarItem
import io.github.velosako3.blossom.feature.library.AddToPlaylistSheet
import io.github.velosako3.blossom.feature.library.CreatePlaylistDialog
import io.github.velosako3.blossom.feature.library.LibraryScreen
import io.github.velosako3.blossom.feature.library.LikedList
import io.github.velosako3.blossom.feature.library.LocalFilesScreen
import io.github.velosako3.blossom.feature.library.PlaylistArtwork
import io.github.velosako3.blossom.feature.library.PlaylistScreen
import io.github.velosako3.blossom.feature.library.PlaylistViewModel
import io.github.velosako3.blossom.feature.nowplaying.PlayerMenuAction
import org.jetbrains.compose.resources.stringResource

/** Builds the shell's slots from the graph: screens per destination, mini player, player bar, queue panel. */
@Composable
fun appSlots(graph: AppGraph, navigator: Navigator, searchFocus: FocusRequester, hasTrack: Boolean, shellState: io.github.velosako3.blossom.shell.ShellState, compact: Boolean = false): ShellSlots {
    val scope = rememberCoroutineScope()
    val play: (List<Track>, Int, Boolean) -> Unit = { tracks, index, shuffle ->
        scope.launch {
            graph.player.setQueue(tracks, index)
            graph.player.setShuffle(shuffle)
            graph.player.play()
        }
    }
    val openAlbum: (String) -> Unit = { navigator.navigate(if (it.startsWith("MPRE")) Destination.Album(it) else Destination.YtPlaylist(it)) }
    val openArtist: (String) -> Unit = { navigator.navigate(Destination.Artist(it)) }
    val openPlaylist: (String) -> Unit = { navigator.navigate(Destination.YtPlaylist(it)) }
    val share: (Track) -> Unit = { t -> t.shareUrl()?.let(graph.shareLink) }
    var addTracks by remember { mutableStateOf<List<Track>?>(null) }
    var creatingWith by remember { mutableStateOf<List<Track>?>(null) }
    val playlists by graph.library.playlists.collectAsState()
    val current = graph.player.state.collectAsState().value.current
    val liked by (current?.let { graph.repo.isLiked(it.id) } ?: flowOf(false)).collectAsState(false)
    val toggleLike: (Track) -> Unit = { t -> graph.library.toggleLike(t) }
    val addToPlaylist = stringResource(Res.string.menu_add_to_playlist)
    val menu = listOf(PlayerMenuAction(addToPlaylist) { addTracks = listOf(it) })
    val openPlaylistLocal: (io.github.velosako3.blossom.core.model.PlaylistId) -> Unit = { navigator.navigate(Destination.Playlist(it.value)) }
    return ShellSlots(
        overlay = {
            AddToPlaylistSheet(
                visible = addTracks != null,
                playlists = playlists.orEmpty(),
                onDismiss = { addTracks = null },
                onPick = { p -> addTracks?.let { graph.library.addTo(p.id, it) }; addTracks = null },
                onNew = { creatingWith = addTracks; addTracks = null },
            )
            creatingWith?.let { tracks ->
                CreatePlaylistDialog({ creatingWith = null }) { name -> graph.library.createWith(name, tracks); creatingWith = null }
            }
        },
        sidebarPlaylists = {
            playlists.orEmpty().forEach { p ->
                GlassSidebarItem(p.name, selected = navigator.current == Destination.Playlist(p.id.value), onClick = { openPlaylistLocal(p.id) },
                    leading = { PlaylistArtwork(p.artworkUrls, size = 28.dp) })
            }
        },
        miniPlayer = if (hasTrack) ({ MiniPlayer(graph.player, onOpen = { navigator.navigate(Destination.NowPlaying) }) }) else null,
        playerBar = { shell ->
            PlayerBar(
                graph.player,
                onOpenNowPlaying = { navigator.navigate(Destination.NowPlaying) },
                onToggleQueue = { shell.toggle(RightPanel.QUEUE) },
                onToggleLyrics = { shell.toggle(RightPanel.LYRICS) },
                liked = liked,
                onToggleLike = { current?.let(toggleLike) },
            )
        },
        queuePanel = { QueueView(graph.player, Modifier.fillMaxSize()) },
        screen = { destination ->
            when (destination) {
                Destination.Home -> { HomeScreen(graph.home, { t, i -> play(t, i, false) }, openAlbum, openArtist, openPlaylist); true }
                Destination.Search -> {
                    SearchScreen(graph.search, { t, i -> play(t, i, false) }, openAlbum, openArtist, openPlaylist, focusRequester = searchFocus, focusTrigger = shellState.searchFocusRequests)
                    true
                }
                is Destination.Album -> {
                    CollectionScreen(destination.id, {
                        val a = graph.ytm.album(destination.id)
                        Collection(a.title, listOfNotNull(a.artists.joinToString(", ") { it.name }.ifBlank { null }, a.year?.toString()).joinToString(" · "), a.artworkUrl, a.tracks)
                    }, play, topBar = { ScreenTopBar("", navigator) })
                    true
                }
                is Destination.YtPlaylist -> {
                    CollectionScreen(destination.id, {
                        val p = graph.ytm.playlist(destination.id)
                        Collection(p.title, p.author, p.artworkUrl, p.tracks)
                    }, play, topBar = { ScreenTopBar("", navigator) })
                    true
                }
                is Destination.Artist -> {
                    ArtistScreen(destination.id, { graph.ytm.artist(destination.id) }, play, openAlbum, openArtist, openPlaylist, topBar = { ScreenTopBar("", navigator) })
                    true
                }
                Destination.NowPlaying -> {
                    FullPlayer(
                        graph.player,
                        onClose = { navigator.back() },
                        onOpenArtist = openArtist,
                        onOpenAlbum = openAlbum,
                        onShare = share,
                        liked = liked,
                        onToggleLike = toggleLike,
                        menuActions = menu,
                    )
                    true
                }
                Destination.Library -> {
                    LibraryScreen(
                        graph.library, openPlaylistLocal, play, onImport = { navigator.navigate(Destination.Import()) },
                        onSettings = if (compact) ({ navigator.navigate(Destination.Settings) }) else null,
                        localFiles = {
                            val localTracks by graph.local.library.tracks().collectAsState(emptyList())
                            val folders by graph.local.folders.collectAsState()
                            val needsPermission by graph.local.needsPermission.collectAsState()
                            LocalFilesScreen(localTracks, folders, play, { addTracks = it }, graph.local.addFolder, graph.local.removeFolder,
                                needsPermission = needsPermission, onRequestPermission = graph.local.requestPermission)
                        },
                    )
                    true
                }
                is Destination.Playlist -> {
                    val vm = remember(destination.id) { PlaylistViewModel(io.github.velosako3.blossom.core.model.PlaylistId(destination.id), graph.repo, graph.commands, graph.scope) }
                    PlaylistScreen(vm, play, onAddToPlaylist = { addTracks = it }, onDeleted = { navigator.back() }, topBar = { ScreenTopBar("", navigator) }, isAvailable = graph.local::isAvailable)
                    true
                }
                Destination.Liked -> {
                    val likedTracks by graph.library.liked.collectAsState()
                    androidx.compose.foundation.layout.Column {
                        ScreenTopBar(stringResource(Res.string.dest_liked), navigator)
                        LikedList(likedTracks.orEmpty(), play)
                    }
                    true
                }
                else -> false
            }
        },
    )
}
