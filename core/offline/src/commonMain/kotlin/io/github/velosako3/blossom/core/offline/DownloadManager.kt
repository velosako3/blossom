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
    private val restartFromZero = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val _busy = kotlinx.coroutines.flow.MutableStateFlow(false)
    /** True while any download runs. */
    val busy: StateFlow<Boolean> = _busy

    /** Called when downloads start; Android uses it to start the background worker. */
    @Volatile var onWorkStarted: (() -> Unit)? = null

    private class StorageFull : Exception()
    /** The server doesn't honour Range, so a partial file can't be continued. */
    private class RestartFromZero : IOException("server ignores Range")

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
            } catch (_: RestartFromZero) {
                restartFromZero += id
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

        // Resume after the last complete chunk, if the file has a valid header and the server honours ranges.
        if (restartFromZero.remove(id)) file.delete()
        var prefix: ByteArray? = null
        var chunks = 0L
        if (file.length() >= ChunkedCipher.HEADER) {
            prefix = runCatching { ChunkedCipher.readHeader(file.inputStream().use { it.readNBytes(ChunkedCipher.HEADER) }) }.getOrNull()
            if (prefix != null) {
                chunks = (file.length() - ChunkedCipher.HEADER) / (ChunkedCipher.CHUNK + ChunkedCipher.TAG)
                RandomAccessFile(file, "rw").use { it.setLength(ChunkedCipher.HEADER + chunks * (ChunkedCipher.CHUNK + ChunkedCipher.TAG)) }
            }
        }
        val start = chunks * ChunkedCipher.CHUNK
        val stream = resolve(track)
        val limit = settings.value.limitBytes
        val others = q.offlineUsageExcept(id).executeAsOne()

        FileOutputStream(file, prefix != null).use { out ->
            val enc = prefix?.let { ChunkedEncryptor.resume(out, key, it, chunks) } ?: ChunkedEncryptor.create(out, key)
            var done = start
            var total = -1L
            var reported = done
            val buf = ByteArray(64 * 1024)
            // Bounded ranges: YouTube throttles open-ended downloads to playback speed.
            while (total < 0 || done < total) {
                val request = Request.Builder().url(stream.url).apply {
                    stream.headers.forEach { (k, v) -> header(k, v) }
                    header("Range", "bytes=$done-${done + REQUEST_BYTES - 1}")
                }.build()
                val call = http.newCall(request)
                runInterruptible { call.execute() }.use { r ->
                    val whole = r.code == 200 // the server ignored Range and sends everything
                    if (!whole && r.code != 206) throw IOException("HTTP ${r.code}")
                    if (whole && done > 0) throw RestartFromZero()
                    total = if (whole) r.body.contentLength() else
                        r.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: throw IOException("no Content-Range")
                    if (total > 0) {
                        if (limit != null && others + total > limit) throw StorageFull()
                        q.offlineSetFile(file.name, total, id)
                    }
                    val input = r.body.byteStream()
                    var got = 0L
                    while (true) {
                        val n = runInterruptible { input.read(buf) }
                        if (n < 0) break
                        enc.write(buf, 0, n)
                        done += n; got += n
                        if (limit != null && others + done > limit) throw StorageFull()
                        if (done - reported >= 256 * 1024) { q.offlineSetProgress(done, id); reported = done }
                    }
                    if (whole) { if (total < 0) total = done }
                    else if (got == 0L) throw IOException("empty range at $done of $total")
                }
            }
            if (done != total) throw IOException("got $done of $total bytes")
            enc.finish()
            q.offlineSetFile(file.name, total, id)
            q.offlineSetProgress(done, id)
        }
    }

    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.encodeToByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        /** Per request; YouTube serves bounded ranges up to ~10 MB at full speed. */
        const val REQUEST_BYTES = 2L * 1024 * 1024
    }
}
