// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.model.sync

import kotlinx.serialization.Serializable
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
class HybridLogicalClock(private val deviceId: String, private val clock: Clock) {
    private var last = Hlc(0, 0, deviceId)

    @Synchronized
    fun now(): Hlc {
        val physical = clock.now().toEpochMilliseconds()
        last = if (physical > last.wallMs) Hlc(physical, 0, deviceId) else Hlc(last.wallMs, last.counter + 1, deviceId)
        return last
    }

    @Synchronized
    fun receive(remote: Hlc): Hlc {
        val physical = clock.now().toEpochMilliseconds()
        val wall = maxOf(last.wallMs, remote.wallMs, physical)
        val counter = when (wall) {
            last.wallMs if wall == remote.wallMs -> maxOf(last.counter, remote.counter) + 1
            last.wallMs -> last.counter + 1
            remote.wallMs -> remote.counter + 1
            else -> 0
        }
        last = Hlc(wall, counter, deviceId)
        return last
    }
}
