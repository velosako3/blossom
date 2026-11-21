// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.lyrics

import io.github.velosako3.blossom.core.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

sealed interface LyricsUi {
    data object Loading : LyricsUi
    /** [lyrics] null = none found. */
    data class Ready(val lyrics: Lyrics?) : LyricsUi
}

/** Lyrics for whatever is playing; a new track cancels the previous lookup. */
@OptIn(ExperimentalCoroutinesApi::class)
class LyricsViewModel(private val resolver: LyricsResolver, current: Flow<Track?>, scope: CoroutineScope) {
    val state: StateFlow<LyricsUi> = current
        .distinctUntilChangedBy { it?.id }
        .transformLatest { t ->
            if (t == null) { emit(LyricsUi.Ready(null)); return@transformLatest }
            emit(LyricsUi.Loading)
            emit(LyricsUi.Ready(runCatching { resolver.resolve(t) }.getOrNull()))
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), LyricsUi.Ready(null))
}
