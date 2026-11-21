// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.components

import io.github.velosako3.blossom.core.designsystem.theme.blossomSpring
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.glass.GlassSurface
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTokens

/** The expanded layout's glass sidebar (260 dp by default). */
@Composable
fun GlassSidebar(
    modifier: Modifier = Modifier,
    width: Dp = 260.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(modifier.width(width).fillMaxHeight()) {
        Column(Modifier.fillMaxHeight().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
    }
}

/** One navigation entry or playlist row in [GlassSidebar]. */
@Composable
fun GlassSidebarItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = BlossomTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(
        when {
            selected -> if (colors.isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.08f)
            hovered -> if (colors.isDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.04f)
            else -> Color.Transparent
        },
        blossomSpring(),
    )
    val tint = if (selected) colors.accent else colors.onSurface
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BlossomTokens.radiusSmall))
            .background(fill)
            .hoverable(interaction)
            .selectable(selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(20.dp), tint = tint)
        leading?.invoke()
        Text(label, style = MaterialTheme.typography.labelLarge, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
