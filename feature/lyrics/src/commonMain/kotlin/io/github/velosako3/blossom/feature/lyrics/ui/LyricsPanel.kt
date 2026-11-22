// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.components.GlassIconButton
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.core.designsystem.theme.LocalGlassQuality
import io.github.velosako3.blossom.core.designsystem.theme.LocalReduceMotion
import io.github.velosako3.blossom.core.player.BlossomPlayer
import io.github.velosako3.blossom.core.player.PlaybackProgress
import io.github.velosako3.blossom.feature.lyrics.LyricsUi
import io.github.velosako3.blossom.feature.lyrics.LyricsViewModel
import io.github.velosako3.blossom.feature.lyrics.resources.Res
import io.github.velosako3.blossom.feature.lyrics.resources.lyrics_full_screen
import io.github.velosako3.blossom.feature.lyrics.ui.panel.PLAYER_GUTTER
import io.github.velosako3.blossom.feature.lyrics.ui.panel.PlaybackPosition
import io.github.velosako3.blossom.feature.lyrics.ui.panel.PlayerSettings
import io.github.velosako3.blossom.feature.lyrics.ui.panel.SyncedLyricsPanel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource

/**
 * Lyrics for the playing track: BitChord's panel (frame-interpolated clock, syllable sweep, spring-scrolled focus),
 * fed from blossom's player. [onFullScreen] adds the desktop full-screen button.
 */
@Composable
fun LyricsPanel(
    vm: LyricsViewModel,
    player: BlossomPlayer,
    modifier: Modifier = Modifier,
    onFullScreen: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") color: androidx.compose.ui.graphics.Color? = null,
) {
    val ui by vm.state.collectAsState()
    val current by remember(player) { player.state.map { it.current?.id?.value }.distinctUntilChanged() }.collectAsState(null)
    val playing by remember(player) { player.state.map { it.isPlaying && !it.isBuffering }.distinctUntilChanged() }.collectAsState(false)
    val position = rememberPlaybackPosition(player)

    val reduceMotion = LocalReduceMotion.current
    val glassOff = LocalGlassQuality.current == GlassQuality.OFF
    SideEffect {
        PlayerSettings.reduceAnimation.value = reduceMotion
        PlayerSettings.reduceDynamicBlur.value = glassOff
    }

    Box(modifier) {
        val u = ui
        SyncedLyricsPanel(
            lines = (u as? LyricsUi.Ready)?.lyrics?.lines.orEmpty(),
            trackKey = current.orEmpty(),
            position = position,
            looking = u is LyricsUi.Loading,
            isPlaying = playing,
            onSeekToLine = { ms -> player.seekTo(ms) },
            // BitChord's player gutter; the panel bleeds its scroll area back over it.
            modifier = Modifier.fillMaxSize().padding(horizontal = PLAYER_GUTTER),
        )
        if (onFullScreen != null) {
            GlassIconButton(onFullScreen, Icons.Rounded.Fullscreen, stringResource(Res.string.lyrics_full_screen), glass = false,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
    }
}

/** BitChord's playhead object, kept in step with [BlossomPlayer.progress] (each reading dated by when the engine made it). */
@Composable
private fun rememberPlaybackPosition(player: BlossomPlayer): PlaybackPosition {
    val position = remember(player) { PlaybackPosition() }
    LaunchedEffect(player, position) {
        combine(player.progress, player.state.map { it.isPlaying && !it.isBuffering }.distinctUntilChanged()) { p, playing -> p to playing }
            .collect { (p: PlaybackProgress, playing) ->
                // Seeks first, so the reading below is recorded as the landing of that jump.
                if (position.seeks != p.seeks) position.seeks = p.seeks
                position.advancing = playing
                position.report(p.positionMs, p.sampledAtNanos.takeIf { it > 0 } ?: System.nanoTime(), playing)
            }
    }
    return position
}
