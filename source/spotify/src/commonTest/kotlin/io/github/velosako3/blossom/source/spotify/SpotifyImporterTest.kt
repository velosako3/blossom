// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.spotify

import io.github.velosako3.blossom.source.api.ImportError
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SpotifyImporterTest {
    private fun fixture(name: String) = javaClass.getResource("/fixtures/spotify/$name")!!.readText()
    private val requests = mutableListOf<RecordedRequest>()
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.requestUrl!!.encodedPath
                return when {
                    path == "/embed/playlist/FixturePlaylist000000" -> MockResponse().setBody(fixture("embed_playlist.html"))
                    path == "/embed/album/4LH4d3cOWNNsVw41Gqt2kv" -> MockResponse().setBody(fixture("embed_album.html"))
                    path == "/embed/track/0VjIjW4GlUZAMYd2vXMi3b" -> MockResponse().setBody(fixture("embed_track.html"))
                    path == "/embed/playlist/DeletedPlaylist0000000" -> MockResponse().setResponseCode(404)
                    path == "/embed/playlist/BusyPlaylist0000000000" -> MockResponse().setResponseCode(429)
                    path == "/embed/playlist/PrivatePlaylist0000000" -> MockResponse().setBody("<html><body>no data</body></html>")
                    path == "/pathfinder/v1/query" -> {
                        val offset = Regex("\"offset\":(\\d+)").find(request.requestUrl!!.queryParameter("variables")!!)!!.groupValues[1]
                        MockResponse().setBody(fixture("pathfinder_playlist_$offset.json"))
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        start()
    }
    private val base = server.url("/").toString().trimEnd('/')
    private val importer = SpotifyImporter(auth = null, openBase = base, apiBase = base)

    @AfterTest fun stop() = server.shutdown()

    @Test fun playlistAcrossPages() = runTest {
        val p = importer.read("https://open.spotify.com/playlist/FixturePlaylist000000")
        assertEquals("Fixture 80s", p.name)
        assertEquals(250, p.tracks.size)
        assertEquals("Thriller", p.tracks.first().title)
        assertEquals(listOf("Michael Jackson"), p.tracks.first().artists)
        assertEquals(358_807, p.tracks.first().durationMs)
        assertEquals("Fixture Track 101", p.tracks[100].title)
        assertEquals("Fixture Track 250", p.tracks.last().title)
        assertEquals((p.tracks.map { it.sourceKey }).toSet().size, 250)
        assertTrue(p.tracks.all { it.nativeTrack == null })
        val auth = requests.first { it.requestUrl!!.encodedPath == "/pathfinder/v1/query" }.getHeader("Authorization")
        assertEquals("Bearer FAKE_ANON_TOKEN", auth)
    }

    @Test fun albumTracksCarryTheAlbumName() = runTest {
        val p = importer.read("https://open.spotify.com/album/4LH4d3cOWNNsVw41Gqt2kv")
        assertTrue(p.tracks.size >= 9)
        assertTrue(p.tracks.all { it.album == p.name }, p.tracks.map { it.album }.toString())
        assertEquals("Speak to Me", p.tracks.first().title)
    }

    @Test fun singleTrack() = runTest {
        val p = importer.read("spotify:track:0VjIjW4GlUZAMYd2vXMi3b")
        assertEquals(1, p.tracks.size)
        assertEquals("Blinding Lights", p.tracks.single().title)
        assertEquals(listOf("The Weeknd"), p.tracks.single().artists)
        assertEquals(200_040, p.tracks.single().durationMs)
    }

    @Test fun privatePlaylistNeedsLogin() = runTest {
        assertFailsWith<ImportError.NeedsLogin> { importer.read("https://open.spotify.com/playlist/PrivatePlaylist0000000") }
    }

    @Test fun handlesSpotifyLinksOnly() {
        assertTrue(importer.canHandle("https://open.spotify.com/intl-de/playlist/abc?si=x"))
        assertTrue(importer.canHandle("spotify:album:abc"))
        assertTrue(!importer.canHandle("https://music.youtube.com/playlist?list=PL"))
    }

    @Test fun deletedIsNotFoundAndBusyIsRetryable() = runTest {
        assertFailsWith<ImportError.NotFound> { importer.read("https://open.spotify.com/playlist/DeletedPlaylist0000000") }
        assertFailsWith<java.io.IOException> { importer.read("https://open.spotify.com/playlist/BusyPlaylist0000000000") }
    }
}
