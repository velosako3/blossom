// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import io.github.velosako3.blossom.core.model.EntryStatus
import io.github.velosako3.blossom.core.model.PlayEvent
import io.github.velosako3.blossom.core.model.Playlist
import io.github.velosako3.blossom.core.model.PlaylistEntry
import io.github.velosako3.blossom.core.model.PlaylistId
import io.github.velosako3.blossom.core.model.PlaylistOrigin
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.core.model.sync.OperationJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/** A playlist row in the library list (the "PlaylistSummary" of the plan; named apart from search results). */
data class LibraryPlaylistSummary(
    val id: PlaylistId,
    val name: String,
    val entryCount: Int,
    val artworkUrls: List<String>,
    val origin: PlaylistOrigin?,
)

interface LibraryRepository {
    fun playlists(): Flow<List<LibraryPlaylistSummary>>
    fun playlist(id: PlaylistId): Flow<Playlist?>
    fun liked(): Flow<List<Track>>
    fun isLiked(id: TrackId): Flow<Boolean>
    fun playEvents(from: Instant, to: Instant): Flow<List<PlayEvent>>
}

class SqlLibraryRepository(private val db: BlossomDatabase) : LibraryRepository {
    private val q get() = db.blossomQueries
    private val io = Dispatchers.IO

    override fun playlists(): Flow<List<LibraryPlaylistSummary>> =
        q.playlistSummaries().asFlow().mapToList(io).map { rows ->
            withContext(io) { rows.map { r ->
                LibraryPlaylistSummary(
                    PlaylistId(r.id), r.name, r.entry_count.toInt(),
                    q.firstArtworkJson(r.id).executeAsList().mapNotNull { it?.let(::decodeTrack)?.artworkUrl },
                    r.origin_json?.let { OperationJson.decodeFromString(PlaylistOrigin.serializer(), it) },
                )
            } }
        }

    override fun playlist(id: PlaylistId): Flow<Playlist?> =
        combine(q.livePlaylist(id.value).asFlow().mapToOneOrNull(io), q.liveEntries(id.value).asFlow().mapToList(io)) { p, entries ->
            p ?: return@combine null
            Playlist(
                id = id,
                name = p.name,
                description = p.description,
                entries = entries.map { e ->
                    PlaylistEntry(
                        entryId = e.entry_id,
                        track = e.track_json?.let(::decodeTrack),
                        status = EntryStatus.valueOf(e.status),
                        sourceTitle = e.source_title,
                        sourceArtist = e.source_artist,
                        position = e.position,
                        addedAt = Instant.fromEpochMilliseconds(e.added_at),
                        matchConfidence = e.match_confidence?.toFloat(),
                    )
                },
                origin = p.origin_json?.let { OperationJson.decodeFromString(PlaylistOrigin.serializer(), it) },
                artworkUrl = null,
                createdAt = Instant.fromEpochMilliseconds(p.created_at),
                updatedAt = Instant.fromEpochMilliseconds(p.updated_at),
            )
        }

    override fun liked(): Flow<List<Track>> = q.likedTracks().asFlow().mapToList(io).map { rows -> rows.mapNotNull { decodeTrackOrNull(it.track_json) } }

    override fun isLiked(id: TrackId): Flow<Boolean> = q.isLiked(id.value).asFlow().mapToOneOrNull(io).map { it == 1L }

    override fun playEvents(from: Instant, to: Instant): Flow<List<PlayEvent>> =
        q.playsBetween(from.toEpochMilliseconds(), to.toEpochMilliseconds()).asFlow().mapToList(io).map { rows ->
            rows.map { PlayEvent(it.id, decodeTrack(it.track_json), Instant.fromEpochMilliseconds(it.started_at), it.played_ms, it.counted == 1L, it.device_id) }
        }

    private fun decodeTrack(json: String): Track = OperationJson.decodeFromString(Track.serializer(), json)
    private fun decodeTrackOrNull(json: String): Track? = json.takeIf { it.isNotEmpty() }?.let(::decodeTrack)
}
