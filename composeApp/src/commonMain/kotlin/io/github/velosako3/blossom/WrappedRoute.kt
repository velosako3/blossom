// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.velosako3.blossom.core.designsystem.components.rememberArtworkPalette
import io.github.velosako3.blossom.core.designsystem.theme.LocalArtworkPalette
import io.github.velosako3.blossom.core.player.PlaybackState
import io.github.velosako3.blossom.feature.stats.Period
import io.github.velosako3.blossom.feature.stats.ui.StatsUi
import io.github.velosako3.blossom.feature.stats.wrapped.WrappedCard
import io.github.velosako3.blossom.feature.stats.wrapped.WrappedDeckBuilder
import io.github.velosako3.blossom.feature.stats.wrapped.WrappedExporter
import io.github.velosako3.blossom.feature.stats.wrapped.WrappedScreen
import kotlinx.coroutines.launch

/**
 * Wrapped for the period in [key]: the story tuned to the top song's artwork, with the top song playing softly from
 * 30% (when it can play here). Whatever was playing before comes back on exit.
 */
@Composable
fun WrappedRoute(graph: AppGraph, key: String, onClose: () -> Unit) {
    val period = remember(key) { Period.fromKey(key, graph.stats.zoneId) } ?: return onClose()
    val ui by remember(period) { graph.stats.statsFor(period) }.collectAsState(StatsUi.Loading)
    val stats = (ui as? StatsUi.Loaded)?.stats
    if (ui is StatsUi.Loading) return
    val deck = stats?.let(WrappedDeckBuilder::build) ?: listOf(WrappedCard.NotEnoughData)
    val top = stats?.topTracks?.firstOrNull()?.item
    val palette = rememberArtworkPalette(top?.artworkUrl)
    WrappedExporter.palette = palette
    var muted by remember { mutableStateOf(false) }
    val online = graph.network.status.collectAsState().value.online
    val playable = top != null && graph.isAvailable(top, online)

    DisposableEffect(top?.id, playable) {
        val before: PlaybackState = graph.player.state.value
        if (playable && top != null) graph.scope.launch {
            graph.player.setVolume(VOLUME)
            graph.player.setQueue(listOf(top), 0, startPositionMs = (top.durationMs * 0.3).toLong())
            graph.player.play()
        }
        onDispose {
            if (!playable) return@onDispose
            graph.scope.launch {
                graph.player.setVolume(1f)
                if (before.queue.isEmpty()) { graph.player.pause(); return@launch }
                graph.player.setQueue(before.queue, before.currentIndex.coerceAtLeast(0), startPositionMs = before.positionMs)
                if (before.isPlaying) graph.player.play() else graph.player.pause()
            }
        }
    }

    CompositionLocalProvider(LocalArtworkPalette provides palette) {
        WrappedScreen(
            deck, onClose,
            muted = muted,
            onToggleMute = if (playable) ({ muted = !muted; graph.player.setVolume(if (muted) 0f else VOLUME) }) else null,
        )
    }
}

private const val VOLUME = 0.6f
