// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

import io.github.velosako3.blossom.core.database.inMemoryDatabase
import io.github.velosako3.blossom.core.model.TrackId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** The owner's 0/3: a new build kept showing what an older, broken lookup had saved. */
class SqlLyricsCacheTest {
    @Test fun aNewBuildDoesNotSeeTheOldBuildsAnswers() = runTest {
        val db = inMemoryDatabase()
        val id = TrackId("untitled:nitrous")
        val old = SqlLyricsCache(db, generation = "build4")
        old.put(id, Lyrics.Plain("Oh, understand, girl", "LyricsPlus"), Instant.fromEpochSeconds(1_800_000_000))
        val new = SqlLyricsCache(db, generation = "build6")
        assertNull(new.get(id), "build 6 read build 4's wrong lyrics")
        new.put(id, Lyrics.Plain("Nitrous really", "Genius"), Instant.fromEpochSeconds(1_800_000_100))
        new.pruneOtherGenerations()
        assertNull(old.get(id), "old rows are pruned")
        assertEquals("Nitrous really", (new.get(id)?.lyrics as Lyrics.Plain).text)
    }
}
