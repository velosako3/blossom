// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.stats.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Bars for [values] with optional labels under some of them; the busiest bar is drawn in the accent colour. */
@Composable
private fun Bars(values: LongArray, labels: List<String?>, description: String, modifier: Modifier = Modifier) {
    val accent = BlossomTheme.colors.accent
    val muted = BlossomTheme.colors.onSurface.copy(alpha = 0.25f)
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1
    val top = values.indices.maxByOrNull { values[it] }
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val gap = 3.dp.toPx()
            val w = (size.width - gap * (values.size - 1)) / values.size
            values.forEachIndexed { i, v ->
                val h = (size.height * v / max).coerceAtLeast(if (v > 0) 3.dp.toPx() else 1.dp.toPx())
                drawRoundRect(if (i == top && v > 0) accent else muted, Offset(i * (w + gap), size.height - h), Size(w, h), CornerRadius(w / 3))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { l ->
                Text(l.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = BlossomTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

/** The "listening clock": 24 bars, one per local hour. */
@Composable
fun HourOfDayChart(byHourMs: LongArray, description: String, modifier: Modifier = Modifier) =
    Bars(byHourMs, List(24) { h -> if (h % 6 == 0) h.toString() else null }, description, modifier)

/** Monday-first bars with the locale's short day names. */
@Composable
fun DayOfWeekChart(byDayMs: LongArray, description: String, modifier: Modifier = Modifier) =
    Bars(byDayMs, DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }, description, modifier)

