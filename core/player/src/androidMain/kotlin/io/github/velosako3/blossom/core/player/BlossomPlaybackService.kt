// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Hosts the media session: notification with artwork, lock-screen and Bluetooth/headset controls. Next/previous
 * from the system go to blossom's own queue. Audio focus and "headphones unplugged" go through [AudioFocusPolicy].
 * The app registers the running player in [PlayerHolder] before starting playback.
 */
@OptIn(UnstableApi::class)
class BlossomPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val focusPolicy = AudioFocusPolicy()
    private lateinit var audioManager: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var ducked = false

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) PlayerHolder.player?.pause()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val engine = PlayerHolder.engine
        val player = PlayerHolder.player
        if (engine == null || player == null) { stopSelf(); return }
        audioManager = getSystemService(AudioManager::class.java)
        session = MediaSession.Builder(this, forwarding(engine, player)).build()
        // Crossfade swaps engines: the notification and lock screen follow the one playing the current track.
        (player as? DefaultBlossomPlayer)?.let { p ->
            scope.launch {
                p.activeEngine.collect { active ->
                    val e = active as? Media3PlaybackEngine ?: return@collect
                    session?.let { if ((it.player as? ForwardingPlayer)?.wrappedPlayer !== e.exoPlayer) it.player = forwarding(e, player) }
                }
            }
        }
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(noisy, filter, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(noisy, filter)
        scope.launch {
            player.state.map { it.isPlaying }.distinctUntilChanged().collect { playing -> if (playing) { focusPolicy.onUserPlay(); requestFocus() } }
        }
    }

    /** The engine's ExoPlayer as the session sees it, with transport commands routed to blossom's player. */
    private fun forwarding(engine: Media3PlaybackEngine, player: BlossomPlayer) = object : ForwardingPlayer(engine.exoPlayer) {
            override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT).add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM).add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM).build()
            override fun isCommandAvailable(command: Int) = availableCommands.contains(command)
            override fun seekToNext() = player.next()
            override fun seekToNextMediaItem() = player.next()
            override fun seekToPrevious() = player.previous()
            override fun seekToPreviousMediaItem() = player.previous()
            override fun play() = player.play()
            override fun pause() = player.pause()
            override fun setPlayWhenReady(playWhenReady: Boolean) = if (playWhenReady) player.play() else player.pause()
            override fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    }

    private fun requestFocus() {
        val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener(::onFocus)
            .build().also { focusRequest = it }
        audioManager.requestAudioFocus(request)
    }

    private fun onFocus(code: Int) {
        val player = PlayerHolder.player ?: return
        val change = when (code) {
            AudioManager.AUDIOFOCUS_GAIN -> FocusChange.GAIN
            AudioManager.AUDIOFOCUS_LOSS -> FocusChange.LOSS
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> FocusChange.LOSS_TRANSIENT
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> FocusChange.LOSS_TRANSIENT_CAN_DUCK
            else -> return
        }
        when (val action = focusPolicy.onFocusChange(change, player.state.value.isPlaying)) {
            is FocusAction.Duck -> { ducked = true; player.setVolume(action.volume) }
            is FocusAction.Pause -> player.pause()
            FocusAction.Resume -> { player.setVolume(1f); player.play() }
            FocusAction.Unduck -> if (ducked) { ducked = false; player.setVolume(1f) }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        runCatching { unregisterReceiver(noisy) }
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        session?.release()
        session = null
        scope.cancel()
        super.onDestroy()
    }
}

/** Where the app hands the running engine and player to the service (the service can't construct them). */
object PlayerHolder {
    @Volatile var engine: Media3PlaybackEngine? = null
    @Volatile var player: BlossomPlayer? = null
}
