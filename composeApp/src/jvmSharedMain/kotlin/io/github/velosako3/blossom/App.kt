// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.background
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
    // With a graph, the user's choices (Settings → Appearance) win; the parameters are the platform defaults.
    val themeMode = graph?.preferences?.theme?.state?.collectAsState()?.value ?: themeMode
    graph?.preferences?.systemReduceMotion = reduceMotion
    val reduceMotion = graph?.preferences?.reduceMotion?.state?.collectAsState()?.value ?: reduceMotion
    // A link shared to blossom opens Import with it.
    graph?.let { g ->
        val shared = g.pendingImport.collectAsState().value
        androidx.compose.runtime.LaunchedEffect(shared) {
            if (shared != null) { navigator.navigate(io.github.velosako3.blossom.navigation.Destination.Import(shared)); g.pendingImport.value = null }
        }
    }
    var compactLayout by remember { mutableStateOf(false) }
    val artworkPalette = rememberArtworkPalette(current?.artworkUrl)
    val activePalette = if (current != null) artworkPalette else palette
    val slots = if (graph != null) appSlots(graph, navigator, searchFocus, hasTrack = current != null, shellState = shellState, compact = compactLayout) else remember { ShellSlots() }
    val darkTheme = resolveDarkTheme(themeMode, isSystemInDarkTheme())
    val glassPreference = graph?.preferences?.glass?.state?.collectAsState()?.value ?: io.github.velosako3.blossom.core.designsystem.glass.GlassPreference.AUTO
    BlossomTheme(darkTheme = darkTheme, palette = activePalette, glassPreference = glassPreference, glassQuality = glassQuality, reduceMotion = reduceMotion) {
        Box(Modifier.fillMaxSize()) {
            BlossomBackground(activePalette, Modifier.fillMaxSize().glassBackdropSource())
            Column(Modifier.fillMaxSize()) {
                titleBar?.invoke()
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    slots.overlay.let { overlay -> Box(Modifier.fillMaxSize().zIndex(2f)) { overlay() } }
                    graph?.let { PlaybackErrorBanner(it.player, Modifier.align(androidx.compose.ui.Alignment.TopCenter).padding(top = 48.dp).zIndex(1f)) }
                    graph?.update?.collectAsState()?.value?.let { u ->
                        UpdateChip(u, graph?.updateInstaller, Modifier.align(androidx.compose.ui.Alignment.TopEnd).padding(top = 8.dp, end = 16.dp).zIndex(1f))
                    }
                    graph?.let { g ->
                        val online = g.network.status.collectAsState().value.online
                        // A notice, not a fixture: shown for a few seconds after going offline, so it never sits over
                        // the player. Screens say themselves what offline changes (Home, Search).
                        var offlineNotice by remember { mutableStateOf(false) }
                        androidx.compose.runtime.LaunchedEffect(online) {
                            offlineNotice = !online
                            if (!online) { kotlinx.coroutines.delay(OFFLINE_NOTICE_MS); offlineNotice = false }
                        }
                        val storagePaused = androidx.compose.runtime.remember(g.offline) { g.offline?.pausedForStorage() ?: kotlinx.coroutines.flow.flowOf(false) }.collectAsState(false).value
                        androidx.compose.foundation.layout.Column(
                            Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = 112.dp).zIndex(1f),
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        ) {
                            if (offlineNotice) OfflineBanner()
                            if (storagePaused) StoragePausedBanner()
                        }
                    }
                    // First run: the welcome, over everything (skippable).
                    graph?.let { g ->
                        if (!g.preferences.onboarded.state.collectAsState().value) {
                            // A scrim keeps the welcome card readable over a busy home feed.
                            Box(
                                Modifier.fillMaxSize().zIndex(3f).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
                                    // Taps outside the card don't reach the app underneath.
                                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
                            ) {
                                io.github.velosako3.blossom.feature.settings.onboarding.OnboardingFlow(
                                    g.preferences.theme.state.collectAsState().value, g.preferences.theme::set,
                                    onFinish = { extra ->
                                        g.preferences.onboarded.set(true)
                                        when (extra) {
                                            io.github.velosako3.blossom.feature.settings.onboarding.OnboardingExtra.LOCAL_FILES -> navigator.navigate(io.github.velosako3.blossom.navigation.Destination.LocalFiles)
                                            io.github.velosako3.blossom.feature.settings.onboarding.OnboardingExtra.SYNC -> navigator.navigate(io.github.velosako3.blossom.navigation.Destination.Sync)
                                            io.github.velosako3.blossom.feature.settings.onboarding.OnboardingExtra.IMPORT -> navigator.navigate(io.github.velosako3.blossom.navigation.Destination.Import())
                                            null -> Unit
                                        }
                                    },
                                )
                            }
                        }
                    }
                    val layout = WindowLayout.from(maxWidth.value)
                    compactLayout = layout == WindowLayout.COMPACT
                    // While the welcome is up, screen readers see only it.
                    val welcoming = graph != null && !graph.preferences.onboarded.state.collectAsState().value
                    Box(if (welcoming) Modifier.fillMaxSize().clearAndSetSemantics {} else Modifier.fillMaxSize()) {
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
}

private const val OFFLINE_NOTICE_MS = 6_000L
