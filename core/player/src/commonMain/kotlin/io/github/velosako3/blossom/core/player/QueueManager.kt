// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.player

import io.github.velosako3.blossom.core.model.Track
import kotlin.random.Random

sealed interface QueueMove {
    data class Moved(val index: Int) : QueueMove
    data object Restart : QueueMove
    data object End : QueueMove
}

/** Pure queue logic: order, shuffle, repeat, edits. No I/O, no engine. */
class QueueManager {
    var queue: List<Track> = emptyList()
        private set
    var currentIndex: Int = -1
        private set
    var repeat: RepeatMode = RepeatMode.OFF
    var shuffled: Boolean = false
        private set
    private var original: List<Track> = emptyList()

    val current: Track? get() = queue.getOrNull(currentIndex)

    fun setQueue(tracks: List<Track>, startIndex: Int) {
        queue = tracks
        original = tracks
        shuffled = false
        currentIndex = if (tracks.isEmpty()) -1 else startIndex.coerceIn(0, tracks.lastIndex)
    }

    fun jumpTo(index: Int) {
        if (index in queue.indices) currentIndex = index
    }

    /** [userInitiated] skips move on even with repeat ONE (the button means "next song"). */
    fun next(userInitiated: Boolean = false): QueueMove {
        if (queue.isEmpty()) return QueueMove.End
        return when {
            repeat == RepeatMode.ONE && !userInitiated -> QueueMove.Moved(currentIndex)
            currentIndex < queue.lastIndex -> QueueMove.Moved(++currentIndex)
            repeat != RepeatMode.OFF -> { currentIndex = 0; QueueMove.Moved(0) }
            else -> QueueMove.End
        }
    }

    /** More than 3 s in restarts the track; otherwise steps back (or restarts the first track). */
    fun previous(positionMs: Long): QueueMove = when {
        positionMs > RESTART_THRESHOLD_MS || currentIndex <= 0 -> QueueMove.Restart
        else -> QueueMove.Moved(--currentIndex)
    }

    /** Shuffle keeps the current track first, deterministically for [seed]; turning it off restores the order. */
    fun setShuffle(enabled: Boolean, seed: Long = Random.nextLong()) {
        if (enabled == shuffled) return
        val current = current
        if (enabled) {
            original = queue
            val rest = queue.filterIndexed { i, _ -> i != currentIndex }.shuffled(Random(seed))
            queue = listOfNotNull(current) + rest
            currentIndex = if (current == null) -1 else 0
        } else {
            val extra = queue.filter { it !in original }
            queue = original.filter { it in queue } + extra
            currentIndex = current?.let { queue.indexOf(it) } ?: -1
        }
        shuffled = enabled
    }

    fun addNext(track: Track) = insert(currentIndex + 1, listOf(track))
    fun addToQueue(tracks: List<Track>) = insert(queue.size, tracks)

    /** Appends without touching shuffle state (autoplay radio). */
    fun append(tracks: List<Track>) = insert(queue.size, tracks)

    private fun insert(at: Int, tracks: List<Track>) {
        val index = at.coerceIn(0, queue.size)
        queue = queue.toMutableList().apply { addAll(index, tracks) }
        original = original + tracks
        if (currentIndex == -1 && queue.isNotEmpty()) currentIndex = 0
    }

    fun move(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices || from == to) return
        val current = current
        queue = queue.toMutableList().apply { add(to, removeAt(from)) }
        currentIndex = queue.indexOf(current)
    }

    fun remove(index: Int) {
        if (index !in queue.indices) return
        val removed = queue[index]
        queue = queue.toMutableList().apply { removeAt(index) }
        original = original - removed
        when {
            queue.isEmpty() -> currentIndex = -1
            index < currentIndex -> currentIndex--
            index == currentIndex -> currentIndex = currentIndex.coerceAtMost(queue.lastIndex)
        }
    }

    private companion object {
        const val RESTART_THRESHOLD_MS = 3_000L
    }
}
