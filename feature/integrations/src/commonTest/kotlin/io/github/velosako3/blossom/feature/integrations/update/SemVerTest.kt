// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.integrations.update

import io.github.velosako3.blossom.core.platform.MapPreferenceStore
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class SemVerTest {
    private fun v(s: String) = SemVer.parse(s)!!

    @Test fun ordering() {
        assertTrue(v("1.10.0") > v("1.2.0"))
        assertTrue(v("1.0.0") > v("1.0.0-beta.2"))
        assertTrue(v("1.0.0-beta.10") > v("1.0.0-beta.2"))
        assertTrue(v("1.0.0-beta") < v("1.0.0-beta.1"))
        assertTrue(v("1.0.0-alpha.9") < v("1.0.0-beta"))
        assertEquals(0, v("1.2.3+build.5").compareTo(v("1.2.3")))
    }

    @Test fun parsing() {
        assertEquals(SemVer(1, 2, 3), SemVer.parse("v1.2.3"))
        assertNull(SemVer.parse("garbage"))
        assertNull(SemVer.parse("1.2"))
        assertNull(SemVer.parse("01.2.3"))
    }
}

class UpdateCheckerTest {
    private class FixedClock(var now: Instant) : Clock { override fun now() = now }

    private fun release(tag: String) = MockResponse().setBody(
        """{"tag_name":"$tag","html_url":"https://github.com/velosako3/blossom/releases/tag/$tag",
           "assets":[{"name":"blossom-$tag.apk","browser_download_url":"https://github.com/velosako3/blossom/releases/download/$tag/blossom.apk"},
                     {"name":"blossom-$tag.msi","browser_download_url":"https://example.com/x.msi"}]}""")

    @Test fun newerReleaseOncePerDay(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(release("v1.1.0")); server.start()
            val clock = FixedClock(Instant.fromEpochSeconds(1_800_000_000))
            val prefs = MapPreferenceStore()
            val checker = UpdateChecker("1.0.0", android = false, prefs, clock, url = server.url("/latest").toString())
            val u = checker.check()!!
            assertEquals("1.1.0", u.version.toString())
            assertEquals("https://github.com/velosako3/blossom/releases/tag/v1.1.0", u.url)
            clock.now += 2.hours
            assertEquals(u, checker.check(), "the day's answer is remembered")
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun androidLinksTheApk(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(release("v2.0.0")); server.start()
            val u = UpdateChecker("1.0.0", android = true, MapPreferenceStore(), Clock.System, url = server.url("/latest").toString()).check()!!
            assertEquals("https://github.com/velosako3/blossom/releases/download/v2.0.0/blossom.apk", u.url)
        }
    }

    @Test fun sameOlderOrGarbageShowsNothing(): Unit = runBlocking {
        for (tag in listOf("v1.0.0", "v0.9.0", "nightly")) {
            MockWebServer().use { server ->
                server.enqueue(release(tag)); server.start()
                assertNull(UpdateChecker("1.0.0", android = false, MapPreferenceStore(), Clock.System, url = server.url("/latest").toString()).check(), tag)
            }
        }
    }

    @Test fun offMeansNoRequest(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val prefs = MapPreferenceStore().apply { put(UpdateChecker.ENABLED_KEY, "false") }
            assertNull(UpdateChecker("1.0.0", android = false, prefs, Clock.System, url = server.url("/latest").toString()).check())
            assertEquals(0, server.requestCount)
        }
    }
}
