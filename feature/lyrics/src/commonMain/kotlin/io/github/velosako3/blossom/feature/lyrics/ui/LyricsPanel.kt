// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.components.GlassIconButton
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.player.BlossomPlayer
import io.github.velosako3.blossom.feature.lyrics.LyricsUi
import io.github.velosako3.blossom.feature.lyrics.LyricsViewModel
import io.github.velosako3.blossom.feature.lyrics.resources.Res
import io.github.velosako3.blossom.feature.lyrics.resources.lyrics_full_screen
import org.jetbrains.compose.resources.stringResource

/** Lyrics for the playing track, following the player. [onFullScreen] adds the desktop full-screen button. */
@Composable
fun LyricsPanel(
    vm: LyricsViewModel,
    player: BlossomPlayer,
    modifier: Modifier = Modifier,
    onFullScreen: (() -> Unit)? = null,
    color: androidx.compose.ui.graphics.Color = BlossomTheme.palette.onBackground,
) {
    val ui by vm.state.collectAsState()
    val playback by player.state.collectAsState()
    Box(modifier) {
        when (val u = ui) {
            LyricsUi.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = color)
            is LyricsUi.Ready -> LyricsView(u.lyrics, playback.positionMs, remember(player) { { ms: Long -> player.seekTo(ms) } }, Modifier.fillMaxSize(), color)
        }
        if (onFullScreen != null) {
            GlassIconButton(onFullScreen, Icons.Rounded.Fullscreen, stringResource(Res.string.lyrics_full_screen), glass = false,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
    }
}
