// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player.media

import io.github.velosako3.blossom.core.model.Track

/** The MPRIS metadata dictionary for a track (xesam/mpris keys, length in microseconds). */
object MprisMetadataMapper {
    fun map(track: Track): Map<String, Any> = buildMap {
        put("mpris:trackid", "/io/github/velosako3/blossom/track/" + track.id.value.replace(Regex("[^A-Za-z0-9]"), "_"))
        put("xesam:title", track.title)
        put("xesam:artist", track.artists.map { it.name })
        track.album?.let { put("xesam:album", it.title) }
        put("mpris:length", track.durationMs * 1000)
        track.artworkUrl?.let { put("mpris:artUrl", it) }
    }
}
