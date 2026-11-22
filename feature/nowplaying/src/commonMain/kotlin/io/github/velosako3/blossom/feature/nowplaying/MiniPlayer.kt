// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.nowplaying

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.components.Artwork
import io.github.velosako3.blossom.core.designsystem.components.GlassIconButton
import io.github.velosako3.blossom.core.designsystem.glass.GlassSurface
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.player.BlossomPlayer
import io.github.velosako3.blossom.feature.nowplaying.resources.*
import org.jetbrains.compose.resources.stringResource

/** The compact mini player: a glass pill above the tab bar with a 2 dp progress hairline. Tap or swipe up to open. */
@Composable
fun MiniPlayer(player: BlossomPlayer, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val state by player.state.collectAsState()
    val track = state.current ?: return
    val colors = BlossomTheme.colors
    // Read only in the draw lambda below: a tick redraws the hairline, nothing else.
    val playhead = player.progress.collectAsState()
    GlassSurface(
        modifier.fillMaxWidth().height(64.dp)
            .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy < -12) onOpen() } },
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).clickable(role = Role.Button, onClick = onOpen).padding(horizontal = 12.dp)
                .drawWithContent {
                    drawContent()
                    val y = size.height - 1.dp.toPx()
                    drawLine(colors.onSurface.copy(alpha = 0.15f), Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
                    val p = playhead.value
                    val progress = if (p.durationMs > 0) (p.positionMs.toFloat() / p.durationMs).coerceIn(0f, 1f) else 0f
                    drawLine(colors.accent, Offset(0f, y), Offset(size.width * progress, y), 2.dp.toPx())
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(track.artworkUrl, null, Modifier.size(40.dp), shape = RoundedCornerShape(10.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.basicMarquee())
                Text(track.artistNames, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, modifier = Modifier.basicMarquee())
            }
            GlassIconButton(
                { if (state.isPlaying) player.pause() else player.play() },
                if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                stringResource(if (state.isPlaying) Res.string.np_pause else Res.string.np_play),
                size = 44.dp, glass = false,
            )
            GlassIconButton(player::next, Icons.Rounded.SkipNext, stringResource(Res.string.np_next), size = 40.dp, glass = false)
        }
    }
}

