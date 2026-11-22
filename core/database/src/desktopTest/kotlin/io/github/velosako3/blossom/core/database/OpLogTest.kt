// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.database

import io.github.velosako3.blossom.core.model.PlaylistId
import io.github.velosako3.blossom.core.model.sync.Hlc
import io.github.velosako3.blossom.core.model.sync.HybridLogicalClock
import io.github.velosako3.blossom.core.model.sync.OpEnvelope
import io.github.velosako3.blossom.core.model.sync.Operation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock

class OpLogTest {
    private fun log(device: String = "A") = SqlOpLog(inMemoryDatabase(), device, HybridLogicalClock(device, Clock.System))

    @Test fun insertRemoteReturnsOnlyUnseen() = runTest {
        val log = log()
        val op = OpEnvelope("op-1", Hlc(10, 0, "B"), "B", 1, Operation.PlaylistDeleted(PlaylistId("p")))
        val op2 = op.copy(opId = "op-2", seq = 2)
        assertEquals(listOf(op), log.insertRemote(listOf(op)))
        assertEquals(listOf(op2), log.insertRemote(listOf(op, op2, op2)))
    }

    @Test fun insertRemoteKeepsOnlyTheContiguousRun() = runTest {
        val log = log("A")
        fun op(seq: Long, device: String = "B") = OpEnvelope("$device-$seq", Hlc(10 + seq, 0, device), device, seq, Operation.PlaylistDeleted(PlaylistId("p")))
        // 3 is missing (an unreadable segment, a newer app's operation): 4 waits, or it would be skipped forever.
        assertEquals(listOf(1L, 2L), log.insertRemote(listOf(op(4), op(1), op(2))).map { it.seq })
        assertEquals(listOf(3L, 4L), log.insertRemote(listOf(op(3), op(4))).map { it.seq })
        // Nobody else writes this device's own history.
        assertEquals(emptyList(), log.insertRemote(listOf(op(1, "A"))))
        assertEquals(4L, log.knownDevices()["B"])
        assertEquals(null, log.knownDevices()["A"])
    }

    @Test fun sinceReturnsInSeqOrder() = runTest {
        val log = log("A")
        repeat(5) { log.append(Operation.PlaylistRenamed(PlaylistId("p"), "n$it")) }
        val since = log.since("A", 2)
        assertEquals(listOf(3L, 4L, 5L), since.map { it.seq })
        assertEquals("n2", (since.first().op as Operation.PlaylistRenamed).name)
    }
}
