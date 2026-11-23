// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.lan

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Socket
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** The pairing or session was refused: wrong secret, unknown device, or someone else answering at that address. */
class PairingRejected(message: String) : IOException(message)

/** Primitives every JVM and Android has: ECDH on P-256, HMAC-SHA256, HKDF, AES-256-GCM. */
internal object PeerCrypto {
    private val random = SecureRandom()

    fun randomBytes(n: Int) = ByteArray(n).also(random::nextBytes)

    fun ephemeral(): KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1"), random) }.generateKeyPair()

    fun agree(mine: KeyPair, theirEncoded: ByteArray): ByteArray {
        val theirs = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(theirEncoded))
        return KeyAgreement.getInstance("ECDH").apply { init(mine.private); doPhase(theirs, true) }.generateSecret()
    }

    fun hmac(key: ByteArray, vararg parts: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").apply {
        init(SecretKeySpec(key, "HmacSHA256"))
        parts.forEach { update(ByteBuffer.allocate(4).putInt(it.size).array()); update(it) } // length-prefixed: no ambiguity
    }.doFinal()

    /** HKDF-SHA256 (extract and one expand block): 32 bytes. */
    fun hkdf(secret: ByteArray, salt: ByteArray, info: String): ByteArray {
        val prk = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(if (salt.isEmpty()) ByteArray(32) else salt, "HmacSHA256")) }.doFinal(secret)
        return Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(prk, "HmacSHA256")) }.doFinal(info.toByteArray() + byteArrayOf(1))
    }

    fun sha256(vararg parts: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").apply { parts.forEach { update(ByteBuffer.allocate(4).putInt(it.size).array()); update(it) } }.digest()

    fun equal(a: ByteArray, b: ByteArray) = MessageDigest.isEqual(a, b)
}

/**
 * Length-prefixed frames over a socket. After [encrypt], every frame is AES-GCM with a per-direction counter nonce,
 * so frames can't be read, changed, reordered or replayed.
 */
internal class FrameChannel(socket: Socket) : AutoCloseable {
    private val input = DataInputStream(socket.getInputStream().buffered())
    private val output = DataOutputStream(socket.getOutputStream().buffered())
    private val socket = socket
    private var key: SecretKeySpec? = null
    /** Before authentication only small frames are read, so a stranger can't make this side allocate much. */
    private var maxFrame = PRE_AUTH_FRAME
    private var sendDir: Byte = 0
    private var sent = 0L
    private var received = 0L

    fun encrypt(sessionKey: ByteArray, isClient: Boolean) {
        key = SecretKeySpec(sessionKey, "AES"); sendDir = if (isClient) 1 else 2
        maxFrame = MAX_FRAME
    }

    private fun nonce(dir: Byte, counter: Long) = ByteBuffer.allocate(12).put(dir).put(0).put(0).put(0).putLong(counter).array()

    fun send(bytes: ByteArray) {
        val payload = key?.let { k ->
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(128, nonce(sendDir, sent++))) }.doFinal(bytes)
        } ?: bytes
        output.writeInt(payload.size); output.write(payload); output.flush()
    }

    fun receive(): ByteArray {
        val size = input.readInt()
        if (size < 0 || size > maxFrame) throw IOException("frame too large")
        val payload = ByteArray(size).also(input::readFully)
        val k = key ?: return payload
        val recvDir: Byte = if (sendDir == 1.toByte()) 2 else 1
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, nonce(recvDir, received++))) }.doFinal(payload)
        } catch (e: Exception) {
            throw PairingRejected("a frame failed authentication")
        }
    }

    override fun close() = socket.close()

    private companion object {
        const val PRE_AUTH_FRAME = 16 * 1024
        const val MAX_FRAME = 64 * 1024 * 1024
    }
}
