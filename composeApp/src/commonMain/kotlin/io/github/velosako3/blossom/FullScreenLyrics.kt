// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.background.BlossomBackground
import io.github.velosako3.blossom.core.designsystem.components.GlassIconButton
import io.github.velosako3.blossom.core.designsystem.glass.glassBackdropSource
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.feature.lyrics.ui.LyricsPanel
import io.github.velosako3.blossom.feature.nowplaying.MiniPlayer
import io.github.velosako3.blossom.composeApp.resources.Res
import io.github.velosako3.blossom.composeApp.resources.lyrics_close_full_screen
import org.jetbrains.compose.resources.stringResource

/** Desktop full-screen lyrics: lyrics centred over the artwork background, a small player at the bottom. */
@Composable
fun FullScreenLyrics(graph: AppGraph, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        BlossomBackground(BlossomTheme.palette, Modifier.fillMaxSize().glassBackdropSource())
        LyricsPanel(graph.lyrics, graph.player, Modifier.align(Alignment.Center).widthIn(max = 900.dp).fillMaxSize().padding(bottom = 96.dp))
        Box(Modifier.align(Alignment.BottomCenter).widthIn(max = 560.dp).fillMaxWidth().padding(16.dp)) {
            MiniPlayer(graph.player, onOpen = onClose)
        }
        GlassIconButton(onClose, Icons.Rounded.Close, stringResource(Res.string.lyrics_close_full_screen), modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))
    }
}
