// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme

/** A song/album/artist row: 48 dp artwork, title, subtitle, optional trailing text and slot. */
@Composable
fun MediaRow(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
    onClick: (() -> Unit)? = null,
    dimmed: Boolean = false,
    circleArtwork: Boolean = false,
    badge: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    GlassListItem(
        headline = title,
        supporting = subtitle,
        modifier = modifier,
        onClick = onClick,
        dimmed = dimmed,
        leading = {
            Artwork(
                artworkUrl, null, Modifier.size(48.dp),
                shape = if (circleArtwork) androidx.compose.foundation.shape.CircleShape else androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            )
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                badge?.invoke()
                if (trailingText != null) Text(trailingText, style = MaterialTheme.typography.labelMedium, color = BlossomTheme.colors.onSurfaceVariant)
                trailing?.invoke()
            }
        },
    )
}

/** "3:45" / "1:02:03". */
fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}" else "$m:${s.toString().padStart(2, '0')}"
}
