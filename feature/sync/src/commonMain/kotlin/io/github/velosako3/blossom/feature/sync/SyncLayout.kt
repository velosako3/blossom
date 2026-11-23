// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync

import io.github.velosako3.blossom.core.model.sync.OperationJson
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Where sync files live in a backend (a cloud folder, a local folder): each device writes only its own folder.
 * `blossom/v1/devices/<deviceId>/manifest.json` and `ops-<n>.jsonl` segments of at most [SEGMENT_MAX_BYTES]; older
 * segments never change, only the newest is rewritten.
 */
object SyncLayout {
    const val ROOT = "blossom/v1"
    const val DEVICES = "$ROOT/devices"
    const val SEGMENT_MAX_BYTES = 5L * 1024 * 1024

    fun deviceDir(deviceId: String) = "$DEVICES/$deviceId"
    fun manifest(deviceId: String) = "${deviceDir(deviceId)}/manifest.json"
    fun segment(deviceId: String, index: Int) = "${deviceDir(deviceId)}/ops-$index.jsonl"
}

@Serializable
data class SegmentInfo(val index: Int, val bytes: Long, val firstSeq: Long, val lastSeq: Long)

/** What a device has published: its segments and the last operation sequence number in them. */
@Serializable
data class DeviceManifest(
    val deviceId: String,
    val deviceName: String,
    val segments: List<SegmentInfo>,
    val lastSeq: Long,
    val updatedAt: Instant,
) {
    fun encode(): String = OperationJson.encodeToString(serializer(), this)

    companion object {
        fun decode(json: String): DeviceManifest = OperationJson.decodeFromString(serializer(), json)
    }
}
