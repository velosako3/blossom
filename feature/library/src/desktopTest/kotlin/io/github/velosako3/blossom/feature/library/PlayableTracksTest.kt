// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.library

import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.EntryStatus
import io.github.velosako3.blossom.core.model.PlaylistEntry
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals

class PlayableTracksTest {
    private fun t(id: String) = Track(TrackId.of(SourceKind.UNTITLED, id), id, listOf(ArtistRef(null, "A")), null, 1000, null)

    /** A dead untitled link doesn't stop a song saved before it died: the saved copy plays. */
    @Test fun aSavedCopyBeatsADeadLink() {
        val saved = t("saved"); val gone = t("gone"); val fine = t("fine")
        val entries = listOf(
            PlaylistEntry("1", saved, EntryStatus.UNAVAILABLE, saved.title, "A", "a1", kotlin.time.Instant.fromEpochSeconds(0), null),
            PlaylistEntry("2", gone, EntryStatus.UNAVAILABLE, gone.title, "A", "a2", kotlin.time.Instant.fromEpochSeconds(0), null),
            PlaylistEntry("3", fine, EntryStatus.MATCHED, fine.title, "A", "a3", kotlin.time.Instant.fromEpochSeconds(0), null),
        )
        assertEquals(listOf("saved", "fine"), playableTracks(entries, isAvailable = { true }, isSaved = { it.id == saved.id }).map { it.title })
    }
}
