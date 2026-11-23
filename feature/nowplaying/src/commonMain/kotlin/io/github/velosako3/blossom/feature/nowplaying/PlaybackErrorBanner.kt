// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.nowplaying

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.glass.GlassSurface
import io.github.velosako3.blossom.core.player.BlossomPlayer
import io.github.velosako3.blossom.core.player.PlaybackError
import io.github.velosako3.blossom.feature.nowplaying.resources.Res
import io.github.velosako3.blossom.feature.nowplaying.resources.np_failed
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** A short glass notice when a song couldn't be played and the player moved on. Announced to screen readers. */
@Composable
fun PlaybackErrorBanner(player: BlossomPlayer, modifier: Modifier = Modifier) {
    val state by player.state.collectAsState()
    var shown by remember { mutableStateOf<PlaybackError?>(null) }
    LaunchedEffect(state.error) {
        shown = state.error
        if (shown != null) { delay(4_000); shown = null }
    }
    val error = shown
    val id = when (error) { is PlaybackError.StreamFailed -> error.trackId; is PlaybackError.Unavailable -> error.trackId; null -> null }
    val title = state.queue.firstOrNull { it.id == id }?.title ?: ""
    AnimatedVisibility(error != null, modifier, enter = fadeIn(), exit = fadeOut()) {
        GlassSurface(Modifier.widthIn(max = 480.dp).semantics { liveRegion = LiveRegionMode.Polite }, shape = CircleShape) {
            Text(stringResource(Res.string.np_failed, title), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        }
    }
}
