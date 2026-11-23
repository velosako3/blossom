// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import io.github.velosako3.blossom.core.designsystem.theme.ThemeMode
import io.github.velosako3.blossom.core.platform.PreferenceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A device preference as a flow: read once, written through on every change. */
class PrefFlow<T>(private val prefs: PreferenceStore, val key: String, private val default: T, private val parse: (String) -> T?, private val format: (T) -> String = { it.toString() }) {
    private val flow = MutableStateFlow(prefs.get(key)?.let(parse) ?: default)
    val state: StateFlow<T> = flow
    val value: T get() = flow.value
    /** For synced settings: called with the stored text of every change the user makes. */
    var publish: ((raw: String) -> Unit)? = null
    fun set(v: T) { flow.value = v; prefs.put(key, format(v)); publish?.invoke(format(v)) }
    /** A value from another device: applied without publishing it again. */
    fun applyRemote(raw: String) {
        val v = parse(raw) ?: return
        if (v != flow.value) { flow.value = v; prefs.put(key, raw) }
    }
}

/** Appearance and playback preferences (device-specific; synced settings are separate). */
class AppPreferences(prefs: PreferenceStore) {
    val theme = PrefFlow(prefs, "ui.theme", ThemeMode.DARK, { runCatching { ThemeMode.valueOf(it) }.getOrNull() }, { it.name })
    val glass = PrefFlow(prefs, "ui.glass", io.github.velosako3.blossom.core.designsystem.glass.GlassPreference.AUTO,
        { runCatching { io.github.velosako3.blossom.core.designsystem.glass.GlassPreference.valueOf(it) }.getOrNull() }, { it.name })
    /** Null: follow the system's "remove animations" setting. */
    /** What the platform asks for (Android's "Remove animations"); used while [reduceMotion] is unset. */
    @kotlin.concurrent.Volatile var systemReduceMotion = false
    val reduceMotion = PrefFlow<Boolean?>(prefs, "ui.reduceMotion", null, { it.toBooleanStrictOrNull() }, { it.toString() })
    val crossfade = PrefFlow(prefs, CROSSFADE_KEY, 0, { it.toIntOrNull()?.coerceIn(0, 12) })
    val autoplay = PrefFlow(prefs, AUTOPLAY_KEY, true, { it.toBooleanStrictOrNull() })
    val untitledAutoSave = PrefFlow(prefs, io.github.velosako3.blossom.feature.importer.UntitledAutoSave.SETTING, true, { it.toBooleanStrictOrNull() })
    val updateCheck = PrefFlow(prefs, io.github.velosako3.blossom.feature.integrations.update.UpdateChecker.ENABLED_KEY, true, { it.toBooleanStrictOrNull() })
    /** First run shows the welcome; tests skip it with `-Dblossom.skipOnboarding=true`. */
    /** The ones that follow you between devices (the sync allowlist; see SyncedSettings). */
    val synced: List<PrefFlow<*>> get() = listOf(theme, reduceMotion, crossfade, autoplay, untitledAutoSave)
    val onboarded = PrefFlow(prefs, "ui.onboarded", System.getProperty("blossom.skipOnboarding") == "true", { it.toBooleanStrictOrNull() })
}

/** "Keep playing similar songs" when the queue ends. */
const val AUTOPLAY_KEY = "player.autoplay"
