// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.database

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

/** Transactions from many threads at once (downloads, playlist watchers, library commands) must queue, not fail. */
class ConcurrentWritesTest {
    @Test fun parallelTransactionsOnFileDatabase() {
        val file = kotlin.io.path.createTempFile("blossom-db", ".db").toFile()
        try {
            val db = fileDatabase(file)
            val pool = Executors.newFixedThreadPool(8)
            val futures = (0 until 200).map { i ->
                pool.submit {
                    db.transaction {
                        db.blossomQueries.offlineInsert("t$i", "{}")
                        db.blossomQueries.offlineAddRef("t$i", "single")
                    }
                }
            }
            futures.forEach { it.get(30, TimeUnit.SECONDS) }
            pool.shutdown()
            assertEquals(0L, db.blossomQueries.offlineOrphans().executeAsList().size.toLong())
            assertEquals(200, (0 until 200).count { db.blossomQueries.offlineItem("t$it").executeAsOneOrNull() != null })
        } finally {
            file.delete(); java.io.File(file.path + "-wal").delete(); java.io.File(file.path + "-shm").delete()
        }
    }
}
