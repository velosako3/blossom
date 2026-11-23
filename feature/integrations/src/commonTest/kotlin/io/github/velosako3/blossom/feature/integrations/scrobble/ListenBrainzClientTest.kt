// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.integrations.scrobble

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class ListenBrainzClientTest {
    @Test fun submitsASingleListen(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"status":"ok"}"""))
            server.start()
            val client = ListenBrainzClient({ "TOKEN" }, endpoint = server.url("/1/").toString())
            client.scrobble(Scrobble(ScrobbleTrack("Artist \"A\"", "Song", "Album", 200_000), Instant.fromEpochSeconds(1_800_000_000)))
            val req = server.takeRequest()
            assertEquals("/1/submit-listens", req.requestUrl!!.encodedPath)
            assertEquals("Token TOKEN", req.getHeader("Authorization"))
            val body = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
            assertEquals("single", body["listen_type"]!!.jsonPrimitive.content)
            val listen = body["payload"]!!.jsonArray[0].jsonObject
            assertEquals(1_800_000_000, listen["listened_at"]!!.jsonPrimitive.long)
            assertEquals("Artist \"A\"", listen["track_metadata"]!!.jsonObject["artist_name"]!!.jsonPrimitive.content)
        }
    }
}
