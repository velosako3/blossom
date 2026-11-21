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

    @Test fun sinceReturnsInSeqOrder() = runTest {
        val log = log("A")
        repeat(5) { log.append(Operation.PlaylistRenamed(PlaylistId("p"), "n$it")) }
        val since = log.since("A", 2)
        assertEquals(listOf(3L, 4L, 5L), since.map { it.seq })
        assertEquals("n2", (since.first().op as Operation.PlaylistRenamed).name)
    }
}
