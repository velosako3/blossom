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

    private val built = Instant.parse("2026-11-22T12:00:00Z").toEpochMilliseconds()

    private fun releases(vararg r: Triple<String, String, Boolean>) = MockResponse().setBody(
        r.joinToString(",", "[", "]") { (commit, published, pre) ->
            """{"tag_name":"test-$commit","name":"build $commit","draft":false,"prerelease":$pre,"target_commitish":"$commit",
               "published_at":"$published","html_url":"https://github.com/velosako3/blossom/releases/tag/test-$commit",
               "assets":[{"name":"blossom-android-armeabi-v7a.apk","browser_download_url":"https://x/armeabi.apk"},
                         {"name":"blossom-android-arm64-v8a.apk","browser_download_url":"https://x/arm64.apk"},
                         {"name":"blossom-android-universal.apk","browser_download_url":"https://x/universal.apk"},
                         {"name":"blossom-linux-x86_64.AppImage","browser_download_url":"https://x/app.AppImage"}]}"""
        },
    )

    private fun checker(server: MockWebServer, prefs: MapPreferenceStore = MapPreferenceStore(), clock: Clock = Clock.System, commit: String = "aaa", abi: String = "arm64-v8a") =
        UpdateChecker(commit, built, UpdateChecker.androidApk(abi), prefs, clock, url = server.url("/releases").toString())

    @Test fun aNewerPreReleaseFromAnotherCommitIsAnUpdate(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(releases(Triple("bbb", "2026-11-23T00:50:00Z", true), Triple("aaa", "2026-11-22T13:00:00Z", true))); server.start()
            val u = checker(server).check()!!
            assertEquals("build bbb", u.label)
            assertEquals("https://x/arm64.apk", u.assetUrl, "this device's ABI")
        }
    }

    @Test fun ownReleaseOrOlderIsNot(): Unit = runBlocking {
        for (newest in listOf(Triple("aaa", "2026-11-23T00:50:00Z", true), Triple("ccc", "2026-11-21T09:00:00Z", false))) {
            MockWebServer().use { server ->
                server.enqueue(releases(newest)); server.start()
                assertNull(checker(server).check(), newest.toString())
            }
        }
    }

    @Test fun universalWhenThereIsNoBuildForThisAbi(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(releases(Triple("bbb", "2026-11-23T00:50:00Z", true))); server.start()
            assertEquals("https://x/universal.apk", checker(server, abi = "x86").check()!!.assetUrl)
        }
    }

    @Test fun askedAtMostOnceAnHour(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(releases(Triple("bbb", "2026-11-23T00:50:00Z", true))); server.enqueue(releases()); server.start()
            val clock = FixedClock(Instant.parse("2026-11-23T01:00:00Z"))
            val prefs = MapPreferenceStore()
            val first = checker(server, prefs, clock).check()
            clock.now += kotlin.time.Duration.parse("30m")
            assertEquals(first, checker(server, prefs, clock).check(), "remembered")
            assertEquals(1, server.requestCount)
            clock.now += 1.hours
            assertNull(checker(server, prefs, clock).check())
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun offOrLocalBuildMeansNoRequest(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val off = MapPreferenceStore().apply { put(UpdateChecker.ENABLED_KEY, "false") }
            assertNull(checker(server, off).check())
            assertNull(checker(server, commit = "").check())
            assertEquals(0, server.requestCount)
        }
    }
}
