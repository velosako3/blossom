// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.backends

import io.github.velosako3.blossom.feature.sync.RemoteFile
import io.github.velosako3.blossom.feature.sync.SyncBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Any folder (for example one kept in step by Syncthing). Each write goes to a hidden temp file next to the target
 * and is renamed over it, so other devices never see half a file.
 */
class LocalFolderBackend(private val root: File) : SyncBackend {
    override val id = "folder:${root.absolutePath}"

    override suspend fun list(prefix: String): List<RemoteFile> = withContext(Dispatchers.IO) {
        val start = File(root, prefix.trim('/')).let { if (it.isDirectory) it else it.parentFile ?: root }
        if (!start.isDirectory) return@withContext emptyList()
        start.walkTopDown().filter { it.isFile && !it.name.startsWith(TEMP) }.mapNotNull { f ->
            val rel = f.relativeTo(root).invariantSeparatorsPath
            if (rel.startsWith(prefix.trim('/'))) RemoteFile(rel, f.length(), Instant.fromEpochMilliseconds(f.lastModified()), null) else null
        }.toList()
    }

    override suspend fun read(path: String): ByteArray = withContext(Dispatchers.IO) { File(root, path).readBytes() }

    override suspend fun write(path: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val target = File(root, path)
        target.parentFile.mkdirs()
        val temp = File(target.parentFile, "$TEMP${Uuid.random()}-${target.name}")
        try {
            temp.writeBytes(bytes)
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            temp.delete()
        }
        Unit
    }

    private companion object { const val TEMP = ".blossom-tmp-" }
}
