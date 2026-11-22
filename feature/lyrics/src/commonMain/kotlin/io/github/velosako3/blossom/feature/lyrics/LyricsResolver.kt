// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** What providers are asked. [videoId] lets the YouTube sources match the exact upload. */
data class LyricsQuery(val title: String, val artists: List<String>, val album: String?, val durationMs: Long, val videoId: String? = null)

/** The online lookup. In the app it is BitChord's `LyricsRepository` (see [bitChordLyrics]). */
fun interface OnlineLyrics {
    suspend fun lyrics(q: LyricsQuery): Lyrics?
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
 * Local (sidecar, then embedded tags; local files only) → cache → [online].
 *
 * Timed lyrics are kept for good. Plain ones are retried after [PLAIN_RETRY] (a synced source may have been down or
 * slow), keeping the plain copy if the retry finds nothing better. "None found" is kept for [NEGATIVE_TTL]; a failed
 * lookup (offline, an error) is not cached at all.
 */
class LyricsResolver(
    private val online: OnlineLyrics,
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
        val cached = cache.get(track.id)
        val age = cached?.let { clock.now() - it.fetchedAt }
        when {
            cached == null -> Unit
            cached.lyrics == null -> if (age!! < NEGATIVE_TTL) return null
            cached.lyrics !is Lyrics.Plain || age!! < PLAIN_RETRY -> return cached.lyrics
        }
        val q = LyricsQuery(
            title = track.title,
            artists = track.artists.map { it.name },
            album = track.album?.title,
            durationMs = track.durationMs,
            videoId = track.id.sourceId.takeIf { track.id.kind == SourceKind.YOUTUBE_MUSIC || track.id.kind == SourceKind.YOUTUBE },
        )
        val found = try { online.lyrics(q) } catch (e: CancellationException) { throw e } catch (_: Exception) { return cached?.lyrics }
        val keep = found ?: cached?.lyrics
        cache.put(track.id, keep, clock.now())
        return keep
    }

    private companion object {
        val NEGATIVE_TTL = 1.days
        val PLAIN_RETRY = 1.days
    }
}
