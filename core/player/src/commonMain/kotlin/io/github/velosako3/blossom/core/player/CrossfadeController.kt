// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player

import io.github.velosako3.blossom.core.model.Track
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Equal-power crossfade: the sum of the two signals' power stays constant through the fade. */
object CrossfadeCurve {
    /** (outgoing, incoming) volume at [elapsedMs] into a fade of [durationMs]. */
    fun volumes(elapsedMs: Long, durationMs: Long): Pair<Float, Float> {
        val t = if (durationMs <= 0) 1.0 else (elapsedMs.toDouble() / durationMs).coerceIn(0.0, 1.0)
        return cos(t * PI / 2).toFloat() to sin(t * PI / 2).toFloat()
    }
}

/**
 * Whether going from [current] to [next] crossfades. Off at 0 s; never between neighbours on one album (they're
 * meant to run into each other, so they play gaplessly). The caller only asks about the track that follows.
 */
fun shouldCrossfade(current: Track, next: Track, settingSeconds: Int): Boolean {
    if (settingSeconds <= 0) return false
    val album = current.album?.id
    return album == null || album != next.album?.id
}
