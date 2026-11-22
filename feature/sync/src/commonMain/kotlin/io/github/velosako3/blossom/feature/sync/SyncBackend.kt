// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync

import kotlin.time.Instant

data class RemoteFile(val path: String, val sizeBytes: Long, val modifiedAt: Instant?, val etag: String?)

/** A shared place every device reads and writes sync files in: a folder, WebDAV, Google Drive, Dropbox. */
interface SyncBackend {
    val id: String
    /** Files whose path starts with [prefix] (recursively). */
    suspend fun list(prefix: String): List<RemoteFile>
    suspend fun read(path: String): ByteArray
    suspend fun write(path: String, bytes: ByteArray)
}

/** Settings that follow you between devices. Never secrets, never device-specific values (folders, offline limits). */
object SyncedSettings {
    val keys: Set<String> = setOf(
        "theme", "reduceMotion", "glassQuality", "crossfadeSeconds", "gaplessPlayback",
        "lyricsReduceMotion", "autoplaySimilar", "untitledAutoSaveOffline", "streamQuality",
    )
}
