// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Small non-secret key/value settings for this device (recent searches, window and glass preferences).
 * Values are strings; callers encode anything richer as JSON. Observable so the UI updates live.
 */
interface PreferenceStore {
    val values: StateFlow<Map<String, String>>
    fun get(key: String): String? = values.value[key]
    fun put(key: String, value: String?)
}

/** Backed by a callback that persists the whole map (a file on desktop, SharedPreferences on Android). */
open class MapPreferenceStore(initial: Map<String, String> = emptyMap(), private val persist: (Map<String, String>) -> Unit = {}) : PreferenceStore {
    private val state = MutableStateFlow(initial)
    override val values: StateFlow<Map<String, String>> = state.asStateFlow()
    override fun put(key: String, value: String?) {
        state.update { if (value == null) it - key else it + (key to value) }
        persist(state.value)
    }
}
