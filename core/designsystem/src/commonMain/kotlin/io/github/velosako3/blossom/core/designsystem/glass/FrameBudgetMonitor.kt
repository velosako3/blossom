// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.glass

/**
 * Watches frame times and steps glass quality down when the device can't keep up.
 *
 * Once a full [windowMs] of frames has been seen, if the [percentile] frame time exceeds [thresholdMs] it returns
 * the next lower quality, then stays quiet for [QUIET_MS] and starts a fresh window. It never upgrades within a
 * session. Allocation-free per frame: a ring buffer plus a reusable sort scratch.
 */
class FrameBudgetMonitor(
    private val windowMs: Long = 3000,
    private val thresholdMs: Double = 20.0,
    private val percentile: Double = 0.9,
    initial: GlassQuality = GlassQuality.FULL,
) {
    var current: GlassQuality = initial
        private set

    private val times = LongArray(CAPACITY)
    private val frames = DoubleArray(CAPACITY)
    private val scratch = DoubleArray(CAPACITY)
    private var head = 0
    private var size = 0
    private var windowStart = -1L
    private var quietUntil = Long.MIN_VALUE

    fun onFrame(frameTimeMs: Double, nowMs: Long): GlassQuality? {
        if (current == GlassQuality.OFF || nowMs < quietUntil) return null
        if (windowStart < 0) windowStart = nowMs
        push(nowMs, frameTimeMs)
        while (size > 0 && times[head] < nowMs - windowMs) { head = (head + 1) % CAPACITY; size-- }
        if (nowMs - windowStart < windowMs) return null

        for (i in 0 until size) scratch[i] = frames[(head + i) % CAPACITY]
        scratch.sort(0, size)
        val idx = (kotlin.math.ceil(percentile * size).toInt() - 1).coerceIn(0, size - 1)
        if (scratch[idx] <= thresholdMs) return null

        current = GlassQuality.entries[current.ordinal - 1]
        quietUntil = nowMs + QUIET_MS
        size = 0; head = 0; windowStart = -1
        return current
    }

    private fun push(t: Long, f: Double) {
        if (size == CAPACITY) { head = (head + 1) % CAPACITY; size-- }
        val i = (head + size) % CAPACITY
        times[i] = t; frames[i] = f; size++
    }

    private companion object {
        const val CAPACITY = 2048
        const val QUIET_MS = 10_000L
    }
}
