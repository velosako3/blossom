// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.offline

import io.github.velosako3.blossom.core.database.BlossomDatabase
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.platform.NetworkMonitor
import io.github.velosako3.blossom.source.api.StreamInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Downloads queued tracks into [dir], encrypting as the bytes arrive (no plaintext on disk). Up to 3 at once; each
 * retries 3 times (2 s, 8 s, 30 s); broken downloads resume with `Range`. Pauses on metered networks when Wi-Fi only,
 * and when the storage limit would be passed (nothing is evicted).
 */
class DownloadManager(
    private val db: BlossomDatabase,
    val dir: File,
    val vault: KeyVault,
    private val resolve: suspend (Track) -> StreamInfo,
    private val http: OkHttpClient,
    private val network: NetworkMonitor,
    val settings: StateFlow<OfflineSettings>,
    private val scope: CoroutineScope,
    private val backoffMs: List<Long> = listOf(2_000, 8_000, 30_000),
    parallel: Int = 3,
) {
    private val q get() = db.blossomQueries
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private val running = HashMap<String, Job>()
    private val slots = Semaphore(parallel)
    private val _busy = kotlinx.coroutines.flow.MutableStateFlow(false)
    /** True while any download runs. */
    val busy: StateFlow<Boolean> = _busy

    /** Called when downloads start; Android uses it to start the background worker. */
    @Volatile var onWorkStarted: (() -> Unit)? = null

    private class StorageFull : Exception()

    init {
        dir.mkdirs()
        // Network or settings changes may unblock paused items (or block running ones).
        scope.launch { combine(network.status, settings) { n, s -> n to s }.collect { kick(retryPaused = true) } }
    }

    /** Re-reads the queue. [retryPaused] also retries storage-paused items (after a limit change or freed space). */
    fun kick(retryPaused: Boolean = false) {
        scope.launch(Dispatchers.IO) { lock.withLock { schedule(retryPaused) } }
    }

    private fun allowed(): Boolean {
        val n = network.status.value
        return n.online && !(settings.value.wifiOnly && n.metered)
    }

    private fun schedule(retryPaused: Boolean) {
        val ok = allowed()
        for (row in q.offlinePending().executeAsList()) {
            val id = row.track_id
            if (!ok) {
                running.remove(id)?.cancel()
                q.offlineSetState(OfflineState.PAUSED_NETWORK.name, id)
                continue
            }
            if (id in running) continue
            if (row.state == OfflineState.PAUSED_STORAGE.name && !retryPaused) continue
            if (running.isEmpty()) onWorkStarted?.invoke()
            val track = json.decodeFromString(Track.serializer(), row.track_json)
            _busy.value = true
            running[id] = scope.launch(Dispatchers.IO) {
                try {
                    slots.withPermit { download(track) }
                } finally {
                    lock.withLock {
                        if (running[id] === coroutineContext[Job]) running.remove(id)
                        _busy.value = running.isNotEmpty()
                    }
                    kick()
                }
            }
        }
    }

    /** Stops [trackId]'s download (it was unsaved). */
    suspend fun cancel(trackId: String) = lock.withLock { running.remove(trackId)?.cancel() }

    suspend fun cancelAll() = lock.withLock { running.values.forEach { it.cancel() }; running.clear(); _busy.value = false }

    fun fileFor(trackId: String): File = File(dir, sha256(trackId) + ".blsm")

    private suspend fun download(track: Track) {
        val id = track.id.value
        if (q.offlineItem(id).executeAsOneOrNull() == null) return
        q.offlineSetState(OfflineState.DOWNLOADING.name, id)
        var attempt = 0
        while (true) {
            try {
                transfer(track)
                q.offlineSetState(OfflineState.SAVED.name, id)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (_: StorageFull) {
                q.offlineSetState(OfflineState.PAUSED_STORAGE.name, id)
                return
            } catch (_: Exception) {
                if (attempt >= backoffMs.size) { q.offlineSetState(OfflineState.FAILED.name, id); return }
                delay(backoffMs[attempt++])
            }
        }
    }

    private suspend fun transfer(track: Track) {
        val id = track.id.value
        val key = vault.offlineKey()
        val file = fileFor(id)
        q.offlineSetFile(file.name, q.offlineItem(id).executeAsOne().bytes, id)

        // Resume after the last complete chunk, if the file has a valid header.
        var prefix: ByteArray? = null
        var chunks = 0L
        if (file.length() >= ChunkedCipher.HEADER) {
            prefix = runCatching { ChunkedCipher.readHeader(file.inputStream().use { it.readNBytes(ChunkedCipher.HEADER) }) }.getOrNull()
            if (prefix != null) {
                chunks = (file.length() - ChunkedCipher.HEADER) / (ChunkedCipher.CHUNK + ChunkedCipher.TAG)
                RandomAccessFile(file, "rw").use { it.setLength(ChunkedCipher.HEADER + chunks * (ChunkedCipher.CHUNK + ChunkedCipher.TAG)) }
            }
        }
        var start = chunks * ChunkedCipher.CHUNK

        val stream = resolve(track)
        val request = Request.Builder().url(stream.url).apply {
            stream.headers.forEach { (k, v) -> header(k, v) }
            if (start > 0) header("Range", "bytes=$start-")
        }.build()
        val call = http.newCall(request)
        val response = runInterruptible { call.execute() }
        response.use { r ->
            if (start > 0 && r.code == 200) { start = 0; chunks = 0; prefix = null } // server ignored Range: start over
            if (r.code != 200 && r.code != 206) throw IOException("HTTP ${r.code}")
            val body = r.body
            val length = body.contentLength()
            val total = if (length >= 0) start + length else -1L
            val limit = settings.value.limitBytes
            val others = q.offlineUsageExcept(id).executeAsOne()
            if (limit != null && total > 0 && others + total > limit) throw StorageFull()
            q.offlineSetFile(file.name, if (total > 0) total else 0, id)

            FileOutputStream(file, prefix != null).use { out ->
                val enc = prefix?.let { ChunkedEncryptor.resume(out, key, it, chunks) } ?: ChunkedEncryptor.create(out, key)
                val buf = ByteArray(64 * 1024)
                var done = start
                var reported = done
                val input = body.byteStream()
                while (true) {
                    val n = runInterruptible { input.read(buf) }
                    if (n < 0) break
                    enc.write(buf, 0, n)
                    done += n
                    if (limit != null && others + done > limit) throw StorageFull()
                    if (done - reported >= 256 * 1024) { q.offlineSetProgress(done, id); reported = done }
                }
                if (total > 0 && done != total) throw IOException("short body: $done of $total")
                enc.finish()
                if (total <= 0) q.offlineSetFile(file.name, done, id)
                q.offlineSetProgress(done, id)
            }
        }
    }

    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.encodeToByteArray()).joinToString("") { "%02x".format(it) }
}
