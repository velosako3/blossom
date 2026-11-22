// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.designsystem.components

import io.github.velosako3.blossom.core.designsystem.theme.blossomSpring
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.glass.GlassSurface
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme

/** Test tag on every tab cell. */
const val GLASS_NAV_ITEM_TAG = "glassNavItem"

data class GlassNavItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector = icon)

/** The floating glass tab bar (compact layout). The selected tab sits in a brighter lens. */
@Composable
fun GlassNavBar(
    items: List<GlassNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(modifier.fillMaxWidth(), shape = CircleShape, floating = true) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(6.dp).selectableGroup(), // grows with large text
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEachIndexed { i, item ->
                NavCell(item, i == selectedIndex, { onSelect(i) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NavCell(item: GlassNavItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = BlossomTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val lens by animateColorAsState(
        if (selected) (if (colors.isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.07f)) else Color.Transparent,
        blossomSpring(),
    )
    val tint by animateColorAsState(if (selected) colors.accent else colors.onSurfaceVariant, blossomSpring())
    Column(
        modifier
            .testTag(GLASS_NAV_ITEM_TAG)
            .pressScale(interaction, 0.92f)
            .clip(CircleShape)
            .background(lens)
            .selectable(selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(if (selected) item.selectedIcon else item.icon, null, Modifier.size(24.dp), tint = tint)
        Text(item.label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
