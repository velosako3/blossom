// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.offline

import com.sun.net.httpserver.HttpServer
import io.github.velosako3.blossom.core.database.BlossomDatabase
import io.github.velosako3.blossom.core.database.fileDatabase
import io.github.velosako3.blossom.core.model.ArtistRef
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import io.github.velosako3.blossom.core.platform.InMemorySecureStore
import io.github.velosako3.blossom.core.platform.NetworkMonitor
import io.github.velosako3.blossom.core.platform.NetworkStatus
import io.github.velosako3.blossom.source.api.StreamInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import java.io.File
import java.net.InetSocketAddress
import java.util.Collections
import kotlin.random.Random

class FakeNetwork(online: Boolean = true, metered: Boolean = false) : NetworkMonitor {
    override val status = MutableStateFlow(NetworkStatus(online, metered))
}

/** Serves `/t/<id>` with deterministic bytes; honours `Range: bytes=n-`; can drop the first response part-way. */
class AudioServer(val sizes: Map<String, Int>) {
    val ranges: MutableList<String?> = Collections.synchronizedList(mutableListOf())
    @Volatile var dropFirstAt: Double? = null
    @Volatile var slowMs = 0L
    /** When set, responses wait for it to open (lets a test observe DOWNLOADING without racing). */
    @Volatile var gate: java.util.concurrent.CountDownLatch? = null
    /** Rejects (403) URLs whose token is older than this, like an expired signed stream URL. */
    @Volatile var minToken = 0
    /** Answers the next ranged request with a Content-Range that starts somewhere else. */
    @Volatile var wrongOffsetOnce = false
    /** Answers the next request with 416 and then serves this many bytes instead (the source changed). */
    @Volatile var resizeTo: Int? = null
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    fun bytes(id: String) = Random(id.hashCode() + (resizeSeed[id] ?: 0)).nextBytes(resizeSeen[id] ?: sizes.getValue(id))
    private val resizeSeen = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val resizeSeed = java.util.concurrent.ConcurrentHashMap<String, Int>()

    init {
        server.createContext("/t/") { ex ->
            val id = ex.requestURI.path.removePrefix("/t/")
            val range = ex.requestHeaders.getFirst("Range")
            ranges += range
            gate?.await(10, java.util.concurrent.TimeUnit.SECONDS)
            val token = ex.requestURI.query?.removePrefix("token=")?.toIntOrNull() ?: 0
            if (token < minToken) { ex.sendResponseHeaders(403, -1); ex.close(); return@createContext }
            resizeTo?.let { n -> resizeTo = null; resizeSeen[id] = n; resizeSeed[id] = 7; ex.sendResponseHeaders(416, -1); ex.close(); return@createContext }
            val all = bytes(id)
            val spec = range?.removePrefix("bytes=")?.split("-")
            val from = spec?.get(0)?.toInt() ?: 0
            val to = spec?.getOrNull(1)?.takeIf { it.isNotEmpty() }?.toInt()?.coerceAtMost(all.size - 1) ?: (all.size - 1)
            // A misbehaving server: sends (and names) a range that starts one byte earlier than asked.
            val shown = if (wrongOffsetOnce && range != null && from > 0) { wrongOffsetOnce = false; from - 1 } else from
            val body = all.copyOfRange(shown, to + 1)
            if (range != null) ex.responseHeaders.add("Content-Range", "bytes $shown-$to/${all.size}")
            ex.sendResponseHeaders(if (range != null) 206 else 200, body.size.toLong())
            val drop = dropFirstAt
            try {
                ex.responseBody.use { out ->
                    if (drop != null) {
                        dropFirstAt = null
                        out.write(body, 0, (body.size * drop).toInt()); out.flush()
                        throw java.io.IOException("drop") // closes the connection mid-body
                    }
                    var i = 0
                    while (i < body.size) {
                        val n = minOf(32 * 1024, body.size - i)
                        out.write(body, i, n); i += n
                        if (slowMs > 0) { out.flush(); Thread.sleep(slowMs) }
                    }
                }
            } catch (_: java.io.IOException) {
                ex.close()
            }
        }
        server.start()
    }

    fun url(id: String, token: Int = 0) = "http://127.0.0.1:${server.address.port}/t/$id?token=$token"
    fun stop() = server.stop(0)
}

fun track(id: String) = Track(TrackId.of(SourceKind.YOUTUBE_MUSIC, id), "Song $id", listOf(ArtistRef(null, "Artist")), null, 200_000, null)

class OfflineHarness(sizes: Map<String, Int>, val settings: MutableStateFlow<OfflineSettings> = MutableStateFlow(OfflineSettings(wifiOnly = false))) {
    val server = AudioServer(sizes)
    val network = FakeNetwork()
    private val root: File = kotlin.io.path.createTempDirectory("blossom-offline").toFile()
    val dir = File(root, "offline")
    // A file database like the app's: the in-memory driver shares one connection, so parallel transactions collide.
    val db: BlossomDatabase = fileDatabase(File(root, "blossom.db"))
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val vault = KeyVault(InMemorySecureStore())
    /** Each fresh resolve hands out a newer token. */
    @Volatile var resolves = 0
    val manager = DownloadManager(
        db, dir, vault,
        resolve = { t, fresh -> if (fresh) resolves++; StreamInfo(server.url(t.id.sourceId, resolves), mapOf("X-Test" to "1"), "audio/webm", 160, null) },
        http = OkHttpClient(), network = network, settings = settings, scope = scope, backoffMs = listOf(50, 50, 50),
    )

    fun close() {
        kotlinx.coroutines.runBlocking { scope.coroutineContext[kotlinx.coroutines.Job]!!.cancelAndJoin() }
        server.stop(); root.deleteRecursively()
    }
}
