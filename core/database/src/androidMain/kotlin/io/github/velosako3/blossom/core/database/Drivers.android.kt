// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.database

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

fun androidDatabase(context: Context): BlossomDatabase =
    BlossomDatabase(AndroidSqliteDriver(BlossomDatabase.Schema, context, "blossom.db"))
