// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.backends

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalFolderBackendTest {
    private val root = kotlin.io.path.createTempDirectory("blossom-folder").toFile()
    private val backend = LocalFolderBackend(root)

    @AfterTest fun cleanup() { root.deleteRecursively() }

    @Test fun roundTrip(): Unit = runBlocking {
        backend.write("blossom/v1/devices/A/manifest.json", "{}".toByteArray())
        backend.write("blossom/v1/devices/A/ops-0.jsonl", "line\n".toByteArray())
        backend.write("blossom/v1/devices/B/manifest.json", "{\"b\":1}".toByteArray())
        assertEquals(listOf("blossom/v1/devices/A/manifest.json", "blossom/v1/devices/A/ops-0.jsonl"), backend.list("blossom/v1/devices/A").map { it.path }.sorted())
        assertEquals(3, backend.list("blossom/v1").size)
        assertContentEquals("{\"b\":1}".toByteArray(), backend.read("blossom/v1/devices/B/manifest.json"))
        backend.write("blossom/v1/devices/A/manifest.json", "{\"v\":2}".toByteArray())
        assertEquals("{\"v\":2}", String(backend.read("blossom/v1/devices/A/manifest.json")))
    }

    @Test fun writesAreAtomic(): Unit = runBlocking {
        val path = "blossom/v1/devices/A/ops-0.jsonl"
        val old = ByteArray(3_000_000) { 'a'.code.toByte() }
        val new = ByteArray(3_000_000) { 'b'.code.toByte() }
        backend.write(path, old)
        val reader = async(Dispatchers.IO) {
            var seen = 0
            repeat(300) {
                val b = backend.read(path)
                assertTrue(b.contentEquals(old) || b.contentEquals(new), "a partial file was visible (${b.size} bytes)")
                assertTrue(backend.list("blossom").all { it.path.endsWith(".jsonl") }, "temp files are never listed")
                seen++
            }
            seen
        }
        repeat(20) { backend.write(path, if (it % 2 == 0) new else old) }
        assertEquals(300, reader.await())
        assertTrue(File(root, "blossom/v1/devices/A").listFiles()!!.all { it.name == "ops-0.jsonl" })
    }
}
