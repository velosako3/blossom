// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.takahirom.roborazzi.captureRoboImage
import io.github.velosako3.blossom.core.designsystem.background.BlossomBackground
import io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.core.designsystem.glass.glassBackdropSource
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.designsystem.theme.ThemeMode
import io.github.velosako3.blossom.feature.settings.onboarding.OnboardingExtra
import io.github.velosako3.blossom.feature.settings.onboarding.OnboardingFlow
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SettingsScreenTest {
    @Composable private fun Themed(content: @Composable () -> Unit) =
        BlossomTheme(true, ArtworkPalette.Default, glassQuality = GlassQuality.FULL, reduceMotion = true) {
            Box(Modifier.fillMaxSize()) {
                BlossomBackground(ArtworkPalette.Default, Modifier.fillMaxSize().glassBackdropSource())
                content()
            }
        }

    private class Recorder : SettingsActions {
        val calls = mutableListOf<String>()
        override fun theme(mode: ThemeMode) { calls += "theme:$mode" }
        override fun reduceMotion(on: Boolean) { calls += "motion:$on" }
        override fun crossfade(seconds: Int) { calls += "xf:$seconds" }
        override fun autoplay(on: Boolean) { calls += "autoplay:$on" }
        override fun untitledAutoSave(on: Boolean) { calls += "untitled:$on" }
        override fun clearLyrics() { calls += "lyrics" }
        override fun open(page: SettingsPage) { calls += "open:$page" }
        override fun signIn(account: SettingsAccount) { calls += "in:$account" }
        override fun signOut(account: SettingsAccount) { calls += "out:$account" }
        override fun startLastFm() { calls += "lastfm" }
        override fun finishLastFm() { calls += "lastfm-done" }
        override fun listenBrainzToken(token: String?) { calls += "lb:$token" }
        override fun updateCheck(on: Boolean) { calls += "update:$on" }
        override fun openUrl(url: String) { calls += "url:$url" }
    }

    @Test fun settingsHub() = runDesktopComposeUiTest(412, 915, testTimeout = kotlin.time.Duration.parse("3m")) {
        val actions = Recorder()
        setContent {
            Themed {
                SettingsScreen(SettingsUi(theme = ThemeMode.SYSTEM, crossfadeSeconds = 4, ytmSignedIn = true, spotifySignedIn = false, lastFmAvailable = true,
                    lastFmUser = "velo", discord = false, systemTitleBar = false, version = "0.9.0"), actions)
            }
        }
        waitForIdle()
        onRoot().captureRoboImage("src/desktopTest/snapshots/settings.png")
        onNodeWithText("Light").performClick()
        onNodeWithText("Keep playing similar songs").performScrollTo().performClick()
        onNodeWithText("Equalizer").performScrollTo().performClick()
        onNodeWithText("Open-source licenses").performScrollTo().performClick()
        assertEquals(listOf("theme:LIGHT", "autoplay:false", "open:EQUALIZER", "open:LICENSES"), actions.calls)
    }

    @Test fun onboarding() = runDesktopComposeUiTest(412, 915) {
        var finished: OnboardingExtra? = null
        var done = false
        var theme by mutableStateOf(ThemeMode.DARK)
        setContent { Themed { OnboardingFlow(theme, { theme = it }, { finished = it; done = true }) } }
        waitForIdle()
        onRoot().captureRoboImage("src/desktopTest/snapshots/onboarding_welcome.png")
        onNodeWithText("Get started").performClick(); waitForIdle()
        onNodeWithText("Light").performClick(); waitForIdle()
        assertEquals(ThemeMode.LIGHT, theme)
        onNodeWithText("Next").performClick(); waitForIdle()
        onRoot().captureRoboImage("src/desktopTest/snapshots/onboarding_extras.png")
        onNodeWithText("Sync with your other devices").performClick()
        assertEquals(OnboardingExtra.SYNC, finished)
        assertEquals(true, done)
    }
}
