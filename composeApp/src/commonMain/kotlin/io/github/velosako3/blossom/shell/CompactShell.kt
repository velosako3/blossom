// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.components.GlassNavBar
import io.github.velosako3.blossom.core.designsystem.components.GlassNavItem
import io.github.velosako3.blossom.navigation.BlossomNavHost
import io.github.velosako3.blossom.navigation.Destination
import io.github.velosako3.blossom.navigation.Navigator
import org.jetbrains.compose.resources.stringResource

/** Phones: content full-bleed, a floating glass tab bar inset 12 dp, and the mini player above it. */
@Composable
fun CompactShell(navigator: Navigator, slots: ShellSlots, modifier: Modifier = Modifier) {
    // The full player covers the whole screen, tab bar included.
    val immersive = navigator.current == Destination.NowPlaying
    // Content ends above the floating bars: their measured height, which includes the system navigation bar and grows
    // with large text (a fixed guess left the end of long screens under the mini player on Android).
    val density = androidx.compose.ui.platform.LocalDensity.current
    var barsHeight by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(if (slots.miniPlayer != null) 168.dp else 96.dp) }
    Box(modifier.fillMaxSize()) {
        BlossomNavHost(
            navigator,
            Modifier.fillMaxSize().padding(bottom = if (immersive) 0.dp else barsHeight),
            showSettingsAction = true,
            screen = slots.screen,
        )
        if (!immersive) Column(
            Modifier.align(Alignment.BottomCenter)
                .onSizeChanged { barsHeight = with(density) { it.height.toDp() } }
                .windowInsetsPadding(WindowInsets.navigationBars).padding(12.dp),
        ) {
            slots.miniPlayer?.let { Box(Modifier.padding(bottom = 8.dp)) { it() } }
            val items = compactEntries.map { GlassNavItem(stringResource(it.label), it.icon, it.selectedIcon) }
            GlassNavBar(
                items = items,
                selectedIndex = compactEntries.indexOfFirst { it.destination == navigator.topLevel },
                onSelect = { navigator.navigate(compactEntries[it].destination) },
            )
        }
    }
}
