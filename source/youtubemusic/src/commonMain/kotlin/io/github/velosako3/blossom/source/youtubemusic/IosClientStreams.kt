// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.youtubemusic

import io.github.velosako3.blossom.source.api.AudioQuality
import io.github.velosako3.blossom.source.api.StreamInfo
import io.github.velosako3.blossom.source.api.TrackUnavailableException
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Audio streams as YouTube's own iPhone app gets them: its player endpoint answers with plain URLs (no signature to
 * decipher, so no JavaScript), and iOS's player plays their AAC directly. Used on iOS, where NewPipe can't run.
 * Opus/WebM is skipped: AVPlayer doesn't play it.
 */
class IosClientStreams(
    private val http: HttpClient = HttpClient { install(HttpTimeout) { requestTimeoutMillis = 20_000 } },
    private val clock: Clock = Clock.System,
) : YtmStreams {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun resolve(videoId: String, quality: AudioQuality): StreamInfo {
        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "IOS"); put("clientVersion", CLIENT_VERSION)
                    put("deviceMake", "Apple"); put("deviceModel", DEVICE_MODEL)
                    put("osName", "iPhone"); put("osVersion", OS_VERSION)
                    put("hl", "en"); put("gl", "US")
                }
            }
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }
        val response = http.post("https://www.youtube.com/youtubei/v1/player?prettyPrint=false") {
            contentType(ContentType.Application.Json)
            header("User-Agent", USER_AGENT)
            header("X-Youtube-Client-Name", "5")
            header("X-Youtube-Client-Version", CLIENT_VERSION)
            setBody(body.toString())
        }
        if (!response.status.isSuccess()) throw IOException("YouTube player HTTP ${response.status.value}")
        return pick(json.parseToJsonElement(response.bodyAsText()) as JsonObject, quality, clock)
    }

    companion object {
        const val CLIENT_VERSION = "20.10.4"
        private const val DEVICE_MODEL = "iPhone16,2"
        private const val OS_VERSION = "18.3.2.22D82"
        const val USER_AGENT = "com.google.ios.youtube/$CLIENT_VERSION ($DEVICE_MODEL; U; CPU iOS 18_3_2 like Mac OS X;)"

        /** The AAC stream for [quality] from a player response: highest bitrate for HIGH, lowest for LOW. */
        fun pick(player: JsonObject, quality: AudioQuality, clock: Clock = Clock.System): StreamInfo {
            val status = player["playabilityStatus"] as? JsonObject
            val state = (status?.get("status") as? JsonPrimitive)?.contentOrNull
            if (state != "OK") throw TrackUnavailableException((status?.get("reason") as? JsonPrimitive)?.contentOrNull ?: "Not playable ($state)")
            val data = player["streamingData"] as? JsonObject ?: throw TrackUnavailableException("No streams")
            val aac = (data["adaptiveFormats"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.filter { f ->
                val mime = (f["mimeType"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                mime.startsWith("audio/mp4") && (f["url"] as? JsonPrimitive)?.contentOrNull != null
            }
            fun bitrate(f: JsonObject) = (f["bitrate"] as? JsonPrimitive)?.longOrNull ?: 0
            val chosen = when (quality) {
                AudioQuality.LOW -> aac.minByOrNull(::bitrate)
                else -> aac.maxByOrNull(::bitrate)
            } ?: throw TrackUnavailableException("No AAC stream")
            val expiresIn = (data["expiresInSeconds"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
            return StreamInfo(
                url = (chosen["url"] as JsonPrimitive).content,
                headers = mapOf("User-Agent" to USER_AGENT),
                mimeType = (chosen["mimeType"] as JsonPrimitive).content.substringBefore(';'),
                bitrateKbps = (bitrate(chosen) / 1000).toInt().takeIf { it > 0 },
                expiresAt = expiresIn?.let { clock.now() + it.seconds },
            )
        }
    }
}
