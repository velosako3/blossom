// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import io.github.velosako3.blossom.feature.sync.lan.PairingPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class SyncScreensTest {
    private val now = Instant.parse("2026-11-22T12:00:00Z")

    @Composable private fun Themed(content: @Composable () -> Unit) =
        BlossomTheme(true, ArtworkPalette.Default, glassQuality = GlassQuality.FULL, reduceMotion = true) {
            Box(Modifier.fillMaxSize()) {
                BlossomBackground(ArtworkPalette.Default, Modifier.fillMaxSize().glassBackdropSource())
                content()
            }
        }

    @Test fun syncSettings() = runDesktopComposeUiTest(412, 915) {
        val state = SyncUi(
            backend = BackendConfig(BackendKind.WEBDAV, "https://dav.example.com/blossom", "velo"),
            deviceName = "Velo's PC", lastSync = now - 2.minutes, error = "server unreachable",
            devices = listOf(
                DeviceRow("A", "Velo's PC", null, paired = false, isThis = true),
                DeviceRow("B", "Pixel 9", now - 3.hours, paired = true, isThis = false),
                DeviceRow("C", "Laptop", now - 50.hours, paired = false, isThis = false),
            ),
        )
        val retried = mutableListOf<String>()
        setContent {
            Themed {
                SyncSettingsScreen(state, now = now, availableKinds = BackendKind.entries, onChoose = { _, _ -> }, onDisconnect = {},
                    onSyncNow = { retried += "sync" }, onRename = {}, onRemoveDevice = { retried += it }, onPair = {})
            }
        }
        waitForIdle()
        onRoot().captureRoboImage("src/desktopTest/snapshots/sync_settings.png")
        onNodeWithText("Last synced 2 min ago").assertExists()
        onNodeWithText("seen 3 h ago", substring = true).assertExists()
        onNodeWithText("Try again").performClick()
        assertEquals(listOf("sync"), retried)
    }

    @Test fun pairing() = runDesktopComposeUiTest(412, 915) {
        val payload = PairingPayload(deviceId = "A", deviceName = "Velo's PC", hosts = listOf("192.168.1.20"), port = 53683, secret = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
        setContent {
            Themed {
                PairDeviceScreen(
                    PairUi(payload = payload, words = listOf("bada", "fehi", "jolo", "mipu", "sava", "zezo")),
                    canScan = false, onScanned = {}, onWords = {},
                )
            }
        }
        waitForIdle()
        onRoot().captureRoboImage("src/desktopTest/snapshots/sync_pair.png")
        onNodeWithText("bada fehi jolo mipu sava zezo").assertExists()
    }
}
