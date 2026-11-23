// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.api

import kotlin.time.Instant

enum class AudioQuality { LOW, NORMAL, HIGH }

/** A resolved, playable stream: the URL plus the headers every request for it must carry. */
data class StreamInfo(
    val url: String,
    val headers: Map<String, String>,
    val mimeType: String,
    val bitrateKbps: Int?,
    val expiresAt: Instant?,
)
