// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import androidx.compose.ui.window.application
import io.github.velosako3.blossom.window.BlossomWindow

fun main() = application {
    BlossomWindow { titleBar ->
        App(titleBar = titleBar)
    }
}
