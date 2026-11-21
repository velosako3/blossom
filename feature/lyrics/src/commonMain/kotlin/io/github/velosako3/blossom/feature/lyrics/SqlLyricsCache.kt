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

    override suspend fun get(id: TrackId): CachedLyrics? = withContext(Dispatchers.IO) {
        db.blossomQueries.lyricsFor(id.value).executeAsOneOrNull()?.let { row ->
            CachedLyrics(row.json?.let { runCatching { json.decodeFromString(Lyrics.serializer(), it) }.getOrNull() }, Instant.fromEpochMilliseconds(row.fetched_at))
        }
    }

    override suspend fun put(id: TrackId, lyrics: Lyrics?, at: Instant) = withContext(Dispatchers.IO) {
        db.blossomQueries.putLyrics(id.value, lyrics?.let { json.encodeToString(Lyrics.serializer(), it) }, at.toEpochMilliseconds())
        Unit
    }
}
