// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.database

import io.github.velosako3.blossom.core.model.sync.Hlc
import io.github.velosako3.blossom.core.model.sync.HybridLogicalClock
import io.github.velosako3.blossom.core.model.sync.OpEnvelope
import io.github.velosako3.blossom.core.model.sync.Operation
import io.github.velosako3.blossom.core.model.sync.OperationJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** The append-only log of library operations from every device. */
interface OpLog {
    suspend fun append(op: Operation): OpEnvelope
    suspend fun since(deviceId: String, afterSeq: Long): List<OpEnvelope>
    /** Stores operations from other devices; returns only the ones not seen before. */
    suspend fun insertRemote(ops: List<OpEnvelope>): List<OpEnvelope>
}

/** `<wall 15 digits>-<counter 6 digits>-<device>`: sorts like the HLC itself. */
fun Hlc.encode(): String = "${wallMs.toString().padStart(15, '0')}-${counter.toString().padStart(6, '0')}-$deviceId"

fun decodeHlc(s: String): Hlc {
    val (wall, counter, device) = s.split('-', limit = 3)
    return Hlc(wall.toLong(), counter.toInt(), device)
}

class SqlOpLog(
    private val db: BlossomDatabase,
    val deviceId: String,
    val clock: HybridLogicalClock,
) : OpLog {
    private val q get() = db.blossomQueries

    override suspend fun append(op: Operation): OpEnvelope = withContext(Dispatchers.IO) { db.transactionWithResult { appendInTransaction(op) } }

    /** For callers that append and project inside one transaction. */
    @OptIn(ExperimentalUuidApi::class)
    fun appendInTransaction(op: Operation): OpEnvelope {
        val seq = (q.maxSeq(deviceId).executeAsOne().seq ?: 0L) + 1
        val env = OpEnvelope(Uuid.random().toString(), clock.now(), deviceId, seq, op)
        insert(env)
        return env
    }

    override suspend fun since(deviceId: String, afterSeq: Long): List<OpEnvelope> = withContext(Dispatchers.IO) {
        q.opsSince(deviceId, afterSeq).executeAsList().map { decode(it.op_id, it.device_id, it.seq, it.hlc_wall, it.hlc_counter, it.payload) }
    }

    suspend fun all(): List<OpEnvelope> = withContext(Dispatchers.IO) {
        q.allOps().executeAsList().map { decode(it.op_id, it.device_id, it.seq, it.hlc_wall, it.hlc_counter, it.payload) }
    }

    suspend fun knownDevices(): Map<String, Long> = withContext(Dispatchers.IO) {
        q.knownDevices().executeAsList().associate { it.device_id to (it.last_seq ?: 0L) }
    }

    override suspend fun insertRemote(ops: List<OpEnvelope>): List<OpEnvelope> = withContext(Dispatchers.IO) {
        db.transactionWithResult { insertRemoteInTransaction(ops) }
    }

    fun insertRemoteInTransaction(ops: List<OpEnvelope>): List<OpEnvelope> = ops.filter { env ->
        val inserted = insert(env) > 0
        if (inserted) clock.receive(env.hlc)
        inserted
    }

    private fun insert(env: OpEnvelope): Long =
        q.insertOp(env.opId, env.deviceId, env.seq, env.hlc.wallMs, env.hlc.counter.toLong(), OperationJson.encodeToString(Operation.serializer(), env.op)).value

    private fun decode(opId: String, device: String, seq: Long, wall: Long, counter: Long, payload: String) =
        OpEnvelope(opId, Hlc(wall, counter.toInt(), device), device, seq, OperationJson.decodeFromString(Operation.serializer(), payload))
}
