// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme

/** A carousel card: square artwork (round for artists) with up to two lines below. */
@Composable
fun MediaCard(
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 152.dp,
    circle: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier.width(width).pressScale(interaction).clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
    ) {
        Artwork(artworkUrl, title, Modifier.fillMaxWidth().aspectRatio(1f), shape = if (circle) CircleShape else RoundedCornerShape(14.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = BlossomTheme.colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
