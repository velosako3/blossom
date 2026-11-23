// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.designsystem.theme.ThemeMode
import io.github.velosako3.blossom.core.designsystem.theme.motionSpec
import io.github.velosako3.blossom.core.designsystem.theme.resolveDarkTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class MotionTest {
    @Test fun reduceMotionMakesAnimationsInstant() = runComposeUiTest {
        mainClock.autoAdvance = false
        val target = mutableStateOf(Color.Red)
        var seen = Color.Unspecified
        setContent {
            BlossomTheme(true, ArtworkPalette.Default, glassQuality = GlassQuality.FULL, reduceMotion = true) {
                val c by animateColorAsState(target.value, motionSpec(tween(600)))
                seen = c
            }
        }
        target.value = Color.Blue
        mainClock.advanceTimeByFrame()
        mainClock.advanceTimeByFrame()
        assertEquals(Color.Blue, seen)
    }

    @Test fun themeModeResolution() {
        assertEquals(true, resolveDarkTheme(ThemeMode.DARK, systemDark = false))
        assertEquals(false, resolveDarkTheme(ThemeMode.LIGHT, systemDark = true))
        assertEquals(true, resolveDarkTheme(ThemeMode.SYSTEM, systemDark = true))
        assertEquals(false, resolveDarkTheme(ThemeMode.SYSTEM, systemDark = false))
    }
}
