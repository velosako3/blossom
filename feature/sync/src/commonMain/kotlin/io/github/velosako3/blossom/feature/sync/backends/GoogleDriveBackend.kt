// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.backends

import io.github.velosako3.blossom.feature.sync.RemoteFile
import io.github.velosako3.blossom.feature.sync.SyncBackend
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Google Drive's hidden app-data folder (`drive.appdata`: blossom sees only its own files, they don't appear in the
 * user's Drive). Drive has no folders there, so each file is named by its full sync path.
 */
class GoogleDriveBackend(
    private val oauth: OAuthClient,
    private val apiBase: String = "https://www.googleapis.com",
) : SyncBackend {
    override val id = "gdrive"
    private val json = Json { ignoreUnknownKeys = true }
    /** Path → Drive file ID, from the last listing. */
    private val ids = ConcurrentHashMap<String, String>()

    override suspend fun list(prefix: String): List<RemoteFile> {
        val out = mutableListOf<RemoteFile>()
        var page: String? = null
        do {
            val url = "$apiBase/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("spaces", "appDataFolder")
                .addQueryParameter("q", "trashed = false")
                .addQueryParameter("pageSize", "1000")
                .addQueryParameter("fields", "nextPageToken,files(id,name,size,md5Checksum,modifiedTime)")
                .apply { page?.let { addQueryParameter("pageToken", it) } }
                .build()
            val o = oauth.authorized { Request.Builder().url(url).header("Authorization", "Bearer $it").build() }.use { r ->
                if (!r.isSuccessful) throw IOException("Drive list HTTP ${r.code}")
                json.parseToJsonElement(r.body.string()) as JsonObject
            }
            for (f in (o["files"] as? JsonArray).orEmpty()) {
                f as JsonObject
                val name = (f["name"] as? JsonPrimitive)?.content ?: continue
                val fileId = (f["id"] as? JsonPrimitive)?.content ?: continue
                ids[name] = fileId
                if (name.startsWith(prefix)) out += RemoteFile(name, (f["size"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0, null, (f["md5Checksum"] as? JsonPrimitive)?.content)
            }
            page = (o["nextPageToken"] as? JsonPrimitive)?.content
        } while (page != null)
        return out
    }

    private suspend fun idOf(path: String): String? = ids[path] ?: run { list(path); ids[path] }

    override suspend fun read(path: String): ByteArray {
        val fileId = idOf(path) ?: throw FileNotFoundException(path)
        val url = "$apiBase/drive/v3/files/$fileId".toHttpUrl().newBuilder().addQueryParameter("alt", "media").build()
        return oauth.authorized { Request.Builder().url(url).header("Authorization", "Bearer $it").build() }.use { r ->
            if (r.code == 404) { ids.remove(path); throw FileNotFoundException(path) }
            if (!r.isSuccessful) throw IOException("Drive download HTTP ${r.code}")
            r.body.bytes()
        }
    }

    override suspend fun write(path: String, bytes: ByteArray) {
        val existing = idOf(path)
        val request: (String) -> Request = if (existing != null) { bearer ->
            Request.Builder()
                .url("$apiBase/upload/drive/v3/files/$existing".toHttpUrl().newBuilder().addQueryParameter("uploadType", "media").build())
                .header("Authorization", "Bearer $bearer")
                .patch(bytes.toRequestBody(OCTETS))
                .build()
        } else { bearer ->
            val meta = buildJsonObject { put("name", path); put("parents", buildJsonArray { add(JsonPrimitive("appDataFolder")) }) }.toString()
            val body = MultipartBody.Builder().setType("multipart/related".toMediaType())
                .addPart(meta.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .addPart(bytes.toRequestBody(OCTETS))
                .build()
            Request.Builder()
                .url("$apiBase/upload/drive/v3/files".toHttpUrl().newBuilder().addQueryParameter("uploadType", "multipart").addQueryParameter("fields", "id").build())
                .header("Authorization", "Bearer $bearer")
                .post(body)
                .build()
        }
        oauth.authorized(request).use { r ->
            if (!r.isSuccessful) throw IOException("Drive upload HTTP ${r.code}")
            ((json.parseToJsonElement(r.body.string()) as? JsonObject)?.get("id") as? JsonPrimitive)?.content?.let { ids[path] = it }
        }
    }

    private companion object { val OCTETS = "application/octet-stream".toMediaType() }
}
