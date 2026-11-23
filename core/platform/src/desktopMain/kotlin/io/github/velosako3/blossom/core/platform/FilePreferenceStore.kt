// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.platform

import java.io.File
import java.util.Properties

/** A properties file, rewritten atomically after every change on a background writer (never on the UI thread). */
class FilePreferenceStore(file: File) : MapPreferenceStore(load(file), { map -> writer.execute { save(file, map) } }) {
    private companion object {
        val writer: java.util.concurrent.ExecutorService = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "blossom-prefs").apply { isDaemon = true } }

        fun load(file: File): Map<String, String> =
            if (!file.isFile) emptyMap() else Properties().apply { file.inputStream().use(::load) }.let { p -> p.stringPropertyNames().associateWith { p.getProperty(it) } }

        fun save(file: File, map: Map<String, String>) {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.outputStream().use { out -> Properties().apply { putAll(map) }.store(out, null) }
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }
}
