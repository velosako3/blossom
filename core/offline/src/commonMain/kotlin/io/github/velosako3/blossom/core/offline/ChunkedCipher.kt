// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.offline

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class CacheCorruptedException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Offline files: AES-256-GCM in independent 64 KiB chunks, so any position can be read without decrypting the rest.
 *
 * Header (13 bytes): `BLSM`, version 1, chunk size (int), a random 4-byte nonce prefix. Then each chunk's ciphertext
 * and 16-byte tag. Chunk nonce = prefix + 8-byte chunk index. The chunk's associated data is its index and whether it
 * is the last one, so reordering, swapping or truncating chunks fails authentication.
 */
object ChunkedCipher {
    const val CHUNK = 65536
    const val TAG = 16
    const val HEADER = 13
    private val MAGIC = "BLSM".encodeToByteArray()
    private const val VERSION: Byte = 1

    fun encrypt(input: InputStream, output: OutputStream, key: ByteArray) {
        val prefix = ByteArray(4).also { SecureRandom().nextBytes(it) }
        output.write(ByteBuffer.allocate(HEADER).put(MAGIC).put(VERSION).putInt(CHUNK).put(prefix).array())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val k = SecretKeySpec(key, "AES")
        var current = readChunk(input)
        var index = 0L
        // One chunk of look-ahead tells us which chunk is last. An empty input still writes one (empty) last chunk.
        while (true) {
            val next = if (current.size == CHUNK) readChunk(input) else ByteArray(0)
            val last = next.isEmpty()
            cipher.init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(TAG * 8, nonce(prefix, index)))
            cipher.updateAAD(aad(index, last))
            output.write(cipher.doFinal(current))
            if (last) break
            current = next; index++
        }
        output.flush()
    }

    internal fun readHeader(bytes: ByteArray): ByteArray {
        if (bytes.size < HEADER || !bytes.copyOfRange(0, 4).contentEquals(MAGIC) || bytes[4] != VERSION ||
            ByteBuffer.wrap(bytes, 5, 4).int != CHUNK
        ) throw CacheCorruptedException("not a blossom offline file")
        return bytes.copyOfRange(9, 13)
    }

    internal fun nonce(prefix: ByteArray, index: Long): ByteArray = ByteBuffer.allocate(12).put(prefix).putLong(index).array()
    internal fun aad(index: Long, last: Boolean): ByteArray = ByteBuffer.allocate(9).putLong(index).put(if (last) 1 else 0).array()

    private fun readChunk(input: InputStream): ByteArray {
        val buf = ByteArray(CHUNK)
        var n = 0
        while (n < CHUNK) { val r = input.read(buf, n, CHUNK - n); if (r < 0) break; n += r }
        return if (n == CHUNK) buf else buf.copyOf(n)
    }
}
