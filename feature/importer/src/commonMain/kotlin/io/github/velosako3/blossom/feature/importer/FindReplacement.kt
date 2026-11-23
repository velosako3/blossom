// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.importer

import io.github.velosako3.blossom.core.database.LibraryCommands
import io.github.velosako3.blossom.core.model.EntryStatus
import io.github.velosako3.blossom.core.model.PlaylistEntry
import io.github.velosako3.blossom.core.model.PlaylistId
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.source.api.MusicSource
import io.github.velosako3.blossom.source.api.SearchFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * "Find replacement" for an unavailable entry: searches [sources] (plain YouTube uploads and SoundCloud, where re-ups
 * of leaks and edits live) for "<artist> <title>", and swaps the entry's track in place (same entry, same position).
 */
class FindReplacement(private val sources: List<MusicSource>, private val commands: LibraryCommands) {
    suspend fun candidates(entry: PlaylistEntry): List<Track> = coroutineScope {
        val query = "${entry.sourceArtist} ${entry.sourceTitle}".trim()
        sources.map { s ->
            async { try { s.search(query, SearchFilter.SONGS).songs.take(15) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() } }
        }.awaitAll().flatten()
    }

    suspend fun choose(playlistId: PlaylistId, entry: PlaylistEntry, track: Track) =
        commands.replaceEntryTrack(playlistId, entry.entryId, track, EntryStatus.MATCHED)
}
