// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.spotify

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/** Imports public Spotify links without signing in. Live: -PliveTests=true. */
class SpotifyImporterLiveTest {
    private val importer = SpotifyImporter(auth = null)

    @Test fun publicPlaylistPastOneHundredTracks(): Unit = runBlocking {
        val p = importer.read("https://open.spotify.com/playlist/37i9dQZF1DX4UtSsGT1Sbe?si=test")
        println("spotify live: '${p.name}' ${p.tracks.size} tracks, first '${p.tracks.first().title}' – ${p.tracks.first().artists}, last '${p.tracks.last().title}', with album ${p.tracks.count { it.album != null }}")
        assertTrue(p.tracks.size > 100)
        assertTrue(p.tracks.all { it.title.isNotBlank() && it.artists.isNotEmpty() && (it.durationMs ?: 0) > 0 })
    }

    @Test fun albumAndTrack(): Unit = runBlocking {
        val album = importer.read("https://open.spotify.com/album/4LH4d3cOWNNsVw41Gqt2kv")
        val track = importer.read("https://open.spotify.com/track/0VjIjW4GlUZAMYd2vXMi3b")
        println("spotify live: album '${album.name}' ${album.tracks.size} tracks; track '${track.tracks.single().title}'")
        assertTrue(album.tracks.isNotEmpty() && track.tracks.size == 1)
    }
}
