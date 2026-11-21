// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.offline

import io.github.velosako3.blossom.source.api.AudioQuality

/** [wifiOnly] defaults to true on Android; [limitBytes] null = unlimited (options 2/5/10/20/50 GB). */
data class OfflineSettings(
    val wifiOnly: Boolean = true,
    val quality: AudioQuality = AudioQuality.HIGH,
    val limitBytes: Long? = null,
) {
    companion object {
        val LIMIT_OPTIONS_GB = listOf(2, 5, 10, 20, 50)
    }
}
