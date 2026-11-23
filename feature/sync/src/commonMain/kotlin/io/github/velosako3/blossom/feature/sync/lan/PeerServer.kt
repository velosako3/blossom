// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.lan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Accepts LAN sessions from trusted peers and, while [openPairing] is active, one pairing (5 tries). Listens on all
 * interfaces ([bind] null) or one address (tests use loopback).
 *
 * Strangers get little: at most [MAX_HANDLERS] connections at once, small frames until they authenticate, and
 * [preAuthTimeoutMs] to do it. During pairing the client proves it knows the secret first, so nothing this side sends
 * can be used to guess the secret offline.
 */
class PeerServer(
    private val sync: PeerSync,
    private val scope: CoroutineScope,
    port: Int = 0,
    private val bind: InetAddress? = null,
    private val preAuthTimeoutMs: Long = 20_000,
) {
    private val socket = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(bind, port)) }
    val port: Int get() = socket.localPort
    private var job: Job? = null
    private val open = ConcurrentHashMap.newKeySet<Socket>()
    private val handlers = Semaphore(MAX_HANDLERS)
    private val lock = Any()

    /** One pairing window: its secrets (QR and typed code), tries left, and whether it was used or closed. */
    private class Window(val secrets: List<ByteArray>) { var tries = 5; var done = false }
    private var window: Window? = null

    fun start() {
        sync.listenPort = port
        job = scope.launch(Dispatchers.IO) {
            while (isActive && !socket.isClosed) {
                val client = try { runInterruptible { socket.accept() } } catch (e: Exception) { if (socket.isClosed) break else continue }
                if (!handlers.tryAcquire()) { runCatching { client.close() }; continue }
                open += client
                launch {
                    try { runCatching { handle(client) } } finally { open -= client; runCatching { client.close() }; handlers.release() }
                }
            }
        }
    }

    fun stop() {
        job?.cancel(); runCatching { socket.close() }
        open.forEach { runCatching { it.close() } }
    }

    /** Accepts one pairing with any of [secrets] (base64url), for 5 tries in total. */
    fun openPairing(vararg secrets: String) = openPairingBytes(secrets.map { Base64.getUrlDecoder().decode(it) })

    fun openPairingBytes(secrets: List<ByteArray>) = synchronized(lock) { window?.done = true; window = Window(secrets) }

    fun openPairing(secret: ByteArray) = openPairingBytes(listOf(secret))

    fun closePairing() = synchronized(lock) { window?.done = true; window = null }

    private suspend fun handle(s: Socket) {
        s.soTimeout = 30_000
        // Close the socket if the peer hasn't authenticated in time (a read timeout alone can be dripped forever).
        val deadline = scope.launch { delay(preAuthTimeoutMs); runCatching { s.close() } }
        FrameChannel(s).use { ch ->
            val hello = sync.decode(ch.receive())
            val ok = when ((hello["type"] as? JsonPrimitive)?.content) {
                "pair" -> pair(ch, hello, deadline)
                "hello" -> session(ch, hello, deadline)
                else -> false
            }
            // Remember where an authenticated peer listens, so this side can start sessions too.
            val back = (hello["port"] as? JsonPrimitive)?.content?.toIntOrNull()
            if (ok && back != null && back in 1..65535) sync.addresses[hello.str("deviceId")] = s.inetAddress.hostAddress to back
        }
        deadline.cancel()
    }

    private suspend fun pair(ch: FrameChannel, msg: JsonObject, deadline: Job): Boolean {
        // Every attempt counts, finished or not.
        val w = synchronized(lock) { window?.takeIf { !it.done && it.tries > 0 }?.also { it.tries-- } }
            ?: return false.also { ch.send(sync.encode(buildJsonObject { put("type", "reject") })) }
        val peerId = msg.str("deviceId"); val peerName = msg.str("deviceName")
        val theirPub = b64(msg.str("pub"))
        val mine = PeerCrypto.ephemeral()
        val transcript = PeerCrypto.sha256(peerId.toByteArray(), theirPub, sync.deviceId.toByteArray(), mine.public.encoded)
        ch.send(sync.encode(buildJsonObject {
            put("type", "pair"); put("deviceId", sync.deviceId); put("deviceName", sync.deviceName); put("pub", e64(mine.public.encoded))
        }))
        val proof = b64(sync.decode(ch.receive()).str("mac"))
        val secret = w.secrets.firstOrNull { PeerCrypto.equal(proof, PeerCrypto.hmac(it, "C".toByteArray(), transcript)) }
        // Still open and unused? Consume it atomically: one pairing per window.
        val won = secret != null && synchronized(lock) { (!w.done).also { w.done = true; if (window === w) window = null } }
        if (!won) {
            ch.send(sync.encode(buildJsonObject { put("type", "reject") }))
            return false
        }
        deadline.cancel()
        sync.trust(peerId, peerName, PeerCrypto.hkdf(PeerCrypto.agree(mine, theirPub), transcript, "blossom-peer-key"))
        ch.send(sync.encode(buildJsonObject { put("type", "ok"); put("mac", e64(PeerCrypto.hmac(secret!!, "S".toByteArray(), transcript))) }))
        return true
    }

    private suspend fun session(ch: FrameChannel, msg: JsonObject, deadline: Job): Boolean {
        val peer = msg.str("deviceId")
        val key = sync.keyOf(peer) ?: return false.also { ch.send(sync.encode(buildJsonObject { put("type", "reject") })) }
        val theirNonce = b64(msg.str("nonce"))
        val nonce = PeerCrypto.randomBytes(32)
        ch.send(sync.encode(buildJsonObject {
            put("type", "hello"); put("deviceId", sync.deviceId); put("nonce", e64(nonce))
            put("mac", e64(PeerCrypto.hmac(key, "S".toByteArray(), theirNonce, nonce, peer.toByteArray(), sync.deviceId.toByteArray())))
        }))
        val proof = sync.decode(ch.receive())
        if (!PeerCrypto.equal(b64(proof.str("mac")), PeerCrypto.hmac(key, "C".toByteArray(), nonce, theirNonce, sync.deviceId.toByteArray(), peer.toByteArray()))) return false
        deadline.cancel()
        ch.encrypt(PeerCrypto.hkdf(key, theirNonce + nonce, "blossom-session"), isClient = false)
        // Exchange: their wants → our newer ops and our wants → their newer ops.
        val want = sync.decode(ch.receive())
        ch.send(sync.encode(buildJsonObject { put("ops", sync.newerThan(want["known"] as? JsonObject ?: JsonObject(emptyMap()))); put("known", sync.known()) }))
        val back = sync.decode(ch.receive())
        sync.receive(back.str("ops"))
        sync.seen(peer)
        ch.send(sync.encode(buildJsonObject { put("type", "done") })) // applied: the client may report success
        return true
    }

    private companion object { const val MAX_HANDLERS = 8 }
}

internal fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.content ?: throw PairingRejected("missing $key")
internal fun e64(b: ByteArray): String = Base64.getEncoder().encodeToString(b)
internal fun b64(s: String): ByteArray = Base64.getDecoder().decode(s)
