// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import io.github.velosako3.blossom.core.designsystem.theme.ThemeMode
import io.github.velosako3.blossom.core.platform.PreferenceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A device preference as a flow: read once, written through on every change. */
class PrefFlow<T>(private val prefs: PreferenceStore, private val key: String, private val default: T, private val parse: (String) -> T?, private val format: (T) -> String = { it.toString() }) {
    private val flow = MutableStateFlow(prefs.get(key)?.let(parse) ?: default)
    val state: StateFlow<T> = flow
    val value: T get() = flow.value
    fun set(v: T) { flow.value = v; prefs.put(key, format(v)) }
}

/** Appearance and playback preferences (device-specific; synced settings are separate). */
class AppPreferences(prefs: PreferenceStore) {
    val theme = PrefFlow(prefs, "ui.theme", ThemeMode.DARK, { runCatching { ThemeMode.valueOf(it) }.getOrNull() }, { it.name })
    /** Null: follow the system's "remove animations" setting. */
    val reduceMotion = PrefFlow<Boolean?>(prefs, "ui.reduceMotion", null, { it.toBooleanStrictOrNull() }, { it.toString() })
    val crossfade = PrefFlow(prefs, CROSSFADE_KEY, 0, { it.toIntOrNull()?.coerceIn(0, 12) })
    val autoplay = PrefFlow(prefs, AUTOPLAY_KEY, true, { it.toBooleanStrictOrNull() })
    val untitledAutoSave = PrefFlow(prefs, io.github.velosako3.blossom.feature.importer.UntitledAutoSave.SETTING, true, { it.toBooleanStrictOrNull() })
    val updateCheck = PrefFlow(prefs, io.github.velosako3.blossom.feature.integrations.update.UpdateChecker.ENABLED_KEY, true, { it.toBooleanStrictOrNull() })
    /** First run shows the welcome; tests skip it with `-Dblossom.skipOnboarding=true`. */
    val onboarded = PrefFlow(prefs, "ui.onboarded", System.getProperty("blossom.skipOnboarding") == "true", { it.toBooleanStrictOrNull() })
}

/** "Keep playing similar songs" when the queue ends. */
const val AUTOPLAY_KEY = "player.autoplay"
