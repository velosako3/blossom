// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.navigation

import androidx.compose.runtime.mutableStateListOf

sealed interface Destination {
    /** Top-level destinations reset the back stack when selected. */
    sealed interface TopLevel : Destination

    data object Home : TopLevel
    data object Search : TopLevel
    data object Library : TopLevel
    data object Stats : TopLevel
    data object Settings : TopLevel

    data class Album(val id: String) : Destination
    data class Artist(val id: String) : Destination
    data class Playlist(val id: String) : Destination
    data class Import(val url: String? = null) : Destination
    data object NowPlaying : Destination
    /** [period] is a period key such as `week:2026-W47`, parsed by the stats feature. */
    data class Wrapped(val period: String) : Destination
    data object Liked : Destination
    data object LocalFiles : Destination
}

/** A plain back stack: enough for blossom's flat hierarchy, no navigation library needed. */
class Navigator(start: Destination = Destination.Home) {
    val backStack = mutableStateListOf(start)
    val current: Destination get() = backStack.last()
    val topLevel: Destination get() = backStack.first()
    val canGoBack: Boolean get() = backStack.size > 1

    fun navigate(destination: Destination) {
        if (destination is Destination.TopLevel) {
            backStack.clear()
            backStack.add(destination)
        } else if (current != destination) {
            backStack.add(destination)
        }
    }

    fun back(): Boolean {
        if (!canGoBack) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }
}
