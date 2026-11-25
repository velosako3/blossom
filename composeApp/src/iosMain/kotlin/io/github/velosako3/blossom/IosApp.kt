// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.velosako3.blossom.composeApp.resources.Res
import io.github.velosako3.blossom.composeApp.resources.dest_liked
import io.github.velosako3.blossom.composeApp.resources.menu_add_to_playlist
import io.github.velosako3.blossom.core.designsystem.background.BlossomBackground
import io.github.velosako3.blossom.core.designsystem.components.rememberArtworkPalette
import io.github.velosako3.blossom.core.designsystem.glass.glassBackdropSource
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.model.PlaylistId
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.feature.album.Collection
import io.github.velosako3.blossom.feature.album.CollectionScreen
import io.github.velosako3.blossom.feature.artist.ArtistScreen
import io.github.velosako3.blossom.feature.home.HomeScreen
import io.github.velosako3.blossom.feature.library.AddToPlaylistSheet
import io.github.velosako3.blossom.feature.library.CreatePlaylistDialog
import io.github.velosako3.blossom.feature.library.LibraryScreen
import io.github.velosako3.blossom.feature.library.LikedList
import io.github.velosako3.blossom.feature.library.PlaylistScreen
import io.github.velosako3.blossom.feature.library.PlaylistViewModel
import io.github.velosako3.blossom.feature.lyrics.ui.LyricsPanel
import io.github.velosako3.blossom.feature.nowplaying.FullPlayer
import io.github.velosako3.blossom.feature.nowplaying.MiniPlayer
import io.github.velosako3.blossom.feature.nowplaying.PlaybackErrorBanner
import io.github.velosako3.blossom.feature.nowplaying.PlayerMenuAction
import io.github.velosako3.blossom.feature.search.SearchScreen
import io.github.velosako3.blossom.navigation.Destination
import io.github.velosako3.blossom.navigation.Navigator
import io.github.velosako3.blossom.navigation.ScreenTopBar
import io.github.velosako3.blossom.shell.CompactShell
import io.github.velosako3.blossom.shell.ShellSlots
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * The iPhone app: the same theme, glass, shell and screens as Android, wired to [IosGraph]. Destinations iOS doesn't
 * have yet (import, sync, stats, settings) fall through to the shell's placeholder.
 */
@Composable
fun IosApp(graph: IosGraph) {
    val navigator = remember { Navigator() }
    val current = graph.player.state.collectAsState().value.current
    val palette = rememberArtworkPalette(current?.artworkUrl)
    BlossomTheme(darkTheme = isSystemInDarkTheme(), palette = palette) {
        Box(Modifier.fillMaxSize()) {
            BlossomBackground(palette, Modifier.fillMaxSize().glassBackdropSource())
            PlaybackErrorBanner(graph.player, Modifier.align(Alignment.TopCenter).padding(top = 56.dp).zIndex(1f))
            CompactShell(navigator, iosSlots(graph, navigator, hasTrack = current != null))
        }
    }
}

@Composable
private fun iosSlots(graph: IosGraph, navigator: Navigator, hasTrack: Boolean): ShellSlots {
    val scope = rememberCoroutineScope()
    val play: (List<Track>, Int, Boolean) -> Unit = { tracks, index, shuffle ->
        scope.launch { graph.player.setQueue(tracks, index); graph.player.setShuffle(shuffle); graph.player.play() }
    }
    val openAlbum: (String) -> Unit = { navigator.navigate(if (it.startsWith("MPRE")) Destination.Album(it) else Destination.YtPlaylist(it)) }
    val openArtist: (String) -> Unit = { navigator.navigate(Destination.Artist(it)) }
    val openPlaylist: (String) -> Unit = { navigator.navigate(Destination.YtPlaylist(it)) }
    var addTracks by remember { mutableStateOf<List<Track>?>(null) }
    var creatingWith by remember { mutableStateOf<List<Track>?>(null) }
    val playlists by graph.library.playlists.collectAsState()
    val current = graph.player.state.collectAsState().value.current
    val liked by (current?.let { graph.repo.isLiked(it.id) } ?: flowOf(false)).collectAsState(false)
    val addToPlaylist = stringResource(Res.string.menu_add_to_playlist)
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
        miniPlayer = if (hasTrack) ({ MiniPlayer(graph.player, onOpen = { navigator.navigate(Destination.NowPlaying) }) }) else null,
        screen = { destination ->
            when (destination) {
                Destination.Home -> { HomeScreen(graph.home, { t, i -> play(t, i, false) }, openAlbum, openArtist, openPlaylist); true }
                Destination.Search -> { SearchScreen(graph.search, { t, i -> play(t, i, false) }, openAlbum, openArtist, openPlaylist); true }
                is Destination.Album -> {
                    CollectionScreen(destination.id, {
                        val a = graph.ytm.album(destination.id)
                        Collection(a.title, listOfNotNull(a.artists.joinToString(", ") { it.name }.ifBlank { null }, a.year?.toString()).joinToString(" · "), a.artworkUrl, a.tracks)
                    }, play, topBar = { ScreenTopBar("", navigator) })
                    true
                }
                is Destination.YtPlaylist -> {
                    CollectionScreen(destination.id, { graph.ytm.playlist(destination.id).let { Collection(it.title, it.author, it.artworkUrl, it.tracks) } }, play, topBar = { ScreenTopBar("", navigator) })
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
                        onShare = {},
                        liked = liked,
                        onToggleLike = { t -> graph.library.toggleLike(t) },
                        menuActions = listOf(PlayerMenuAction(addToPlaylist) { addTracks = listOf(it) }),
                        lyrics = { m -> LyricsPanel(graph.lyrics, graph.player, m) },
                    )
                    true
                }
                Destination.Library -> {
                    LibraryScreen(graph.library, { navigator.navigate(Destination.Playlist(it.value)) }, play, onImport = { navigator.navigate(Destination.Import()) })
                    true
                }
                is Destination.Playlist -> {
                    val vm = remember(destination.id) { PlaylistViewModel(PlaylistId(destination.id), graph.repo, graph.commands, graph.scope) }
                    PlaylistScreen(vm, play, onAddToPlaylist = { addTracks = it }, onDeleted = { navigator.back() }, topBar = { ScreenTopBar("", navigator) })
                    true
                }
                Destination.Liked -> {
                    val likedTracks by graph.library.liked.collectAsState()
                    Column {
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
