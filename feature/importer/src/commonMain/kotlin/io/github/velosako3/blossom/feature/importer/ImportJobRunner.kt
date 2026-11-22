// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.importer

import app.cash.sqldelight.coroutines.asFlow
import io.github.velosako3.blossom.core.database.BlossomDatabase
import io.github.velosako3.blossom.core.database.LibraryCommands
import io.github.velosako3.blossom.core.database.LibraryRepository
import io.github.velosako3.blossom.core.database.NewEntry
import io.github.velosako3.blossom.core.model.EntryStatus
import io.github.velosako3.blossom.core.model.PlaylistId
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.source.api.ImportedTrack
import io.github.velosako3.blossom.source.api.PlaylistImporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Imports a playlist in resumable steps, all persisted: read the source → store every item → match in windows of
 * [batchSize] (4 at a time, 3 tries each) → write each window with `addEntries`, storing its match results and marking
 * its items written in the same transaction. A crash or quit at any point resumes from the first unwritten item ([resumePending] on app start), so
 * the playlist ends up complete, in source order, without duplicates.
 */
class ImportJobRunner(
    private val db: BlossomDatabase,
    private val commands: LibraryCommands,
    private val repo: LibraryRepository,
    private val importers: Map<ImportService, PlaylistImporter>,
    /** Candidates for a track that doesn't play natively (YouTube Music song search). */
    private val search: suspend (ImportedTrack) -> List<Track>,
    private val scope: CoroutineScope,
    private val batchSize: Int = 50,
    private val clock: Clock = Clock.System,
    /** Test hook: total items written so far, after each batch commits. */
    private val onBatchWritten: (Int) -> Unit = {},
) {
    private val q get() = db.blossomQueries
    private val io = Dispatchers.IO
    private val json = Json { ignoreUnknownKeys = true }
    private val matching = Semaphore(4)

    suspend fun start(link: DetectedLink, target: ImportTarget): ImportJobId {
        val id = ImportJobId(Uuid.random().toString())
        withContext(io) {
            q.importJobInsert(id.value, link.canonicalUrl, link.service.name, json.encodeToString(ImportTarget.serializer(), target),
                ImportJobState.READING.name, clock.now().toEpochMilliseconds())
        }
        scope.launch(io) { run(id) }
        return id
    }

    /** Continues every job that was reading or matching when the app last stopped. */
    suspend fun resumePending() {
        withContext(io) { q.importJobsActive().executeAsList() }.forEach { id -> scope.launch(io) { run(ImportJobId(id)) } }
    }

    /**
     * Re-reads the job, then its counts, on every change. Counts only grow and DONE is set after the last write, so
     * counts read after a DONE job are final (no transaction needed, which would take the write lock).
     */
    fun progress(id: ImportJobId): Flow<ImportProgress> =
        merge(q.importJob(id.value).asFlow(), q.importItemCounts(id.value).asFlow()).map {
            withContext(io) {
                run {
                    val job = q.importJob(id.value).executeAsOneOrNull()
                    val c = q.importItemCounts(id.value).executeAsOne()
                    job?.let {
                        ImportProgress(
                            state = ImportJobState.valueOf(it.state),
                            total = it.total?.toInt(),
                            done = (c.written ?: 0).toInt(),
                            matched = (c.matched ?: 0).toInt(),
                            uncertain = (c.uncertain ?: 0).toInt(),
                            unavailable = (c.unavailable ?: 0).toInt(),
                            playlistId = it.playlist_id?.let(::PlaylistId),
                        )
                    }
                }
            }
        }.filterNotNull().distinctUntilChanged()

    private suspend fun run(id: ImportJobId) {
        try {
            val job = q.importJob(id.value).executeAsOneOrNull() ?: return
            val target = json.decodeFromString(ImportTarget.serializer(), job.target_json)
            var playlistId = job.playlist_id?.let(::PlaylistId)

            if (job.total == null) {
                val importer = importers[ImportService.valueOf(job.service)] ?: error("no importer for ${job.service}")
                val source = importer.read(job.url)
                playlistId = playlistId ?: when (target) {
                    is ImportTarget.NewPlaylist -> commands.createPlaylist(target.name ?: source.name, source.description, source.origin)
                    is ImportTarget.Existing -> target.id
                    is ImportTarget.UpdateExisting -> target.id
                }
                val tracks = if (target is ImportTarget.UpdateExisting) withoutPresent(target.id, source.tracks) else source.tracks
                db.transaction {
                    q.importJobSetPlaylist(playlistId.value, id.value)
                    tracks.forEachIndexed { i, t -> q.importItemInsert(id.value, i.toLong(), json.encodeToString(StoredImport.serializer(), StoredImport.of(t))) }
                    q.importJobSetTotal(tracks.size.toLong(), id.value)
                    q.importJobSetState(ImportJobState.MATCHING.name, id.value)
                }
            }
            val pid = playlistId ?: q.importJob(id.value).executeAsOne().playlist_id!!.let(::PlaylistId)

            var written = q.importItemCounts(id.value).executeAsOne().written?.toInt() ?: 0
            for (window in q.importItemsUnwritten(id.value).executeAsList().chunked(batchSize)) {
                val results = coroutineScope {
                    window.map { row ->
                        async {
                            matchWithRetries(json.decodeFromString(StoredImport.serializer(), row.imported_json).toImported())
                        }
                    }.awaitAll()
                }
                val entries = window.zip(results).map { (row, m) ->
                    val imported = json.decodeFromString(StoredImport.serializer(), row.imported_json)
                    NewEntry(m.track, m.status, imported.title, imported.artists.joinToString(", "), m.confidence.takeIf { imported.nativeTrack == null })
                }
                // Results and "written" are stored with the entries, in one transaction: a crash re-matches at most one window.
                commands.addEntries(pid, entries) {
                    window.zip(results).forEach { (row, m) ->
                        q.importItemSetResult(json.encodeToString(StoredMatch.serializer(), m), id.value, row.idx)
                        q.importItemMarkWritten(id.value, row.idx)
                    }
                }
                written += window.size
                onBatchWritten(written)
            }
            withContext(io) { q.importJobSetState(ImportJobState.DONE.name, id.value) }
        } catch (e: CancellationException) {
            throw e // resumed on next start
        } catch (e: Exception) {
            withContext(io) { q.importJobSetState(ImportJobState.FAILED.name, id.value) }
        }
    }

    private suspend fun matchWithRetries(t: ImportedTrack): StoredMatch {
        t.nativeTrack?.let { return StoredMatch(it, 1f, EntryStatus.MATCHED) }
        return matching.withPermit {
            var attempt = 0
            while (true) {
                try {
                    val m = TrackMatcher.match(t, search(t))
                    return@withPermit StoredMatch(m.track, m.confidence, m.status)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (++attempt >= 3) return@withPermit StoredMatch(null, 0f, EntryStatus.UNAVAILABLE)
                    delay(500L * attempt)
                }
            }
            @Suppress("UNREACHABLE_CODE") error("unreachable")
        }
    }

    /** Source tracks not yet in the playlist, compared by the source title and artist each entry keeps. */
    private suspend fun withoutPresent(id: PlaylistId, tracks: List<ImportedTrack>): List<ImportedTrack> {
        val entries = repo.playlist(id).first()?.entries.orEmpty()
        val present = entries.mapTo(HashSet()) { key(it.sourceTitle, it.sourceArtist) }
        val presentIds = entries.mapNotNullTo(HashSet()) { it.track?.id }
        return tracks.filter { t -> key(t.title, t.artists.joinToString(", ")) !in present && (t.nativeTrack?.id !in presentIds || t.nativeTrack == null) }
    }

    private fun key(title: String, artist: String) = title.trim().lowercase() + "\u0000" + artist.trim().lowercase()
}
