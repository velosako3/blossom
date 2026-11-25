// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.database

import app.cash.sqldelight.driver.native.NativeSqliteDriver

/** The app's database in its Application Support folder (SQLDelight's native driver over iOS's SQLite). */
fun iosDatabase(name: String = "blossom.db"): BlossomDatabase = BlossomDatabase(NativeSqliteDriver(BlossomDatabase.Schema, name))
