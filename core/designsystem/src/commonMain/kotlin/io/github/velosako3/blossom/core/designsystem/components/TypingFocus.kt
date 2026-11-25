// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.components

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged

/**
 * Whether a text field has the keyboard. Desktop shortcuts (Space for play/pause, arrows to seek) leave keys alone
 * then: a text field takes the typed character but not the key press itself, which otherwise reached the shortcut.
 */
object TypingFocus {
    var active by mutableStateOf(false)
        internal set
}

/** Marks this text field for [TypingFocus]. */
fun Modifier.typingFocus(): Modifier = composed {
    val focused = remember { booleanArrayOf(false) }
    DisposableEffect(Unit) { onDispose { if (focused[0]) TypingFocus.active = false } }
    onFocusChanged { focused[0] = it.isFocused; TypingFocus.active = it.isFocused }
}
