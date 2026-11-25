// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.importer

import io.github.velosako3.blossom.core.database.LibraryCommands
import io.github.velosako3.blossom.core.model.EntryStatus
import io.github.velosako3.blossom.core.model.PlaylistEntry
import io.github.velosako3.blossom.core.model.PlaylistId
import io.github.velosako3.blossom.core.model.Track
import kotlinx.coroutines.CancellationException
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
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
    @OptIn(ExperimentalAtomicApi::class)
    private val lastChecked = AtomicReference(emptyMap<String, Instant>())

    suspend fun recheckIfDue(playlistId: PlaylistId, entry: PlaylistEntry) {
        val track = entry.track ?: return
        val now = clock.now()
        @OptIn(ExperimentalAtomicApi::class)
        lastChecked.load()[entry.entryId]?.let { if (now - it < 24.hours) return }
        // Only an answer uses up the day; offline or an error means "ask again next time".
        val available = try { isAvailable(track) } catch (e: CancellationException) { throw e } catch (_: Exception) { null } ?: return
        while (true) { val old = lastChecked.load(); if (lastChecked.compareAndSet(old, old + (entry.entryId to now))) break }
        when {
            !available && entry.status != EntryStatus.UNAVAILABLE -> commands.replaceEntryTrack(playlistId, entry.entryId, track, EntryStatus.UNAVAILABLE)
            available && entry.status == EntryStatus.UNAVAILABLE -> commands.replaceEntryTrack(playlistId, entry.entryId, track, EntryStatus.MATCHED)
        }
    }
}
