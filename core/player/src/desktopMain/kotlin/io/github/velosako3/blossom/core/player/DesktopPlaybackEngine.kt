// SPDX-License-Identifier: AGPL-3.0-or-later
// Based on Spotube v6 (https://github.com/KRTirtho/spotube, AGPL-3.0-or-later),
// composeApp/src/jvmMain/kotlin/org/spotube/spotube/core/audioplayer/AudioPlayer.jvm.kt,
// Copyright (C) 2026 Kingkor Roy Tirtho and Spotube Contributors.
// Modified for blossom, 2026: one item per player (blossom owns the queue), events mapped to EngineEvent,
// remote streams routed through RemoteStreamProxy for arbitrary headers and chunked reads.
package io.github.velosako3.blossom.core.player

import io.github.velosako3.blossom.core.model.TrackId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter

/**
 * libVLC through vlcj, bundled with the app (see composeApp's vlcj-bundler setup). [offlineUrl] maps a saved track
 * to its loopback URL on the encrypted-cache server (Task 5.3).
 */
class DesktopPlaybackEngine(
    private val offlineUrl: (TrackId) -> String? = { null },
    private val proxy: RemoteStreamProxy = RemoteStreamProxy(),
) : PlaybackEngine {
    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<EngineEvent> = _events

    private val factory = MediaPlayerFactory(
        null as NativeDiscovery?,
        "--no-video", "--vout=dummy", "--aout=any", "--no-osd", "--no-snapshot-preview", "--quiet",
    )
    val mediaPlayer: MediaPlayer = factory.mediaPlayers().newMediaPlayer()

    init {
        mediaPlayer.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                _events.tryEmit(EngineEvent.Position(newTime, mediaPlayer.status().length().coerceAtLeast(0)))
            }
            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                _events.tryEmit(EngineEvent.Buffering(newCache < 100f))
            }
            override fun playing(mediaPlayer: MediaPlayer) { _events.tryEmit(EngineEvent.Buffering(false)) }
            override fun finished(mediaPlayer: MediaPlayer) { _events.tryEmit(EngineEvent.Ended) }
            override fun error(mediaPlayer: MediaPlayer) {
                _events.tryEmit(EngineEvent.Error(proxy.lastUpstreamStatus?.takeIf { it >= 400 }, null))
            }
        })
    }

    override suspend fun load(input: MediaInput, startPositionMs: Long) {
        val mrl = when (input) {
            is MediaInput.Remote -> proxy.register(input.stream)
            is MediaInput.LocalFile -> input.path
            is MediaInput.Offline -> offlineUrl(input.trackId)
                ?: run { _events.tryEmit(EngineEvent.Error(null, IllegalStateException("not saved"))); return }
        }
        val options = if (startPositionMs > 0) arrayOf(":start-time=${startPositionMs / 1000.0}") else emptyArray()
        mediaPlayer.media().prepare(mrl, *options)
    }

    override fun play() { mediaPlayer.submit { mediaPlayer.controls().play() } }
    override fun pause() { mediaPlayer.submit { mediaPlayer.controls().setPause(true) } }
    override fun seekTo(ms: Long) { mediaPlayer.submit { mediaPlayer.controls().setTime(ms) } }
    override fun setVolume(v: Float) { mediaPlayer.submit { mediaPlayer.audio().setVolume((v * 100).toInt().coerceIn(0, 200)) } }

    override fun release() {
        mediaPlayer.release()
        factory.release()
        proxy.stop()
    }
}
