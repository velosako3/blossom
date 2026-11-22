// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.importer

import io.github.velosako3.blossom.core.database.LibraryCommands
import io.github.velosako3.blossom.core.model.EntryStatus
import io.github.velosako3.blossom.core.model.PlaylistEntry
import io.github.velosako3.blossom.core.model.PlaylistId
import io.github.velosako3.blossom.core.model.Track
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * When a playlist opens, checks each entry's link at most once a day. A dead link turns the entry UNAVAILABLE (its
 * title stays); a link that works again turns it back to MATCHED. [isAvailable] null means "couldn't tell" (offline).
 */
class DeadLinkRechecker(
    private val clock: Clock,
    private val commands: LibraryCommands,
    private val isAvailable: suspend (Track) -> Boolean?,
) {
    // ponytail: remembered per app session; a restart checks again, which is cheap.
    private val lastChecked = ConcurrentHashMap<String, Instant>()

    suspend fun recheckIfDue(playlistId: PlaylistId, entry: PlaylistEntry) {
        val track = entry.track ?: return
        val now = clock.now()
        lastChecked[entry.entryId]?.let { if (now - it < 24.hours) return }
        lastChecked[entry.entryId] = now
        val available = try { isAvailable(track) } catch (e: CancellationException) { throw e } catch (_: Exception) { null } ?: return
        when {
            !available && entry.status != EntryStatus.UNAVAILABLE -> commands.replaceEntryTrack(playlistId, entry.entryId, track, EntryStatus.UNAVAILABLE)
            available && entry.status == EntryStatus.UNAVAILABLE -> commands.replaceEntryTrack(playlistId, entry.entryId, track, EntryStatus.MATCHED)
        }
    }
}
