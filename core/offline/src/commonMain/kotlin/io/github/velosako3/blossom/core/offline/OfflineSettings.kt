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

/** [OfflineSettings] kept in device preferences (never synced). */
class OfflineSettingsStore(private val prefs: io.github.velosako3.blossom.core.platform.PreferenceStore, defaultWifiOnly: Boolean) {
    private val defaults = OfflineSettings(wifiOnly = defaultWifiOnly)
    private val _settings = kotlinx.coroutines.flow.MutableStateFlow(read())
    val settings: kotlinx.coroutines.flow.StateFlow<OfflineSettings> = _settings

    private fun read() = OfflineSettings(
        wifiOnly = prefs.get(WIFI)?.toBooleanStrictOrNull() ?: defaults.wifiOnly,
        quality = prefs.get(QUALITY)?.let { runCatching { AudioQuality.valueOf(it) }.getOrNull() } ?: defaults.quality,
        limitBytes = prefs.get(LIMIT)?.toLongOrNull(),
    )

    fun update(change: (OfflineSettings) -> OfflineSettings) {
        val next = change(_settings.value)
        prefs.put(WIFI, next.wifiOnly.toString())
        prefs.put(QUALITY, next.quality.name)
        prefs.put(LIMIT, next.limitBytes?.toString())
        _settings.value = next
    }

    private companion object {
        const val WIFI = "offline.wifiOnly"
        const val QUALITY = "offline.quality"
        const val LIMIT = "offline.limitBytes"
    }
}
