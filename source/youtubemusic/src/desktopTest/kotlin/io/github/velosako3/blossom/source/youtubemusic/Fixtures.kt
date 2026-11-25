// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.youtubemusic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** Loads a recorded, sanitized response from `src/commonTest/resources/fixtures/`. */
fun fixture(name: String): JsonObject {
    val text = object {}.javaClass.getResource("/fixtures/$name.json")!!.readText()
    return Json.parseToJsonElement(text).jsonObject
}
