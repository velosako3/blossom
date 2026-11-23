// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync

import io.github.velosako3.blossom.core.model.sync.OpEnvelope
import io.github.velosako3.blossom.core.model.sync.Operation
import io.github.velosako3.blossom.core.model.sync.OperationJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

sealed interface DecodedLine {
    data class Known(val op: OpEnvelope) : DecodedLine
    /** From a newer blossom: an operation type or format version this one doesn't know. Kept, never applied. */
    data class Unknown(val raw: String) : DecodedLine
    data class Corrupt(val raw: String, val error: String) : DecodedLine
}

/** One [OpEnvelope] per line of JSON, tagged `"v":1`. */
object OpLogCodec {
    const val VERSION = 1

    /** The serial names of every operation this build understands. */
    val knownTypes: Set<String> = Operation.serializer().descriptor.getElementDescriptor(1).let { d -> (0 until d.elementsCount).map(d::getElementName).toSet() }

    fun encode(op: OpEnvelope): String {
        val body = OperationJson.encodeToJsonElement(OpEnvelope.serializer(), op).jsonObject
        return buildJsonObject {
            put("v", VERSION)
            body.forEach { (k, v) -> put(k, v) }
        }.toString()
    }

    fun decode(line: String): DecodedLine {
        val obj = try { OperationJson.parseToJsonElement(line) as? JsonObject } catch (e: Exception) { null }
            ?: return DecodedLine.Corrupt(line, "not a JSON object")
        val version = (obj["v"] as? JsonPrimitive)?.intOrNull ?: return DecodedLine.Corrupt(line, "no version")
        if (version > VERSION) return DecodedLine.Unknown(line)
        val type = ((obj["op"] as? JsonObject)?.get("type") as? JsonPrimitive)?.content
        if (type != null && type !in knownTypes) return DecodedLine.Unknown(line)
        return try {
            DecodedLine.Known(OperationJson.decodeFromJsonElement(OpEnvelope.serializer(), JsonObject(obj - "v")))
        } catch (e: Exception) {
            DecodedLine.Corrupt(line, e.message ?: e::class.simpleName.orEmpty())
        }
    }
}
