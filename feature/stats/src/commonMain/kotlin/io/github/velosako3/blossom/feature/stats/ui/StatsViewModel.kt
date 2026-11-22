// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.stats.ui

import io.github.velosako3.blossom.core.database.LibraryRepository
import io.github.velosako3.blossom.feature.stats.Period
import io.github.velosako3.blossom.feature.stats.PeriodStats
import io.github.velosako3.blossom.feature.stats.StatsCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

enum class PeriodKind { WEEK, MONTH, YEAR }

sealed interface StatsUi {
    data object Loading : StatsUi
    data class Empty(val period: Period) : StatsUi
    data class Loaded(val stats: PeriodStats) : StatsUi
}

/** The Stats screen: a week, month or year (‹ › to move), from the play history every device shares. */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(private val repo: LibraryRepository, private val clock: Clock, private val zone: TimeZone, scope: CoroutineScope) {
    private val _period = MutableStateFlow<Period>(Period.weekContaining(clock.now(), zone))
    val period: StateFlow<Period> = _period

    val state: StateFlow<StatsUi> = _period.flatMapLatest { p ->
        val prev = p.previous()
        combine(repo.playEvents(p.start, p.endExclusive), repo.playEvents(prev.start, prev.endExclusive), repo.firstPlays()) { events, before, firsts ->
            if (events.isEmpty()) StatsUi.Empty(p)
            else {
                val firstByArtist = HashMap<String, Instant>()
                for ((track, at) in firsts) for (a in track.artists) {
                    val k = StatsCalculator.artistKey(a)
                    if (firstByArtist[k]?.let { at < it } != false) firstByArtist[k] = at
                }
                StatsUi.Loaded(StatsCalculator.compute(p, events, firstByArtist, before.sumOf { it.playedMs }))
            }
        }
    }.stateIn(scope, SharingStarted.Eagerly, StatsUi.Loading)

    fun select(kind: PeriodKind) {
        val anchor = clock.now()
        _period.value = when (kind) {
            PeriodKind.WEEK -> Period.weekContaining(anchor, zone)
            PeriodKind.MONTH -> Period.monthContaining(anchor, zone)
            PeriodKind.YEAR -> Period.yearContaining(anchor, zone)
        }
    }

    fun previous() { _period.value = _period.value.previous() }

    /** Never past the period holding today. */
    fun next() { _period.value.next().takeIf { it.start <= clock.now() }?.let { _period.value = it } }
}

val Period.kind: PeriodKind get() = when (this) { is Period.Week -> PeriodKind.WEEK; is Period.Month -> PeriodKind.MONTH; is Period.Year -> PeriodKind.YEAR }
