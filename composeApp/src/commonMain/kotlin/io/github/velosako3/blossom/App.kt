// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import io.github.velosako3.blossom.core.designsystem.components.rememberArtworkPalette
import io.github.velosako3.blossom.shell.ShellState
import androidx.compose.ui.Modifier
import io.github.velosako3.blossom.core.designsystem.background.BlossomBackground
import io.github.velosako3.blossom.core.designsystem.color.ArtworkPalette
import io.github.velosako3.blossom.core.designsystem.glass.GlassQuality
import io.github.velosako3.blossom.core.designsystem.glass.glassBackdropSource
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.designsystem.theme.ThemeMode
import io.github.velosako3.blossom.core.designsystem.theme.resolveDarkTheme
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
    themeMode: ThemeMode = ThemeMode.DARK,
    palette: ArtworkPalette = ArtworkPalette.Default,
    reduceMotion: Boolean = false,
    glassQuality: GlassQuality? = null,
    graph: AppGraph? = null,
    shellState: ShellState = remember { ShellState() },
    searchFocus: FocusRequester = remember { FocusRequester() },
    titleBar: (@Composable () -> Unit)? = null,
) {
    val current = graph?.player?.state?.collectAsState()?.value?.current
    val artworkPalette = rememberArtworkPalette(current?.artworkUrl)
    val activePalette = if (current != null) artworkPalette else palette
    val slots = if (graph != null) appSlots(graph, navigator, searchFocus, hasTrack = current != null) else remember { ShellSlots() }
    val darkTheme = resolveDarkTheme(themeMode, isSystemInDarkTheme())
    BlossomTheme(darkTheme = darkTheme, palette = activePalette, glassQuality = glassQuality, reduceMotion = reduceMotion) {
        Box(Modifier.fillMaxSize()) {
            BlossomBackground(activePalette, Modifier.fillMaxSize().glassBackdropSource())
            Column(Modifier.fillMaxSize()) {
                titleBar?.invoke()
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    when (WindowLayout.from(maxWidth.value)) {
                        WindowLayout.COMPACT -> CompactShell(navigator, slots)
                        WindowLayout.MEDIUM -> MediumShell(navigator, slots)
                        WindowLayout.EXPANDED -> ExpandedShell(navigator, slots, shell = shellState)
                    }
                }
            }
        }
    }
}
