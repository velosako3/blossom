// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.nowplaying

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.components.Artwork
import io.github.velosako3.blossom.core.designsystem.components.GlassIconButton
import io.github.velosako3.blossom.core.designsystem.components.GlassSlider
import io.github.velosako3.blossom.core.designsystem.glass.GlassSurface
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.player.BlossomPlayer
import io.github.velosako3.blossom.feature.nowplaying.resources.*
import org.jetbrains.compose.resources.stringResource

/** The desktop/tablet player bar (88 dp): track info · controls and seek · volume and panel toggles. */
@Composable
fun PlayerBar(
    player: BlossomPlayer,
    onOpenNowPlaying: () -> Unit,
    onToggleQueue: () -> Unit,
    onToggleLyrics: () -> Unit,
    modifier: Modifier = Modifier,
    liked: Boolean = false,
    onToggleLike: (() -> Unit)? = null,
) {
    val state by player.state.collectAsState()
    val track = state.current
    val colors = BlossomTheme.colors
    var volume by remember { mutableFloatStateOf(1f) }
    GlassSurface(modifier.fillMaxWidth().height(88.dp), floating = true) {
        Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).fillMaxHeight().clickable(enabled = track != null, role = Role.Button, onClick = onOpenNowPlaying),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(track?.artworkUrl, null, Modifier.size(56.dp), shape = RoundedCornerShape(12.dp))
                Column(Modifier.padding(horizontal = 12.dp).weight(1f, fill = false)) {
                    Text(track?.title ?: "", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    Text(track?.artistNames ?: "", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
                }
                if (onToggleLike != null && track != null) {
                    GlassIconButton(onToggleLike, if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        stringResource(if (liked) Res.string.np_unlike else Res.string.np_like), size = 40.dp, iconSize = 20.dp, glass = false,
                        tint = if (liked) colors.accent else colors.onSurfaceVariant)
                }
            }
            Column(Modifier.widthIn(min = 360.dp, max = 560.dp).weight(1.4f), horizontalAlignment = Alignment.CenterHorizontally) {
                TransportControls(state, player, playSize = 44.dp, sideSize = 40.dp)
                SeekBar(state, player, Modifier.fillMaxWidth().height(28.dp), compactTimes = true)
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, Modifier.size(20.dp), tint = colors.onSurfaceVariant)
                GlassSlider(volume, { volume = it; player.setVolume(it) }, Modifier.width(110.dp).padding(horizontal = 8.dp), contentDescription = stringResource(Res.string.np_volume))
                GlassIconButton(onToggleLyrics, Icons.Rounded.Lyrics, stringResource(Res.string.np_lyrics), size = 40.dp, iconSize = 20.dp, glass = false)
                GlassIconButton(onToggleQueue, Icons.AutoMirrored.Rounded.QueueMusic, stringResource(Res.string.np_queue), size = 40.dp, iconSize = 20.dp, glass = false)
            }
        }
    }
}
