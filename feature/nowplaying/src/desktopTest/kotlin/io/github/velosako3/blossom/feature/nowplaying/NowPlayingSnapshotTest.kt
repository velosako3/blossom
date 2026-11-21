// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.nowplaying

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.takahirom.roborazzi.captureRoboImage
import io.github.velosako3.blossom.core.designsystem.background.BlossomBackground
import io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.core.designsystem.glass.glassBackdropSource
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.model.AlbumRef
import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.core.player.BlossomPlayer
import io.github.velosako3.blossom.core.player.PlaybackState
import io.github.velosako3.blossom.core.player.RepeatMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test

class FakePlayer(state: PlaybackState) : BlossomPlayer {
    override val state = MutableStateFlow(state)
    override suspend fun setQueue(tracks: List<Track>, startIndex: Int, startPositionMs: Long) {}
    override fun play() {}
    override fun pause() {}
    override fun seekTo(positionMs: Long) {}
    override fun next() {}
    override fun previous() {}
    override fun skipTo(index: Int) {}
    override fun setShuffle(enabled: Boolean) {}
    override fun setRepeat(mode: RepeatMode) {}
    override fun addNext(track: Track) {}
    override fun addToQueue(tracks: List<Track>) {}
    override fun moveInQueue(from: Int, to: Int) {}
    override fun removeFromQueue(index: Int) {}
    override fun setVolume(volume: Float) {}
}

private val sample = (1..6).map {
    Track(TrackId("ytm:v$it"), if (it == 1) "Pink + White" else "Song $it", listOf(ArtistRef("UC1", "Frank Ocean")), AlbumRef("MPRE1", "Blonde"), 184_000, null)
}
private val playing = FakePlayer(PlaybackState(queue = sample, currentIndex = 0, positionMs = 61_000, durationMs = 184_000, isPlaying = true, shuffle = true, repeat = RepeatMode.ALL))
private val palette = ArtworkPalette.Default

@OptIn(ExperimentalTestApi::class)
private fun shot(name: String, w: Int, h: Int, content: @Composable () -> Unit) = runDesktopComposeUiTest(w, h) {
    setContent {
        BlossomTheme(true, palette, glassQuality = GlassQuality.FULL, reduceMotion = true) {
            Box(Modifier.fillMaxSize()) {
                BlossomBackground(palette, Modifier.fillMaxSize().glassBackdropSource())
                content()
            }
        }
    }
    waitForIdle()
    onRoot().captureRoboImage("src/desktopTest/snapshots/$name.png")
}

class NowPlayingSnapshotTest {
    @Test fun fullPlayer_compact_412x915() = shot("fullPlayer_compact_412x915", 412, 915) {
        FullPlayer(playing, onClose = {}, onOpenArtist = {}, onOpenAlbum = {}, onShare = {}, liked = true, onToggleLike = {}, lyrics = { })
    }

    @Test fun playerBar_expanded_1440x900() = shot("playerBar_expanded_1440x900", 1440, 900) {
        Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.BottomCenter) {
            PlayerBar(playing, onOpenNowPlaying = {}, onToggleQueue = {}, onToggleLyrics = {}, liked = false, onToggleLike = {})
        }
    }

    @Test fun miniPlayer_compact() = shot("miniPlayer_compact", 412, 200) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Spacer(Modifier.weight(1f))
            MiniPlayer(playing, onOpen = {})
        }
    }
}
