// SPDX-License-Identifier: GPL-3.0-only
// Ported from BitChord (https://github.com/kushagrasinghx/BitChord) v1.9.1,
// shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/innertube/InnertubeParser.kt (GPL-3.0).
// Modified for blossom, 2026: returns blossom models; library, podcast and account parsing removed.
package io.github.velosako3.blossom.source.youtubemusic

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun JsonElement?.o(key: String): JsonObject? = (this as? JsonObject)?.get(key) as? JsonObject
internal fun JsonElement?.a(key: String): JsonArray? = (this as? JsonObject)?.get(key) as? JsonArray
internal fun JsonElement?.s(key: String): String? = ((this as? JsonObject)?.get(key) as? JsonPrimitive)?.contentOrNull

internal fun JsonElement?.runs(): String = this.a("runs")?.joinToString("") { it.s("text").orEmpty() }.orEmpty()

internal fun JsonElement?.findString(key: String): String? = when (this) {
    is JsonObject -> (this[key] as? JsonPrimitive)?.contentOrNull ?: values.firstNotNullOfOrNull { it.findString(key) }
    is JsonArray -> firstNotNullOfOrNull { it.findString(key) }
    else -> null
}

internal fun JsonElement?.hasKey(key: String): Boolean = when (this) {
    is JsonObject -> containsKey(key) || values.any { it.hasKey(key) }
    is JsonArray -> any { it.hasKey(key) }
    else -> false
}

/** The largest thumbnail, upgraded to 544 px where the URL encodes a size. */
internal fun JsonArray?.best(): String? = this?.lastOrNull().s("url")?.let(::upsizeArtwork)

internal fun upsizeArtwork(url: String): String =
    url.replace(Regex("""=w\d+-h\d+"""), "=w544-h544").replace(Regex("""=s\d+"""), "=s544")

internal fun JsonArray?.isNotSquare(): Boolean {
    val last = this?.lastOrNull()
    val width = last.s("width")?.toDoubleOrNull() ?: return false
    val height = last.s("height")?.toDoubleOrNull() ?: return false
    if (width <= 0 || height <= 0) return false
    return width / height !in 0.85..1.15
}

/** All renderers called [name] anywhere under [root], depth-first. */
internal fun collectRenderers(root: JsonElement, name: String): List<JsonObject> {
    val out = mutableListOf<JsonObject>()
    fun walk(node: JsonElement) {
        when (node) {
            is JsonObject -> {
                (node[name] as? JsonObject)?.let(out::add)
                node.values.forEach(::walk)
            }
            is JsonArray -> node.forEach(::walk)
            else -> Unit
        }
    }
    walk(root)
    return out
}

/** "3:45" or "1:02:03" → milliseconds. */
internal fun parseDurationMs(text: String?): Long? {
    if (text == null || !text.matches(Regex("""\d+(:\d{2}){1,2}"""))) return null
    return text.split(':').fold(0L) { acc, part -> acc * 60 + part.toLong() } * 1000
}
