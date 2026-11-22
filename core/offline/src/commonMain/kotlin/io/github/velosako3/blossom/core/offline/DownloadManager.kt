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
import kotlinx.coroutines.cancelAndJoin
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
    /** The stream to download; `fresh = true` after a 403/410 (the URL expired), bypassing any cache. */
    private val resolve: suspend (track: Track, fresh: Boolean) -> StreamInfo,
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
        if (!allowed()) {
            running.values.forEach { it.cancel() }
            running.clear()
            _busy.value = false
            // One conditional update: never touches SAVED or FAILED rows, whatever a stale read said.
            q.offlinePauseForNetwork()
            return
        }
        for (row in q.offlinePending().executeAsList()) {
            val id = row.track_id
            if (id in running) continue
            if (row.state == OfflineState.PAUSED_STORAGE.name && !retryPaused) continue
            if (running.isEmpty()) onWorkStarted?.invoke()
            val track = json.decodeFromString(Track.serializer(), row.track_json)
            _busy.value = true
            running[id] = scope.launch(Dispatchers.IO) {
                try {
                    download(track)
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

    /** Stops [trackId]'s download and waits until it has stopped writing (it was unsaved). */
    suspend fun cancel(trackId: String) {
        lock.withLock { running.remove(trackId) }?.cancelAndJoin()
    }

    suspend fun cancelAll() {
        val jobs = lock.withLock { running.values.toList().also { running.clear(); _busy.value = false } }
        jobs.forEach { it.cancelAndJoin() }
    }

    fun fileFor(trackId: String): File = File(dir, sha256(trackId) + ".blsm")

    private suspend fun download(track: Track) {
        val id = track.id.value
        if (q.offlineItem(id).executeAsOneOrNull() == null) return
        q.offlineSetState(OfflineState.DOWNLOADING.name, id)
        var attempt = 0
        var restarts = 0
        while (true) {
            var progressed = false
            try {
                // A download slot is held only while bytes move, never during a backoff wait.
                val finished = slots.withPermit { transfer(track) { progressed = true } }
                if (finished) q.offlineSetState(OfflineState.SAVED.name, id)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (_: RestartFromZero) {
                fileFor(id).delete()
                if (++restarts > 3) { q.offlineSetState(OfflineState.FAILED.name, id); return }
            } catch (_: StorageFull) {
                q.offlineSetState(OfflineState.PAUSED_STORAGE.name, id)
                return
            } catch (_: Exception) {
                if (progressed) attempt = 0 // three failures in a row, not three in the whole song
                if (attempt >= backoffMs.size) { q.offlineSetState(OfflineState.FAILED.name, id); return }
                delay(backoffMs[attempt++])
            }
        }
    }

    /** Downloads what's missing. False when the row disappeared (unsaved) and there's nothing to finish. */
    private suspend fun transfer(track: Track, onProgress: () -> Unit): Boolean {
        val id = track.id.value
        val key = vault.offlineKey()
        val file = fileFor(id)
        val row = q.offlineItem(id).executeAsOneOrNull() ?: return false

        // A file that already authenticates to its last chunk is complete (a crash came before SAVED was recorded).
        if (file.isFile && isComplete(file, key)) {
            val length = DecryptingReader(file, key).let { r -> r.plainLength.also { r.close() } }
            q.offlineSetFile(file.name, length, id)
            q.offlineSetProgress(length, id)
            return true
        }
        // Resume after the last whole chunk, but only if nothing past it was written: re-encrypting a chunk index
        // that is already on disk (perhaps with other bytes) would reuse a nonce. Otherwise start over with new nonces.
        var prefix: ByteArray? = null
        var chunks = 0L
        if (file.length() >= ChunkedCipher.HEADER) {
            val body = file.length() - ChunkedCipher.HEADER
            prefix = runCatching { ChunkedCipher.readHeader(file.inputStream().use { it.readNBytes(ChunkedCipher.HEADER) }) }.getOrNull()
            if (prefix == null || body % (ChunkedCipher.CHUNK + ChunkedCipher.TAG) != 0L) { prefix = null; file.delete() }
            else chunks = body / (ChunkedCipher.CHUNK + ChunkedCipher.TAG)
        } else file.delete()
        q.offlineSetFile(file.name, row.bytes, id)

        val start = chunks * ChunkedCipher.CHUNK
        var stream = resolve(track, false)
        var refreshed = false
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
                val again = runInterruptible { call.execute() }.use { r ->
                    when (r.code) {
                        403, 410 -> {
                            // The signed URL expired mid-song: resolve it again once, then go on from here.
                            if (refreshed) throw IOException("HTTP ${r.code} after refreshing the stream")
                            refreshed = true
                            return@use true
                        }
                        416 -> throw RestartFromZero() // the source changed size
                        200, 206 -> Unit
                        else -> throw IOException("HTTP ${r.code}")
                    }
                    val whole = r.code == 200 // the server ignored Range and sends everything
                    if (whole && done > 0) throw RestartFromZero()
                    if (whole) total = r.body.contentLength()
                    else {
                        val range = CONTENT_RANGE.matchEntire(r.header("Content-Range").orEmpty()) ?: throw IOException("bad Content-Range")
                        if (range.groupValues[1].toLong() != done) throw IOException("server sent bytes from ${range.groupValues[1]}, asked $done")
                        val size = range.groupValues[3].toLong()
                        if (total >= 0 && size != total) throw RestartFromZero()
                        total = size
                    }
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
                        onProgress()
                        if (limit != null && others + done > limit) throw StorageFull()
                        if (done - reported >= PROGRESS_STEP) { q.offlineSetProgress(done, id); reported = done }
                    }
                    if (whole) { if (total < 0) total = done }
                    else if (got == 0L) throw IOException("empty range at $done of $total")
                    refreshed = false
                    false
                }
                if (again) stream = resolve(track, true)
            }
            if (done != total) throw IOException("got $done of $total bytes")
            enc.finish()
            q.offlineSetFile(file.name, total, id)
            q.offlineSetProgress(done, id)
        }
        return true
    }

    private fun isComplete(file: File, key: ByteArray): Boolean = try {
        val r = DecryptingReader(file, key)
        try { r.plainLength == 0L || r.read(r.plainLength - 1, ByteArray(1), 0, 1) == 1 } finally { r.close() }
    } catch (_: Exception) {
        false
    }

    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.encodeToByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        /** Per request; YouTube serves bounded ranges up to ~10 MB at full speed. */
        const val REQUEST_BYTES = 2L * 1024 * 1024
        /** Progress is written to the database every MiB (each write re-runs the offline flows). */
        const val PROGRESS_STEP = 1024L * 1024
        val CONTENT_RANGE = Regex("""bytes (\d+)-(\d+)/(\d+)""")
    }
}
