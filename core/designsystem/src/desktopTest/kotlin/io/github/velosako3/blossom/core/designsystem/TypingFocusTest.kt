// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette
import io.github.velosako3.blossom.core.designsystem.components.GlassTextField
import io.github.velosako3.blossom.core.designsystem.components.TypingFocus
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The desktop shortcuts leave Space alone while a text field has the keyboard: the field must say so. */
@OptIn(ExperimentalTestApi::class)
class TypingFocusTest {
    @Test fun aFocusedTextFieldIsTyping() = runComposeUiTest {
        val other = FocusRequester()
        var shown by mutableStateOf(true)
        setContent {
            BlossomTheme(true, ArtworkPalette.Default, glassQuality = GlassQuality.OFF) {
                Column {
                    var text by remember { mutableStateOf("") }
                    if (shown) GlassTextField(text, { text = it }, placeholder = "Search")
                    Text("elsewhere", Modifier.focusRequester(other).focusable())
                }
            }
        }
        onNode(androidx.compose.ui.test.hasSetTextAction()).performClick()
        waitForIdle()
        assertTrue(TypingFocus.active, "focused field")
        runOnIdle { other.requestFocus() }
        waitForIdle()
        assertFalse(TypingFocus.active, "focus moved on")
        onNode(androidx.compose.ui.test.hasSetTextAction()).performClick()
        waitForIdle()
        shown = false // the screen closes while typing
        waitForIdle()
        assertFalse(TypingFocus.active, "field gone")
    }
}
