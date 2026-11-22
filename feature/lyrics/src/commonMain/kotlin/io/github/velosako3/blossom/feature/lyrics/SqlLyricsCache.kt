// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

import io.github.velosako3.blossom.core.database.BlossomDatabase
import io.github.velosako3.blossom.core.model.TrackId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.time.Instant

class SqlLyricsCache(private val db: BlossomDatabase) : LyricsCache {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Rows are keyed per lookup generation. v2 is BitChord's ordered lookup (2026-11-22); rows from the old
     * resolver (often a plain Genius page, sometimes the wrong song) are ignored rather than trusted.
     */
    private fun key(id: TrackId) = "v2:" + id.value

    override suspend fun get(id: TrackId): CachedLyrics? = withContext(Dispatchers.IO) {
        val row = db.blossomQueries.lyricsFor(key(id)).executeAsOneOrNull() ?: return@withContext null
        val stored = row.json ?: return@withContext CachedLyrics(null, Instant.fromEpochMilliseconds(row.fetched_at))
        // An unreadable row (older format, broken write) is a miss, not "none found".
        runCatching { CachedLyrics(json.decodeFromString(Lyrics.serializer(), stored), Instant.fromEpochMilliseconds(row.fetched_at)) }.getOrNull()
    }

    override suspend fun put(id: TrackId, lyrics: Lyrics?, at: Instant) = withContext(Dispatchers.IO) {
        db.blossomQueries.putLyrics(key(id), lyrics?.let { json.encodeToString(Lyrics.serializer(), it) }, at.toEpochMilliseconds())
        Unit
    }
}
