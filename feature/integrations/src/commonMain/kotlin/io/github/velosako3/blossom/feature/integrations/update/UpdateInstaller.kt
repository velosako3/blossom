// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.integrations.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/** Installs an [Update] the platform's own way (the APK installer, the AppImage swapped in place, msiexec). */
fun interface UpdateInstaller {
    /** [progress] gets 0..1 while downloading. Throws when the download or install can't start. */
    suspend fun install(update: Update, progress: (Float) -> Unit)
}

/** Downloads [url] to [target] (through a temporary file beside it, so a broken download never replaces anything). */
suspend fun downloadUpdate(url: String, target: File, progress: (Float) -> Unit, http: OkHttpClient = OkHttpClient()) = withContext(Dispatchers.IO) {
    target.parentFile?.mkdirs()
    val part = File(target.parentFile, target.name + ".part")
    http.newCall(Request.Builder().url(url).header("User-Agent", "blossom").build()).execute().use { r ->
        check(r.isSuccessful) { "HTTP ${r.code}" }
        val total = r.body.contentLength()
        part.outputStream().use { out ->
            r.body.byteStream().use { input ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n); done += n
                    if (total > 0) progress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
    }
    check(part.renameTo(target) || (target.delete() && part.renameTo(target))) { "couldn't replace $target" }
}
