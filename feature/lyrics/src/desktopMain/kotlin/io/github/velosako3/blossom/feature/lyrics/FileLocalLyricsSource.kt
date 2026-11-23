// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

/** Desktop: a sidecar `<name>.lrc` next to the file, then the embedded lyrics tag (USLT/LYRICS, LRC or plain). */
class FileLocalLyricsSource(private val pathFor: (TrackId) -> String?) : LocalLyricsSource {
    override suspend fun lyrics(track: Track): Lyrics? = withContext(Dispatchers.IO) {
        val file = pathFor(track.id)?.let(::File)?.takeIf { it.isFile } ?: return@withContext null
        val sidecar = File(file.parentFile, file.nameWithoutExtension + ".lrc")
        if (sidecar.isFile) LrcParser.parse(sidecar.readText(), "local", track.durationMs)?.let { return@withContext it }
        val embedded = runCatching { AudioFileIO.read(file).tag?.getFirst(FieldKey.LYRICS) }.getOrNull()
        embedded?.takeIf { it.isNotBlank() }?.let { LrcParser.parse(it, "local", track.durationMs) }
    }
}
