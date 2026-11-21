// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QueueManagerTest {
    private fun queueAt(index: Int, size: Int = 5) = QueueManager().apply { setQueue(tracks(size), index) }

    @Test fun nextAtEndRepeatAllWraps() {
        val q = queueAt(4).apply { repeat = RepeatMode.ALL }
        assertEquals(QueueMove.Moved(0), q.next())
    }

    @Test fun nextAtEndRepeatOffEnds() {
        val q = queueAt(4).apply { repeat = RepeatMode.OFF }
        assertEquals(QueueMove.End, q.next())
        assertEquals(4, q.currentIndex)
    }

    @Test fun nextRepeatOneStays() {
        val q = queueAt(4).apply { repeat = RepeatMode.ONE }
        assertEquals(QueueMove.Moved(4), q.next())
    }

    @Test fun previousEarlyGoesBackLateRestarts() {
        assertEquals(QueueMove.Restart, queueAt(2).previous(positionMs = 4_000))
        assertEquals(QueueMove.Moved(1), queueAt(2).previous(positionMs = 2_000))
    }

    @Test fun shuffleKeepsCurrentFirstAndUnique() {
        val q = queueAt(3, size = 10)
        val current = q.current
        q.setShuffle(true, seed = 42)
        assertEquals(current, q.queue[0])
        assertEquals(0, q.currentIndex)
        assertEquals(10, q.queue.map { it.id }.toSet().size)
        // Deterministic for a given seed.
        val again = queueAt(3, size = 10).apply { setShuffle(true, seed = 42) }
        assertEquals(q.queue, again.queue)
    }

    @Test fun unshuffleRestoresOrder() {
        val q = queueAt(3, size = 10)
        val original = q.queue
        q.setShuffle(true, seed = 7)
        q.setShuffle(false)
        assertEquals(original, q.queue)
        assertEquals(3, q.currentIndex)
    }

    @Test fun moveKeepsCurrentTrack() {
        val q = queueAt(2)
        val current = q.current
        q.move(1, 4)
        assertEquals(listOf("t0", "t2", "t3", "t4", "t1"), q.queue.map { it.id.sourceId })
        assertEquals(current, q.current)
        assertTrue(q.currentIndex == 1)
    }
}
