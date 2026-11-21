// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import io.github.velosako3.blossom.core.player.BlossomPlayer
import io.github.velosako3.blossom.navigation.Destination
import io.github.velosako3.blossom.navigation.Navigator
import io.github.velosako3.blossom.shell.ShellState

/**
 * Desktop shortcuts. [preview] sees Ctrl combinations before any focused control; [bubble] sees plain keys only if
 * nothing focused (a text field) used them, so typing a space in search never pauses the music.
 */
class KeyboardShortcuts(
    private val player: BlossomPlayer,
    private val navigator: Navigator,
    private val shell: ShellState,
    private val searchFocus: FocusRequester,
    private val toggleLike: () -> Unit = {},
) {
    fun preview(e: KeyEvent): Boolean {
        if (e.type != KeyEventType.KeyDown || !e.isCtrlPressed) return false
        when (e.key) {
            Key.DirectionLeft -> player.previous()
            Key.DirectionRight -> player.next()
            Key.L -> toggleLike()
            Key.F -> { navigator.navigate(Destination.Search); shell.searchFocusRequests++ }
            Key.Comma -> navigator.navigate(Destination.Settings)
            else -> return false
        }
        return true
    }

    fun bubble(e: KeyEvent): Boolean {
        if (e.type != KeyEventType.KeyDown || e.isCtrlPressed) return false
        val state = player.state.value
        when (e.key) {
            Key.Spacebar -> if (state.isPlaying) player.pause() else player.play()
            Key.DirectionLeft -> player.seekTo((state.positionMs - SEEK_STEP_MS).coerceAtLeast(0))
            Key.DirectionRight -> player.seekTo((state.positionMs + SEEK_STEP_MS).let { if (state.durationMs > 0) it.coerceAtMost(state.durationMs) else it })
            Key.Escape -> when {
                navigator.current == Destination.NowPlaying -> navigator.back()
                shell.rightPanel != null -> shell.rightPanel = null
                else -> return navigator.back()
            }
            else -> return false
        }
        return true
    }

    private companion object {
        const val SEEK_STEP_MS = 5_000L
    }
}
