// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.model.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class SettableClock(var ms: Long) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(ms)
}

class HlcTest {
    @Test fun counterIncrementsWithinSameMillisecond() {
        val clock = SettableClock(1000)
        val hlc = HybridLogicalClock("d", clock)
        assertEquals(Hlc(1000, 0, "d"), hlc.now())
        assertEquals(Hlc(1000, 1, "d"), hlc.now())
    }

    @Test fun monotonicWhenClockGoesBack() {
        val clock = SettableClock(1000)
        val hlc = HybridLogicalClock("d", clock)
        val first = hlc.now()
        clock.ms = 900
        assertTrue(hlc.now() > first)
    }

    @Test fun receiveMovesPastRemote() {
        val hlc = HybridLogicalClock("d", SettableClock(1000))
        val remote = Hlc(5000, 3, "other")
        assertTrue(hlc.receive(remote) > remote)
        assertTrue(hlc.now() > remote)
    }

    @Test fun orderingByWallThenCounterThenDevice() {
        assertTrue(Hlc(1, 9, "z") < Hlc(2, 0, "a"))
        assertTrue(Hlc(2, 0, "z") < Hlc(2, 1, "a"))
        assertTrue(Hlc(2, 1, "a") < Hlc(2, 1, "b"))
    }
}
