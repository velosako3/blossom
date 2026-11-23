// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.nowplaying

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreHoriz
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
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.components.Artwork
import io.github.velosako3.blossom.core.designsystem.components.GlassIconButton
import io.github.velosako3.blossom.core.designsystem.components.GlassListItem
import io.github.velosako3.blossom.core.designsystem.components.GlassSheet
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTokens
import io.github.velosako3.blossom.core.designsystem.theme.motionSpec
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.player.BlossomPlayer
import io.github.velosako3.blossom.feature.nowplaying.resources.*
import org.jetbrains.compose.resources.stringResource

/** An entry in the player's "more" menu; features add theirs (add to playlist, save offline, sleep timer). */
data class PlayerMenuAction(val label: String, val onClick: (Track) -> Unit)

/**
 * The full-screen player: artwork (≤ 360 dp) or lyrics, title and artist, glass seek bar, transport controls,
 * like/queue/lyrics toggles and a "more" menu. On wide windows the lyrics/queue sit beside the artwork.
 */
@Composable
fun FullPlayer(
    player: BlossomPlayer,
    onClose: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onShare: (Track) -> Unit,
    modifier: Modifier = Modifier,
    liked: Boolean = false,
    onToggleLike: ((Track) -> Unit)? = null,
    menuActions: List<PlayerMenuAction> = emptyList(),
    lyrics: (@Composable (Modifier) -> Unit)? = null,
) {
    val state by player.state.collectAsState()
    val track = state.current
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val colors = BlossomTheme.colors
    Box(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val wide = maxWidth >= 840.dp
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(onClose, Icons.Rounded.KeyboardArrowDown, stringResource(Res.string.np_close))
                    Text(stringResource(Res.string.np_now_playing), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    GlassIconButton({ showMenu = true }, Icons.Rounded.MoreHoriz, stringResource(Res.string.np_more), enabled = track != null)
                }
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Crossfade(targetState = showLyrics && !wide && lyrics != null, animationSpec = motionSpec(tween(BlossomTokens.motionStandard))) { lyricsMode ->
                                if (lyricsMode) lyrics?.invoke(Modifier.fillMaxSize())
                                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    val shape = RoundedCornerShape(20.dp)
                                    Artwork(
                                        track?.artworkUrl, track?.title,
                                        Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(1f)
                                            .dropShadow(shape, Shadow(32.dp, Color.Black.copy(alpha = 0.35f), offset = DpOffset(0.dp, 16.dp))),
                                        shape = shape, iconSize = 72.dp,
                                    )
                                }
                            }
                        }
                        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(top = 20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(track?.title.orEmpty(), style = MaterialTheme.typography.headlineSmall, maxLines = 1, modifier = Modifier.basicMarquee())
                                    Text(
                                        track?.artistNames.orEmpty(), style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant, maxLines = 1,
                                        modifier = Modifier.basicMarquee().clickable(enabled = track?.artists?.firstOrNull()?.id != null) {
                                            track?.artists?.firstOrNull()?.id?.let(onOpenArtist)
                                        },
                                    )
                                }
                                if (onToggleLike != null && track != null) {
                                    GlassIconButton({ onToggleLike(track) }, if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        stringResource(if (liked) Res.string.np_unlike else Res.string.np_like), glass = false,
                                        tint = if (liked) colors.accent else colors.onSurface)
                                }
                            }
                            SeekBar(state, player, Modifier.padding(top = 12.dp))
                            TransportControls(state, player, Modifier.fillMaxWidth().padding(vertical = 8.dp))
                            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                if (lyrics != null && !wide) {
                                    GlassIconButton({ showLyrics = !showLyrics }, Icons.Rounded.Lyrics, stringResource(Res.string.np_lyrics), glass = false,
                                        tint = if (showLyrics) colors.accent else colors.onSurfaceVariant)
                                } else Spacer(Modifier)
                                GlassIconButton({ showQueue = true }, Icons.AutoMirrored.Rounded.QueueMusic, stringResource(Res.string.np_queue), glass = false, tint = colors.onSurfaceVariant)
                            }
                        }
                    }
                    if (wide && lyrics != null) lyrics(Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        GlassSheet(visible = showQueue, onDismissRequest = { showQueue = false }) {
            QueueView(player, Modifier.fillMaxWidth().height(480.dp))
        }
        GlassSheet(visible = showMenu && track != null, onDismissRequest = { showMenu = false }) {
            val t = track ?: return@GlassSheet
            menuActions.forEach { a -> GlassListItem(a.label, onClick = { showMenu = false; a.onClick(t) }) }
            t.album?.id?.let { id -> GlassListItem(stringResource(Res.string.np_go_album), onClick = { showMenu = false; onOpenAlbum(id) }) }
            t.artists.firstOrNull()?.id?.let { id -> GlassListItem(stringResource(Res.string.np_go_artist), onClick = { showMenu = false; onOpenArtist(id) }) }
            GlassListItem(stringResource(Res.string.np_share), onClick = { showMenu = false; onShare(t) })
        }
    }
}
