// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private var disconnect: (() -> Unit)? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { AndroidLocalBridge.onPermissionResult(it) }
    private val folderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            AndroidLocalBridge.onFolderPicked(uri.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val graph = (application as BlossomApplication).graph
        AndroidLocalBridge.requestPermission = {
            permissionLauncher.launch(if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        AndroidLocalBridge.pickFolder = { folderLauncher.launch(null) }
        // Until the in-app Reduce motion setting exists, follow the system "Remove animations" switch.
        val systemReduceMotion = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        setContent { App(graph = graph, reduceMotion = systemReduceMotion) }
    }

    override fun onStart() {
        super.onStart()
        disconnect = connectPlaybackService(this)
    }

    override fun onDestroy() {
        // The bridge is process-wide; drop the launchers that capture this Activity.
        AndroidLocalBridge.requestPermission = null
        AndroidLocalBridge.pickFolder = null
        super.onDestroy()
    }

    override fun onStop() {
        disconnect?.invoke()
        disconnect = null
        super.onStop()
    }
}
