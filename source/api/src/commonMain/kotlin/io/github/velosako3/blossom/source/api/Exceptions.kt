// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.api

/** The track can't be played from this source (removed, region-locked, private, preview-only…). */
class TrackUnavailableException(reason: String, cause: Throwable? = null) : Exception(reason, cause)
