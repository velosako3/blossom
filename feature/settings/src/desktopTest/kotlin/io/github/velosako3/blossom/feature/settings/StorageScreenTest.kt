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
import io.github.velosako3.blossom.core.offline.OfflineSettings
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class StorageScreenTest {
    @Test fun removeAllAsksFirstAndSnapshot() = runDesktopComposeUiTest(412, 915) {
        var removed = 0
        var settings by mutableStateOf(OfflineSettings(wifiOnly = true, limitBytes = 5_000_000_000))
        setContent {
            BlossomTheme(true, ArtworkPalette.Default, glassQuality = GlassQuality.FULL, reduceMotion = true) {
                Box(Modifier.fillMaxSize()) {
                    BlossomBackground(ArtworkPalette.Default, Modifier.fillMaxSize().glassBackdropSource())
                    StorageScreen(1_840_000_000, settings, { change -> settings = change(settings) }, { removed++ })
                }
            }
        }
        waitForIdle()
        onRoot().captureRoboImage("src/desktopTest/snapshots/storage_compact.png")
        onNodeWithText("10 GB").performClick()
        assertEquals(10_000_000_000, settings.limitBytes)
        onNodeWithText("Remove all saved music").performClick()
        assertEquals(0, removed)
        onNodeWithText("Remove").performClick()
        assertEquals(1, removed)
    }

    @Test fun bytes() {
        assertEquals("1.8 GB", formatBytes(1_840_000_000))
        assertEquals("340 MB", formatBytes(340_000_000))
    }
}
