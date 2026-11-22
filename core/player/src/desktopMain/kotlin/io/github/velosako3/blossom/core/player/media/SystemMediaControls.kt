// SPDX-License-Identifier: AGPL-3.0-or-later
// Based on Spotube v6 (https://github.com/KRTirtho/spotube, AGPL-3.0-or-later),
// composeApp/src/jvmMain/kotlin/org/spotube/spotube/core/mediacontrol/SystemMediaControlService.kt,
// Copyright (C) 2026 Kingkor Roy Tirtho and Spotube Contributors.
// Modified for blossom, 2026: driven by BlossomPlayer state and MprisMetadataMapper.
package io.github.velosako3.blossom.core.player.media

import dev.nucleusframework.media.control.MediaControlEvent
import dev.nucleusframework.media.control.MediaControlService
import dev.nucleusframework.media.control.MediaMetadata
import dev.nucleusframework.media.control.MediaPlaybackState
import dev.nucleusframework.media.control.MediaPlaybackStatus
import io.github.velosako3.blossom.core.player.BlossomPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Linux MPRIS (bus name `org.mpris.MediaPlayer2.blossom`) and Windows System Media Transport Controls, through
 * nucleus media-control. Media keys, the GNOME/KDE media widgets and the Windows overlay all control blossom.
 */
class SystemMediaControls(private val player: BlossomPlayer, scope: CoroutineScope) {
    val available: Boolean = runCatching { MediaControlService.isAvailable() }.getOrDefault(false)

    init {
        if (available) {
            MediaControlService.configure("org.mpris.MediaPlayer2.blossom", "blossom")
            MediaControlService.attach(::onEvent)
            scope.launch {
                player.state.map { it.current }.distinctUntilChanged().collect { track ->
                    if (track == null) return@collect
                    val m = MprisMetadataMapper.map(track)
                    @Suppress("UNCHECKED_CAST")
                    MediaControlService.setMetadata(
                        MediaMetadata(
                            m["xesam:title"] as String,
                            (m["xesam:artist"] as List<String>).joinToString(", "),
                            m["xesam:album"] as String?,
                            m["mpris:artUrl"] as String?,
                            track.durationMs,
                        ),
                    )
                }
            }
            scope.launch {
                kotlinx.coroutines.flow.combine(player.state, player.progress) { it, p -> (if (it.current == null) MediaPlaybackStatus.STOPPED else if (it.isPlaying) MediaPlaybackStatus.PLAYING else MediaPlaybackStatus.PAUSED) to p.positionMs / 1000 }
                    .distinctUntilChanged()
                    .collect { (status, _) -> MediaControlService.setPlaybackState(MediaPlaybackState(status, player.progress.value.positionMs)) }
            }
        }
    }

    private fun onEvent(event: MediaControlEvent) {
        when (event) {
            MediaControlEvent.Play -> player.play()
            MediaControlEvent.Pause, MediaControlEvent.Stop -> player.pause()
            MediaControlEvent.Toggle -> if (player.state.value.isPlaying) player.pause() else player.play()
            MediaControlEvent.Next -> player.next()
            MediaControlEvent.Previous -> player.previous()
            is MediaControlEvent.SeekBy -> player.seekTo((player.progress.value.positionMs + event.offsetMs).coerceAtLeast(0))
            is MediaControlEvent.SetPosition -> player.seekTo(event.positionMs)
            is MediaControlEvent.SetVolume -> player.setVolume(event.volume.toFloat())
            else -> Unit
        }
    }
}
