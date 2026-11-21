// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.shell

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
import io.github.velosako3.blossom.navigation.Navigator
import org.jetbrains.compose.resources.stringResource

/** Phones: content full-bleed, a floating glass tab bar inset 12 dp, and the mini player above it. */
@Composable
fun CompactShell(navigator: Navigator, slots: ShellSlots, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        BlossomNavHost(navigator, Modifier.fillMaxSize().padding(bottom = 96.dp), showSettingsAction = true, screen = slots.screen)
        Column(
            Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(12.dp),
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
