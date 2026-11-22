// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.stats.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.components.Artwork
import io.github.velosako3.blossom.core.designsystem.components.GlassListItem
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme

/** One ranked row: position, artwork, name and a supporting line ("12 plays"). */
data class TopRow(val title: String, val subtitle: String, val artworkUrl: String?, val round: Boolean = false)

@Composable
fun TopList(title: String, rows: List<TopRow>, modifier: Modifier = Modifier) {
    if (rows.isEmpty()) return
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        rows.forEachIndexed { i, r ->
            GlassListItem(
                headline = r.title,
                supporting = r.subtitle,
                leading = {
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("${i + 1}", Modifier.width(22.dp), style = MaterialTheme.typography.titleMedium, color = BlossomTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center)
                        Artwork(r.artworkUrl, null, Modifier.padding(start = 6.dp).size(44.dp), shape = if (r.round) CircleShape else androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    }
                },
            )
        }
    }
}
