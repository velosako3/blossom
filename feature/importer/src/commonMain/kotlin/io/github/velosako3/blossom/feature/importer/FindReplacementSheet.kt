// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.importer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.components.GlassSheet
import io.github.velosako3.blossom.core.designsystem.components.MediaRow
import io.github.velosako3.blossom.core.designsystem.components.formatDuration
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.model.PlaylistEntry
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.feature.importer.resources.*
import org.jetbrains.compose.resources.stringResource

/** Candidates from YouTube uploads and SoundCloud for an unavailable [entry]; picking one replaces it in place. */
@Composable
fun FindReplacementSheet(entry: PlaylistEntry?, find: FindReplacement, onDismiss: () -> Unit, onPick: (Track) -> Unit) =
    ReplacementSheet(entry, find, onDismiss, onPick, Res.string.replace_title, Res.string.replace_subtitle, Res.string.replace_none)

/** "Fix this match" for an uncertain or unmatched imported song: YouTube Music results for its source title and artist. */
@Composable
fun MatchFixSheet(entry: PlaylistEntry?, find: FindReplacement, onDismiss: () -> Unit, onPick: (Track) -> Unit) =
    ReplacementSheet(entry, find, onDismiss, onPick, Res.string.match_title, Res.string.match_subtitle, Res.string.match_none)

@Composable
private fun ReplacementSheet(
    entry: PlaylistEntry?,
    find: FindReplacement,
    onDismiss: () -> Unit,
    onPick: (Track) -> Unit,
    title: org.jetbrains.compose.resources.StringResource,
    subtitle: org.jetbrains.compose.resources.StringResource,
    none: org.jetbrains.compose.resources.StringResource,
) {
    GlassSheet(entry != null, onDismiss) {
        if (entry == null) return@GlassSheet
        val candidates by produceState<List<Track>?>(null, entry.entryId) { value = find.candidates(entry) }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(subtitle, entry.track?.title ?: entry.sourceTitle),
                color = BlossomTheme.colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            val list = candidates
            when {
                list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BlossomTheme.colors.accent)
                }
                list.isEmpty() -> Text(stringResource(none), color = BlossomTheme.colors.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
                else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(list, key = { it.id.value }) { t ->
                        val source = when (t.id.kind) {
                            SourceKind.SOUNDCLOUD -> stringResource(Res.string.replace_source_soundcloud)
                            SourceKind.YOUTUBE -> stringResource(Res.string.replace_source_youtube)
                            else -> null
                        }
                        MediaRow(
                            t.title, listOfNotNull(t.artistNames, source).joinToString(" · "), t.artworkUrl,
                            trailingText = formatDuration(t.durationMs).takeIf { t.durationMs > 0 },
                            onClick = { onPick(t) },
                        )
                    }
                }
            }
        }
    }
}
