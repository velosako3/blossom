// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import javax.swing.JFileChooser

@Composable
actual fun rememberFolderPicker(onPicked: (String) -> Unit): (() -> Unit)? {
    val picked = rememberUpdatedState(onPicked)
    return {
        val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) picked.value(chooser.selectedFile.absolutePath)
    }
}
