// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.model.sync

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class FractionalIndexTest {
    @Test fun firstKeyIsNotEmpty() = assertTrue(FractionalIndex.between(null, null).isNotEmpty())

    @Test fun randomPairsStayBetween() {
        val rnd = Random(7)
        val keys = mutableListOf(FractionalIndex.between(null, null))
        repeat(300) {
            val i = rnd.nextInt(keys.size + 1)
            keys.add(i, FractionalIndex.between(keys.getOrNull(i - 1), keys.getOrNull(i)))
        }
        repeat(1000) {
            val i = rnd.nextInt(keys.size - 1)
            val a = keys[i]; val b = keys[i + 1]
            val m = FractionalIndex.between(a, b)
            assertTrue(a < m && m < b, "$a < $m < $b")
        }
    }

    @Test fun frontInsertsStayShortAndOrdered() {
        var first = FractionalIndex.between(null, null)
        val keys = mutableListOf(first)
        repeat(500) {
            first = FractionalIndex.between(null, first)
            keys.add(0, first)
        }
        assertTrue(keys.zipWithNext().all { (a, b) -> a < b })
        assertTrue(keys.maxOf { it.length } < 100, "longest ${keys.maxOf { it.length }}")
    }

    @Test fun batchBetweenIsOrderedAndShort() {
        val keys = FractionalIndex.nBetween("A", "B", 5000)
        assertTrue((listOf("A") + keys + "B").zipWithNext().all { (a, b) -> a < b })
        assertTrue(keys.maxOf { it.length } < 20, "longest ${keys.maxOf { it.length }}")
    }

    @Test fun appendsStayShort() {
        var last = FractionalIndex.between(null, null)
        repeat(500) { val next = FractionalIndex.between(last, null); assertTrue(last < next); last = next }
        assertTrue(last.length < 20, last)
    }
}
