// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.github.velosako3.blossom.core.designsystem.background.BlossomBackground
import io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.core.designsystem.glass.glassBackdropSource
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.navigation.Navigator
import io.github.velosako3.blossom.navigation.WindowLayout
import io.github.velosako3.blossom.shell.CompactShell
import io.github.velosako3.blossom.shell.ExpandedShell
import io.github.velosako3.blossom.shell.MediumShell
import io.github.velosako3.blossom.shell.ShellSlots

/**
 * The whole app. [titleBar] is the desktop's custom glass title bar (null on Android or with system decorations).
 * [glassQuality] and [reduceMotion] are overridable for snapshot tests.
 */
@Composable
fun App(
    navigator: Navigator = remember { Navigator() },
    darkTheme: Boolean = true,
    palette: ArtworkPalette = ArtworkPalette.Default,
    reduceMotion: Boolean = false,
    glassQuality: GlassQuality? = null,
    slots: ShellSlots = remember { ShellSlots() },
    titleBar: (@Composable () -> Unit)? = null,
) {
    BlossomTheme(darkTheme = darkTheme, palette = palette, glassQuality = glassQuality, reduceMotion = reduceMotion) {
        Box(Modifier.fillMaxSize()) {
            BlossomBackground(palette, Modifier.fillMaxSize().glassBackdropSource())
            Column(Modifier.fillMaxSize()) {
                titleBar?.invoke()
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    when (WindowLayout.from(maxWidth.value)) {
                        WindowLayout.COMPACT -> CompactShell(navigator, slots)
                        WindowLayout.MEDIUM -> MediumShell(navigator, slots)
                        WindowLayout.EXPANDED -> ExpandedShell(navigator, slots)
                    }
                }
            }
        }
    }
}

/** System theme helper for the platform entry points. */
@Composable
fun systemDark(): Boolean = isSystemInDarkTheme()
