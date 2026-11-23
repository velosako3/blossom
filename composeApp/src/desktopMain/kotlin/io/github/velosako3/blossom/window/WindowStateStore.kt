// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.window

import java.io.File
import java.util.Properties

/** Where blossom keeps its data on desktop: `%APPDATA%\blossom` on Windows, `$XDG_DATA_HOME/blossom` on Linux. */
fun desktopAppDataDir(): File {
    System.getProperty("blossom.dataDir")?.let { return File(it).apply { mkdirs() } }
    val os = System.getProperty("os.name").lowercase()
    val base = when {
        os.contains("win") -> System.getenv("APPDATA")?.let(::File) ?: File(System.getProperty("user.home"), "AppData/Roaming")
        else -> System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }?.let(::File)
            ?: File(System.getProperty("user.home"), ".local/share")
    }
    return File(base, "blossom").apply { mkdirs() }
}

/** Window size, position and maximized state, plus the "Use system title bar" setting (device-specific, never synced). */
data class SavedWindow(
    val widthDp: Int = 1280,
    val heightDp: Int = 800,
    val x: Int? = null,
    val y: Int? = null,
    val maximized: Boolean = false,
    val systemTitleBar: Boolean = false,
)

class WindowStateStore(private val file: File = File(desktopAppDataDir(), "window.properties")) {
    fun load(): SavedWindow {
        if (!file.isFile) return SavedWindow()
        val p = Properties().apply { file.inputStream().use(::load) }
        fun int(k: String) = p.getProperty(k)?.toIntOrNull()
        val d = SavedWindow()
        return SavedWindow(
            widthDp = int("width")?.coerceAtLeast(MIN_WIDTH) ?: d.widthDp,
            heightDp = int("height")?.coerceAtLeast(MIN_HEIGHT) ?: d.heightDp,
            x = int("x"),
            y = int("y"),
            maximized = p.getProperty("maximized") == "true",
            systemTitleBar = p.getProperty("systemTitleBar") == "true",
        )
    }

    fun save(w: SavedWindow) {
        val p = Properties()
        p["width"] = w.widthDp.toString(); p["height"] = w.heightDp.toString()
        w.x?.let { p["x"] = it.toString() }; w.y?.let { p["y"] = it.toString() }
        p["maximized"] = w.maximized.toString(); p["systemTitleBar"] = w.systemTitleBar.toString()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.outputStream().use { p.store(it, "blossom window state") }
        tmp.renameTo(file) || run { file.delete(); tmp.renameTo(file) }
    }

    companion object {
        const val MIN_WIDTH = 960
        const val MIN_HEIGHT = 600
    }
}
