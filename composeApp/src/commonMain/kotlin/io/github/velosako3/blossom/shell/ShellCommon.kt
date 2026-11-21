// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.velosako3.blossom.composeApp.resources.Res
import io.github.velosako3.blossom.composeApp.resources.nav_home
import io.github.velosako3.blossom.composeApp.resources.nav_library
import io.github.velosako3.blossom.composeApp.resources.nav_search
import io.github.velosako3.blossom.composeApp.resources.nav_settings
import io.github.velosako3.blossom.composeApp.resources.nav_stats
import io.github.velosako3.blossom.core.designsystem.components.GLASS_NAV_ITEM_TAG
import io.github.velosako3.blossom.navigation.Destination
import org.jetbrains.compose.resources.StringResource

const val NAV_ITEM_TAG = GLASS_NAV_ITEM_TAG
const val SIDEBAR_TAG = "sidebar"

data class TopLevelEntry(val destination: Destination.TopLevel, val label: StringResource, val icon: ImageVector, val selectedIcon: ImageVector)

/** Bottom bar / rail entries. Settings lives in the Library top bar on compact and in the sidebar on wider layouts. */
val compactEntries = listOf(
    TopLevelEntry(Destination.Home, Res.string.nav_home, Icons.Outlined.Home, Icons.Rounded.Home),
    TopLevelEntry(Destination.Search, Res.string.nav_search, Icons.Rounded.Search, Icons.Rounded.Search),
    TopLevelEntry(Destination.Library, Res.string.nav_library, Icons.Outlined.LibraryMusic, Icons.Rounded.LibraryMusic),
    TopLevelEntry(Destination.Stats, Res.string.nav_stats, Icons.Outlined.QueryStats, Icons.Rounded.QueryStats),
)

val expandedEntries = compactEntries +
    TopLevelEntry(Destination.Settings, Res.string.nav_settings, Icons.Outlined.Settings, Icons.Rounded.Settings)

/** Slots the features fill in later milestones. */
class ShellSlots(
    val miniPlayer: (@androidx.compose.runtime.Composable () -> Unit)? = null,
    val playerBar: (@androidx.compose.runtime.Composable (ShellState) -> Unit)? = null,
    val queuePanel: (@androidx.compose.runtime.Composable () -> Unit)? = null,
    val lyricsPanel: (@androidx.compose.runtime.Composable () -> Unit)? = null,
    val sidebarPlaylists: (@androidx.compose.runtime.Composable () -> Unit)? = null,
    val screen: @androidx.compose.runtime.Composable (Destination) -> Boolean = { false },
)
