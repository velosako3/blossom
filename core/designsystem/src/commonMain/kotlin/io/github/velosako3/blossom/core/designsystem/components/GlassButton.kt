// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.glass.GlassSurface
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme

/**
 * A pill button. Default is glass; [prominent] fills it with the accent for the one main action on a screen
 * (Play, Save). Height is at least 44 dp, the minimum touch target.
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    prominent: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val colors = BlossomTheme.colors
    val clickable = Modifier
        .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
    val inner: @Composable () -> Unit = {
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.labelLarge) {
            Row(
                Modifier.defaultMinSize(minHeight = 44.dp).padding(contentPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
    val base = modifier.pressScale(interaction).alpha(if (enabled) 1f else 0.45f)
    if (prominent) {
        Row(
            base
                .dropShadow(CircleShape, Shadow(radius = 18.dp, color = colors.accent.copy(alpha = 0.35f), offset = DpOffset(0.dp, 6.dp)))
                .clip(CircleShape)
                .background(colors.accent)
                .then(clickable),
        ) { CompositionLocalProvider(LocalContentColor provides colors.onAccent) { inner() } }
    } else {
        GlassSurface(base, shape = CircleShape) { Row(clickable) { inner() } }
    }
}

