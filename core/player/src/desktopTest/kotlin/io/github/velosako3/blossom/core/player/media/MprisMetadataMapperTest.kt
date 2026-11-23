// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player.media

import io.github.velosako3.blossom.core.model.AlbumRef
import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import kotlin.test.Test
import kotlin.test.assertEquals

class MprisMetadataMapperTest {
    private val track = Track(
        id = TrackId("ytm:ab-c_1"),
        title = "One More Time",
        artists = listOf(ArtistRef("a", "Daft Punk"), ArtistRef(null, "Romanthony")),
        album = AlbumRef("b", "Discovery"),
        durationMs = 215_000,
        artworkUrl = "https://img/x.jpg",
    )

    @Test fun mapsLengthAndTrackId() {
        val m = MprisMetadataMapper.map(track)
        assertEquals(215_000_000L, m["mpris:length"])
        assertEquals("/io/github/velosako3/blossom/track/ytm_ab_c_1", m["mpris:trackid"])
    }

    @Test fun mapsTextFields() {
        val m = MprisMetadataMapper.map(track)
        assertEquals("One More Time", m["xesam:title"])
        assertEquals(listOf("Daft Punk", "Romanthony"), m["xesam:artist"])
        assertEquals("Discovery", m["xesam:album"])
        assertEquals("https://img/x.jpg", m["mpris:artUrl"])
    }
}
