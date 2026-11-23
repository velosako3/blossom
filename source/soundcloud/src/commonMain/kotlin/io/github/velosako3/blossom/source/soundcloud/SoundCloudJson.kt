// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.soundcloud

import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

internal fun JsonElement.obj(key: String) = (this as? JsonObject)?.get(key) as? JsonObject
internal fun JsonElement.arr(key: String) = (this as? JsonObject)?.get(key) as? JsonArray
internal fun JsonElement.str(key: String) = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
internal fun JsonElement.long(key: String) = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.longOrNull
internal fun JsonElement.bool(key: String) = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.booleanOrNull

/** One way to stream a track. */
data class Transcoding(val url: String, val preset: String, val protocol: String, val mimeType: String, val snipped: Boolean)

internal fun JsonElement.transcodings(): List<Transcoding> = obj("media")?.arr("transcodings").orEmpty().mapNotNull { t ->
    Transcoding(
        url = t.str("url") ?: return@mapNotNull null,
        preset = t.str("preset").orEmpty(),
        protocol = t.obj("format")?.str("protocol").orEmpty(),
        mimeType = t.obj("format")?.str("mime_type").orEmpty(),
        snipped = t.bool("snipped") == true,
    )
}

/** Progressive MP3/Opus first (plain ranged files), then HLS; never a 30-second preview. */
fun selectTranscoding(all: List<Transcoding>): Transcoding? {
    val full = all.filterNot { it.snipped }
    fun rank(t: Transcoding) = when {
        t.protocol == "progressive" && ("mpeg" in t.mimeType || "opus" in t.mimeType || "ogg" in t.mimeType) -> 0
        t.protocol == "progressive" -> 1
        t.protocol == "hls" && "mp4" in t.mimeType -> 2
        t.protocol == "hls" -> 3
        else -> 9
    }
    return full.filter { rank(it) < 9 }.minByOrNull(::rank)
}

/** Go+ tracks: only a 30-second snippet plays without a subscription. */
internal val JsonElement.previewOnly: Boolean get() = str("policy") == "SNIP" || transcodings().let { t -> t.isNotEmpty() && t.all { it.snipped } }

internal fun JsonElement.artistName(): String =
    obj("publisher_metadata")?.str("artist")?.takeIf { it.isNotBlank() } ?: obj("user")?.str("username").orEmpty()

internal fun JsonElement.toTrack(): Track? {
    val id = long("id") ?: return null
    val title = str("title") ?: return null
    return Track(
        id = TrackId.of(SourceKind.SOUNDCLOUD, id.toString()),
        title = title,
        artists = listOf(ArtistRef(null, artistName())),
        album = null,
        durationMs = long("full_duration") ?: long("duration") ?: 0,
        // "-large" is 100 px; the same artwork exists at 500 px.
        artworkUrl = (str("artwork_url") ?: obj("user")?.str("avatar_url"))?.replace("-large.", "-t500x500."),
    )
}
