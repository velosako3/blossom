// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.importer

import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track

/**
 * untitled links can vanish, so imported untitled songs are saved offline as they're written (setting
 * `untitledAutoSaveOffline`, on by default). [save] queues one track (OfflineRef.UntitledAuto in the app).
 */
class UntitledAutoSave(private val enabled: () -> Boolean, private val save: suspend (Track) -> Unit) {
    suspend fun onWritten(service: ImportService, tracks: List<Track>) {
        if (service != ImportService.UNTITLED || !enabled()) return
        tracks.filter { it.id.kind == SourceKind.UNTITLED }.forEach { save(it) }
    }

    companion object {
        const val SETTING = "untitledAutoSaveOffline"
    }
}
