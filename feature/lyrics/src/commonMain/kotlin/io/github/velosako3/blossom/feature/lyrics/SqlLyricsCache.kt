// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

import io.github.velosako3.blossom.core.database.BlossomDatabase
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.feature.lyrics.providers.LyricAlignment
import io.github.velosako3.blossom.feature.lyrics.providers.LyricLine
import io.github.velosako3.blossom.feature.lyrics.providers.LyricSyllable
import io.github.velosako3.blossom.feature.lyrics.providers.LyricWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

class SqlLyricsCache(private val db: BlossomDatabase) : LyricsCache {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Rows are keyed per format. v3 stores BitChord's full lines (2026-11-22); rows from before (v2, and the old
     * resolver's unprefixed rows, often a plain Genius page or the wrong song) are ignored rather than trusted.
     */
    private fun key(id: TrackId) = "v3:" + id.value

    override suspend fun get(id: TrackId): CachedLyrics? = withContext(Dispatchers.IO) {
        val row = db.blossomQueries.lyricsFor(key(id)).executeAsOneOrNull() ?: return@withContext null
        val stored = row.json ?: return@withContext CachedLyrics(null, Instant.fromEpochMilliseconds(row.fetched_at))
        // An unreadable row (broken write) is a miss, not "none found".
        runCatching { CachedLyrics(json.decodeFromString(StoredLyrics.serializer(), stored).toLyrics(), Instant.fromEpochMilliseconds(row.fetched_at)) }.getOrNull()
    }

    override suspend fun put(id: TrackId, lyrics: Lyrics?, at: Instant) = withContext(Dispatchers.IO) {
        db.blossomQueries.putLyrics(key(id), lyrics?.let { json.encodeToString(StoredLyrics.serializer(), StoredLyrics.of(it)) }, at.toEpochMilliseconds())
        Unit
    }
}

/** BitChord's lines as stored. Derived values (spans, growth) are recomputed by [LyricLine] itself. */
@Serializable
internal data class StoredLyrics(val provider: String, val plain: String? = null, val lines: List<StoredLine> = emptyList()) {
    fun toLyrics(): Lyrics? = if (plain != null) Lyrics.Plain(plain, provider) else lyricsOf(lines.map { it.toLine() }, provider)

    companion object {
        fun of(l: Lyrics) = when (l) {
            is Lyrics.Plain -> StoredLyrics(l.provider, plain = l.text)
            else -> StoredLyrics(l.provider, lines = l.lines.map(StoredLine::of))
        }
    }
}

@Serializable
internal data class StoredLine(
    val t: Long,
    val text: String,
    val words: List<StoredWord> = emptyList(),
    val until: Long? = null,
    val background: StoredLine? = null,
    val end: Boolean = false,
) {
    fun toLine(): LyricLine = LyricLine(t, text, words.map { it.toWord() }, until, background?.toLine(), if (end) LyricAlignment.End else LyricAlignment.Start)

    companion object {
        fun of(l: LyricLine): StoredLine =
            StoredLine(l.timeMs, l.text, l.words.map(StoredWord::of), l.sungUntilMs, l.background?.let(::of), l.alignment == LyricAlignment.End)
    }
}

@Serializable
internal data class StoredWord(val s: Long, val e: Long, val text: String, val syl: List<List<Long>> = emptyList()) {
    fun toWord() = LyricWord(s, e, text, syl.map { (a, b, c, d) -> LyricSyllable(a, b, c.toInt(), d.toInt()) })

    companion object {
        fun of(w: LyricWord) = StoredWord(w.startMs, w.endMs, w.text, w.syllables.map { listOf(it.startMs, it.endMs, it.startChar.toLong(), it.endChar.toLong()) })
    }
}
