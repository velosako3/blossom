// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.youtubemusic

import io.github.velosako3.blossom.core.platform.InMemorySecureStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YtmImporterTest {
    private fun raw(name: String) = object {}.javaClass.getResource("/fixtures/$name.json")!!.readText()
    private val engine = MockEngine { request ->
        val body = when {
            request.url.encodedPath.endsWith("/browse") && request.url.parameters["continuation"] != null -> raw("playlist_page2")
            request.url.encodedPath.endsWith("/browse") -> raw("playlist_page1")
            request.url.encodedPath.endsWith("/next") -> raw("next")
            else -> error("unexpected ${request.url}")
        }
        respond(body, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
    }
    private val client = InnertubeClient(http = HttpClient(engine) { install(ContentNegotiation) { json() } })
    private val ytm = YtmSource(InMemorySecureStore(), YouTubeStreams({ null }, kotlin.io.path.createTempDirectory("ytm").toFile()), client = client)
    private val importer = YtmImporter(ytm)

    @Test fun playlistWithContinuation() = runTest {
        val expected = YtmParser.playlist("PLx", fixture("playlist_page1")).tracks.size + YtmParser.continuationTracks(fixture("playlist_page2")).first.size
        val p = importer.read("https://music.youtube.com/playlist?list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI")
        assertEquals(expected, p.tracks.size)
        assertTrue(expected > 4, "both pages contribute")
        assertTrue(p.tracks.all { it.nativeTrack != null && it.nativeTrack!!.id.value.startsWith("ytm:") })
        assertEquals("YouTube Music", p.origin.service)
        assertTrue(p.name.isNotBlank())
    }

    @Test fun singleVideo() = runTest {
        val first = YtmParser.watchQueue(fixture("next")).first()
        val p = importer.read("https://www.youtube.com/watch?v=${first.id.sourceId}&feature=share")
        assertEquals(1, p.tracks.size)
        assertEquals(first.id, p.tracks.single().nativeTrack!!.id)
    }

    @Test fun handlesYouTubeLinks() {
        assertTrue(importer.canHandle("https://youtu.be/abc"))
        assertTrue(importer.canHandle("https://music.youtube.com/browse/MPREb_x"))
        assertTrue(!importer.canHandle("https://open.spotify.com/track/x"))
    }
}
