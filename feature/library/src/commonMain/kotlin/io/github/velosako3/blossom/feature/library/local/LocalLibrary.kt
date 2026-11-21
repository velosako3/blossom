// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.library.local

import io.github.velosako3.blossom.core.model.AlbumRef
import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import kotlinx.coroutines.flow.Flow

/** Music files on this device. Paths (or content URIs on Android) are for this device only and never sync. */
interface LocalLibrary {
    fun tracks(): Flow<List<Track>>
    suspend fun rescan()
    fun pathFor(id: TrackId): String?
}

data class ScannedFile(val track: Track, val path: String, val mtime: Long, val size: Long)

val SUPPORTED_EXTENSIONS = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav")

const val UNKNOWN_ARTIST = "Unknown artist"

/** Builds the track for a file; untagged files use the file name as title and "Unknown artist". */
fun localTrack(title: String?, artist: String?, album: String?, durationMs: Long, fileName: String, artworkUrl: String?): Track {
    val t = title?.trim()?.takeIf { it.isNotEmpty() } ?: fileName.substringBeforeLast('.')
    val a = artist?.trim()?.takeIf { it.isNotEmpty() } ?: UNKNOWN_ARTIST
    val al = album?.trim()?.takeIf { it.isNotEmpty() }
    return Track(localTrackId(t, a, al), t, listOf(ArtistRef(null, a)), al?.let { AlbumRef(null, it) }, durationMs, artworkUrl)
}
