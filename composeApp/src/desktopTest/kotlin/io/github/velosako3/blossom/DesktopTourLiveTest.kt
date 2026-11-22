// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.takahirom.roborazzi.captureRoboImage
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.navigation.Destination
import io.github.velosako3.blossom.navigation.Navigator
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

/**
 * The final hands-on pass: the real desktop app (live services, libVLC) off-screen, every screen visited from a
 * fresh start, onboarding included. Screenshots go to docs/screenshots/m9/desktop/. Live only.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopTourLiveTest {
    @Test fun everyScreen() = runDesktopComposeUiTest(1440, 900, testTimeout = 10.minutes) {
        resetTestDataDir()
        val graph = createDesktopGraph()
        graph.preferences.onboarded.set(false)
        val navigator = Navigator()
        setContent { App(navigator = navigator, graph = graph, glassQuality = GlassQuality.FULL, reduceMotion = true) }
        var n = 0
        fun shot(name: String) = onRoot().captureRoboImage("../docs/screenshots/m9/desktop/%02d_%s.png".format(++n, name))
        fun pump(ms: Long) { repeat((ms / 250).toInt().coerceAtLeast(1)) { mainClock.advanceTimeByFrame(); Thread.sleep(250) } }
        fun waitFor(timeoutMs: Long = 45_000, what: String, cond: () -> Boolean) {
            val end = System.currentTimeMillis() + timeoutMs
            while (!runCatching(cond).getOrDefault(false)) { check(System.currentTimeMillis() < end) { "timed out waiting for $what" }; pump(200) }
        }
        fun go(d: Destination, name: String, settle: Long = 2500) { navigator.navigate(d); pump(settle); shot(name) }
        mainClock.autoAdvance = false

        pump(1500); shot("onboarding")
        onAllNodesWithText("Skip")[0].performClick()
        waitFor(what = "home feed") { graph.home.state.value is io.github.velosako3.blossom.feature.home.HomeState.Loaded }
        pump(4000); shot("home")

        val songs = kotlinx.coroutines.runBlocking { graph.ytm.search("Daft Punk Around the World").songs }
        println("tour: ${songs.size} songs")
        kotlinx.coroutines.runBlocking { graph.player.setQueue(songs.take(5), 0); graph.player.play() }
        waitFor(what = "playback") { graph.player.state.value.positionMs > 3_000 }
        val song = graph.player.state.value.current!!
        println("tour playing: ${song.title} – ${song.artistNames}")
        go(Destination.NowPlaying, "now_playing_lyrics", 6000)
        go(Destination.Search, "search")
        song.album?.id?.let { go(Destination.Album(it), "album", 5000) }
        song.artists.firstOrNull()?.id?.let { go(Destination.Artist(it), "artist", 5000) }

        kotlinx.coroutines.runBlocking { graph.commands.like(song) }
        graph.library.createWith("Road trip", songs.take(5))
        waitFor(what = "playlist") { graph.library.playlists.value?.any { it.name == "Road trip" } == true }
        go(Destination.Library, "library")
        go(Destination.Playlist(graph.library.playlists.value!!.first { it.name == "Road trip" }.id.value), "playlist", 4000)
        go(Destination.Liked, "liked")
        go(Destination.LocalFiles, "local_files")
        go(Destination.Import(), "import")
        go(Destination.Stats, "stats")
        go(Destination.Settings, "settings") // the whole page is covered by SettingsScreenTest; scrolling here waits for idle
        go(Destination.Equalizer, "equalizer")
        go(Destination.Storage, "storage")
        go(Destination.Sync, "sync")
        go(Destination.PairDevice, "pair_device")
        go(Destination.SignIn(Account.YOUTUBE_MUSIC.name), "ytm_sign_in")
        go(Destination.Licenses, "licenses", 3000)
        graph.player.pause()
        println("tour: $n screenshots")
    }
}
