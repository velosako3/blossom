// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.lan

import io.github.velosako3.blossom.core.database.BlossomDatabase
import io.github.velosako3.blossom.core.database.LibraryProjector
import io.github.velosako3.blossom.core.database.SqlOpLog
import io.github.velosako3.blossom.core.model.sync.OpEnvelope
import io.github.velosako3.blossom.core.platform.SecureStore
import io.github.velosako3.blossom.feature.sync.DecodedLine
import io.github.velosako3.blossom.feature.sync.OpLogCodec
import io.github.velosako3.blossom.feature.sync.applyRemote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.util.Base64

/**
 * What both ends of a LAN session share: trusted peers (keys in the secure store, rows in `sync_peer`) and the
 * operation exchange. Each side sends what it knows per device and gets back everything newer it has, including other
 * devices' operations it relays, so changes spread through the group even between devices that never meet.
 */
class PeerSync(
    private val db: BlossomDatabase,
    private val opLog: SqlOpLog,
    private val projector: LibraryProjector,
    private val secure: SecureStore,
    val deviceId: String,
    @Volatile var deviceName: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Where each paired device was last reached or announced itself (host to sync port); learned, not stored. */
    val addresses = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Int>>()

    /** This device's sync port, sent to peers so they can reach it back; 0 while not listening. */
    @Volatile var listenPort: Int = 0

    private val _paired = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** The name of each device paired with, as it happens (either side). */
    val paired: kotlinx.coroutines.flow.SharedFlow<String> = _paired

    suspend fun keyOf(peer: String): ByteArray? = secure.get("peer.$peer")?.let { Base64.getDecoder().decode(it) }

    suspend fun trust(peer: String, name: String, key: ByteArray) {
        secure.put("peer.$peer", Base64.getEncoder().encodeToString(key))
        withContext(Dispatchers.IO) {
            db.blossomQueries.upsertPeer(peer, name, PeerCrypto.sha256(key).joinToString("") { "%02x".format(it) }, System.currentTimeMillis())
        }
        _paired.tryEmit(name)
    }

    /** Stops trusting [peer]. Its past changes stay merged. */
    suspend fun forget(peer: String) {
        secure.remove("peer.$peer")
        addresses.remove(peer)
        withContext(Dispatchers.IO) { db.blossomQueries.deletePeer(peer) }
    }

    /** Paired devices: ID, name, last seen. */
    fun peerRows(): List<Triple<String, String, kotlin.time.Instant?>> =
        db.blossomQueries.allPeers().executeAsList().map { Triple(it.device_id, it.name, it.last_seen?.let(kotlin.time.Instant::fromEpochMilliseconds)) }

    suspend fun seen(peer: String) = withContext(Dispatchers.IO) { db.blossomQueries.touchPeer(System.currentTimeMillis(), peer) }

    /** Per device, the last operation sequence this side has. */
    suspend fun known(): JsonObject = buildJsonObject { opLog.knownDevices().forEach { (d, s) -> put(d, s) } }

    /** Every operation this side has that [theirs] lacks, one encoded line each. */
    suspend fun newerThan(theirs: JsonObject): String {
        val have = opLog.knownDevices()
        val lines = StringBuilder()
        for ((device, last) in have) {
            val after = (theirs[device] as? JsonPrimitive)?.longOrNull ?: 0
            if (last > after) opLog.since(device, after).forEach { lines.append(OpLogCodec.encode(it)).append('\n') }
        }
        return lines.toString()
    }

    /** Applies operations received from a peer; returns how many were new. */
    suspend fun receive(lines: String): Int {
        val ops: List<OpEnvelope> = lines.lineSequence().filter { it.isNotBlank() }.mapNotNull { (OpLogCodec.decode(it) as? DecodedLine.Known)?.op }.toList()
        return applyRemote(db, opLog, projector, ops)
    }

    internal fun encode(o: JsonObject) = o.toString().toByteArray()
    internal fun decode(bytes: ByteArray) = json.parseToJsonElement(String(bytes)) as JsonObject
}
