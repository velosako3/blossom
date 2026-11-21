// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics.ui

import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Auto-scroll pauses for 3 s after the user scrolls the lyrics themselves. */
class LyricsScrollController(private val clock: Clock) {
    private var pausedUntil: Instant = Instant.DISTANT_PAST

    fun onUserScroll() { pausedUntil = clock.now() + PAUSE }
    fun resume() { pausedUntil = Instant.DISTANT_PAST }
    fun shouldAutoScroll(): Boolean = clock.now() >= pausedUntil

    companion object { val PAUSE = 3.seconds }
}
