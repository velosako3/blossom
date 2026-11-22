// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync

import io.github.velosako3.blossom.core.database.BlossomDatabase
import io.github.velosako3.blossom.core.database.LibraryProjector
import io.github.velosako3.blossom.core.database.SqlOpLog
import io.github.velosako3.blossom.core.model.sync.OpEnvelope
import io.github.velosako3.blossom.core.model.sync.Operation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Clock

data class SyncReport(val pushed: Int, val pulled: Int, val devicesSeen: List<DeviceManifest>, val errors: List<String>)

/**
 * One device's view of sync: it appends its own operations to its own folder in [backend] and reads everyone else's.
 * The engine never writes outside `blossom/v1/devices/<deviceId>/`. Merging is the projector's last-writer-wins, so
 * the order files are read in doesn't matter. A corrupt line from another device is reported and skipped; the rest
 * still applies. Operation types from newer versions are left alone.
 */
class SyncEngine(
    private val backend: SyncBackend,
    private val opLog: SqlOpLog,
    private val projector: LibraryProjector,
    private val db: BlossomDatabase,
    private val deviceId: String,
    private val deviceName: String,
    private val clock: Clock = Clock.System,
    private val segmentMaxBytes: Long = SyncLayout.SEGMENT_MAX_BYTES,
) {
    private val lock = Mutex()

    suspend fun syncOnce(): SyncReport = lock.withLock {
        val errors = mutableListOf<String>()
        val pushed = try { push() } catch (e: CancellationException) { throw e } catch (e: Exception) { errors += "push: ${e.message}"; 0 }
        val (pulled, seen) = try { pull(errors) } catch (e: CancellationException) { throw e } catch (e: Exception) { errors += "pull: ${e.message}"; 0 to emptyList() }
        SyncReport(pushed, pulled, seen, errors)
    }

    private suspend fun ownManifest(): DeviceManifest? =
        runCatching { DeviceManifest.decode(String(backend.read(SyncLayout.manifest(deviceId)))) }.getOrNull()

    /** Appends this device's new operations to its newest segment (rolling over at the size limit), then the manifest. */
    private suspend fun push(): Int {
        val manifest = ownManifest() ?: DeviceManifest(deviceId, deviceName, emptyList(), 0, clock.now())
        val fresh = opLog.since(deviceId, manifest.lastSeq)
        if (fresh.isEmpty() && manifest.deviceName == deviceName && manifest.segments.isNotEmpty()) return 0
        val segments = manifest.segments.toMutableList()
        var current = segments.lastOrNull()
        var buffer = StringBuilder(current?.let { String(backend.read(SyncLayout.segment(deviceId, it.index))) } ?: "")
        var first = current?.firstSeq ?: fresh.firstOrNull()?.seq ?: 0
        var index = current?.index ?: 0
        val dirty = mutableSetOf<Int>()

        fun close() {
            if (buffer.isEmpty()) return
            val info = SegmentInfo(index, buffer.length.toLong(), first, lastSeqOf(buffer))
            if (segments.lastOrNull()?.index == index) segments[segments.lastIndex] = info else segments += info
        }
        val contents = HashMap<Int, String>()
        for (op in fresh) {
            val line = OpLogCodec.encode(op) + "\n"
            if (buffer.isNotEmpty() && buffer.length + line.length > segmentMaxBytes) {
                close(); contents[index] = buffer.toString()
                index++; buffer = StringBuilder(); first = op.seq
            }
            buffer.append(line)
            dirty += index
        }
        close(); contents[index] = buffer.toString()
        // Older segments never change: only the ones that got new lines are written, then the manifest.
        for (i in dirty) backend.write(SyncLayout.segment(deviceId, i), contents.getValue(i).toByteArray())
        val lastSeq = fresh.lastOrNull()?.seq ?: manifest.lastSeq
        backend.write(SyncLayout.manifest(deviceId), DeviceManifest(deviceId, deviceName, segments, lastSeq, clock.now()).encode().toByteArray())
        current = null
        return fresh.size
    }

    private fun lastSeqOf(segment: CharSequence): Long =
        segment.lineSequence().filter { it.isNotBlank() }.lastOrNull()?.let { (OpLogCodec.decode(it) as? DecodedLine.Known)?.op?.seq } ?: 0

    /** Reads every other device's new operations, inserts them and projects them in one transaction per device. */
    private suspend fun pull(errors: MutableList<String>): Pair<Int, List<DeviceManifest>> {
        val known = opLog.knownDevices()
        val manifests = backend.list(SyncLayout.DEVICES).filter { it.path.endsWith("/manifest.json") }.mapNotNull { f ->
            try { DeviceManifest.decode(String(backend.read(f.path))) } catch (e: CancellationException) { throw e } catch (e: Exception) { errors += "${f.path}: unreadable manifest"; null }
        }
        var pulled = 0
        for (m in manifests) {
            if (m.deviceId == deviceId) continue
            val have = known[m.deviceId] ?: 0
            if (m.lastSeq <= have) continue
            val ops = mutableListOf<OpEnvelope>()
            for (seg in m.segments.filter { it.lastSeq > have }) {
                val path = SyncLayout.segment(m.deviceId, seg.index)
                val text = try { String(backend.read(path)) } catch (e: CancellationException) { throw e } catch (e: Exception) { errors += "$path: unreadable"; continue }
                text.lineSequence().filter { it.isNotBlank() }.forEachIndexed { n, line ->
                    when (val d = OpLogCodec.decode(line)) {
                        is DecodedLine.Known -> if (d.op.seq > have && d.op.deviceId == m.deviceId) ops += d.op
                        is DecodedLine.Unknown -> Unit // a newer blossom's operation: kept in the file, not applied here
                        is DecodedLine.Corrupt -> errors += "$path:${n + 1}: ${d.error}"
                    }
                }
            }
            val applied = withContext(Dispatchers.IO) {
                db.transactionWithResult {
                    val inserted = opLog.insertRemoteInTransaction(ops)
                    // Device-specific or unknown setting keys never cross devices.
                    projector.applyInTransaction(inserted.filterNot { (it.op as? Operation.SettingChanged)?.key?.let { k -> k !in SyncedSettings.keys } == true })
                    inserted.size
                }
            }
            pulled += applied
        }
        return pulled to manifests
    }
}
