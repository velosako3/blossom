// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.importer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Help
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.database.LibraryPlaylistSummary
import io.github.velosako3.blossom.core.database.LibraryRepository
import io.github.velosako3.blossom.core.designsystem.components.GlassButton
import io.github.velosako3.blossom.core.designsystem.components.GlassChip
import io.github.velosako3.blossom.core.designsystem.components.GlassListItem
import io.github.velosako3.blossom.core.designsystem.components.GlassSheet
import io.github.velosako3.blossom.core.designsystem.components.GlassTextField
import io.github.velosako3.blossom.core.designsystem.components.MediaRow
import io.github.velosako3.blossom.core.designsystem.glass.GlassSurface
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.model.EntryStatus
import io.github.velosako3.blossom.core.model.PlaylistEntry
import io.github.velosako3.blossom.core.model.PlaylistId
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.feature.importer.FindReplacement
import io.github.velosako3.blossom.feature.importer.FindReplacementSheet
import io.github.velosako3.blossom.feature.importer.ImportService
import io.github.velosako3.blossom.feature.importer.LinkKind
import io.github.velosako3.blossom.feature.importer.MatchFixSheet
import io.github.velosako3.blossom.feature.importer.resources.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.stringResource

/** What the import screen can do besides importing. */
class ImportActions(
    val openPlaylist: (PlaylistId) -> Unit,
    val play: (Track) -> Unit,
    val queue: (Track) -> Unit,
    val like: (Track) -> Unit,
    /** Applies a fix chosen in a match/replacement sheet (same entry, same position). */
    val fix: (PlaylistId, PlaylistEntry, Track) -> Unit,
)

/** The Import screen, wired to [vm]. [matchFix] searches YouTube Music; [replacement] YouTube uploads and SoundCloud. */
@Composable
fun ImportScreen(
    vm: ImportViewModel,
    repo: LibraryRepository,
    clipboard: String?,
    matchFix: FindReplacement,
    replacement: FindReplacement,
    actions: ImportActions,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
) {
    val state by vm.state.collectAsState()
    val text by vm.text.collectAsState()
    val playlists by remember(repo) { repo.playlists() }.collectAsState(emptyList())
    val target = (state as? ImportUi.InProgress)?.let { s -> s.progress?.playlistId?.let { it to s.startedAt } }
        ?: (state as? ImportUi.Done)?.let { it.playlistId to it.startedAt }
    val entries by remember(target) {
        target?.let { (pid, since) -> repo.playlist(pid).map { p -> p?.entries.orEmpty().filter { it.addedAt >= since } } } ?: flowOf(emptyList())
    }.collectAsState(emptyList())
    var fixing by remember { mutableStateOf<PlaylistEntry?>(null) }
    val pid = target?.first
    ImportContent(
        state = state,
        text = text,
        clipboardOffer = vm.clipboardOffer(clipboard)?.takeIf { state is ImportUi.Idle },
        entries = entries,
        playlists = playlists,
        onTextChange = vm::onTextChange,
        onStart = { name, into -> vm.start(name, into) },
        onUpdateExisting = vm::updateExisting,
        onCreateCopy = vm::createCopy,
        onFix = { fixing = it },
        onReset = vm::reset,
        actions = actions,
        modifier = modifier,
        topBar = topBar,
    )
    val entry = fixing
    val untitled = entry?.track?.id?.kind == SourceKind.UNTITLED || (state as? ImportUi.InProgress)?.link?.service == ImportService.UNTITLED ||
        (state as? ImportUi.Done)?.link?.service == ImportService.UNTITLED
    val pick: (Track) -> Unit = { t -> if (pid != null && entry != null) actions.fix(pid, entry, t); fixing = null }
    if (untitled) FindReplacementSheet(entry, replacement, { fixing = null }, pick)
    else MatchFixSheet(entry, matchFix, { fixing = null }, pick)
}

/** The import screen for a given [state] (no logic; used directly by snapshot tests). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportContent(
    state: ImportUi,
    text: String,
    clipboardOffer: io.github.velosako3.blossom.feature.importer.DetectedLink?,
    entries: List<PlaylistEntry>,
    playlists: List<LibraryPlaylistSummary>,
    onTextChange: (String) -> Unit,
    onStart: (name: String?, into: PlaylistId?) -> Unit,
    onUpdateExisting: () -> Unit,
    onCreateCopy: () -> Unit,
    onFix: (PlaylistEntry) -> Unit,
    onReset: () -> Unit,
    actions: ImportActions,
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
) {
    val muted = BlossomTheme.colors.onSurfaceVariant
    var name by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            topBar()
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.import_title), style = MaterialTheme.typography.headlineLarge)
                if (state !is ImportUi.InProgress && state !is ImportUi.Done) {
                    GlassTextField(text, onTextChange, Modifier.fillMaxWidth(), placeholder = stringResource(Res.string.import_paste_hint), leadingIcon = Icons.Rounded.Link)
                }
                if (clipboardOffer != null) {
                    GlassChip(false, { onTextChange(clipboardOffer.canonicalUrl) }, stringResource(Res.string.import_from_clipboard), leadingIcon = Icons.Rounded.ContentPaste)
                }
                when (state) {
                    ImportUi.Idle -> Unit
                    ImportUi.NotALink -> Text(stringResource(Res.string.import_not_a_link), color = muted)
                    is ImportUi.Detected -> GlassSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("${serviceName(state.link.service)} · ${stringResource(kindLabel(state.link.kind))}", style = MaterialTheme.typography.titleMedium)
                            GlassTextField(name, { name = it }, Modifier.fillMaxWidth(), placeholder = stringResource(Res.string.import_name_hint))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassButton({ onStart(name, null) }, prominent = true) { Text(stringResource(Res.string.import_save_new)) }
                                if (playlists.isNotEmpty()) GlassButton({ picking = true }) { Text(stringResource(Res.string.import_add_existing)) }
                            }
                        }
                    }
                    is ImportUi.DuplicateFound -> GlassSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(Res.string.import_duplicate, state.existingName), style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                GlassButton(onUpdateExisting, prominent = true) { Text(stringResource(Res.string.import_update_existing)) }
                                GlassButton(onCreateCopy) { Text(stringResource(Res.string.import_create_copy)) }
                            }
                        }
                    }
                    is ImportUi.InProgress -> ProgressHeader(state.progress?.done, state.progress?.total, state.progress?.let { it.uncertain + it.unavailable } ?: attention(entries), finished = false)
                    is ImportUi.Done -> {
                        ProgressHeader(state.progress.done, state.progress.total, state.progress.uncertain + state.progress.unavailable, finished = true)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val single = entries.singleOrNull()?.track?.takeIf { state.progress.total == 1 }
                            if (single != null) {
                                GlassButton({ actions.play(single) }, prominent = true) { Text(stringResource(Res.string.import_play_now)) }
                                GlassButton({ actions.queue(single) }) { Text(stringResource(Res.string.import_add_queue)) }
                                GlassButton({ actions.like(single) }) { Text(stringResource(Res.string.import_like)) }
                            }
                            GlassButton({ actions.openPlaylist(state.playlistId) }, prominent = single == null) { Text(stringResource(Res.string.import_open_playlist)) }
                            GlassButton(onReset) { Text(stringResource(Res.string.import_again)) }
                        }
                    }
                    is ImportUi.Failed -> {
                        Text(
                            stringResource(when (state.failure) {
                                io.github.velosako3.blossom.feature.importer.ImportFailure.NEEDS_LOGIN -> Res.string.import_failed_login
                                io.github.velosako3.blossom.feature.importer.ImportFailure.NOT_FOUND -> Res.string.import_failed_not_found
                                else -> Res.string.import_failed
                            }),
                            color = muted,
                        )
                        GlassButton(onReset) { Text(stringResource(Res.string.import_again)) }
                    }
                }
            }
            if (state is ImportUi.InProgress || state is ImportUi.Done) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 24.dp)) {
                    items(entries, key = { it.entryId }) { e -> ImportRow(e, onFix) }
                }
            }
        }
        GlassSheet(picking, { picking = false }) {
            Text(stringResource(Res.string.import_pick_playlist), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            playlists.forEach { p -> GlassListItem(p.name, onClick = { picking = false; onStart(null, p.id) }) }
        }
    }
}

@Composable
private fun ProgressHeader(doneCount: Int?, total: Int?, attention: Int, finished: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (total == null) {
            Text(stringResource(Res.string.import_reading), style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = BlossomTheme.colors.accent)
            return@Column
        }
        val parts = listOfNotNull(
            stringResource(Res.string.import_done).takeIf { finished },
            stringResource(Res.string.import_progress, doneCount ?: 0, total),
            stringResource(Res.string.import_attention, attention).takeIf { attention > 0 },
        )
        // "142 of 300 · 9 need attention"
        Text(parts.joinToString(" · "), style = MaterialTheme.typography.titleMedium)
        if (!finished) LinearProgressIndicator(
            progress = { if (total == 0) 1f else (doneCount ?: 0).toFloat() / total },
            modifier = Modifier.fillMaxWidth(),
            color = BlossomTheme.colors.accent,
        )
    }
}

private fun attention(entries: List<PlaylistEntry>) = entries.count { it.status != EntryStatus.MATCHED }

/** ✓ matched, ? not sure, ✗ not found; the last two open the fix sheet. */
@Composable
private fun ImportRow(e: PlaylistEntry, onFix: (PlaylistEntry) -> Unit) {
    val colors = BlossomTheme.colors
    val (icon, tint, label) = when (e.status) {
        EntryStatus.MATCHED -> Triple(Icons.Rounded.CheckCircle, colors.accent, Res.string.import_status_matched)
        EntryStatus.UNCERTAIN -> Triple(Icons.Rounded.Help, colors.onSurfaceVariant, Res.string.import_status_uncertain)
        EntryStatus.UNAVAILABLE -> Triple(Icons.Rounded.Error, colors.error, Res.string.import_status_unavailable)
    }
    val status = stringResource(label)
    MediaRow(
        title = e.track?.title ?: e.sourceTitle,
        subtitle = if (e.status == EntryStatus.MATCHED) (e.track?.artistNames ?: e.sourceArtist) else "${e.sourceArtist} · $status",
        artworkUrl = e.track?.artworkUrl,
        dimmed = e.status == EntryStatus.UNAVAILABLE,
        onClick = if (e.status != EntryStatus.MATCHED) ({ onFix(e) }) else null,
        badge = { Icon(icon, status, tint = tint, modifier = Modifier.padding(end = 6.dp).size(18.dp)) },
    )
}

private fun serviceName(s: ImportService) = when (s) {
    ImportService.SPOTIFY -> "Spotify"
    ImportService.YOUTUBE_MUSIC -> "YouTube Music"
    ImportService.SOUNDCLOUD -> "SoundCloud"
    ImportService.UNTITLED -> "untitled"
}

private fun kindLabel(k: LinkKind) = when (k) {
    LinkKind.TRACK -> Res.string.import_kind_track
    LinkKind.PLAYLIST -> Res.string.import_kind_playlist
    LinkKind.ALBUM -> Res.string.import_kind_album
}
