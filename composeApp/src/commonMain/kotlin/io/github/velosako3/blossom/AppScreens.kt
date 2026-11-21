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

/** Builds the shell's slots from the graph: screens per destination, mini player, player bar, queue panel. */
@Composable
fun appSlots(graph: AppGraph, navigator: Navigator, searchFocus: FocusRequester, hasTrack: Boolean, shellState: io.github.velosako3.blossom.shell.ShellState): ShellSlots {
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
    return ShellSlots(
        miniPlayer = if (hasTrack) ({ MiniPlayer(graph.player, onOpen = { navigator.navigate(Destination.NowPlaying) }) }) else null,
        playerBar = { shell ->
            PlayerBar(
                graph.player,
                onOpenNowPlaying = { navigator.navigate(Destination.NowPlaying) },
                onToggleQueue = { shell.toggle(RightPanel.QUEUE) },
                onToggleLyrics = { shell.toggle(RightPanel.LYRICS) },
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
                    )
                    true
                }
                else -> false
            }
        },
    )
}
