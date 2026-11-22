// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.importer

import io.github.velosako3.blossom.core.database.LibraryCommands
import io.github.velosako3.blossom.core.database.LibraryProjector
import io.github.velosako3.blossom.core.database.SqlLibraryRepository
import io.github.velosako3.blossom.core.database.SqlOpLog
import io.github.velosako3.blossom.core.database.fileDatabase
import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.PlaylistOrigin
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.core.model.sync.HybridLogicalClock
import io.github.velosako3.blossom.source.api.ImportedPlaylist
import io.github.velosako3.blossom.source.api.ImportedTrack
import io.github.velosako3.blossom.source.api.PlaylistImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

private fun native(i: Int) = ImportedTrack(
    "Song $i", listOf("Artist"), 180_000, null,
    Track(TrackId.of(SourceKind.YOUTUBE_MUSIC, "v$i"), "Song $i", listOf(ArtistRef(null, "Artist")), null, 180_000, null),
    sourceKey = "k$i",
)

private class FakeImporter(private val n: Int) : PlaylistImporter {
    override fun canHandle(url: String) = true
    override suspend fun read(url: String, onProgress: (Int, Int?) -> Unit) =
        ImportedPlaylist("Imported", null, null, PlaylistOrigin(SourceKind.YOUTUBE_MUSIC, "YouTube Music", url), (0 until n).map(::native))
}

class ImportJobRunnerTest {
    private val root = kotlin.io.path.createTempDirectory("blossom-import").toFile()
    private val db = fileDatabase(File(root, "blossom.db"))
    private val commands = LibraryCommands(SqlOpLog(db, "A", HybridLogicalClock("A", Clock.System)), LibraryProjector(db), db)
    private val repo = SqlLibraryRepository(db)
    private val link = DetectedLink(ImportService.YOUTUBE_MUSIC, LinkKind.PLAYLIST, "https://music.youtube.com/playlist?list=PLx")

    @AfterTest fun cleanup() { root.deleteRecursively() }

    private fun runner(scope: CoroutineScope, n: Int, batch: Int = 50, onBatch: (Int) -> Unit = {}) =
        ImportJobRunner(db, commands, repo, mapOf(ImportService.YOUTUBE_MUSIC to FakeImporter(n)), search = { emptyList() }, scope = scope, batchSize = batch, onBatchWritten = onBatch)

    @Test fun resumesAfterInterruptionWithoutDuplicates(): Unit = runBlocking {
        val first = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val r1 = runner(first, 100, batch = 10) { written -> if (written >= 40) first.cancel() }
        val id = r1.start(link, ImportTarget.NewPlaylist(null))
        withTimeout(10_000) { while (first.coroutineContext[kotlinx.coroutines.Job]!!.isActive) kotlinx.coroutines.delay(10) }
        val pid = repo.playlists().first().single().id
        assertEquals(40, repo.playlist(pid).first()!!.entries.size)

        val second = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val r2 = runner(second, 100, batch = 10)
        r2.resumePending()
        val done = withTimeout(20_000) { r2.progress(id).first { it.state == ImportJobState.DONE } }
        assertEquals(100, done.done)
        val entries = repo.playlist(pid).first()!!.entries
        assertEquals((0 until 100).map { "v$it" }, entries.map { it.track!!.id.sourceId })
        assertEquals(1, repo.playlists().first().size)
        second.cancel()
    }

    @Test fun fiveThousandNativeTracksUnderTenSeconds(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val started = System.nanoTime()
        val r = runner(scope, 5_000) { w -> if (w % 1000 == 0) println("written $w at ${"%.2f".format((System.nanoTime() - started) / 1e9)} s") }
        val id = r.start(link, ImportTarget.NewPlaylist("Big"))
        withTimeout(30_000) { r.progress(id).first { it.state == ImportJobState.DONE } }
        val seconds = (System.nanoTime() - started) / 1e9
        println("5000 native tracks imported in ${"%.2f".format(seconds)} s")
        assertTrue(seconds < 10, "took $seconds s")
        assertEquals(5_000, repo.playlist(repo.playlists().first().single().id).first()!!.entries.size)
        scope.cancel()
    }

    @Test fun refreshAddsOnlyNewTracks(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val id1 = runner(scope, 5).start(link, ImportTarget.NewPlaylist(null))
        val r = runner(scope, 8)
        withTimeout(10_000) { r.progress(id1).first { it.state == ImportJobState.DONE } }
        val pid = repo.playlists().first().single().id
        val id2 = r.start(link, ImportTarget.UpdateExisting(pid))
        withTimeout(10_000) { r.progress(id2).first { it.state == ImportJobState.DONE } }
        assertEquals((0 until 8).map { "v$it" }, repo.playlist(pid).first()!!.entries.map { it.track!!.id.sourceId })
        scope.cancel()
    }
}
