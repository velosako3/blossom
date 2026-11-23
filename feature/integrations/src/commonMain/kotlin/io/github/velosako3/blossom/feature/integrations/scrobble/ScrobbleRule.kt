// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.integrations.scrobble

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** Last.fm's rule, used for both services: longer than 30 s, played for half its length or 4 minutes, whichever is first. */
object ScrobbleRule {
    fun shouldScrobble(durationMs: Long, playedMs: Long): Boolean =
        durationMs > 30_000 && playedMs >= minOf(durationMs / 2, 240_000)
}

@Serializable
data class ScrobbleTrack(val artist: String, val title: String, val album: String?, val durationMs: Long)

/** A finished listen, stamped with when it started (both services want the start time). */
@Serializable
data class Scrobble(val track: ScrobbleTrack, val startedAt: Instant)

/** A scrobbling service. [enabled] is false while it isn't set up (no session or token). */
interface ScrobbleService {
    val id: String
    val enabled: Boolean
    suspend fun nowPlaying(track: ScrobbleTrack)
    suspend fun scrobble(item: Scrobble)
}
