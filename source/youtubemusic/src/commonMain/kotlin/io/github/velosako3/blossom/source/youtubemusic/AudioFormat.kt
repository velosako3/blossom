// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.youtubemusic

import io.github.velosako3.blossom.source.api.AudioQuality
import io.github.velosako3.blossom.source.api.TrackUnavailableException
import kotlin.math.abs

/** One audio-only rendition offered for a video. */
data class AudioFormat(
    val codec: String,
    val bitrateKbps: Int,
    val url: String,
    val mimeType: String = "",
)

/**
 * HIGH: highest bitrate, opus winning ties. NORMAL: closest to 128 kbps. LOW: the lowest at or above 48 kbps
 * (or the lowest of all when none reach 48).
 */
fun selectAudioFormat(formats: List<AudioFormat>, quality: AudioQuality): AudioFormat {
    if (formats.isEmpty()) throw TrackUnavailableException("No audio formats")
    val opusFirst = compareBy<AudioFormat> { if (it.codec.equals("opus", ignoreCase = true)) 0 else 1 }
    return when (quality) {
        AudioQuality.HIGH -> formats.sortedWith(compareByDescending<AudioFormat> { it.bitrateKbps }.then(opusFirst)).first()
        AudioQuality.NORMAL -> formats.sortedWith(compareBy<AudioFormat> { abs(it.bitrateKbps - 128) }.then(opusFirst)).first()
        AudioQuality.LOW -> (formats.filter { it.bitrateKbps >= 48 }.ifEmpty { formats })
            .sortedWith(compareBy<AudioFormat> { it.bitrateKbps }.then(opusFirst)).first()
    }
}
