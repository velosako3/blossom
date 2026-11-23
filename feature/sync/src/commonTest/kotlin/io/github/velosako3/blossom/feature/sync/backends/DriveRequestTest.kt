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

class DriveRequestTest {
    private val requests = mutableListOf<RecordedRequest>()
    private var tokenValid = "fresh"
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val url = request.requestUrl!!
                if (url.encodedPath == "/token") {
                    return MockResponse().setBody("""{"access_token":"fresh","expires_in":3600,"token_type":"Bearer"}""")
                }
                if (request.getHeader("Authorization") != "Bearer $tokenValid") return MockResponse().setResponseCode(401)
                return when {
                    url.encodedPath == "/drive/v3/files" && request.method == "GET" ->
                        MockResponse().setBody("""{"files":[{"id":"f1","name":"blossom/v1/devices/A/manifest.json","size":"2","md5Checksum":"x"},{"id":"f2","name":"other/thing","size":"1"}]}""")
                    url.encodedPath == "/drive/v3/files/f1" -> MockResponse().setBody("{}")
                    url.encodedPath == "/upload/drive/v3/files" -> MockResponse().setBody("""{"id":"f9"}""")
                    url.encodedPath == "/upload/drive/v3/files/f1" -> MockResponse().setBody("""{"id":"f1"}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        start()
    }
    private val base = server.url("/").toString().trimEnd('/')
    private val store = InMemorySecureStore()
    private val oauth = OAuthClient(OAuthConfig("client-1", "secret-1", "$base/auth", "$base/token", listOf("https://www.googleapis.com/auth/drive.appdata")), store, "drive")
    private val drive = GoogleDriveBackend(oauth, apiBase = base)

    @AfterTest fun stop() = server.shutdown()

    @Test fun listsTheAppDataFolderWithBearerToken() = runTest {
        oauth.save(OAuthTokens("fresh", "refresh-1", Long.MAX_VALUE))
        val files = drive.list("blossom/v1")
        assertEquals(listOf("blossom/v1/devices/A/manifest.json"), files.map { it.path })
        val r = requests.last()
        assertEquals("appDataFolder", r.requestUrl!!.queryParameter("spaces"))
        assertTrue(r.requestUrl!!.queryParameter("fields")!!.contains("files(id,name,size"))
    }

    @Test fun expiredTokenRefreshesAndRetriesOnce() = runTest {
        oauth.save(OAuthTokens("stale", "refresh-1", Long.MAX_VALUE))
        assertEquals("{}", String(drive.read("blossom/v1/devices/A/manifest.json")))
        val refresh = requests.single { it.requestUrl!!.encodedPath == "/token" }
        val form = refresh.body.readUtf8()
        assertTrue("grant_type=refresh_token" in form && "refresh_token=refresh-1" in form && "client_id=client-1" in form, form)
        assertEquals("media", requests.last().requestUrl!!.queryParameter("alt"))
    }

    /** Android's Google sign-in hands out access tokens only; a stale one is renewed through Play services instead. */
    @Test fun withoutARefreshTokenTheRenewHookGetsANewToken() = runTest {
        var renewed = 0
        val android = OAuthClient(OAuthConfig("client-1", null, "$base/auth", "$base/token", emptyList()), store, "drive-android") {
            renewed++; OAuthTokens("fresh", null, Long.MAX_VALUE)
        }
        android.save(OAuthTokens("stale", null, Long.MAX_VALUE))
        assertEquals(1, GoogleDriveBackend(android, apiBase = base).list("blossom/v1").size)
        assertEquals(1, renewed)
        assertTrue(requests.none { it.requestUrl!!.encodedPath == "/token" })
        assertEquals("fresh", android.tokens()!!.access, "the renewed token is kept")
    }

    @Test fun writeUpdatesExistingOrCreatesInAppData() = runTest {
        oauth.save(OAuthTokens("fresh", "refresh-1", Long.MAX_VALUE))
        drive.write("blossom/v1/devices/A/manifest.json", "{\"x\":1}".toByteArray())
        val update = requests.last()
        assertEquals("PATCH", update.method); assertEquals("/upload/drive/v3/files/f1", update.requestUrl!!.encodedPath); assertEquals("media", update.requestUrl!!.queryParameter("uploadType"))
        drive.write("blossom/v1/devices/A/ops-0.jsonl", "x\n".toByteArray())
        val create = requests.last()
        assertEquals("POST", create.method); assertEquals("multipart", create.requestUrl!!.queryParameter("uploadType"))
        val body = create.body.readUtf8()
        assertTrue(""""parents":["appDataFolder"]""" in body && """"name":"blossom/v1/devices/A/ops-0.jsonl"""" in body, body)
    }

    @Test fun pkceAuthorizeUrl() {
        val pkce = Pkce.generate()
        assertTrue(pkce.verifier.length in 43..128)
        val url = oauth.authorizeUrl(pkce, "http://127.0.0.1:5000/", state = "st")
        val u = okhttp3.HttpUrl.Builder().scheme("http").host("x").build().resolve(url) ?: error(url)
        assertEquals("S256", u.queryParameter("code_challenge_method"))
        assertEquals(pkce.challenge, u.queryParameter("code_challenge"))
        assertEquals("code", u.queryParameter("response_type"))
        assertEquals("https://www.googleapis.com/auth/drive.appdata", u.queryParameter("scope"))
        assertEquals("offline", u.queryParameter("access_type"))
    }
}
