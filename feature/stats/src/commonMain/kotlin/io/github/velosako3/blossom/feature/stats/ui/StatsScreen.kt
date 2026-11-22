// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.stats.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.velosako3.blossom.core.designsystem.components.GlassButton
import io.github.velosako3.blossom.core.designsystem.components.GlassChip
import io.github.velosako3.blossom.core.designsystem.components.GlassIconButton
import io.github.velosako3.blossom.core.designsystem.glass.GlassSurface
import io.github.velosako3.blossom.core.designsystem.theme.BlossomTheme
import io.github.velosako3.blossom.feature.stats.Period
import io.github.velosako3.blossom.feature.stats.PeriodStats
import io.github.velosako3.blossom.feature.stats.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaInstant

@Composable
fun StatsScreen(vm: StatsViewModel, onOpenWrapped: (Period) -> Unit, modifier: Modifier = Modifier) {
    val period by vm.period.collectAsState()
    val state by vm.state.collectAsState()
    StatsContent(period, state, vm::select, vm::previous, vm::next, onOpenWrapped, modifier)
}

/** The Stats screen for a [period] and its [state] (no logic; snapshot tests use it directly). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatsContent(
    period: Period,
    state: StatsUi,
    onSelect: (PeriodKind) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenWrapped: (Period) -> Unit,
    modifier: Modifier = Modifier,
) {
    val muted = BlossomTheme.colors.onSurfaceVariant
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 12.dp).widthIn(max = 900.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(Res.string.stats_title), style = MaterialTheme.typography.headlineLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PeriodKind.WEEK to Res.string.stats_week, PeriodKind.MONTH to Res.string.stats_month, PeriodKind.YEAR to Res.string.stats_year)
                    .forEach { (k, label) -> GlassChip(period.kind == k, { onSelect(k) }, stringResource(label)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(onPrevious, Icons.Rounded.ChevronLeft, stringResource(Res.string.stats_previous), size = 40.dp)
                Text(label(period), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                GlassIconButton(onNext, Icons.Rounded.ChevronRight, stringResource(Res.string.stats_next), size = 40.dp)
            }
            when (state) {
                StatsUi.Loading -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = BlossomTheme.colors.accent) }
                is StatsUi.Empty -> Text(
                    stringResource(when (period.kind) { PeriodKind.WEEK -> Res.string.stats_empty_week; PeriodKind.MONTH -> Res.string.stats_empty_month; PeriodKind.YEAR -> Res.string.stats_empty_year }),
                    color = muted, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 32.dp),
                )
                is StatsUi.Loaded -> Loaded(state.stats, onOpenWrapped)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Loaded(s: PeriodStats, onOpenWrapped: (Period) -> Unit) {
    val muted = BlossomTheme.colors.onSurfaceVariant
    GlassSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("%,d".format(s.totalListeningMs / 60_000), fontSize = 44.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(Res.string.stats_minutes), color = muted)
            Text(pluralStringResource(Res.plurals.stats_plays, s.countedPlays, s.countedPlays), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            s.listeningChangePercent?.let { change ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    val up = change >= 0
                    Icon(if (up) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, null, tint = if (up) BlossomTheme.colors.accent else muted, modifier = Modifier.size(18.dp))
                    Text(stringResource(Res.string.stats_vs_previous, "${abs(change).roundToInt()}%"), color = muted, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
    val plays: @Composable (Int) -> String = { n -> pluralStringResource(Res.plurals.stats_plays, n, n) }
    TopList(stringResource(Res.string.stats_top_songs), s.topTracks.map { TopRow(it.item.title, "${it.item.artistNames} · ${plays(it.plays)}", it.item.artworkUrl) })
    TopList(stringResource(Res.string.stats_top_artists), s.topArtists.map { r ->
        TopRow(r.item.name, plays(r.plays), s.topTracks.firstOrNull { t -> t.item.artists.any { it.name == r.item.name } }?.item?.artworkUrl, round = true)
    })
    TopList(stringResource(Res.string.stats_top_albums), s.topAlbums.map { r ->
        TopRow(r.item.title, plays(r.plays), s.topTracks.firstOrNull { it.item.album?.title == r.item.title }?.item?.artworkUrl)
    })
    Section(stringResource(Res.string.stats_clock)) { HourOfDayChart(s.byHourOfDayMs, stringResource(Res.string.stats_clock)) }
    Section(stringResource(Res.string.stats_days)) { DayOfWeekChart(s.byDayOfWeekMs, stringResource(Res.string.stats_days)) }
    if (s.newArtists.isNotEmpty()) Section(stringResource(Res.string.stats_new_artists)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.newArtists.take(12).forEach { GlassChip(false, {}, it.name) }
        }
    }
    if (s.longestStreakDays > 1) Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.LocalFireDepartment, null, tint = BlossomTheme.colors.accent)
        Text(pluralStringResource(Res.plurals.stats_streak, s.longestStreakDays, s.longestStreakDays), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
    }
    GlassButton({ onOpenWrapped(s.period) }, prominent = true) { Text(stringResource(Res.string.stats_open_wrapped)) }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    GlassSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** "16–22 Nov 2026", "November 2026", "2026". */
fun label(p: Period): String {
    val locale = Locale.getDefault()
    return when (p) {
        is Period.Week -> {
            val zone = java.time.ZoneId.of(p.zone.id)
            val first = p.start.toJavaInstant().atZone(zone).toLocalDate()
            val last = (p.endExclusive - 1.days).toJavaInstant().atZone(zone).toLocalDate()
            val month = { d: LocalDate -> d.month.getDisplayName(TextStyle.SHORT, locale) }
            when {
                first.year != last.year -> "${first.dayOfMonth} ${month(first)} ${first.year} – ${last.dayOfMonth} ${month(last)} ${last.year}"
                first.month != last.month -> "${first.dayOfMonth} ${month(first)} – ${last.dayOfMonth} ${month(last)} ${last.year}"
                else -> "${first.dayOfMonth}–${last.dayOfMonth} ${month(last)} ${last.year}"
            }
        }
        is Period.Month -> "${java.time.Month.of(p.month).getDisplayName(TextStyle.FULL_STANDALONE, locale)} ${p.year}"
        is Period.Year -> p.year.toString()
    }
}
