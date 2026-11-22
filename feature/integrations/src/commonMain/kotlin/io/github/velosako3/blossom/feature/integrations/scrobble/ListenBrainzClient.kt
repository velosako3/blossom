// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.integrations.scrobble

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** ListenBrainz `submit-listens` with the user's token (pasted in Settings). */
class ListenBrainzClient(
    private val token: suspend () -> String?,
    private val http: OkHttpClient = OkHttpClient(),
    private val endpoint: String = "https://api.listenbrainz.org/1/",
) : ScrobbleService {
    override val id = "listenbrainz"
    @Volatile var hasToken = false
    override val enabled get() = hasToken

    private fun metadata(t: ScrobbleTrack): JsonObject = buildJsonObject {
        put("artist_name", t.artist); put("track_name", t.title)
        t.album?.let { put("release_name", it) }
        putJsonObject("additional_info") {
            put("media_player", "blossom"); put("submission_client", "blossom")
            if (t.durationMs > 0) put("duration_ms", t.durationMs)
        }
    }

    private suspend fun submit(type: String, listen: JsonObject) {
        val body = buildJsonObject { put("listen_type", type); put("payload", buildJsonArray { add(listen) }) }.toString()
        val request = Request.Builder().url(endpoint + "submit-listens")
            .header("Authorization", "Token ${token() ?: throw IOException("ListenBrainz: no token")}")
            .post(body.toRequestBody("application/json".toMediaType())).build()
        runInterruptible(Dispatchers.IO) { http.newCall(request).execute().use { if (!it.isSuccessful) throw IOException("ListenBrainz: HTTP ${it.code}") } }
    }

    override suspend fun nowPlaying(track: ScrobbleTrack) = submit("playing_now", buildJsonObject { put("track_metadata", metadata(track)) })

    override suspend fun scrobble(item: Scrobble) =
        submit("single", buildJsonObject { put("listened_at", item.startedAt.epochSeconds); put("track_metadata", metadata(item.track)) })
}
