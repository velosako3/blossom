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
fun FindReplacementSheet(entry: PlaylistEntry?, find: FindReplacement, onDismiss: () -> Unit, onPick: (Track) -> Unit) {
    GlassSheet(entry != null, onDismiss) {
        if (entry == null) return@GlassSheet
        val candidates by produceState<List<Track>?>(null, entry.entryId) { value = find.candidates(entry) }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(stringResource(Res.string.replace_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(Res.string.replace_subtitle, entry.track?.title ?: entry.sourceTitle),
                color = BlossomTheme.colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            val list = candidates
            when {
                list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BlossomTheme.colors.accent)
                }
                list.isEmpty() -> Text(stringResource(Res.string.replace_none), color = BlossomTheme.colors.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
                else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(list, key = { it.id.value }) { t ->
                        val source = stringResource(if (t.id.kind == SourceKind.SOUNDCLOUD) Res.string.replace_source_soundcloud else Res.string.replace_source_youtube)
                        MediaRow(
                            t.title, "${t.artistNames} · $source", t.artworkUrl,
                            trailingText = formatDuration(t.durationMs).takeIf { t.durationMs > 0 },
                            onClick = { onPick(t) },
                        )
                    }
                }
            }
        }
    }
}
