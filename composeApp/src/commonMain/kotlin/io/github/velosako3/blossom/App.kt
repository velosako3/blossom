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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import io.github.velosako3.blossom.core.designsystem.components.rememberArtworkPalette
import io.github.velosako3.blossom.shell.ShellState
import androidx.compose.ui.Modifier
import io.github.velosako3.blossom.core.designsystem.background.BlossomBackground
import io.github.velosako3.blossom.feature.nowplaying.PlaybackErrorBanner
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
    var compactLayout by remember { mutableStateOf(false) }
    val artworkPalette = rememberArtworkPalette(current?.artworkUrl)
    val activePalette = if (current != null) artworkPalette else palette
    val slots = if (graph != null) appSlots(graph, navigator, searchFocus, hasTrack = current != null, shellState = shellState, compact = compactLayout) else remember { ShellSlots() }
    val darkTheme = resolveDarkTheme(themeMode, isSystemInDarkTheme())
    BlossomTheme(darkTheme = darkTheme, palette = activePalette, glassQuality = glassQuality, reduceMotion = reduceMotion) {
        Box(Modifier.fillMaxSize()) {
            BlossomBackground(activePalette, Modifier.fillMaxSize().glassBackdropSource())
            Column(Modifier.fillMaxSize()) {
                titleBar?.invoke()
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    slots.overlay.let { overlay -> Box(Modifier.fillMaxSize().zIndex(2f)) { overlay() } }
                    graph?.let { PlaybackErrorBanner(it.player, Modifier.align(androidx.compose.ui.Alignment.TopCenter).padding(top = 48.dp).zIndex(1f)) }
                    graph?.let { g ->
                        val online = g.network.status.collectAsState().value.online
                        val storagePaused = androidx.compose.runtime.remember(g.offline) { g.offline?.pausedForStorage() ?: kotlinx.coroutines.flow.flowOf(false) }.collectAsState(false).value
                        androidx.compose.foundation.layout.Column(
                            Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = 112.dp).zIndex(1f),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        ) {
                            if (!online) OfflineBanner()
                            if (storagePaused) StoragePausedBanner()
                        }
                    }
                    val layout = WindowLayout.from(maxWidth.value)
                    compactLayout = layout == WindowLayout.COMPACT
                    when (layout) {
                        WindowLayout.COMPACT -> CompactShell(navigator, slots)
                        WindowLayout.MEDIUM -> MediumShell(navigator, slots)
                        WindowLayout.EXPANDED -> ExpandedShell(navigator, slots, shell = shellState)
                    }
                }
            }
        }
    }
}
