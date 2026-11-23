// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.source.api

import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId

/** One music service blossom can search and stream from. */
interface MusicSource {
    val kind: SourceKind
    suspend fun search(query: String, filter: SearchFilter = SearchFilter.SONGS): SearchResults
    suspend fun track(id: TrackId): Track?
    /** @throws TrackUnavailableException when the track can't be streamed. */
    suspend fun resolveStream(id: TrackId, quality: AudioQuality): StreamInfo
}
