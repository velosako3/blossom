// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** What providers are asked. [videoId] lets the YouTube sources match the exact upload. */
data class LyricsQuery(val title: String, val artists: List<String>, val album: String?, val durationMs: Long, val videoId: String? = null)

/** One lyrics site. Lower [priority] wins ties between equally rich results. */
interface LyricsProvider {
    val name: String
    val priority: Int
    suspend fun fetch(q: LyricsQuery): Lyrics?
}

/** Sidecar `.lrc` files and embedded lyrics tags, for local files. */
fun interface LocalLyricsSource {
    suspend fun lyrics(track: Track): Lyrics?
}

data class CachedLyrics(val lyrics: Lyrics?, val fetchedAt: Instant)

interface LyricsCache {
    suspend fun get(id: TrackId): CachedLyrics?
    suspend fun put(id: TrackId, lyrics: Lyrics?, at: Instant)
}

/**
 * Local (sidecar, then embedded tags; local files only) → cache → every online provider in parallel, 5 s each →
 * the richest answer (word > line > plain), ties by provider priority. "None found" is cached for 7 days when every
 * provider answered, 1 hour when some failed, and not at all when all failed.
 */
class LyricsResolver(
    private val providers: List<LyricsProvider>,
    private val local: LocalLyricsSource,
    private val cache: LyricsCache,
    private val clock: Clock,
) {
    suspend fun resolve(track: Track): Lyrics? {
        // Local files: sidecar/tag first, every time, so a newly added .lrc shows up at once.
        if (track.id.kind == SourceKind.LOCAL) {
            val local = try { local.lyrics(track) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            if (local != null) { cache.put(track.id, local, clock.now()); return local }
        }
        cache.get(track.id)?.let { c ->
            if (c.lyrics != null) return c.lyrics
            if (clock.now() - c.fetchedAt < NEGATIVE_TTL) return null
        }
        val q = LyricsQuery(
            title = track.title,
            artists = track.artists.map { it.name },
            album = track.album?.title,
            durationMs = track.durationMs,
            videoId = track.id.sourceId.takeIf { track.id.kind == SourceKind.YOUTUBE_MUSIC || track.id.kind == SourceKind.YOUTUBE },
        )
        var failures = 0
        val results = coroutineScope {
            providers.map { p ->
                async {
                    val outcome = withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                        try { Result.success(p.fetch(q)) } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
                    }
                    if (outcome == null || outcome.isFailure) { synchronized(this@LyricsResolver) { failures++ }; null }
                    else outcome.getOrNull()?.let { p to it }
                }
            }.awaitAll().filterNotNull()
        }
        val best = results.sortedWith(compareByDescending<Pair<LyricsProvider, Lyrics>> { it.second.richness }.thenBy { it.first.priority })
            .firstOrNull()?.second
        when {
            best != null -> cache.put(track.id, best, clock.now())
            failures == providers.size -> Unit // offline or everything broke: ask again next time
            failures > 0 -> cache.put(track.id, null, clock.now() - NEGATIVE_TTL + RETRY_AFTER_FAILURE) // ponytail: short TTL by back-dating
            else -> cache.put(track.id, null, clock.now())
        }
        return best
    }

    private companion object {
        const val PROVIDER_TIMEOUT_MS = 5_000L
        val NEGATIVE_TTL = 7.days
        /** "None found" while some providers failed (timeouts, errors) is only trusted this long. */
        val RETRY_AFTER_FAILURE = 1.hours
    }
}
