// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.soundcloud

import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.source.api.AudioQuality
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SoundCloudTest {
    private val server = MockWebServer()
    private val base = server.url("/").toString().trimEnd('/')
    private fun fixture(name: String) = javaClass.getResource("/fixtures/soundcloud/$name")!!.readText().replace("{API}", base).replace("{WEB}", base)
    private val streamUrls = mutableListOf<String>()

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                val path = url.encodedPath
                if (path.startsWith("/resolve") || path.startsWith("/tracks") || path.startsWith("/media") || path.startsWith("/search")) {
                    if (url.queryParameter("client_id") != "FAKECLIENTID0000000000000000FAKE") return MockResponse().setResponseCode(401)
                }
                return when {
                    path == "/" -> MockResponse().setBody(fixture("home.html"))
                    path == "/assets/49-def.js" -> MockResponse().setBody(fixture("app.js"))
                    path == "/assets/0-abc.js" -> MockResponse().setBody("var x=1;")
                    path == "/AbC12" -> MockResponse().setResponseCode(302).setHeader("Location", "https://soundcloud.com/newpunjabisongs2027/sets/study-lofi?si=x&utm_source=clipboard")
                    path == "/resolve" -> when (url.queryParameter("url")) {
                        "https://soundcloud.com/newpunjabisongs2027/sets/study-lofi" -> MockResponse().setBody(fixture("set.json"))
                        "https://soundcloud.com/some/snippet" -> MockResponse().setBody(fixture("snip_track.json"))
                        else -> MockResponse().setResponseCode(404)
                    }
                    path == "/tracks" -> MockResponse().setBody(fixture("tracks_by_ids.json"))
                    path.startsWith("/tracks/") -> MockResponse().setBody(fixture("mixed_track.json"))
                    path.startsWith("/media/") -> { streamUrls += path; MockResponse().setBody("""{"url":"https://cf-media.example/${path.substringAfterLast('/')}.mp3"}""") }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
    }

    private val client = SoundCloudClient(webBase = base, apiBase = base, shortBase = base)
    private val importer = SoundCloudImporter(client)

    @AfterTest fun stop() = server.shutdown()

    @Test fun setImportsEveryTrackInOrder() = runTest {
        val p = importer.read("https://soundcloud.com/newpunjabisongs2027/sets/study-lofi")
        assertEquals(7, p.tracks.size)
        assertTrue(p.tracks.all { it.nativeTrack?.id?.kind == SourceKind.SOUNDCLOUD }, p.tracks.toString())
        assertTrue(p.tracks.all { it.title.isNotBlank() && (it.durationMs ?: 0) > 0 })
        assertEquals("SoundCloud", p.origin.service)
    }

    @Test fun previewOnlyTrackIsMatchedInstead() = runTest {
        val p = importer.read("https://soundcloud.com/some/snippet")
        val t = p.tracks.single()
        assertNull(t.nativeTrack)
        assertEquals("previewOnly", t.unplayableReason)
        assertTrue((t.durationMs ?: 0) > 30_000, "full length, not the 30 s snippet")
    }

    @Test fun shortLinkFollowsTheRedirect() = runTest {
        val p = importer.read("https://on.soundcloud.com/AbC12")
        assertEquals("https://soundcloud.com/newpunjabisongs2027/sets/study-lofi", p.origin.url)
        assertEquals(7, p.tracks.size)
    }

    @Test fun streamPrefersProgressiveOverHls() = runTest {
        val s = SoundCloudSource(client).resolveStream(TrackId.of(SourceKind.SOUNDCLOUD, "123"), AudioQuality.HIGH)
        assertTrue(streamUrls.single().endsWith("/stream/progressive"), streamUrls.toString())
        assertEquals("audio/mpeg", s.mimeType)
        assertTrue(s.url.endsWith("progressive.mp3"))
    }

    @Test fun hlsWhenThatIsAllThereIs() {
        val hlsOnly = listOf(Transcoding("u1", "aac_160k", "hls", "audio/mp4; codecs=\"mp4a.40.2\"", false), Transcoding("u2", "abr_sq", "hls", "audio/mpegurl", false))
        assertEquals("u1", selectTranscoding(hlsOnly)?.url)
        assertNull(selectTranscoding(listOf(Transcoding("u3", "mp3_0_0", "progressive", "audio/mpeg", snipped = true))))
    }
}
