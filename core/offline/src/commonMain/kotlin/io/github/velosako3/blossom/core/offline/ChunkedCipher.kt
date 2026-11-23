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
 * Header (17 bytes): `BLSM`, version 2, chunk size (int), a random 8-byte nonce prefix. Then each chunk's ciphertext
 * and 16-byte tag. Chunk nonce = prefix + 4-byte chunk index. (8 random bytes per file, not 4: with 4, two files
 * under the same key share a nonce with ~0.3% odds at 5,000 files, which breaks GCM.) The chunk's associated data is its index and whether it
 * is the last one, so reordering, swapping or truncating chunks fails authentication.
 */
object ChunkedCipher {
    const val CHUNK = 65536
    const val TAG = 16
    const val HEADER = 17
    private val MAGIC = "BLSM".encodeToByteArray()
    private const val VERSION: Byte = 2

    fun encrypt(input: InputStream, output: OutputStream, key: ByteArray) {
        val enc = ChunkedEncryptor.create(output, key)
        val buf = ByteArray(CHUNK)
        while (true) { val n = input.read(buf); if (n < 0) break; enc.write(buf, 0, n) }
        enc.finish()
    }

    internal fun header(prefix: ByteArray): ByteArray = ByteBuffer.allocate(HEADER).put(MAGIC).put(VERSION).putInt(CHUNK).put(prefix).array()

    internal fun readHeader(bytes: ByteArray): ByteArray {
        if (bytes.size < HEADER || !bytes.copyOfRange(0, 4).contentEquals(MAGIC) || bytes[4] != VERSION ||
            ByteBuffer.wrap(bytes, 5, 4).int != CHUNK
        ) throw CacheCorruptedException("not a blossom offline file")
        return bytes.copyOfRange(9, HEADER)
    }

    internal fun nonce(prefix: ByteArray, index: Long): ByteArray {
        require(index in 0..Int.MAX_VALUE.toLong()) { "chunk index out of range" } // 2^31 × 64 KiB: far beyond any song
        return ByteBuffer.allocate(12).put(prefix).putInt(index.toInt()).array()
    }
    internal fun aad(index: Long, last: Boolean): ByteArray = ByteBuffer.allocate(9).putLong(index).put(if (last) 1 else 0).array()
}

/**
 * Encrypts a stream as it arrives. One full chunk is held back until more data shows it isn't the last, so every
 * chunk already on disk is final and a broken download can resume after [chunksWritten] chunks.
 */
class ChunkedEncryptor private constructor(
    private val out: OutputStream,
    key: ByteArray,
    private val prefix: ByteArray,
    startIndex: Long,
) {
    private val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    private val key = SecretKeySpec(key, "AES")
    private val pending = ByteArray(ChunkedCipher.CHUNK)
    private var filled = 0
    var chunksWritten = startIndex
        private set

    fun write(src: ByteArray, offset: Int, length: Int) {
        var off = offset
        var left = length
        while (left > 0) {
            if (filled == ChunkedCipher.CHUNK) flush(last = false)
            val n = minOf(left, ChunkedCipher.CHUNK - filled)
            src.copyInto(pending, filled, off, off + n)
            filled += n; off += n; left -= n
        }
    }

    fun finish() { flush(last = true); out.flush() }

    private fun flush(last: Boolean) {
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(ChunkedCipher.TAG * 8, ChunkedCipher.nonce(prefix, chunksWritten)))
        cipher.updateAAD(ChunkedCipher.aad(chunksWritten, last))
        out.write(cipher.doFinal(pending, 0, filled))
        chunksWritten++; filled = 0
    }

    companion object {
        /** A new file: writes the header first. */
        fun create(out: OutputStream, key: ByteArray): ChunkedEncryptor {
            val prefix = ByteArray(8).also { SecureRandom().nextBytes(it) }
            out.write(ChunkedCipher.header(prefix))
            return ChunkedEncryptor(out, key, prefix, 0)
        }

        /** Appends to a file whose header and first [chunks] chunks are already written. */
        fun resume(out: OutputStream, key: ByteArray, prefix: ByteArray, chunks: Long) = ChunkedEncryptor(out, key, prefix, chunks)
    }
}
