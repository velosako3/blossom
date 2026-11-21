// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.glass

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FrameBudgetMonitorTest {
    /** Feeds [count] frames of [frameMs] each, one every [frameMs], starting at [startMs]; returns the non-null results with their times. */
    private fun FrameBudgetMonitor.feed(count: Int, frameMs: Double, startMs: Long = 0): List<Pair<Long, GlassQuality>> {
        val out = mutableListOf<Pair<Long, GlassQuality>>()
        var t = startMs.toDouble()
        repeat(count) {
            t += frameMs
            onFrame(frameMs, t.toLong())?.let { out += t.toLong() to it }
        }
        return out
    }

    @Test fun fastFramesNeverDowngrade() {
        val m = FrameBudgetMonitor()
        assertEquals(emptyList(), m.feed(count = 200, frameMs = 16.0))
    }

    @Test fun slowFramesDowngradeOnceThenStayQuiet() {
        val m = FrameBudgetMonitor()
        val first = m.feed(count = 200, frameMs = 25.0)
        assertEquals(listOf(GlassQuality.REDUCED), first.map { it.second })
        val downgradeAt = first.single().first
        // The next 10 s of slow frames fall in the quiet period.
        var t = 5_000L
        while (t < downgradeAt + 10_000) {
            t += 25
            if (t < downgradeAt + 10_000) assertNull(m.onFrame(25.0, t), "at $t")
        }
    }

    @Test fun nothingBelowOff() {
        val m = FrameBudgetMonitor(initial = GlassQuality.REDUCED)
        val results = m.feed(count = 4_000, frameMs = 25.0) // 100 s of slow frames
        assertEquals(listOf(GlassQuality.OFF), results.map { it.second })
    }
}
