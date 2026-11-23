// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.importer

import io.github.velosako3.blossom.core.database.LibraryCommands
import io.github.velosako3.blossom.core.database.LibraryProjector
import io.github.velosako3.blossom.core.database.NewEntry
import io.github.velosako3.blossom.core.database.SqlLibraryRepository
import io.github.velosako3.blossom.core.database.SqlOpLog
import io.github.velosako3.blossom.core.database.fileDatabase
import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.EntryStatus
import io.github.velosako3.blossom.core.model.PlaylistOrigin
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.core.model.sync.HybridLogicalClock
import io.github.velosako3.blossom.source.api.AudioQuality
import io.github.velosako3.blossom.source.api.ImportedPlaylist
import io.github.velosako3.blossom.source.api.ImportedTrack
import io.github.velosako3.blossom.source.api.MusicSource
import io.github.velosako3.blossom.source.api.PlaylistImporter
import io.github.velosako3.blossom.source.api.SearchFilter
import io.github.velosako3.blossom.source.api.SearchResults
import io.github.velosako3.blossom.source.api.StreamInfo
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
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

private fun t(kind: SourceKind, id: String, title: String = "Song $id") = Track(TrackId.of(kind, id), title, listOf(ArtistRef(null, "Uploader")), null, 200_000, null)

private class FakeSource(override val kind: SourceKind, private val results: List<Track>) : MusicSource {
    val queries = mutableListOf<String>()
    override suspend fun search(query: String, filter: SearchFilter): SearchResults { queries += query; return SearchResults(results) }
    override suspend fun track(id: TrackId): Track? = null
    override suspend fun resolveStream(id: TrackId, quality: AudioQuality): StreamInfo = error("unused")
}

private class TestClock(var now: Instant = Instant.fromEpochSeconds(1_800_000_000)) : Clock {
    override fun now() = now
    fun advance(d: Duration) { now += d }
}

class ReplacementAndDeadLinkTest {
    private val root = kotlin.io.path.createTempDirectory("blossom-repl").toFile()
    private val db = fileDatabase(File(root, "blossom.db"))
    private val commands = LibraryCommands(SqlOpLog(db, "A", HybridLogicalClock("A", Clock.System)), LibraryProjector(db), db)
    private val repo = SqlLibraryRepository(db)

    @AfterTest fun cleanup() { root.deleteRecursively() }

    private suspend fun playlistWith(track: Track, status: EntryStatus = EntryStatus.MATCHED) = commands.createPlaylist("P").also { pid ->
        commands.addEntries(pid, listOf(NewEntry(t(SourceKind.YOUTUBE_MUSIC, "first"), EntryStatus.MATCHED, "First", "A", null)))
        commands.addEntries(pid, listOf(NewEntry(track, status, track.title, "Uploader", null)))
    }

    @Test fun replacementKeepsEntryAndPosition(): Unit = runBlocking {
        val dead = t(SourceKind.UNTITLED, "dead", "Kanye West – Song (Yedit)")
        val pid = playlistWith(dead, EntryStatus.UNAVAILABLE)
        val before = repo.playlist(pid).first()!!.entries[1]
        val yt = FakeSource(SourceKind.YOUTUBE, listOf(t(SourceKind.YOUTUBE, "upload1")))
        val sc = FakeSource(SourceKind.SOUNDCLOUD, listOf(t(SourceKind.SOUNDCLOUD, "123")))
        val find = FindReplacement(listOf(yt, sc), commands)
        val candidates = find.candidates(before)
        assertEquals(listOf("Uploader Kanye West – Song (Yedit)"), yt.queries)
        assertEquals(listOf("yt:upload1", "sc:123"), candidates.map { it.id.value })
        find.choose(pid, before, candidates.last())
        val after = repo.playlist(pid).first()!!.entries[1]
        assertEquals(before.entryId, after.entryId)
        assertEquals(before.position, after.position)
        assertEquals("sc:123", after.track!!.id.value)
        assertEquals(EntryStatus.MATCHED, after.status)
    }

    @Test fun deadLinkCheckedOncePerDay(): Unit = runBlocking {
        val track = t(SourceKind.UNTITLED, "gone", "Kept Title")
        val pid = playlistWith(track)
        val clock = TestClock()
        var checks = 0
        val rechecker = DeadLinkRechecker(clock, commands) { checks++; false }
        val entry = repo.playlist(pid).first()!!.entries[1]
        rechecker.recheckIfDue(pid, entry)
        rechecker.recheckIfDue(pid, entry)
        assertEquals(1, checks)
        val after = repo.playlist(pid).first()!!.entries[1]
        assertEquals(EntryStatus.UNAVAILABLE, after.status)
        assertEquals("Kept Title", after.track!!.title)
        clock.advance(25.hours)
        rechecker.recheckIfDue(pid, after)
        assertEquals(2, checks)
    }

    @Test fun untitledImportsQueueOfflineSavesWhenEnabled(): Unit = runBlocking {
        for (enabled in listOf(true, false)) {
            val saved = mutableListOf<Track>()
            val autoSave = UntitledAutoSave(enabled = { enabled }) { saved += it }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val importer = object : PlaylistImporter {
                override fun canHandle(url: String) = true
                override suspend fun read(url: String, onProgress: (Int, Int?) -> Unit) = ImportedPlaylist("Project", null, null,
                    PlaylistOrigin(SourceKind.UNTITLED, "untitled", url), (0 until 5).map { i -> t(SourceKind.UNTITLED, "s$i").let { ImportedTrack(it.title, listOf("Uploader"), it.durationMs, null, it) } })
            }
            val runner = ImportJobRunner(db, commands, repo, mapOf(ImportService.UNTITLED to importer), search = { emptyList() }, scope = scope, onWritten = autoSave::onWritten)
            val id = runner.start(DetectedLink(ImportService.UNTITLED, LinkKind.ALBUM, "https://untitled.stream/library/project/x$enabled"), ImportTarget.NewPlaylist(null))
            withTimeout(10_000) { runner.progress(id).first { it.state == ImportJobState.DONE } }
            assertEquals(if (enabled) 5 else 0, saved.size)
            assertEquals(if (enabled) (0 until 5).map { "untitled:s$it" } else emptyList(), saved.map { it.id.value })
            scope.cancel()
        }
    }

    @Test fun anUnansweredCheckDoesNotUseUpTheDay(): Unit = runBlocking {
        val pid = playlistWith(t(SourceKind.UNTITLED, "maybe"))
        var answer: Boolean? = null
        var checks = 0
        val rechecker = DeadLinkRechecker(TestClock(), commands) { checks++; answer }
        val entry = repo.playlist(pid).first()!!.entries[1]
        rechecker.recheckIfDue(pid, entry) // offline: no answer
        answer = false
        rechecker.recheckIfDue(pid, entry)
        assertEquals(2, checks)
        assertEquals(EntryStatus.UNAVAILABLE, repo.playlist(pid).first()!!.entries[1].status)
    }
}
