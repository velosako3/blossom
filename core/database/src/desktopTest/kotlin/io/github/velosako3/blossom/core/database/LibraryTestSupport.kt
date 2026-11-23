// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.database

import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.EntryStatus
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.core.model.sync.HybridLogicalClock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

class OffsetClock(private val offset: Duration) : Clock {
    var base = Instant.fromEpochMilliseconds(1_800_000_000_000)
    override fun now(): Instant = base + offset
    fun tick(ms: Long = 7) { base += Duration.parse("${ms}ms") }
}

fun sampleTrack(n: Int) = Track(TrackId.of(SourceKind.YOUTUBE_MUSIC, "v$n"), "Song $n", listOf(ArtistRef(null, "Artist $n")), null, 180_000, null)

fun newEntry(n: Int) = NewEntry(sampleTrack(n), EntryStatus.MATCHED, "Song $n", "Artist $n", null)

/** A device: its own database, log, clock and commands. */
class Device(val id: String, val clock: Clock = Clock.System) {
    val db = inMemoryDatabase()
    val hlc = HybridLogicalClock(id, clock)
    val log = SqlOpLog(db, id, hlc)
    val projector = LibraryProjector(db)
    val commands = LibraryCommands(log, projector, db, clock)
    val repo = SqlLibraryRepository(db)
}

/** Everything that must converge, as plain values. */
data class LibrarySnapshot(
    val playlists: List<Triple<String, String, String?>>,
    val entries: Map<String, List<String>>,
    val liked: List<String>,
    val plays: List<String>,
)

fun snapshot(db: BlossomDatabase): LibrarySnapshot {
    val q = db.blossomQueries
    val playlists = q.livePlaylists().executeAsList().map { Triple(it.id, it.name, it.description) }.sortedBy { it.first }
    val entries = playlists.associate { (id) -> id to q.liveEntries(id).executeAsList().map { it.entry_id } }
    return LibrarySnapshot(
        playlists,
        entries,
        q.likedTracks().executeAsList().map { it.track_id }.sorted(),
        q.allPlayIds().executeAsList().sorted(),
    )
}
