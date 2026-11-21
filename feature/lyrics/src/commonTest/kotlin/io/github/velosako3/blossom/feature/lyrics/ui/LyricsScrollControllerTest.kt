// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class LyricsScrollControllerTest {
    private class C(var now: Instant = Instant.fromEpochSeconds(100)) : Clock { override fun now() = now }

    @Test fun pausesForThreeSecondsAfterUserScroll() {
        val clock = C()
        val c = LyricsScrollController(clock)
        assertEquals(true, c.shouldAutoScroll())
        c.onUserScroll()
        clock.now += 2_900.milliseconds
        assertEquals(false, c.shouldAutoScroll())
        clock.now += 100.milliseconds
        assertEquals(true, c.shouldAutoScroll())
    }
}
