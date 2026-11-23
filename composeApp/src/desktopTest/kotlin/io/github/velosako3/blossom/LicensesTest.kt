// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class LicensesTest {
    @Test fun generatedListParses() {
        val libs = parseLicenses(File("src/commonMain/composeResources/files/aboutlibraries.json").readText())
        assertTrue(libs.size > 100, "${libs.size} libraries")
        val okhttp = libs.first { it.name.contains("OkHttp", ignoreCase = true) }
        assertTrue(okhttp.licenses.any { "Apache" in it }, okhttp.toString())
        assertTrue(libs.none { it.licenses.any { l -> l.matches(Regex("[0-9a-f]{32}")) } }, "every license ID resolves to a name")
    }
}
