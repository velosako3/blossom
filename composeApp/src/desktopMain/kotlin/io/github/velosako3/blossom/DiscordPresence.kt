// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import io.github.velosako3.blossom.core.player.PlaybackState
import io.github.velosako3.blossom.feature.integrations.discord.DiscordIpc
import io.github.velosako3.blossom.feature.integrations.discord.PresenceBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * Discord Rich Presence while [enabled] (off by default, for privacy). Updates on a track change, play/pause or a
 * seek, at most one every 5 s (Discord's limit); silently does nothing while Discord isn't running.
 */
class DiscordPresence(state: StateFlow<PlaybackState>, progress: StateFlow<io.github.velosako3.blossom.core.player.PlaybackProgress>, enabled: StateFlow<Boolean>, scope: CoroutineScope, applicationId: String = BlossomSecrets.DISCORD_APPLICATION_ID) {
    private val ipc = DiscordIpc(applicationId)

    /** What the presence shows; the position only matters as "where the track started" (seeks change it). */
    private data class Shown(val trackId: String?, val playing: Boolean, val startedAtSec: Long)

    init {
        if (applicationId.isNotEmpty()) scope.launch(Dispatchers.IO) {
            combine(enabled, state, progress) { on, s, p ->
                val t = s.current?.takeIf { on }
                Triple(Shown(t?.id?.value, s.isPlaying, (Clock.System.now().toEpochMilliseconds() - p.positionMs) / 5_000), t, s)
            }.distinctUntilChanged { a, b -> a.first == b.first }.conflate().collectLatest { (_, track, s) ->
                if (track == null) { if (ipc.connected) ipc.setActivity(null); return@collectLatest }
                if (!ipc.connect()) return@collectLatest
                ipc.setActivity(PresenceBuilder.activity(track, progress.value.positionMs, s.isPlaying, Clock.System.now()))
                delay(5_000)
            }
        }
    }
}
