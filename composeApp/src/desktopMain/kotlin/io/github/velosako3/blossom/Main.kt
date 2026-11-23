// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.window.application
import io.github.velosako3.blossom.navigation.Navigator
import io.github.velosako3.blossom.shell.ShellState
import io.github.velosako3.blossom.window.BlossomWindow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

fun main(args: Array<String>) {
    if ("--self-check" in args) selfCheck()
    fewerMallocArenas()
    val graph = createDesktopGraph()
    application {
        val navigator = remember { Navigator() }
        val shell = remember { ShellState() }
        val searchFocus = remember { FocusRequester() }
        val shortcuts = remember {
            KeyboardShortcuts(graph.player, navigator, shell, searchFocus) {
                graph.player.state.value.current?.let { t ->
                    graph.library.toggleLike(t)
                }
            }
        }
        BlossomWindow(onPreviewKeyEvent = shortcuts::preview, onKeyEvent = shortcuts::bubble) { titleBar ->
            App(navigator = navigator, graph = graph, shellState = shell, searchFocus = searchFocus, titleBar = titleBar)
        }
    }
}

/**
 * glibc gives each thread that allocates its own malloc arena, and most of them stay nearly empty. MALLOC_ARENA_MAX=2
 * from the start cut startup anonymous memory from 445 MB to 359 MB; the AppImage's AppRun sets it. Elsewhere (deb,
 * MSI is not glibc) this limits the arenas of threads started from here on (Skia, VLC), which recovers less.
 */
private fun fewerMallocArenas() {
    if (!System.getProperty("os.name").orEmpty().startsWith("Linux")) return
    val mArenaMax = -8
    runCatching { com.sun.jna.NativeLibrary.getInstance("c").getFunction("mallopt").invokeInt(arrayOf(mArenaMax, 2)) }
}
