// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player

import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.source.api.AudioQuality
import io.github.velosako3.blossom.source.api.MusicSource
import io.github.velosako3.blossom.source.api.StreamInfo
import io.github.velosako3.blossom.source.api.TrackUnavailableException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** Resolves stream URLs through the right source and caches each until 5 minutes before it expires. */
class StreamResolver(private val sources: Map<SourceKind, MusicSource>, private val clock: Clock) {
    private val cache = HashMap<Pair<TrackId, AudioQuality>, StreamInfo>()

    suspend fun resolve(id: TrackId, quality: AudioQuality): StreamInfo {
        val key = id to quality
        synchronized(cache) { cache[key] }?.let { cached ->
            val expires = cached.expiresAt
            if (expires == null || clock.now() < expires - EXPIRY_MARGIN) return cached
        }
        val source = sources[id.kind] ?: throw TrackUnavailableException("No source for ${id.kind}")
        val fresh = source.resolveStream(id, quality)
        synchronized(cache) { cache[key] = fresh }
        return fresh
    }

    fun invalidate(id: TrackId) {
        synchronized(cache) { cache.keys.removeAll { it.first == id } }
    }

    private companion object {
        val EXPIRY_MARGIN = 5.minutes
    }
}
