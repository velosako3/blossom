// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** One listening session of one track. Every play is recorded; [counted] follows the 30-second rule. */
@Serializable
data class PlayEvent(
    val id: String,
    val track: Track,
    val startedAt: Instant,
    val playedMs: Long,
    val counted: Boolean,
    val deviceId: String,
)
