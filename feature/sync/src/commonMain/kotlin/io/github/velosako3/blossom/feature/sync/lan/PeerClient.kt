// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.lan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/** Pairs with a device from its QR payload or code, and syncs with paired devices over the LAN. */
class PeerClient(private val sync: PeerSync) {
    private fun connect(host: String, port: Int) = Socket().apply { connect(InetSocketAddress(host, port), 3_000); soTimeout = 30_000 }

    /** Pairs using [payload]: tries each of its addresses. Throws [PairingRejected] on a wrong secret. */
    suspend fun pair(payload: PairingPayload) = withContext(Dispatchers.IO) {
        val socket = payload.hosts.firstNotNullOfOrNull { h -> runCatching { connect(h, payload.port) }.getOrNull() }
            ?: throw IOException("can't reach ${payload.deviceName}")
        FrameChannel(socket).use { ch ->
            val secret = payload.secretBytes()
            val mine = PeerCrypto.ephemeral()
            ch.send(sync.encode(buildJsonObject { put("type", "pair"); put("deviceId", sync.deviceId); put("deviceName", sync.deviceName); put("pub", e64(mine.public.encoded)) }))
            val reply = sync.decode(ch.receive())
            if (reply.type() != "pair") throw PairingRejected("the other device isn't waiting to pair")
            val theirPub = b64(reply.str("pub"))
            val serverId = reply.str("deviceId")
            val transcript = PeerCrypto.sha256(sync.deviceId.toByteArray(), mine.public.encoded, serverId.toByteArray(), theirPub)
            // The server proves it knows the secret before we prove we do.
            if (!PeerCrypto.equal(b64(reply.str("mac")), PeerCrypto.hmac(secret, "S".toByteArray(), transcript))) throw PairingRejected("wrong pairing code")
            ch.send(sync.encode(buildJsonObject { put("mac", e64(PeerCrypto.hmac(secret, "C".toByteArray(), transcript))) }))
            if (sync.decode(ch.receive()).type() != "ok") throw PairingRejected("the other device refused")
            sync.trust(serverId, reply.str("deviceName"), PeerCrypto.hkdf(PeerCrypto.agree(mine, theirPub), transcript, "blossom-peer-key"))
        }
    }

    /** One sync session with paired [peer] at [host]:[port]; returns how many new operations arrived. */
    suspend fun sync(host: String, port: Int, peer: String): Int = withContext(Dispatchers.IO) {
        val key = sync.keyOf(peer) ?: throw PairingRejected("$peer isn't paired")
        FrameChannel(connect(host, port)).use { ch ->
            val nonce = PeerCrypto.randomBytes(32)
            ch.send(sync.encode(buildJsonObject { put("type", "hello"); put("deviceId", sync.deviceId); put("nonce", e64(nonce)) }))
            val reply = sync.decode(ch.receive())
            if (reply.type() != "hello" || reply.str("deviceId") != peer) throw PairingRejected("$peer refused")
            val theirNonce = b64(reply.str("nonce"))
            // Only the real peer has the key: anyone else at this address fails here.
            if (!PeerCrypto.equal(b64(reply.str("mac")), PeerCrypto.hmac(key, "S".toByteArray(), nonce, theirNonce, sync.deviceId.toByteArray(), peer.toByteArray()))) {
                throw PairingRejected("this isn't $peer")
            }
            ch.send(sync.encode(buildJsonObject { put("mac", e64(PeerCrypto.hmac(key, "C".toByteArray(), theirNonce, nonce, peer.toByteArray(), sync.deviceId.toByteArray()))) }))
            ch.encrypt(PeerCrypto.hkdf(key, nonce + theirNonce, "blossom-session"), isClient = true)
            ch.send(sync.encode(buildJsonObject { put("known", sync.known()) }))
            val answer = sync.decode(ch.receive())
            val pulled = sync.receive(answer.str("ops"))
            ch.send(sync.encode(buildJsonObject { put("ops", sync.newerThan(answer["known"] as? JsonObject ?: JsonObject(emptyMap()))) }))
            if (sync.decode(ch.receive()).type() != "done") throw IOException("$peer didn't confirm")
            sync.seen(peer)
            pulled
        }
    }

    private fun JsonObject.type() = (this["type"] as? JsonPrimitive)?.content
}
