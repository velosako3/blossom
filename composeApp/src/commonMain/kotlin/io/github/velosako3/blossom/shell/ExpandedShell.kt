// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.composeApp.resources.Res
import io.github.velosako3.blossom.composeApp.resources.app_name
import io.github.velosako3.blossom.composeApp.resources.no_playlists_yet
import io.github.velosako3.blossom.composeApp.resources.nothing_playing
import io.github.velosako3.blossom.composeApp.resources.nav_playlists
import io.github.velosako3.blossom.composeApp.resources.panel_lyrics
import io.github.velosako3.blossom.composeApp.resources.panel_queue
import io.github.velosako3.blossom.core.designsystem.components.GlassChip
import io.github.velosako3.blossom.core.designsystem.components.GlassIconButton
import io.github.velosako3.blossom.core.designsystem.components.GlassSidebar
import io.github.velosako3.blossom.core.designsystem.glass.glassPageSource
import io.github.velosako3.blossom.core.designsystem.components.GlassSidebarItem
import io.github.velosako3.blossom.core.designsystem.glass.GlassSurface
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.designsystem.theme.motionSpec
import io.github.velosako3.blossom.navigation.BlossomNavHost
import io.github.velosako3.blossom.navigation.Navigator
import org.jetbrains.compose.resources.stringResource

enum class RightPanel { QUEUE, LYRICS }

/** UI state shared between the shell and the desktop keyboard shortcuts. */
class ShellState {
    var rightPanel by mutableStateOf<RightPanel?>(null)
    /** Bumped by Ctrl+F; the search screen focuses its field whenever this changes. */
    var searchFocusRequests by mutableStateOf(0)
    fun toggle(p: RightPanel) { rightPanel = if (rightPanel == p) null else p }
}

/**
 * Desktop and tablets: a 260 dp glass sidebar (navigation + playlists), the content, an optional 360 dp
 * Queue/Lyrics panel on the right, and a full-width 88 dp glass player bar along the bottom.
 */
@Composable
fun ExpandedShell(navigator: Navigator, slots: ShellSlots, modifier: Modifier = Modifier, shell: ShellState = remember { ShellState() }) {
    val panel = shell.rightPanel
    // The player bar floats over the page, which runs under it so its glass has something to bend. Now playing has its
    // own controls, so the bar steps aside there (it covered them on shorter windows).
    val immersive = navigator.current == io.github.velosako3.blossom.navigation.Destination.NowPlaying
    val barSpace = if (immersive) 0.dp else PLAYER_BAR_HEIGHT + 12.dp
    Box(modifier.fillMaxSize().padding(12.dp)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassSidebar(Modifier.testTag(SIDEBAR_TAG).padding(bottom = barSpace)) {
                Text(
                    stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 12.dp),
                )
                Column(Modifier.selectableGroup()) {
                    expandedEntries.forEach { e ->
                        GlassSidebarItem(
                            label = stringResource(e.label),
                            selected = navigator.topLevel == e.destination,
                            onClick = { navigator.navigate(e.destination) },
                            icon = if (navigator.topLevel == e.destination) e.selectedIcon else e.icon,
                        )
                    }
                }
                Text(
                    stringResource(Res.string.nav_playlists),
                    style = MaterialTheme.typography.labelMedium,
                    color = BlossomTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 6.dp),
                )
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    slots.sidebarPlaylists?.invoke() ?: Text(
                        stringResource(Res.string.no_playlists_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = BlossomTheme.colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
            androidx.compose.runtime.CompositionLocalProvider(io.github.velosako3.blossom.core.designsystem.glass.LocalBarsPadding provides barSpace) {
                BlossomNavHost(navigator, Modifier.weight(1f).fillMaxHeight().glassPageSource(), screen = slots.screen)
            }
            AnimatedVisibility(panel != null, enter = expandHorizontally(motionSpec(spring<IntSize>())), exit = shrinkHorizontally(motionSpec(spring<IntSize>()))) {
                GlassSurface(Modifier.width(360.dp).fillMaxHeight().padding(bottom = barSpace)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassChip(panel == RightPanel.QUEUE, { shell.rightPanel = RightPanel.QUEUE }, stringResource(Res.string.panel_queue))
                            GlassChip(panel == RightPanel.LYRICS, { shell.rightPanel = RightPanel.LYRICS }, stringResource(Res.string.panel_lyrics))
                        }
                        Spacer(Modifier.height(12.dp))
                        when (panel) {
                            RightPanel.QUEUE -> slots.queuePanel?.invoke()
                            RightPanel.LYRICS -> slots.lyricsPanel?.invoke()
                            null -> Unit
                        }
                    }
                }
            }
        }
        if (!immersive) Box(Modifier.align(Alignment.BottomCenter)) {
            slots.playerBar?.invoke(shell) ?: PlaceholderPlayerBar(
                onToggleQueue = { shell.toggle(RightPanel.QUEUE) },
                onToggleLyrics = { shell.toggle(RightPanel.LYRICS) },
            )
        }
    }
}

private val PLAYER_BAR_HEIGHT = 88.dp

@Composable
private fun PlaceholderPlayerBar(onToggleQueue: () -> Unit, onToggleLyrics: () -> Unit) {
    GlassSurface(Modifier.fillMaxWidth().height(88.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.nothing_playing),
                style = MaterialTheme.typography.titleMedium,
                color = BlossomTheme.colors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            GlassIconButton(onToggleLyrics, Icons.Rounded.Lyrics, stringResource(Res.string.panel_lyrics), glass = false)
            GlassIconButton(onToggleQueue, Icons.Rounded.QueueMusic, stringResource(Res.string.panel_queue), glass = false)
        }
    }
}

/** Tablets in portrait / narrow windows: a slim glass navigation rail. */
@Composable
fun MediumShell(navigator: Navigator, slots: ShellSlots, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassSurface(Modifier.width(88.dp).fillMaxHeight()) {
            Column(
                Modifier.fillMaxHeight().padding(vertical = 16.dp).selectableGroup(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                expandedEntries.forEach { e ->
                    val selected = navigator.topLevel == e.destination
                    val tint = if (selected) BlossomTheme.colors.accent else BlossomTheme.colors.onSurfaceVariant
                    Column(
                        Modifier.width(72.dp)
                            .selectable(selected, role = Role.Tab) { navigator.navigate(e.destination) }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.then(
                                if (selected) Modifier.padding(0.dp) else Modifier,
                            ),
                        ) { Icon(if (selected) e.selectedIcon else e.icon, null, tint = tint) }
                        Text(stringResource(e.label), style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
                    }
                }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BlossomNavHost(navigator, Modifier.weight(1f).fillMaxWidth(), screen = slots.screen)
            slots.miniPlayer?.invoke()
        }
    }
}

