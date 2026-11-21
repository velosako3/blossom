// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Until the in-app Reduce motion setting exists, follow the system "Remove animations" switch.
        val systemReduceMotion = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        setContent { App(reduceMotion = systemReduceMotion) }
    }
}
