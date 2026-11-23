// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.library.local

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class FolderWatcherTest {
    @Test fun newAndDeletedFilesShowUpWithinFiveSeconds(): Unit = runBlocking {
        val dir = Files.createTempDirectory("watch").toFile()
        copyFixture("tagged.mp3", dir)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val library = FolderLocalLibrary(MutableStateFlow(listOf(dir)), Files.createTempDirectory("art").toFile(), scope)
        try {
            withTimeout(5_000) { library.tracks().first { it.size == 1 } }
            val added = copyFixture("tagged.flac", dir)
            withTimeout(5_000) { library.tracks().first { t -> t.any { it.title == "Lossless Silence" } } }
            assertTrue(library.pathFor(library.tracks().first().single { it.title == "Lossless Silence" }.id) == added.absolutePath)
            added.delete()
            withTimeout(5_000) { library.tracks().first { t -> t.none { it.title == "Lossless Silence" } } }
        } finally {
            scope.cancel()
        }
    }
}
