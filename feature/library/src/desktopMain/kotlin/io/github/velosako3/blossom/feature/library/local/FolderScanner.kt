// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.library.local

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.security.MessageDigest
import java.util.logging.Level
import java.util.logging.Logger

/** Reads tags (jaudiotagger) from every supported file under the folders, recursively. Embedded art is cached. */
class FolderScanner(private val artworkDir: File) {
    private val cache = HashMap<String, ScannedFile>()

    init { Logger.getLogger("org.jaudiotagger").level = Level.OFF }

    fun scan(folders: List<File>): List<ScannedFile> {
        val found = folders.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension.lowercase() in SUPPORTED_EXTENSIONS }.mapNotNull(::read).toList()
        }.distinctBy { it.path }
        synchronized(this) { cache.keys.retainAll(found.mapTo(HashSet()) { it.path }) }
        return found
    }

    @Synchronized
    fun read(file: File): ScannedFile? {
        val path = file.absolutePath
        cache[path]?.takeIf { it.mtime == file.lastModified() && it.size == file.length() }?.let { return it }
        val scanned = runCatching {
            val audio = AudioFileIO.read(file)
            val tag = audio.tag
            fun f(k: FieldKey) = runCatching { tag?.getFirst(k) }.getOrNull()
            val durationMs = (audio.audioHeader.preciseTrackLength * 1000).toLong()
            val title = f(FieldKey.TITLE); val artist = f(FieldKey.ARTIST); val album = f(FieldKey.ALBUM)
            val base = localTrack(title, artist, album, durationMs, file.name, null)
            val art = runCatching { tag?.firstArtwork?.binaryData }.getOrNull()?.let { bytes -> cacheArtwork(base.id.sourceId, bytes) }
            base.copy(artworkUrl = art)
        }.getOrElse { localTrack(null, null, null, 0, file.name, null) }
        return ScannedFile(scanned, path, file.lastModified(), file.length()).also { cache[path] = it }
    }

    private fun cacheArtwork(key: String, bytes: ByteArray): String {
        artworkDir.mkdirs()
        val name = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }.take(16)
        val out = File(artworkDir, "$key-$name.img")
        if (!out.isFile) out.writeBytes(bytes)
        return out.toURI().toString()
    }
}
