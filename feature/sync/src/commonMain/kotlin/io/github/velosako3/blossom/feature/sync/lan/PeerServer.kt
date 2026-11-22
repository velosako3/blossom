// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.lan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64

/**
 * Accepts LAN sessions from trusted peers and, while [openPairing] is active, one pairing (5 tries). Listens on all
 * interfaces ([bind] null) or one address (tests use loopback).
 */
class PeerServer(private val sync: PeerSync, private val scope: CoroutineScope, port: Int = 0, private val bind: InetAddress? = null) {
    private val socket = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(bind, port)) }
    val port: Int get() = socket.localPort
    private var job: Job? = null
    @Volatile private var pairingSecret: ByteArray? = null
    @Volatile private var triesLeft = 0

    fun start() {
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val client = runCatching { runInterruptible { socket.accept() } }.getOrNull() ?: break
                launch { runCatching { handle(client) }; runCatching { client.close() } }
            }
        }
    }

    fun stop() { job?.cancel(); runCatching { socket.close() } }

    /** Accepts one pairing with [secret] (base64url), for 5 tries. */
    fun openPairing(secret: String) = openPairing(Base64.getUrlDecoder().decode(secret))

    fun openPairing(secret: ByteArray) { pairingSecret = secret; triesLeft = 5 }

    fun closePairing() { pairingSecret = null }

    private suspend fun handle(s: Socket) {
        s.soTimeout = 30_000
        FrameChannel(s).use { ch ->
            val hello = sync.decode(ch.receive())
            when ((hello["type"] as? JsonPrimitive)?.content) {
                "pair" -> pair(ch, hello)
                "hello" -> session(ch, hello)
            }
        }
    }

    private suspend fun pair(ch: FrameChannel, msg: JsonObject) {
        val secret = pairingSecret ?: return ch.send(sync.encode(buildJsonObject { put("type", "reject") }))
        val peerId = msg.str("deviceId"); val peerName = msg.str("deviceName")
        val theirPub = b64(msg.str("pub"))
        val mine = PeerCrypto.ephemeral()
        val transcript = PeerCrypto.sha256(peerId.toByteArray(), theirPub, sync.deviceId.toByteArray(), mine.public.encoded)
        ch.send(sync.encode(buildJsonObject {
            put("type", "pair"); put("deviceId", sync.deviceId); put("deviceName", sync.deviceName)
            put("pub", e64(mine.public.encoded)); put("mac", e64(PeerCrypto.hmac(secret, "S".toByteArray(), transcript)))
        }))
        val proof = sync.decode(ch.receive())
        if (!PeerCrypto.equal(b64(proof.str("mac")), PeerCrypto.hmac(secret, "C".toByteArray(), transcript))) {
            if (--triesLeft <= 0) pairingSecret = null
            ch.send(sync.encode(buildJsonObject { put("type", "reject") }))
            return
        }
        pairingSecret = null // single use
        sync.trust(peerId, peerName, PeerCrypto.hkdf(PeerCrypto.agree(mine, theirPub), transcript, "blossom-peer-key"))
        ch.send(sync.encode(buildJsonObject { put("type", "ok") }))
    }

    private suspend fun session(ch: FrameChannel, msg: JsonObject) {
        val peer = msg.str("deviceId")
        val key = sync.keyOf(peer) ?: return ch.send(sync.encode(buildJsonObject { put("type", "reject") }))
        val theirNonce = b64(msg.str("nonce"))
        val nonce = PeerCrypto.randomBytes(32)
        ch.send(sync.encode(buildJsonObject {
            put("type", "hello"); put("deviceId", sync.deviceId); put("nonce", e64(nonce))
            put("mac", e64(PeerCrypto.hmac(key, "S".toByteArray(), theirNonce, nonce, peer.toByteArray(), sync.deviceId.toByteArray())))
        }))
        val proof = sync.decode(ch.receive())
        if (!PeerCrypto.equal(b64(proof.str("mac")), PeerCrypto.hmac(key, "C".toByteArray(), nonce, theirNonce, sync.deviceId.toByteArray(), peer.toByteArray()))) return
        ch.encrypt(PeerCrypto.hkdf(key, theirNonce + nonce, "blossom-session"), isClient = false)
        // Exchange: their wants → our newer ops and our wants → their newer ops.
        val want = sync.decode(ch.receive())
        ch.send(sync.encode(buildJsonObject { put("ops", sync.newerThan(want["known"] as? JsonObject ?: JsonObject(emptyMap()))); put("known", sync.known()) }))
        val back = sync.decode(ch.receive())
        sync.receive(back.str("ops"))
        sync.seen(peer)
        ch.send(sync.encode(buildJsonObject { put("type", "done") })) // applied: the client may report success
    }
}

internal fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.content ?: throw PairingRejected("missing $key")
internal fun e64(b: ByteArray): String = Base64.getEncoder().encodeToString(b)
internal fun b64(s: String): ByteArray = Base64.getDecoder().decode(s)
