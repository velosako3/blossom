// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.offline

/** A read position over a [DecryptingReader], for players that read sequentially and seek. */
class DecryptingCursor(private val reader: DecryptingReader) {
    val size: Long get() = reader.plainLength
    var position = 0L
        private set

    /** Fills up to [length] bytes of [buffer] from the start; -1 at the end. */
    fun read(buffer: ByteArray, length: Int): Int = read(buffer, 0, length)

    fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val n = reader.read(position, buffer, offset, length)
        if (n > 0) position += n
        return n
    }

    fun seek(to: Long): Boolean {
        if (to < 0 || to > size) return false
        position = to
        return true
    }

    fun close() = reader.close()
}
