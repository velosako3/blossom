// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.untitled

import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.source.api.AudioQuality
import io.github.velosako3.blossom.source.api.TrackUnavailableException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UntitledTest {
    private fun fixture(name: String) = javaClass.getResource("/fixtures/$name")!!.readText()
    private val userAgents = mutableListOf<String?>()
    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                userAgents += request.getHeader("User-Agent")
                val url = request.requestUrl!!
                val path = url.encodedPath
                return when {
                    path == "/library/project/WKbtDE6kGZpDiPCFdcERF" && url.queryParameter("_data") == "routes/library.project.\$projectSlug" -> MockResponse().setBody(fixture("project_data.json"))
                    path == "/library/track/WJw8irtvzDf939v5mbbbb" && url.queryParameter("_data") == "routes/library.track.\$trackSlug" -> MockResponse().setBody(fixture("track_data.json"))
                    path == "/library/track/LockedLockedLocked1234" -> MockResponse().setResponseCode(403)
                    path == "/library/track/BrokenBrokenBroken1234" -> MockResponse().setResponseCode(503)
                    path.startsWith("/library/") -> MockResponse().setResponseCode(204) // private or removed
                    path.startsWith("/api/storage/buckets/") && path.endsWith("/signedUrl") ->
                        if ("private-transcoded-audio" in path) MockResponse().setBody(fixture("signed_url.json")) else MockResponse().setResponseCode(404)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        start()
    }
    private val client = UntitledClient(base = server.url("/").toString().trimEnd('/'))

    @AfterTest fun stop() = server.shutdown()

    @Test fun everyLinkFormat() {
        assertEquals(UntitledLink.Project("AbCdEfGhIjKlMnOpQrStU"), UntitledLinks.parse("https://untitled.stream/library/project/AbCdEfGhIjKlMnOpQrStU?ref=x"))
        assertEquals(UntitledLink.Track("WxYzWxYzWxYzWxYz12345", project = "AbCdEfGhIjKlMnOpQrStU"), UntitledLinks.parse("https://www.untitled.stream/library/project/AbCdEfGhIjKlMnOpQrStU/track/WxYzWxYzWxYzWxYz12345"))
        assertEquals(UntitledLink.Track("QrStUvWxYzAbCdEfGhIjK"), UntitledLinks.parse("https://untitled.stream/library/track/QrStUvWxYzAbCdEfGhIjK"))
        assertEquals(UntitledLink.Track("QrStUvWxYzAbCdEfGhIjK"), UntitledLinks.parse("https://untitled.stream/library/track/QrStUvWxYzAbCdEfGhIjK/embed"))
        assertEquals(UntitledLink.Project("AbCdEfGhIjKlMnOpQrStU"), UntitledLinks.parse("https://untitled.stream/library/project/AbCdEfGhIjKlMnOpQrStU/embed"))
        assertEquals(UntitledLink.Track("QrStUvWxYzAbCdEfGhIjK"), UntitledLinks.parse("https://untitled.stream/embed/QrStUvWxYzAbCdEfGhIjK"))
        assertEquals(UntitledLink.Invite("abc123"), UntitledLinks.parse("https://untitled.stream/invite/abc123"))
        assertNull(UntitledLinks.parse("https://example.com/library/track/QrStUvWxYzAbCdEfGhIjK"))
    }

    @Test fun projectImportKeepsTitlesVerbatim() = runTest {
        val p = UntitledImporter(client).read("https://untitled.stream/library/project/WKbtDE6kGZpDiPCFdcERF")
        assertEquals(5, p.tracks.size)
        assertEquals("Kanye West – Never Abandon Your Family (Yedit)", p.tracks.first().title)
        assertEquals("Kanye West – Never Abandon Your Family (Yedit)", p.tracks.first().nativeTrack!!.title)
        assertTrue(p.tracks.all { it.nativeTrack!!.id.kind == SourceKind.UNTITLED })
        // Project tracks have no standalone track page: their ID names the project too.
        assertEquals("untitled:WKbtDE6kGZpDiPCFdcERF/m8JUcKkHnVm6vMGpnsNZG", p.tracks.first().nativeTrack!!.id.value)
        assertEquals("Fake Project (Yedits) ✦", p.name)
        assertEquals(listOf("fake_uploader"), p.tracks.first().artists)
        assertTrue(userAgents.all { it != null && it.startsWith("Mozilla/5.0 (") }, "browser UA on every request: $userAgents")
    }

    @Test fun trackLinkInsideAProject() = runTest {
        val p = UntitledImporter(client).read("https://untitled.stream/library/project/WKbtDE6kGZpDiPCFdcERF/track/m8JUcKkHnVm6vMGpnsNZG")
        assertEquals("untitled:WKbtDE6kGZpDiPCFdcERF/m8JUcKkHnVm6vMGpnsNZG", p.tracks.single().nativeTrack!!.id.value)
    }

    @Test fun streamIsSignedTranscode() = runTest {
        val s = UntitledSource(client).resolveStream(TrackId.of(SourceKind.UNTITLED, "WJw8irtvzDf939v5mbbbb"), AudioQuality.HIGH)
        assertTrue(s.url.contains("token="))
        assertEquals("audio/mpeg", s.mimeType)
        assertTrue(s.headers["User-Agent"]!!.startsWith("Mozilla/5.0 ("))
        assertTrue(s.expiresAt != null)
    }

    @Test fun projectTrackStreamsThroughProjectData() = runTest {
        val id = TrackId.of(SourceKind.UNTITLED, "WKbtDE6kGZpDiPCFdcERF/m8JUcKkHnVm6vMGpnsNZG")
        val s = UntitledSource(client).resolveStream(id, AudioQuality.HIGH)
        assertTrue(s.url.contains("token="))
        assertEquals("Kanye West – Never Abandon Your Family (Yedit)", UntitledSource(client).track(id)!!.title)
    }

    @Test fun removedLinkIsUnavailable() = runTest {
        assertFailsWith<TrackUnavailableException> {
            UntitledSource(client).resolveStream(TrackId.of(SourceKind.UNTITLED, "GoneGoneGoneGoneGone1"), AudioQuality.HIGH)
        }
        assertNull(UntitledSource(client).track(TrackId.of(SourceKind.UNTITLED, "GoneGoneGoneGoneGone1")))
    }

    @Test fun lockedIsGoneButOutageIsAnError() = runTest {
        assertNull(UntitledSource(client).track(TrackId.of(SourceKind.UNTITLED, "LockedLockedLocked1234")))
        assertFailsWith<java.io.IOException> { UntitledSource(client).track(TrackId.of(SourceKind.UNTITLED, "BrokenBrokenBroken1234")) }
    }
}
