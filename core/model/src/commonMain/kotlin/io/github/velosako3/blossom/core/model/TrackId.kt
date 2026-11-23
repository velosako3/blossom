// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/** Where a track comes from; the prefix is the first segment of every [TrackId]. */
@Serializable
enum class SourceKind(val prefix: String) {
    YOUTUBE_MUSIC("ytm"), YOUTUBE("yt"), SOUNDCLOUD("sc"), UNTITLED("untitled"), LOCAL("local");

    companion object {
        fun fromPrefix(prefix: String): SourceKind? = entries.firstOrNull { it.prefix == prefix }
    }
}

/** `<prefix>:<sourceId>`, e.g. `ytm:dQw4w9WgXcQ`. The source ID may itself contain colons. */
@Serializable
@JvmInline
value class TrackId(val value: String) {
    init {
        val sep = value.indexOf(':')
        require(sep > 0 && sep < value.lastIndex) { "TrackId needs <prefix>:<id>: $value" }
        require(SourceKind.fromPrefix(value.substring(0, sep)) != null) { "Unknown TrackId prefix: $value" }
    }

    val kind: SourceKind get() = SourceKind.fromPrefix(value.substringBefore(':'))!!
    val sourceId: String get() = value.substringAfter(':')

    override fun toString(): String = value

    companion object {
        fun of(kind: SourceKind, sourceId: String): TrackId = TrackId("${kind.prefix}:$sourceId")
        fun parse(value: String): TrackId = TrackId(value)
    }
}

/** A blossom playlist ID (UUID string). */
@Serializable
@JvmInline
value class PlaylistId(val value: String) {
    override fun toString(): String = value
}
