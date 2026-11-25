// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.youtubemusic

import io.github.velosako3.blossom.core.model.SourceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YtmParsingTest {
    @Test fun searchParsesSongs() {
        val songs = YtmParser.searchSongs(fixture("search_songs"))
        assertTrue(songs.isNotEmpty())
        songs.forEach { t ->
            assertTrue(t.id.value.startsWith("ytm:"), t.id.value)
            assertEquals(SourceKind.YOUTUBE_MUSIC, t.id.kind)
            assertTrue(t.title.isNotBlank())
            assertTrue(t.durationMs > 0, "${t.title} duration ${t.durationMs}")
            assertTrue(t.artists.isNotEmpty(), t.title)
        }
    }

    @Test fun albumTrackCountMatches() {
        // Daft Punk – Discovery has 14 tracks.
        val album = YtmParser.album("MPREb_7ltM34kr0mH", fixture("album"))
        assertEquals(14, album.tracks.size)
        assertTrue(album.title.isNotBlank())
        assertTrue(album.tracks.all { it.album?.id == "MPREb_7ltM34kr0mH" })
    }

    @Test fun artistHasTopSongs() {
        val artist = YtmParser.artist("UCRr1xG_2WIDs18a6cIiCxeA", fixture("artist"))
        assertTrue(artist.topSongs.isNotEmpty())
        assertEquals("Daft Punk", artist.name)
    }

    @Test fun homeHasSections() {
        val home = YtmParser.home(fixture("home"))
        assertTrue(home.isNotEmpty())
        assertTrue(home.all { it.title.isNotBlank() && it.items.isNotEmpty() })
    }

    @Test fun radioQueueParses() {
        val queue = YtmParser.watchQueue(fixture("next"))
        assertTrue(queue.size > 5)
        assertTrue(queue.all { it.id.value.startsWith("ytm:") })
    }
}
