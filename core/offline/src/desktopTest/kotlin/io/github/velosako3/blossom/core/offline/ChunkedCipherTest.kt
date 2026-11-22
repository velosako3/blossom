// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.offline

import io.github.velosako3.blossom.core.platform.InMemorySecureStore
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.RandomAccessFile
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ChunkedCipherTest {
    private val dir = kotlin.io.path.createTempDirectory("blossom-cipher").toFile()
    private val key = Random(1).nextBytes(32)
    private val plain = Random(2).nextBytes(1024 * 1024 + 3)

    @AfterTest fun cleanup() { dir.deleteRecursively() }

    private fun encrypted(data: ByteArray = plain, k: ByteArray = key): File =
        File(dir, "x.blsm").also { f -> data.inputStream().use { i -> f.outputStream().use { o -> ChunkedCipher.encrypt(i, o, k) } } }

    private fun readAll(r: DecryptingReader): ByteArray {
        val out = ByteArray(r.plainLength.toInt())
        var pos = 0
        while (pos < out.size) { val n = r.read(pos.toLong(), out, pos, minOf(50_000, out.size - pos)); check(n > 0); pos += n }
        return out
    }

    @Test fun roundTrip() {
        val r = DecryptingReader(encrypted(), key)
        assertEquals(plain.size.toLong(), r.plainLength)
        assertContentEquals(plain, readAll(r))
        assertEquals(-1, r.read(r.plainLength, ByteArray(4), 0, 4))
        r.close()
    }

    @Test fun randomAccessAndChunkBoundary() {
        val r = DecryptingReader(encrypted(), key)
        val buf = ByteArray(100)
        assertEquals(100, r.read(70_000, buf, 0, 100))
        assertContentEquals(plain.copyOfRange(70_000, 70_100), buf)
        val cross = ByteArray(200)
        var got = 0
        while (got < 200) got += r.read(ChunkedCipher.CHUNK - 100L + got, cross, got, 200 - got)
        assertContentEquals(plain.copyOfRange(ChunkedCipher.CHUNK - 100, ChunkedCipher.CHUNK + 100), cross)
        r.close()
    }

    @Test fun tamperedChunkFailsOnlyThere() {
        val f = encrypted()
        RandomAccessFile(f, "rw").use { raf ->
            val at = ChunkedCipher.HEADER + 2L * (ChunkedCipher.CHUNK + ChunkedCipher.TAG) + 10
            raf.seek(at); val b = raf.read(); raf.seek(at); raf.write(b xor 0x01)
        }
        val r = DecryptingReader(f, key)
        assertFailsWith<CacheCorruptedException> { r.read(2L * ChunkedCipher.CHUNK + 5, ByteArray(10), 0, 10) }
        val ok = ByteArray(10)
        r.read(5, ok, 0, 10)
        assertContentEquals(plain.copyOfRange(5, 15), ok)
        r.close()
    }

    @Test fun probeNotInCiphertext() {
        val probe = ByteArray(32) { (it * 7 + 3).toByte() }
        val data = plain.copyOf().also { probe.copyInto(it, 123_456) }
        val bytes = encrypted(data).readBytes()
        assertFalse((0..bytes.size - 32).any { i -> (0 until 32).all { bytes[i + it] == probe[it] } })
    }

    @Test fun wrongKey() {
        val r = DecryptingReader(encrypted(), Random(9).nextBytes(32))
        assertFailsWith<CacheCorruptedException> { r.read(0, ByteArray(10), 0, 10) }
        r.close()
    }

    @Test fun truncationDetected() {
        val f = encrypted()
        RandomAccessFile(f, "rw").use { it.setLength(ChunkedCipher.HEADER + 3L * (ChunkedCipher.CHUNK + ChunkedCipher.TAG)) }
        val r = DecryptingReader(f, key)
        assertFailsWith<CacheCorruptedException> { r.read(2L * ChunkedCipher.CHUNK, ByteArray(10), 0, 10) }
        r.close()
    }

    @Test fun eightByteRandomNoncePrefix() {
        val header = encrypted().readBytes().copyOf(ChunkedCipher.HEADER)
        assertEquals(17, ChunkedCipher.HEADER)
        assertEquals(2, header[4].toInt())
        assertFalse(header.copyOfRange(9, 17).all { it == 0.toByte() })
    }

    @Test fun shrunkFileIsCorruptNotACrash() {
        val f = encrypted()
        val r = DecryptingReader(f, key)
        RandomAccessFile(f, "rw").use { it.setLength(ChunkedCipher.HEADER + 10L) }
        assertFailsWith<CacheCorruptedException> { r.read(5L * ChunkedCipher.CHUNK, ByteArray(10), 0, 10) }
        r.close()
    }

    @Test fun keyVaultKeepsOneKey() = runTest {
        val store = InMemorySecureStore()
        val a = KeyVault(store).offlineKey()
        assertEquals(32, a.size)
        assertContentEquals(a, KeyVault(store).offlineKey())
    }
}
