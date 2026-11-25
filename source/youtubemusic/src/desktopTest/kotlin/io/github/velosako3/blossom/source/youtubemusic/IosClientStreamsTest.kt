// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.youtubemusic

import io.github.velosako3.blossom.source.api.AudioQuality
import io.github.velosako3.blossom.source.api.TrackUnavailableException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IosClientStreamsTest {
    private fun player(json: String) = Json.parseToJsonElement(json) as JsonObject
    private val ok = player(
        """{"playabilityStatus":{"status":"OK"},"streamingData":{"expiresInSeconds":"21540","adaptiveFormats":[
           {"mimeType":"video/mp4; codecs=\"avc1\"","bitrate":900000,"url":"https://v/video"},
           {"mimeType":"audio/webm; codecs=\"opus\"","bitrate":160000,"url":"https://v/opus"},
           {"mimeType":"audio/mp4; codecs=\"mp4a.40.5\"","bitrate":50000,"url":"https://v/aac-low"},
           {"mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","bitrate":130000,"url":"https://v/aac-high"}]}}""",
    )

    @Test fun aacOnlyBestOrSmallest() {
        assertEquals("https://v/aac-high", IosClientStreams.pick(ok, AudioQuality.HIGH).url)
        assertEquals("https://v/aac-low", IosClientStreams.pick(ok, AudioQuality.LOW).url)
        assertEquals("audio/mp4", IosClientStreams.pick(ok, AudioQuality.HIGH).mimeType)
    }

    @Test fun unplayableSaysWhy() {
        val e = assertFailsWith<TrackUnavailableException> { IosClientStreams.pick(player("""{"playabilityStatus":{"status":"UNPLAYABLE","reason":"Private video"}}"""), AudioQuality.HIGH) }
        assertEquals("Private video", e.message)
    }
}
