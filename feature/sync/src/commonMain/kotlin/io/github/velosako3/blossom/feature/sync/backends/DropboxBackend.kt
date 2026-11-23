// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.backends

import io.github.velosako3.blossom.feature.sync.RemoteFile
import io.github.velosako3.blossom.feature.sync.SyncBackend
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.FileNotFoundException
import java.io.IOException

/** Dropbox with app-folder access: blossom only sees `Apps/<app name>/`, where the sync files live. */
class DropboxBackend(
    private val oauth: OAuthClient,
    private val apiBase: String = "https://api.dropboxapi.com",
    private val contentBase: String = "https://content.dropboxapi.com",
) : SyncBackend {
    override val id = "dropbox"
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun rpc(endpoint: String, args: JsonObject): JsonObject? = oauth.authorized { bearer ->
        Request.Builder().url("$apiBase/2/$endpoint").header("Authorization", "Bearer $bearer").post(args.toString().toRequestBody(JSON)).build()
    }.use { r ->
        if (r.code == 409) return@use null // e.g. path/not_found
        if (!r.isSuccessful) throw IOException("Dropbox $endpoint HTTP ${r.code}")
        json.parseToJsonElement(r.body.string()) as JsonObject
    }

    override suspend fun list(prefix: String): List<RemoteFile> {
        val out = mutableListOf<RemoteFile>()
        var page = rpc("files/list_folder", buildJsonObject { put("path", "/" + prefix.trim('/')); put("recursive", true) }) ?: return out
        while (true) {
            for (e in (page["entries"] as? JsonArray).orEmpty()) {
                e as JsonObject
                if ((e[".tag"] as? JsonPrimitive)?.content != "file") continue
                val path = (e["path_display"] as? JsonPrimitive)?.content?.removePrefix("/") ?: continue
                if (path.startsWith(prefix.trimStart('/'))) out += RemoteFile(path, (e["size"] as? JsonPrimitive)?.longOrNull ?: 0, null, (e["rev"] as? JsonPrimitive)?.content)
            }
            if ((page["has_more"] as? JsonPrimitive)?.booleanOrNull != true) break
            val cursor = (page["cursor"] as? JsonPrimitive)?.content ?: break
            page = rpc("files/list_folder/continue", buildJsonObject { put("cursor", cursor) }) ?: break
        }
        return out
    }

    override suspend fun read(path: String): ByteArray = oauth.authorized { bearer ->
        Request.Builder().url("$contentBase/2/files/download")
            .header("Authorization", "Bearer $bearer")
            .header("Dropbox-API-Arg", buildJsonObject { put("path", "/" + path.trim('/')) }.toString())
            .post(ByteArray(0).toRequestBody(null))
            .build()
    }.use { r ->
        if (r.code == 409) throw FileNotFoundException(path)
        if (!r.isSuccessful) throw IOException("Dropbox download HTTP ${r.code}")
        r.body.bytes()
    }

    override suspend fun write(path: String, bytes: ByteArray) = oauth.authorized { bearer ->
        Request.Builder().url("$contentBase/2/files/upload")
            .header("Authorization", "Bearer $bearer")
            .header("Dropbox-API-Arg", buildJsonObject { put("path", "/" + path.trim('/')); put("mode", "overwrite"); put("mute", true) }.toString())
            .post(bytes.toRequestBody("application/octet-stream".toMediaType()))
            .build()
    }.use { r -> if (!r.isSuccessful) throw IOException("Dropbox upload HTTP ${r.code}") }

    private companion object { val JSON = "application/json".toMediaType() }
}
