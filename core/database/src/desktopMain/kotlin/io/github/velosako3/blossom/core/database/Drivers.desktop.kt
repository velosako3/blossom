// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties

/** The library database file in the app data folder (schema created on first open). */
fun fileDatabase(file: File): BlossomDatabase {
    file.parentFile?.mkdirs()
    val driver = BlossomSqliteDriver.open("jdbc:sqlite:${file.absolutePath}", BlossomDatabase.Schema)
    return BlossomDatabase(driver)
}

/** For tests. */
fun inMemoryDatabase(): BlossomDatabase = BlossomDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), BlossomDatabase.Schema))
