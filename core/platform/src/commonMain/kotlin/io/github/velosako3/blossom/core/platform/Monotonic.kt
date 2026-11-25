// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.platform

/** A monotonic clock in nanoseconds (System.nanoTime on the JVM): playback positions are stamped and read with it. */
expect fun monotonicNanos(): Long
