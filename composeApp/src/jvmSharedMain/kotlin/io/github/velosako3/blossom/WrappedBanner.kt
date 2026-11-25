// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.velosako3.blossom.composeApp.resources.Res
import io.github.velosako3.blossom.composeApp.resources.wrapped_banner_month
import io.github.velosako3.blossom.composeApp.resources.wrapped_banner_week
import io.github.velosako3.blossom.composeApp.resources.wrapped_banner_year
import io.github.velosako3.blossom.core.designsystem.glass.GlassSurface
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.feature.stats.Period
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.toJavaInstant

/**
 * Which Wrapped to offer on Home today: on 1 January last year's, on the 1st last month's, on Mondays last week's.
 * Null on other days.
 */
@Composable
fun wrappedBanner(zone: TimeZone, clock: Clock = Clock.System): Pair<Period, String>? {
    val now = clock.now()
    val today = now.toJavaInstant().atZone(java.time.ZoneId.of(zone.id)).toLocalDate()
    return when {
        today.dayOfYear == 1 -> Period.yearContaining(now, zone).previous().let { p -> p to stringResource(Res.string.wrapped_banner_year, (p as Period.Year).year.toString()) }
        today.dayOfMonth == 1 -> Period.monthContaining(now, zone).previous().let { p ->
            p to stringResource(Res.string.wrapped_banner_month, java.time.Month.of((p as Period.Month).month).getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()))
        }
        today.dayOfWeek == DayOfWeek.MONDAY -> Period.weekContaining(now, zone).previous() to stringResource(Res.string.wrapped_banner_week)
        else -> null
    }
}

@Composable
fun WrappedBanner(text: String, onOpen: () -> Unit) {
    GlassSurface(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable(onClick = onOpen)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = BlossomTheme.colors.accent)
            Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
        }
    }
}
