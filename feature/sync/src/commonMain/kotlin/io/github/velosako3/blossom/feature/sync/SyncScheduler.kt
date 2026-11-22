// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * When to sync while the app is open: at start, [quietPeriod] after the last local change, and every [interval].
 * (Android also syncs hourly in the background through WorkManager.) [engine] is null while no backend is set up.
 */
@OptIn(FlowPreview::class)
class SyncScheduler(
    private val engine: () -> SyncEngine?,
    private val localChanges: Flow<Any>,
    private val scope: CoroutineScope,
    private val quietPeriod: Duration = 10.seconds,
    private val interval: Duration = 15.minutes,
) {
    private val _last = MutableStateFlow<SyncReport?>(null)
    /** The latest sync's report, for the settings screen. */
    val last: StateFlow<SyncReport?> = _last
    private var job: Job? = null

    fun start() {
        job?.cancel()
        job = scope.launch {
            val ticks = flow { while (true) { emit(Unit); delay(interval) } }
            merge(ticks, localChanges.map { }.debounce(quietPeriod)).collect { syncNow() }
        }
    }

    suspend fun syncNow(): SyncReport? = engine()?.syncOnce()?.also { _last.value = it }

    fun stop() { job?.cancel(); job = null }
}
