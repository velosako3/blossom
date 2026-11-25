// SPDX-License-Identifier: GPL-3.0-only
// Ported from BitChord (https://github.com/kushagrasinghx/BitChord) v1.9.1,
// shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/innertube/{StreamResolver,InnerTubeXResolver,PlayerClient}.kt
// (GPL-3.0). Modified for blossom, 2026: one class behind MusicSource.resolveStream returning StreamInfo; upload
// escalation, loudness, video and listen-together paths removed; NewPipe formats chosen by selectAudioFormat.
package io.github.velosako3.blossom.source.youtubemusic

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import com.metrolist.innertubex.models.YouTubeLocale
import io.github.velosako3.blossom.source.api.AudioQuality
import io.github.velosako3.blossom.source.api.StreamInfo
import io.github.velosako3.blossom.source.api.TrackUnavailableException
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Turns a YouTube video ID into a stream URL: InnerTubeX first (client catalog, cipher, PoToken contracts), NewPipe
 * Extractor as the failsafe, and nothing is returned until a ranged probe has fetched real audio bytes from it.
 */
class YouTubeStreams(
    private val cookie: suspend () -> String?,
    cacheDir: File,
    private val clock: Clock = Clock.System,
) : YtmStreams {
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val http = HttpClient(OkHttp) {
        engine { preconfigured = okHttp }
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }) }
        install(HttpTimeout) { requestTimeoutMillis = 30_000; connectTimeoutMillis = 15_000; socketTimeoutMillis = 20_000 }
        expectSuccess = false
    }

    private val configFile = File(cacheDir, "innertubex_player_config.properties")
    private val repository = object : PlayerConfigRepository {
        private val props = java.util.Properties().apply { if (configFile.isFile) configFile.inputStream().use(::load) }
        private fun save() = runCatching { configFile.parentFile?.mkdirs(); configFile.outputStream().use { props.store(it, null) } }
        override val enabled: Boolean = true
        override val sourceUrl: String = PLAYER_CONFIG_URL
        override val defaultSourceUrl: String = PLAYER_CONFIG_URL
        override var cachedJson: String
            get() = props.getProperty("json").orEmpty()
            set(v) { props.setProperty("json", v); save() }
        override var cachedAtMs: Long
            get() = props.getProperty("cached_at_ms")?.toLongOrNull() ?: 0L
            set(v) { props.setProperty("cached_at_ms", v.toString()); save() }
        override var cachedSourceUrl: String
            get() = props.getProperty("source_url").orEmpty()
            set(v) { props.setProperty("source_url", v); save() }
        override var cachedEtag: String
            get() = props.getProperty("etag").orEmpty()
            set(v) { props.setProperty("etag", v); save() }
    }

    private val innerTube = InnerTube(http)
    private val remoteStore = RemotePlayerConfigStore(http, repository)
    private val cipherService = YouTubeCipherService(http, remoteStore)
    private val extractor = InnerTubeExtractor(
        configParser = YtConfigParserImpl(http, innerTube, remoteStore, cipherService = cipherService),
        cipherService = cipherService,
        innerTube = innerTube,
    )
    private val visitor = InnertubeClient(cookie)
    private val newPipeGate = Mutex()

    override suspend fun resolve(videoId: String, quality: AudioQuality): StreamInfo = withContext(Dispatchers.IO) {
        val found = try {
            innerTubeX(videoId, quality) ?: newPipe(videoId, quality)
        } catch (e: CancellationException) {
            throw e
        } catch (e: TrackUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw TrackUnavailableException("Couldn't resolve $videoId: ${e.message}", e)
        }
        found
    }

    private suspend fun innerTubeX(videoId: String, quality: AudioQuality): StreamInfo? {
        val skip = mutableSetOf<String>()
        repeat(INNERTUBEX_ATTEMPTS) {
            innerTube.cookie = cookie()
            innerTube.visitorData = visitor.ensureVisitorData()
            innerTube.locale = YouTubeLocale(gl = java.util.Locale.getDefault().country.ifBlank { "US" }, hl = java.util.Locale.getDefault().language.ifBlank { "en" })
            val stream = runCatching {
                extractor.extract(
                    videoId = videoId,
                    hints = ContentHints().withStreamCapabilities(allowHls = false, allowSabr = false, allowBoundedRange = true),
                    excludedClients = skip,
                    audioQuality = when (quality) {
                        AudioQuality.LOW -> com.metrolist.innertubex.extraction.AudioQuality.LOW
                        AudioQuality.NORMAL -> com.metrolist.innertubex.extraction.AudioQuality.AUTO
                        AudioQuality.HIGH -> com.metrolist.innertubex.extraction.AudioQuality.HIGH
                    },
                    clientPlaybackNonce = generateClientPlaybackNonce(),
                )
            }.onFailure { if (it is CancellationException) throw it }.getOrNull() ?: return null
            if (stream.sabrBootstrap != null) { skip += stream.profileId; return@repeat }
            val mime = stream.mimeType.orEmpty()
            val info = StreamInfo(
                url = stream.audioUrl,
                headers = stream.headers,
                mimeType = if (stream.codecs.isNullOrBlank()) mime else "$mime; codecs=\"${stream.codecs}\"",
                bitrateKbps = stream.bitrate?.div(1000),
                expiresAt = expiryOf(stream.audioUrl),
            )
            if (probe(info)) return info
            skip += stream.profileId
        }
        return null
    }

    private suspend fun newPipe(videoId: String, quality: AudioQuality): StreamInfo = newPipeGate.withLock {
        newPipeInit
        val extractor = try {
            ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId").also { it.fetchPage() }
        } catch (e: AgeRestrictedContentException) {
            throw TrackUnavailableException("This track is age-restricted", e)
        } catch (e: ContentNotAvailableException) {
            throw TrackUnavailableException(e.message ?: "This track isn't available", e)
        }
        val formats = extractor.audioStreams
            .filter { !it.content.isNullOrBlank() && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .map { AudioFormat(it.codec ?: it.format?.name.orEmpty(), it.averageBitrate, it.content, it.format?.mimeType.orEmpty()) }
        val chosen = selectAudioFormat(formats, quality)
        StreamInfo(chosen.url, mediaHeadersFor(chosen.url), chosen.mimeType, chosen.bitrateKbps, expiryOf(chosen.url))
    }

    /** A real ranged fetch: only audio bytes count as playable. */
    private fun probe(info: StreamInfo): Boolean {
        val builder = okhttp3.Request.Builder().url(info.url).header("Range", "bytes=0-${PROBE_BYTES - 1}")
        info.headers.forEach { (k, v) -> builder.header(k, v) }
        return runCatching {
            okHttp.newCall(builder.build()).execute().use { r ->
                r.code in 200..299 && r.header("Content-Type")?.startsWith("audio/") == true && r.body.source().request(1024)
            }
        }.getOrDefault(false)
    }

    private fun expiryOf(url: String): Instant? =
        url.toHttpUrlOrNull()?.queryParameter("expire")?.toLongOrNull()?.let { Instant.fromEpochSeconds(it) }
            ?: (clock.now() + 5.hours)

    private val newPipeInit by lazy { NewPipe.init(OkHttpDownloader()) }

    internal fun ensureNewPipe() = newPipeInit

    private inner class OkHttpDownloader : Downloader() {
        override fun execute(request: Request): Response {
            val builder = okhttp3.Request.Builder().method(request.httpMethod(), request.dataToSend()?.toRequestBody()).url(request.url())
            var hasUa = false
            request.headers().forEach { (name, values) ->
                if (name.equals("User-Agent", true) && values.isNotEmpty()) hasUa = true
                if (values.size > 1) { builder.removeHeader(name); values.forEach { builder.addHeader(name, it) } }
                else if (values.size == 1) builder.header(name, values[0])
            }
            if (!hasUa) builder.header("User-Agent", InnertubeClient.WEB_USER_AGENT)
            val response = okHttp.newCall(builder.build()).execute()
            if (response.code == 429) { response.close(); throw ReCaptchaException("reCaptcha Challenge requested", request.url()) }
            return Response(response.code, response.message, response.headers.toMultimap(), response.body.string(), response.request.url.toString())
        }
    }

    /** The headers a media fetch must carry to match the client that minted [url] (googlevideo checks `c=`). */
    private fun mediaHeadersFor(url: String): Map<String, String> {
        val c = url.toHttpUrlOrNull()?.queryParameter("c")?.uppercase().orEmpty()
        val ua = when {
            c.startsWith("IOS") -> "com.google.ios.youtube/21.29.1 (iPhone16,2; U; CPU iOS 18_5 like Mac OS X;)"
            c == "ANDROID_VR" -> "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
            c == "ANDROID_MUSIC" -> "com.google.android.apps.youtube.music/8.39.42 (Linux; U; Android 15; en_US; Pixel 9 Pro; Build/AP4A.250205.002) gzip"
            c.startsWith("ANDROID") -> "com.google.android.youtube/21.26.364 (Linux; U; Android 15; en_US; Pixel 9 Pro; Build/AP4A.250205.002; Cronet/132.0.6834.79) gzip"
            c.startsWith("TVHTML5") -> "Mozilla/5.0(SMART-TV; Linux; Tizen 4.0.0.2) AppleWebkit/605.1.15 (KHTML, like Gecko) SamsungBrowser/9.2 TV Safari/605.1.15"
            else -> InnertubeClient.WEB_USER_AGENT
        }
        val origin = when {
            c == "WEB_REMIX" -> "https://music.youtube.com"
            c.startsWith("WEB") || c == "MWEB" || c.startsWith("TVHTML5") -> "https://www.youtube.com"
            else -> null
        }
        return buildMap {
            put("User-Agent", ua)
            origin?.let { put("Origin", it); put("Referer", "$it/") }
        }
    }

    private companion object {
        const val PLAYER_CONFIG_URL = "https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json"
        const val INNERTUBEX_ATTEMPTS = 3
        const val PROBE_BYTES = 16L * 1024
    }
}
