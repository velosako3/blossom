// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.backends

import io.github.velosako3.blossom.core.platform.InMemorySecureStore
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DropboxRequestTest {
    private val requests = mutableListOf<RecordedRequest>()
    private var valid = "fresh"
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.requestUrl!!.encodedPath
                if (path == "/oauth2/token") return MockResponse().setBody("""{"access_token":"fresh","expires_in":14400}""")
                if (request.getHeader("Authorization") != "Bearer $valid") return MockResponse().setResponseCode(401).setBody("""{"error":{".tag":"expired_access_token"}}""")
                return when (path) {
                    "/2/files/list_folder" -> MockResponse().setBody("""{"entries":[{".tag":"file","path_display":"/blossom/v1/devices/A/manifest.json","size":2,"rev":"r1"},{".tag":"folder","path_display":"/blossom/v1/devices/A"}],"cursor":"c1","has_more":true}""")
                    "/2/files/list_folder/continue" -> MockResponse().setBody("""{"entries":[{".tag":"file","path_display":"/blossom/v1/devices/B/manifest.json","size":3,"rev":"r2"}],"cursor":"c2","has_more":false}""")
                    "/2/files/download" -> MockResponse().setBody("{}")
                    "/2/files/upload" -> MockResponse().setBody("""{"rev":"r3"}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        start()
    }
    private val base = server.url("/").toString().trimEnd('/')
    private val oauth = OAuthClient(OAuthConfig("app-key", null, "$base/oauth2/authorize", "$base/oauth2/token", emptyList()), InMemorySecureStore(), "dropbox")
    private val dropbox = DropboxBackend(oauth, apiBase = base, contentBase = base)

    @AfterTest fun stop() = server.shutdown()

    @Test fun listsRecursivelyAcrossPages() = runTest {
        oauth.save(OAuthTokens("fresh", "r", Long.MAX_VALUE))
        assertEquals(listOf("blossom/v1/devices/A/manifest.json", "blossom/v1/devices/B/manifest.json"), dropbox.list("blossom/v1").map { it.path })
        val first = requests.first { it.requestUrl!!.encodedPath == "/2/files/list_folder" }.body.readUtf8()
        assertTrue(""""path":"/blossom/v1"""" in first && """"recursive":true""" in first, first)
        assertTrue(""""cursor":"c1"""" in requests.first { it.requestUrl!!.encodedPath == "/2/files/list_folder/continue" }.body.readUtf8())
    }

    @Test fun uploadOverwritesWithApiArgHeader() = runTest {
        oauth.save(OAuthTokens("fresh", "r", Long.MAX_VALUE))
        dropbox.write("blossom/v1/devices/A/ops-0.jsonl", "x\n".toByteArray())
        val r = requests.last()
        assertEquals("application/octet-stream", r.getHeader("Content-Type"))
        val arg = r.getHeader("Dropbox-API-Arg")!!
        assertTrue(""""path":"/blossom/v1/devices/A/ops-0.jsonl"""" in arg && """"mode":"overwrite"""" in arg && """"mute":true""" in arg, arg)
    }

    @Test fun expiredTokenRefreshesOnce() = runTest {
        oauth.save(OAuthTokens("stale", "refresh-9", Long.MAX_VALUE))
        assertEquals("{}", String(dropbox.read("blossom/v1/devices/A/manifest.json")))
        val form = requests.single { it.requestUrl!!.encodedPath == "/oauth2/token" }.body.readUtf8()
        assertTrue("grant_type=refresh_token" in form && "refresh_token=refresh-9" in form && "client_id=app-key" in form, form)
        assertTrue(""""path":"/blossom/v1/devices/A/manifest.json"""" in requests.last().getHeader("Dropbox-API-Arg")!!)
    }
}
