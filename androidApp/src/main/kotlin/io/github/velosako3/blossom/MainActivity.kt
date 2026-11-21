// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    private var disconnect: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = (application as BlossomApplication).graph
        // Until the in-app Reduce motion setting exists, follow the system "Remove animations" switch.
        val systemReduceMotion = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        setContent { App(graph = graph, reduceMotion = systemReduceMotion) }
    }

    override fun onStart() {
        super.onStart()
        disconnect = connectPlaybackService(this)
    }

    override fun onStop() {
        disconnect?.invoke()
        disconnect = null
        super.onStop()
    }
}
