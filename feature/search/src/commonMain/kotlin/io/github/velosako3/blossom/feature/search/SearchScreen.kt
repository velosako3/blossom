// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.components.GlassButton
import io.github.velosako3.blossom.core.designsystem.components.GlassChip
import io.github.velosako3.blossom.core.designsystem.components.GlassIconButton
import io.github.velosako3.blossom.core.designsystem.components.GlassListItem
import io.github.velosako3.blossom.core.designsystem.components.GlassTextField
import io.github.velosako3.blossom.core.designsystem.components.MediaRow
import io.github.velosako3.blossom.core.designsystem.components.formatDuration
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.feature.search.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun SearchScreen(
    vm: SearchViewModel,
    onPlay: (tracks: List<Track>, index: Int) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = FocusRequester(),
    /** Each new value moves keyboard focus to the search field (Ctrl+F on desktop). */
    focusTrigger: Int = 0,
    offlineNotice: (@Composable () -> Unit)? = null,
) {
    LaunchedEffect(focusTrigger) {
        if (focusTrigger == 0) return@LaunchedEffect
        // The screen may still be entering; retry for a few frames until the field is attached.
        repeat(20) { if (runCatching { focusRequester.requestFocus() }.isSuccess) return@LaunchedEffect; withFrameNanos { } }
    }
    val state by vm.state.collectAsState()
    val colors = BlossomTheme.colors
    Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Text(stringResource(Res.string.search_title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 12.dp))
        GlassTextField(
            value = state.query,
            onValueChange = vm::onQueryChange,
            placeholder = stringResource(Res.string.search_placeholder),
            leadingIcon = Icons.Rounded.Search,
            modifier = Modifier.padding(horizontal = 16.dp).focusRequester(focusRequester),
            trailing = if (state.query.isNotEmpty()) {
                { GlassIconButton({ vm.onQueryChange("") }, Icons.Rounded.Close, stringResource(Res.string.search_clear), size = 32.dp, iconSize = 18.dp, glass = false) }
            } else null,
        )
        if (offlineNotice != null) offlineNotice() else if (state.offline) {
            Text(
                stringResource(Res.string.search_offline),
                color = BlossomTheme.colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        // Offline results are songs only (local files and saved music), so the tabs would mislead.
        if (state.query.isNotBlank() && !state.offline) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchTab.entries.forEach { t ->
                    GlassChip(state.tab == t, { vm.onTabChange(t) }, stringResource(t.label()))
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.query.isBlank() -> Recents(state.recents, vm::onQueryChange, vm::removeRecent)
                state.error != null -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.search_failed), color = colors.onSurfaceVariant)
                    GlassButton({ vm.onQueryChange(state.query + " "); vm.onQueryChange(state.query.trim()) }) { Text(stringResource(Res.string.search_retry)) }
                }
                else -> Results(state, onPlay, onOpenAlbum, onOpenArtist, onOpenPlaylist)
            }
        }
    }
}

private fun SearchTab.label() = when (this) {
    SearchTab.SONGS -> Res.string.search_tab_songs
    SearchTab.ALBUMS -> Res.string.search_tab_albums
    SearchTab.ARTISTS -> Res.string.search_tab_artists
    SearchTab.PLAYLISTS -> Res.string.search_tab_playlists
}

@Composable
private fun Recents(recents: List<String>, onPick: (String) -> Unit, onRemove: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 12.dp + io.github.velosako3.blossom.core.designsystem.glass.LocalBarsPadding.current)) {
        item {
            Text(
                stringResource(if (recents.isEmpty()) Res.string.search_no_recent else Res.string.search_recent),
                style = MaterialTheme.typography.labelLarge, color = BlossomTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        items(recents) { q ->
            GlassListItem(
                headline = q,
                onClick = { onPick(q) },
                leading = { Icon(Icons.Rounded.History, null, tint = BlossomTheme.colors.onSurfaceVariant) },
                trailing = { GlassIconButton({ onRemove(q) }, Icons.Rounded.Close, stringResource(Res.string.search_remove_recent), size = 36.dp, iconSize = 18.dp, glass = false) },
            )
        }
    }
}

@Composable
private fun Results(
    state: SearchUiState,
    onPlay: (List<Track>, Int) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    val r = state.results
    val empty = r != null && when (state.tab) {
        SearchTab.SONGS -> r.songs.isEmpty()
        SearchTab.ALBUMS -> r.albums.isEmpty()
        SearchTab.ARTISTS -> r.artists.isEmpty()
        SearchTab.PLAYLISTS -> r.playlists.isEmpty()
    }
    if (empty) {
        Text(stringResource(Res.string.search_no_results, state.query.trim()), color = BlossomTheme.colors.onSurfaceVariant, modifier = Modifier.padding(24.dp))
        return
    }
    LazyColumn(contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp + io.github.velosako3.blossom.core.designsystem.glass.LocalBarsPadding.current)) {
        when (state.tab) {
            SearchTab.SONGS -> itemsIndexed(r?.songs.orEmpty(), key = { _, t -> t.id.value }) { i, t ->
                MediaRow(t.title, t.artistNames, t.artworkUrl, trailingText = formatDuration(t.durationMs).takeIf { t.durationMs > 0 }, onClick = { onPlay(r!!.songs, i) })
            }
            SearchTab.ALBUMS -> items(r?.albums.orEmpty(), key = { it.id }) { a ->
                MediaRow(a.title, listOfNotNull(a.artists.joinToString(", ") { it.name }.ifBlank { null }, a.year?.toString()).joinToString(" · "), a.artworkUrl, onClick = { onOpenAlbum(a.id) })
            }
            SearchTab.ARTISTS -> items(r?.artists.orEmpty(), key = { it.id }) { a ->
                MediaRow(a.name, null, a.artworkUrl, circleArtwork = true, onClick = { onOpenArtist(a.id) })
            }
            SearchTab.PLAYLISTS -> items(r?.playlists.orEmpty(), key = { it.id }) { p ->
                MediaRow(p.title, p.author, p.artworkUrl, onClick = { onOpenPlaylist(p.id) })
            }
        }
    }
}
