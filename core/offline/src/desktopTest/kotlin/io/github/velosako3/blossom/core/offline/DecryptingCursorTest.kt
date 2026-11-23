// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.offline

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The sequential, seekable view players read (vlcj callback media on desktop, a Media3 DataSource on Android). */
class DecryptingCursorTest {
    private val dir = kotlin.io.path.createTempDirectory("blossom-cursor").toFile()
    private val key = Random(3).nextBytes(32)
    private val plain = Random(4).nextBytes(200_000)

    @AfterTest fun cleanup() { dir.deleteRecursively() }

    private fun cursor(): DecryptingCursor {
        val f = File(dir, "c.blsm")
        plain.inputStream().use { i -> f.outputStream().use { o -> ChunkedCipher.encrypt(i, o, key) } }
        return DecryptingCursor(DecryptingReader(f, key))
    }

    @Test fun readsSequentiallyAndSeeks() {
        val c = cursor()
        assertEquals(plain.size.toLong(), c.size)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(7_000)
        while (true) { val n = c.read(buf, buf.size); if (n <= 0) break; out.write(buf, 0, n) }
        assertContentEquals(plain, out.toByteArray())
        assertTrue(c.seek(150_000))
        val exact = ByteArray(100)
        var p = 0
        while (p < 100) { val tmp = ByteArray(100 - p); val n = c.read(tmp, tmp.size); tmp.copyInto(exact, p, 0, n); p += n }
        assertContentEquals(plain.copyOfRange(150_000, 150_100), exact)
        assertEquals(false, c.seek(plain.size + 1L))
        c.close()
    }

    @Test fun storeKnowsSavedIds() = runBlocking {
        val h = OfflineHarness(mapOf("a" to 100_000))
        try {
            val store = SqlOfflineStore(h.db, h.manager, null, h.scope)
            val t = track("a")
            assertEquals(false, store.isSaved(t.id))
            store.saveTrack(t, OfflineRef.Single(t.id))
            withTimeout(30_000) { store.state(t.id).first { it == OfflineState.SAVED } }
            withTimeout(5_000) { while (!store.isSaved(t.id)) kotlinx.coroutines.delay(20) }
        } finally { h.close() }
    }
}
