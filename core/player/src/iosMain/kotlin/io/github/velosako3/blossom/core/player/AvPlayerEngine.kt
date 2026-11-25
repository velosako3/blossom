// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player

import io.github.velosako3.blossom.core.model.Track
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemFailedToPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.removeTimeObserver
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.timeControlStatus
import platform.AVFoundation.volume
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.darwin.NSObjectProtocol

/**
 * iOS playback through AVPlayer: remote streams with their request headers, background audio (the Playback session
 * category), and the lock screen / Control Center through MPNowPlayingInfoCenter. [remote] receives the lock screen's
 * commands ("play", "pause", "next", "previous") so the player's own state stays the truth.
 *
 * Offline and local files aren't on iOS yet; they report an error.
 */
@OptIn(ExperimentalForeignApi::class)
class AvPlayerEngine : PlaybackEngine {
    private val player = AVPlayer()
    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 64)
    override val events: Flow<EngineEvent> = _events
    override var mediaId: Long = 0
        private set
    private var timeObserver: Any? = null
    private val observers = mutableListOf<NSObjectProtocol>()
    private var track: Track? = null
    private var wasWaiting = false

    /** Lock screen and Control Center commands, routed to the app's player. */
    var remote: ((String) -> Unit)? = null

    init {
        runCatching {
            AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, null)
            AVAudioSession.sharedInstance().setActive(true, null)
        }
        timeObserver = player.addPeriodicTimeObserverForInterval(CMTimeMake(1, 4), null) { time ->
            val id = mediaId
            val position = (CMTimeGetSeconds(time) * 1000).toLong().coerceAtLeast(0)
            val item = player.currentItem
            val duration = item?.duration?.let { d -> CMTimeGetSeconds(d).takeIf { !it.isNaN() && it > 0 } }?.let { (it * 1000).toLong() } ?: 0
            _events.tryEmit(EngineEvent.Position(position, duration, id))
            if (item?.status == AVPlayerItemStatusFailed) _events.tryEmit(EngineEvent.Error(null, Exception(item.error?.localizedDescription ?: "AVPlayer failed"), id))
            val waiting = player.timeControlStatus == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
            if (waiting != wasWaiting) { wasWaiting = waiting; _events.tryEmit(EngineEvent.Buffering(waiting, id)) }
            updateNowPlaying(position, duration)
        }
        val center = NSNotificationCenter.defaultCenter
        observers += center.addObserverForName(AVPlayerItemDidPlayToEndTimeNotification, null, NSOperationQueue.mainQueue) { note ->
            if (note?.`object` == player.currentItem) _events.tryEmit(EngineEvent.Ended(mediaId))
        }
        observers += center.addObserverForName(AVPlayerItemFailedToPlayToEndTimeNotification, null, NSOperationQueue.mainQueue) { note ->
            if (note?.`object` == player.currentItem) _events.tryEmit(EngineEvent.Error(null, Exception("Playback stopped"), mediaId))
        }
        val commands = MPRemoteCommandCenter.sharedCommandCenter()
        commands.playCommand.addTargetWithHandler { remote?.invoke("play"); MPRemoteCommandHandlerStatusSuccess }
        commands.pauseCommand.addTargetWithHandler { remote?.invoke("pause"); MPRemoteCommandHandlerStatusSuccess }
        commands.togglePlayPauseCommand.addTargetWithHandler { remote?.invoke("toggle"); MPRemoteCommandHandlerStatusSuccess }
        commands.nextTrackCommand.addTargetWithHandler { remote?.invoke("next"); MPRemoteCommandHandlerStatusSuccess }
        commands.previousTrackCommand.addTargetWithHandler { remote?.invoke("previous"); MPRemoteCommandHandlerStatusSuccess }
    }

    override suspend fun load(input: MediaInput, startPositionMs: Long) {
        mediaId++
        when (input) {
            is MediaInput.Remote -> {
                val url = NSURL.URLWithString(input.stream.url) ?: run { _events.tryEmit(EngineEvent.Error(null, Exception("Bad stream URL"), mediaId)); return }
                // The stream's headers (YouTube's needs its client's User-Agent) ride on every request AVPlayer makes.
                val asset = AVURLAsset.URLAssetWithURL(url, mapOf<Any?, Any?>("AVURLAssetHTTPHeaderFieldsKey" to input.stream.headers))
                player.replaceCurrentItemWithPlayerItem(AVPlayerItem(asset = asset))
                if (startPositionMs > 0) seekTo(startPositionMs)
            }
            else -> _events.tryEmit(EngineEvent.Error(null, UnsupportedOperationException("Saved and local songs aren't on iOS yet"), mediaId))
        }
    }

    override fun play() = player.play()
    override fun pause() = player.pause()
    override fun seekTo(ms: Long) { player.seekToTime(CMTimeMakeWithSeconds(ms / 1000.0, 1000)) }
    override fun setVolume(v: Float) { player.volume = v.coerceIn(0f, 1f) }

    override fun onTrackChanged(track: Track?) {
        this.track = track
        updateNowPlaying(0, track?.durationMs ?: 0)
    }

    private fun updateNowPlaying(positionMs: Long, durationMs: Long) {
        val t = track ?: run { MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null; return }
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = mapOf<Any?, Any?>(
            MPMediaItemPropertyTitle to t.title,
            MPMediaItemPropertyArtist to t.artistNames,
            MPMediaItemPropertyPlaybackDuration to (durationMs.takeIf { it > 0 } ?: t.durationMs) / 1000.0,
            MPNowPlayingInfoPropertyElapsedPlaybackTime to positionMs / 1000.0,
        )
    }

    override fun release() {
        timeObserver?.let { player.removeTimeObserver(it) }
        observers.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        player.pause()
        player.replaceCurrentItemWithPlayerItem(null)
    }
}
