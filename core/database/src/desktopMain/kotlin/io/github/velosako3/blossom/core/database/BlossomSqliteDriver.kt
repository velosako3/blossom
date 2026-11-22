// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.database

import app.cash.sqldelight.Query
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import java.sql.Connection
import java.sql.DriverManager
import java.util.Collections
import java.util.Properties

/**
 * SQLite file driver for desktop: one connection per thread, WAL (readers never block the writer), and
 * `BEGIN IMMEDIATE` transactions. SQLDelight's own SQLite driver begins DEFERRED transactions, so one that reads before
 * it writes fails at once with SQLITE_BUSY when another writer holds the lock; IMMEDIATE takes the write lock up front
 * and waits for it (`busy_timeout`).
 */
class BlossomSqliteDriver(private val url: String, private val properties: Properties) : JdbcDriver() {
    private val connections = ThreadLocal<Connection>()
    private val opened = Collections.synchronizedSet(mutableSetOf<Connection>())
    private val listeners = HashMap<String, MutableSet<Query.Listener>>()

    override fun getConnection(): Connection = connections.get() ?: DriverManager.getConnection(url, properties).also {
        connections.set(it)
        opened += it
    }

    override fun closeConnection(connection: Connection) = Unit // kept open for this thread

    override fun Connection.beginTransaction() = exec("BEGIN IMMEDIATE")
    override fun Connection.endTransaction() = exec("END TRANSACTION")
    override fun Connection.rollbackTransaction() = exec("ROLLBACK TRANSACTION")

    private fun Connection.exec(sql: String) { prepareStatement(sql).use { it.execute() } }

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) {
        synchronized(listeners) { queryKeys.forEach { listeners.getOrPut(it) { LinkedHashSet() }.add(listener) } }
    }

    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) {
        synchronized(listeners) { queryKeys.forEach { listeners[it]?.remove(listener) } }
    }

    override fun notifyListeners(vararg queryKeys: String) {
        val toNotify = synchronized(listeners) { queryKeys.flatMapTo(LinkedHashSet()) { listeners[it].orEmpty() } }
        toNotify.forEach { it.queryResultsChanged() }
    }

    override fun close() {
        synchronized(opened) { opened.forEach { runCatching { it.close() } }; opened.clear() }
    }

    companion object {
        fun open(url: String, schema: SqlSchema<QueryResult.Value<Unit>>): BlossomSqliteDriver {
            val props = Properties().apply {
                setProperty("journal_mode", "WAL")
                setProperty("busy_timeout", "10000")
                // WAL + NORMAL: no fsync per commit; the database stays consistent, a power cut may lose the last commits.
                setProperty("synchronous", "NORMAL")
            }
            val driver = BlossomSqliteDriver(url, props)
            val version = driver.executeQuery(null, "PRAGMA user_version", { c -> QueryResult.Value(if (c.next().value) c.getLong(0) ?: 0 else 0) }, 0).value
            when {
                version == 0L -> { schema.create(driver); driver.execute(null, "PRAGMA user_version = ${schema.version}", 0) }
                version < schema.version -> { schema.migrate(driver, version, schema.version); driver.execute(null, "PRAGMA user_version = ${schema.version}", 0) }
            }
            return driver
        }
    }
}
