// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.components.GlassIconButton
import io.github.velosako3.blossom.core.designsystem.components.GlassSlider
import io.github.velosako3.blossom.core.designsystem.components.formatDuration
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.player.BlossomPlayer
import io.github.velosako3.blossom.core.player.PlaybackState
import io.github.velosako3.blossom.core.player.RepeatMode
import io.github.velosako3.blossom.feature.nowplaying.resources.*
import org.jetbrains.compose.resources.stringResource

/** Seek slider with elapsed and remaining time. Dragging previews locally and seeks on release. */
@Composable
fun SeekBar(state: PlaybackState, player: BlossomPlayer, modifier: Modifier = Modifier, compactTimes: Boolean = false) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    // The playhead is read here, not by the caller, so a tick recomposes the seek bar alone.
    val progress by player.progress.collectAsState()
    val duration = (progress.durationMs.takeIf { it > 0 } ?: state.durationMs).coerceAtLeast(1)
    val fraction = dragging ?: (progress.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    val shownPos = (fraction * duration).toLong()
    Column(modifier) {
        GlassSlider(
            value = fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = { dragging?.let { player.seekTo((it * duration).toLong()) }; dragging = null },
            contentDescription = stringResource(Res.string.np_seek),
        )
        if (!compactTimes) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(shownPos), style = MaterialTheme.typography.labelSmall, color = BlossomTheme.colors.onSurfaceVariant)
                Text("-" + formatDuration(duration - shownPos), style = MaterialTheme.typography.labelSmall, color = BlossomTheme.colors.onSurfaceVariant)
            }
        }
    }
}

/** Shuffle · previous · play/pause · next · repeat. */
@Composable
fun TransportControls(state: PlaybackState, player: BlossomPlayer, modifier: Modifier = Modifier, playSize: Dp = 72.dp, sideSize: Dp = 52.dp) {
    val colors = BlossomTheme.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        GlassIconButton({ player.setShuffle(!state.shuffle) }, Icons.Rounded.Shuffle, stringResource(Res.string.np_shuffle), glass = false,
            tint = if (state.shuffle) colors.accent else colors.onSurfaceVariant)
        GlassIconButton(player::previous, Icons.Rounded.SkipPrevious, stringResource(Res.string.np_previous), size = sideSize, iconSize = sideSize * 0.55f, glass = false)
        GlassIconButton(
            { if (state.isPlaying) player.pause() else player.play() },
            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            stringResource(if (state.isPlaying) Res.string.np_pause else Res.string.np_play),
            size = playSize, iconSize = playSize * 0.5f,
        )
        GlassIconButton(player::next, Icons.Rounded.SkipNext, stringResource(Res.string.np_next), size = sideSize, iconSize = sideSize * 0.55f, glass = false)
        GlassIconButton(
            { player.setRepeat(when (state.repeat) { RepeatMode.OFF -> RepeatMode.ALL; RepeatMode.ALL -> RepeatMode.ONE; RepeatMode.ONE -> RepeatMode.OFF }) },
            if (state.repeat == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            stringResource(if (state.repeat == RepeatMode.ONE) Res.string.np_repeat_one else Res.string.np_repeat),
            glass = false,
            tint = if (state.repeat != RepeatMode.OFF) colors.accent else colors.onSurfaceVariant,
        )
    }
}
