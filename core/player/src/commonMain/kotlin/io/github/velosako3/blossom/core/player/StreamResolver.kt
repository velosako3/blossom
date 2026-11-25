// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player

import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.source.api.AudioQuality
import io.github.velosako3.blossom.source.api.MusicSource
import io.github.velosako3.blossom.source.api.StreamInfo
import io.github.velosako3.blossom.source.api.TrackUnavailableException
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** Resolves stream URLs through the right source and caches each until 5 minutes before it expires. */
@OptIn(ExperimentalAtomicApi::class)
class StreamResolver(private val sources: Map<SourceKind, MusicSource>, private val clock: Clock) {
    // An immutable map swapped by compare-and-set: the same on every platform, no lock.
    private val cache = AtomicReference(emptyMap<Pair<TrackId, AudioQuality>, StreamInfo>())

    suspend fun resolve(id: TrackId, quality: AudioQuality): StreamInfo {
        val key = id to quality
        cache.load()[key]?.let { cached ->
            val expires = cached.expiresAt
            if (expires == null || clock.now() < expires - EXPIRY_MARGIN) return cached
        }
        val source = sources[id.kind] ?: throw TrackUnavailableException("No source for ${id.kind}")
        val fresh = source.resolveStream(id, quality)
        update { it + (key to fresh) }
        return fresh
    }

    fun invalidate(id: TrackId) = update { m -> m.filterKeys { it.first != id } }

    private inline fun update(f: (Map<Pair<TrackId, AudioQuality>, StreamInfo>) -> Map<Pair<TrackId, AudioQuality>, StreamInfo>) {
        while (true) { val old = cache.load(); if (cache.compareAndSet(old, f(old))) return }
    }

    private companion object {
        val EXPIRY_MARGIN = 5.minutes
    }
}
