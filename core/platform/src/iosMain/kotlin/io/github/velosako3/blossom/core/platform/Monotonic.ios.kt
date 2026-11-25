// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.platform

import platform.posix.CLOCK_UPTIME_RAW
import platform.posix.clock_gettime_nsec_np

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
actual fun monotonicNanos(): Long = clock_gettime_nsec_np(CLOCK_UPTIME_RAW.toUInt()).toLong()
