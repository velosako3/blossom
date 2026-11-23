// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.integrations.discord

import io.github.velosako3.blossom.core.model.Track
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Instant

/** The Rich Presence activity for a track: "Listening to <title>", "by <artists>", elapsed/remaining while playing. */
object PresenceBuilder {
    fun activity(track: Track, positionMs: Long, playing: Boolean, now: Instant): JsonObject = buildJsonObject {
        put("type", 2) // Listening
        put("details", track.title.take(128))
        put("state", ("by ${track.artistNames}" + if (playing) "" else " · paused").take(128))
        if (playing) {
            val start = now.toEpochMilliseconds() - positionMs
            putJsonObject("timestamps") {
                put("start", start)
                if (track.durationMs > 0) put("end", start + track.durationMs)
            }
        }
        putJsonObject("assets") {
            track.artworkUrl?.takeIf { it.startsWith("https://") }?.let { put("large_image", it) }
            put("large_text", (track.album?.title ?: track.title).take(128))
        }
    }
}
