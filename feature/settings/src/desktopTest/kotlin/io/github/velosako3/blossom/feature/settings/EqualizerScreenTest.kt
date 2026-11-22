// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import io.github.takahirom.roborazzi.captureRoboImage
import io.github.velosako3.blossom.core.designsystem.background.BlossomBackground
import io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.core.designsystem.glass.glassBackdropSource
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.player.eq.EqPreset
import io.github.velosako3.blossom.core.player.eq.EqSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class EqualizerScreenTest {
    @Test fun presetsAndSnapshot() = runDesktopComposeUiTest(412, 915) {
        var eq by mutableStateOf(EqSettings())
        setContent {
            BlossomTheme(true, ArtworkPalette.Default, glassQuality = GlassQuality.FULL, reduceMotion = true) {
                Box(Modifier.fillMaxSize()) {
                    BlossomBackground(ArtworkPalette.Default, Modifier.fillMaxSize().glassBackdropSource())
                    EqualizerScreen(eq, { eq = it })
                }
            }
        }
        onNodeWithText("Bass boost").performClick()
        assertTrue(eq.enabled, "choosing a preset turns the equalizer on")
        assertEquals(EqPreset.BASS_BOOST.gains.toList(), eq.gains)
        waitForIdle()
        onRoot().captureRoboImage("src/desktopTest/snapshots/equalizer.png")
    }
}
