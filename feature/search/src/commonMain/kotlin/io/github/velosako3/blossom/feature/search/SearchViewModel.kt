// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.search

import io.github.velosako3.blossom.core.platform.PreferenceStore
import io.github.velosako3.blossom.source.api.MusicSource
import io.github.velosako3.blossom.source.api.SearchFilter
import io.github.velosako3.blossom.source.api.SearchResults
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

enum class SearchTab(val filter: SearchFilter) {
    SONGS(SearchFilter.SONGS), ALBUMS(SearchFilter.ALBUMS), ARTISTS(SearchFilter.ARTISTS), PLAYLISTS(SearchFilter.PLAYLISTS)
}

data class SearchUiState(
    val query: String = "",
    val tab: SearchTab = SearchTab.SONGS,
    val recents: List<String> = emptyList(),
    val results: SearchResults? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/** Up to 20 recent searches, most recent first, unique ignoring case. Kept on this device only. */
class RecentSearchStore(private val prefs: PreferenceStore) {
    fun recents(): List<String> = prefs.get(KEY)?.let { runCatching { Json.decodeFromString<List<String>>(it) }.getOrNull() }.orEmpty()

    fun add(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val next = (listOf(q) + recents().filterNot { it.equals(q, ignoreCase = true) }).take(MAX)
        prefs.put(KEY, Json.encodeToString(next))
    }

    fun remove(query: String) = prefs.put(KEY, Json.encodeToString(recents().filterNot { it == query }))

    private companion object {
        const val KEY = "search.recent"
        const val MAX = 20
    }
}

/** Search with a 300 ms debounce; a blank query shows recent searches and searches nothing. */
@OptIn(FlowPreview::class)
class SearchViewModel(
    private val source: MusicSource,
    private val recentStore: RecentSearchStore,
    scope: CoroutineScope,
) {
    private val query = MutableStateFlow("")
    private val tab = MutableStateFlow(SearchTab.SONGS)
    private val _state = MutableStateFlow(SearchUiState(recents = recentStore.recents()))
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    init {
        scope.launch {
            combine(query.debounce(DEBOUNCE_MS).map { it.trim() }.distinctUntilChanged(), tab) { q, t -> q to t }
                .collectLatest { (q, t) ->
                    if (q.isEmpty()) {
                        _state.update { it.copy(results = null, loading = false, error = null, recents = recentStore.recents()) }
                        return@collectLatest
                    }
                    _state.update { it.copy(loading = true, error = null) }
                    try {
                        val results = source.search(q, t.filter)
                        recentStore.add(q)
                        _state.update { it.copy(results = results, loading = false, recents = recentStore.recents()) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _state.update { it.copy(loading = false, error = e.message ?: "Search failed") }
                    }
                }
        }
    }

    fun onQueryChange(q: String) {
        query.value = q
        _state.update { it.copy(query = q) }
    }

    fun onTabChange(t: SearchTab) {
        tab.value = t
        _state.update { it.copy(tab = t, results = null) }
    }

    fun removeRecent(q: String) {
        recentStore.remove(q)
        _state.update { it.copy(recents = recentStore.recents()) }
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}
