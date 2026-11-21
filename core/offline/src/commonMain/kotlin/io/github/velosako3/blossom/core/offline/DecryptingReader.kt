// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.offline

import io.github.velosako3.blossom.core.offline.ChunkedCipher.CHUNK
import io.github.velosako3.blossom.core.offline.ChunkedCipher.HEADER
import io.github.velosako3.blossom.core.offline.ChunkedCipher.TAG
import java.io.File
import java.io.RandomAccessFile
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Random-access reads of a [ChunkedCipher] file. Keeps the last decrypted chunk. Not thread-safe. */
class DecryptingReader(file: File, key: ByteArray) {
    private val raf = RandomAccessFile(file, "r")
    private val prefix: ByteArray
    private val chunks: Long
    private val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    private val key = SecretKeySpec(key, "AES")
    val plainLength: Long

    private var cachedIndex = -1L
    private var cached = ByteArray(0)
    private val sealed = ByteArray(CHUNK + TAG)

    init {
        val header = ByteArray(HEADER)
        try {
            raf.readFully(header)
            prefix = ChunkedCipher.readHeader(header)
        } catch (e: Exception) {
            raf.close()
            throw e as? CacheCorruptedException ?: CacheCorruptedException("unreadable header", e)
        }
        val body = raf.length() - HEADER
        chunks = (body + CHUNK + TAG - 1) / (CHUNK + TAG)
        plainLength = body - chunks * TAG
        if (chunks == 0L || plainLength < 0) { raf.close(); throw CacheCorruptedException("no chunks") }
    }

    /** Copies up to [length] bytes from [position]; returns the count, or -1 at the end. */
    fun read(position: Long, dest: ByteArray, offset: Int, length: Int): Int {
        if (position >= plainLength) return -1
        if (length == 0) return 0
        val index = position / CHUNK
        val chunk = chunk(index)
        val within = (position - index * CHUNK).toInt()
        val n = minOf(length, chunk.size - within)
        chunk.copyInto(dest, offset, within, within + n)
        return n
    }

    private fun chunk(index: Long): ByteArray {
        if (index == cachedIndex) return cached
        val start = HEADER + index * (CHUNK + TAG)
        val size = minOf((CHUNK + TAG).toLong(), raf.length() - start).toInt()
        raf.seek(start)
        raf.readFully(sealed, 0, size)
        cached = try {
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG * 8, ChunkedCipher.nonce(prefix, index)))
            cipher.updateAAD(ChunkedCipher.aad(index, index == chunks - 1))
            cipher.doFinal(sealed, 0, size)
        } catch (e: GeneralSecurityException) {
            cachedIndex = -1
            throw CacheCorruptedException("chunk $index failed authentication", e)
        }
        cachedIndex = index
        return cached
    }

    fun close() = raf.close()
}
