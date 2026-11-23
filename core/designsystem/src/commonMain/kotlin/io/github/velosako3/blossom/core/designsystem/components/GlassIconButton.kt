// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.glass.GlassSurface

/**
 * A round icon button. [glass] = false draws just the icon (for controls already sitting on glass, like the
 * player bar); [contentDescription] is required because the button has no text.
 */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    glass: Boolean = true,
    tint: Color = Color.Unspecified,
) {
    val interaction = remember { MutableInteractionSource() }
    val click = Modifier.size(size).clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
    val iconContent: @Composable () -> Unit = {
        Box(click, contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription,
                Modifier.size(iconSize),
                tint = if (tint == Color.Unspecified) androidx.compose.material3.LocalContentColor.current else tint,
            )
        }
    }
    val base = modifier.pressScale(interaction, 0.9f).alpha(if (enabled) 1f else 0.4f)
    if (glass) GlassSurface(base, shape = CircleShape) { iconContent() } else Box(base) { iconContent() }
}
