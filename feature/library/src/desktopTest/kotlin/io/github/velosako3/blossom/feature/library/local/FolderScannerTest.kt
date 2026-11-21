// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.library.local

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

fun copyFixture(name: String, dir: File, as_: String = name): File {
    val target = File(dir, as_)
    object {}.javaClass.getResourceAsStream("/fixtures/audio/$name")!!.use { input -> target.outputStream().use { input.copyTo(it) } }
    return target
}

class FolderScannerTest {
    @Test fun scansAudioAndIgnoresOtherFiles() {
        val dir = Files.createTempDirectory("scan").toFile()
        copyFixture("tagged.mp3", dir)
        File(dir, "sub").mkdirs()
        copyFixture("tagged.flac", File(dir, "sub"))
        copyFixture("untagged_take_3.opus", dir)
        copyFixture("notes.txt", dir)
        val tracks = FolderScanner(Files.createTempDirectory("art").toFile()).scan(listOf(dir)).map { it.track }.sortedBy { it.title }
        assertEquals(3, tracks.size)
        val mp3 = tracks.single { it.title == "Silent Song" }
        assertEquals("Quiet Artist", mp3.artistNames)
        assertEquals("Hush", mp3.album?.title)
        assertEquals(localTrackId("Silent Song", "Quiet Artist", "Hush"), mp3.id)
        assertTrue(mp3.durationMs in 1000..2500, "duration ${mp3.durationMs}")
        assertEquals("Flac Band", tracks.single { it.title == "Lossless Silence" }.artistNames)
        val untagged = tracks.single { it.title == "untagged_take_3" }
        assertEquals("Unknown artist", untagged.artistNames)
    }
}
