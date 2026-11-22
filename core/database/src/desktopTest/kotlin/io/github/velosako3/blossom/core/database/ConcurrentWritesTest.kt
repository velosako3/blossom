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

    /** Read-then-write transactions racing each other and plain readers (lock upgrades must wait, not fail). */
    @Test fun readThenWriteTransactionsWithReaders() {
        val file = kotlin.io.path.createTempFile("blossom-db", ".db").toFile()
        try {
            val db = fileDatabase(file)
            val pool = Executors.newFixedThreadPool(8)
            val writers = (0 until 200).map { i ->
                pool.submit {
                    db.transaction {
                        db.blossomQueries.offlineUsage().executeAsOne() // read first: takes a shared lock
                        db.blossomQueries.offlineInsert("t$i", "{}")
                    }
                }
            }
            val readers = (0 until 400).map { pool.submit { db.blossomQueries.offlineOrphans().executeAsList() } }
            (writers + readers).forEach { it.get(60, TimeUnit.SECONDS) }
            pool.shutdown()
            assertEquals(200, (0 until 200).count { db.blossomQueries.offlineItem("t$it").executeAsOneOrNull() != null })
        } finally {
            file.delete(); java.io.File(file.path + "-wal").delete(); java.io.File(file.path + "-shm").delete()
        }
    }

    /** A version-1 database (before import_job.error) upgrades in place. */
    @Test fun versionOneDatabaseMigrates() {
        val file = kotlin.io.path.createTempFile("blossom-v1", ".db").toFile()
        try {
            java.sql.DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { c ->
                c.createStatement().use { st ->
                    st.execute("CREATE TABLE import_job (id TEXT NOT NULL PRIMARY KEY, url TEXT NOT NULL, service TEXT NOT NULL, target_json TEXT NOT NULL, playlist_id TEXT, state TEXT NOT NULL, total INTEGER, created_at INTEGER NOT NULL)")
                    st.execute("PRAGMA user_version = 1")
                }
            }
            val db = fileDatabase(file)
            db.blossomQueries.importJobInsert("j", "u", "SPOTIFY", "{}", "READING", 0)
            db.blossomQueries.importJobFail("NEEDS_LOGIN", "j")
            assertEquals("NEEDS_LOGIN", db.blossomQueries.importJob("j").executeAsOne().error)
        } finally {
            file.delete(); java.io.File(file.path + "-wal").delete(); java.io.File(file.path + "-shm").delete()
        }
    }
}
