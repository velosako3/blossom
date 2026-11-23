// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem

import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette
import io.github.velosako3.blossom.core.designsystem.components.GlassButton
import io.github.velosako3.blossom.core.designsystem.components.GlassIconButton
import io.github.velosako3.blossom.core.designsystem.components.GlassSlider
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GlassComponentsUiTest {
    @Test fun buttonClickInvokesOnce() = runComposeUiTest {
        var clicks = 0
        setContent { BlossomTheme(true, ArtworkPalette.Default, glassQuality = GlassQuality.FULL) { GlassButton(onClick = { clicks++ }) { Text("Tap me") } } }
        onNodeWithText("Tap me").performClick()
        waitForIdle()
        assertEquals(1, clicks)
    }

    @Test fun iconButtonHasDescriptionAndButtonRole() = runComposeUiTest {
        setContent { BlossomTheme(true, ArtworkPalette.Default, glassQuality = GlassQuality.FULL) { GlassIconButton({}, Icons.Rounded.PlayArrow, "Play") } }
        onNodeWithContentDescription("Play").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test fun sliderDragToEndReachesOne() = runComposeUiTest {
        var last = -1f
        setContent {
            BlossomTheme(true, ArtworkPalette.Default, glassQuality = GlassQuality.FULL) {
                GlassSlider(0f, { last = it }, Modifier.width(300.dp).testTag("slider"))
            }
        }
        onNodeWithTag("slider").performTouchInput { swipeRight(startX = left, endX = right) }
        waitForIdle()
        assertTrue(last in 0.99f..1.0f, "last=$last")
    }
}
