// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.platform

import android.content.Context

class SharedPreferenceStore(context: Context) : MapPreferenceStore(
    initial = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap(),
    persist = { map ->
        val editor = context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().clear()
        map.forEach { (k, v) -> editor.putString(k, v) }
        editor.apply()
    },
) {
    private companion object { const val NAME = "blossom_prefs" }
}
