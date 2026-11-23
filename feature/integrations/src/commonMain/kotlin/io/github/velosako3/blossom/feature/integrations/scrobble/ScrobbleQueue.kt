// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.integrations.scrobble

import io.github.velosako3.blossom.core.platform.NetworkMonitor
import io.github.velosako3.blossom.core.platform.PreferenceStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Scrobbles waiting to be sent, kept in preferences so they survive restarts and offline days. Sent oldest first
 * while online. Each item remembers which services still need it, so a retry never sends it twice to one service.
 * A failing item is retried after each of [retryDelaysMs] and dropped after [MAX_TRIES] tries.
 */
class ScrobbleQueue(
    private val prefs: PreferenceStore,
    private val services: List<ScrobbleService>,
    private val network: NetworkMonitor,
    private val scope: CoroutineScope,
    private val retryDelaysMs: List<Long> = listOf(30_000, 120_000, 600_000, 1_800_000, 3_600_000),
) {
    @Serializable
    private data class Item(val scrobble: Scrobble, val pending: Set<String>, val tries: Int = 0)

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Item.serializer())
    private val lock = Mutex()
    private val kick = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private fun load(): List<Item> = prefs.get(KEY)?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()
    private fun save(items: List<Item>) = prefs.put(KEY, if (items.isEmpty()) null else json.encodeToString(serializer, items.takeLast(MAX_ITEMS)))

    fun pending(): List<Scrobble> = load().map { it.scrobble }

    /** Sends whenever something is queued or the network comes back. */
    fun start() {
        scope.launch {
            merge(kick, network.status.map { it.online }.filter { it }.map { }).collect { flush() }
        }
    }

    suspend fun enqueue(scrobble: Scrobble) {
        val targets = services.filter { it.enabled }.map { it.id }.toSet()
        if (targets.isEmpty()) return
        lock.withLock { save(load() + Item(scrobble, targets)) }
        kick.tryEmit(Unit)
    }

    private suspend fun flush() {
        while (network.status.value.online) {
            val item = lock.withLock { load().minByOrNull { it.scrobble.startedAt } } ?: return
            val done = item.pending.filter { id ->
                val service = services.firstOrNull { it.id == id } ?: return@filter true // removed: nothing to send
                try { service.scrobble(item.scrobble); true } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
            }.toSet()
            val left = item.pending - done
            val tries = item.tries + 1
            lock.withLock {
                val rest = load().filterNot { it == item }
                save(if (left.isEmpty() || tries >= MAX_TRIES) rest else rest + item.copy(pending = left, tries = tries))
            }
            if (left.isNotEmpty() && tries < MAX_TRIES) delay(retryDelaysMs.getOrElse(tries - 1) { retryDelaysMs.last() })
        }
    }

    private companion object {
        const val KEY = "scrobble.queue"
        const val MAX_TRIES = 5
        const val MAX_ITEMS = 5_000
    }
}
