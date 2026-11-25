// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.model.sync

import kotlinx.serialization.Serializable
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Clock

/** A hybrid logical clock timestamp. Total order: wall time, then counter, then device ID. */
@Serializable
data class Hlc(val wallMs: Long, val counter: Int, val deviceId: String) : Comparable<Hlc> {
    override fun compareTo(other: Hlc): Int =
        compareValuesBy(this, other, Hlc::wallMs, Hlc::counter, Hlc::deviceId)
}

/**
 * Issues [Hlc]s that never go backwards, even when the device clock does, and that always sort after anything
 * received from other devices — so "last writer wins" is well defined across devices with skewed clocks.
 */
@OptIn(ExperimentalAtomicApi::class)
class HybridLogicalClock(private val deviceId: String, private val clock: Clock) {
    // Compare-and-set instead of a lock: the same on every platform (iOS has no @Synchronized).
    private val last = AtomicReference(Hlc(0, 0, deviceId))

    fun now(): Hlc = advance { last ->
        val physical = clock.now().toEpochMilliseconds()
        if (physical > last.wallMs) Hlc(physical, 0, deviceId) else Hlc(last.wallMs, last.counter + 1, deviceId)
    }

    fun receive(remote: Hlc): Hlc = advance { last ->
        val physical = clock.now().toEpochMilliseconds()
        val wall = maxOf(last.wallMs, remote.wallMs, physical)
        val counter = when {
            wall == last.wallMs && wall == remote.wallMs -> maxOf(last.counter, remote.counter) + 1
            wall == last.wallMs -> last.counter + 1
            wall == remote.wallMs -> remote.counter + 1
            else -> 0
        }
        Hlc(wall, counter, deviceId)
    }

    private inline fun advance(next: (Hlc) -> Hlc): Hlc {
        while (true) {
            val prev = last.load()
            val n = next(prev)
            if (last.compareAndSet(prev, n)) return n
        }
    }
}
