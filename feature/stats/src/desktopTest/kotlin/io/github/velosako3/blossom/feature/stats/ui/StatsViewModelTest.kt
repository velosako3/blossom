// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.stats.ui

import io.github.velosako3.blossom.core.database.SqlLibraryRepository
import io.github.velosako3.blossom.core.database.inMemoryDatabase
import io.github.velosako3.blossom.feature.stats.Period
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Instant

class StatsViewModelTest {
    private val london = TimeZone.of("Europe/London")
    private val now = Instant.parse("2026-11-20T12:00:00Z")
    private val clock = object : Clock { override fun now() = now }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val vm = StatsViewModel(SqlLibraryRepository(inMemoryDatabase()), clock, london, scope)

    @AfterTest fun stop() = scope.cancel()

    @Test fun startsOnThisWeek() = assertEquals(Period.weekContaining(now, london), vm.period.value)

    @Test fun monthAndBack() {
        vm.select(PeriodKind.MONTH)
        assertEquals(Period.monthContaining(now, london), vm.period.value)
        vm.previous()
        assertEquals(Period.monthContaining(now, london).previous(), vm.period.value)
        vm.next()
        assertEquals(Period.monthContaining(now, london), vm.period.value)
    }

    @Test fun emptyPeriodIsEmpty(): Unit = runBlocking {
        assertIs<StatsUi.Empty>(withTimeout(5_000) { vm.state.first { it !is StatsUi.Loading } })
    }
}
