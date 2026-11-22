// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.integrations.scrobble

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class LastFmClientTest {
    @Test fun signsAndPostsAScrobble(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"scrobbles":{"@attr":{"accepted":1,"ignored":0}}}"""))
            server.start()
            val client = LastFmClient("KEY", "SECRET", { "SESSION" }, endpoint = server.url("/2.0/").toString())
            client.scrobble(Scrobble(ScrobbleTrack("Artist A", "Song A", "Album", 200_000), Instant.fromEpochSeconds(1_800_000_000)))
            val req = server.takeRequest()
            val form = req.body.readUtf8().split('&').associate { java.net.URLDecoder.decode(it.substringBefore('='), "UTF-8") to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8") }
            assertEquals("track.scrobble", form["method"])
            assertEquals("json", form["format"])
            // md5 of the sorted params (format excluded) + secret, computed independently.
            assertEquals("6cb18f7b6e822baf1d7635564df4545a", form["api_sig"])
            assertTrue(req.getHeader("User-Agent")!!.startsWith("blossom"))
        }
    }

    @Test fun anErrorBodyThrows(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"error":9,"message":"Invalid session key"}"""))
            server.start()
            val client = LastFmClient("KEY", "SECRET", { "SESSION" }, endpoint = server.url("/2.0/").toString())
            val e = kotlin.runCatching { client.nowPlaying(ScrobbleTrack("A", "S", null, 1000)) }.exceptionOrNull()
            assertTrue(e is LastFmException && e.code == 9, e.toString())
        }
    }
}
