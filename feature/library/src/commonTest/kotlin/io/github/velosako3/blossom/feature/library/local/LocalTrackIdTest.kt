// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.library.local

import io.github.velosako3.blossom.core.model.SourceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LocalTrackIdTest {
    @Test fun normalizedTagsGiveSameId() =
        assertEquals(localTrackId("Song", "Artist", "Album"), localTrackId("  song ", "ARTIST", "album"))

    @Test fun collapsedWhitespace() = assertEquals(localTrackId("A  B", "C", null), localTrackId("a b", "c", ""))

    @Test fun differentAlbumDifferentId() = assertNotEquals(localTrackId("Song", "Artist", "One"), localTrackId("Song", "Artist", "Two"))

    @Test fun shape() {
        val id = localTrackId("Song", "Artist", "Album")
        assertEquals(SourceKind.LOCAL, id.kind)
        assertEquals(40, id.sourceId.length)
    }
}
