// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import io.github.velosako3.blossom.core.platform.NetworkMonitor
import io.github.velosako3.blossom.core.platform.PreferenceStore
import io.github.velosako3.blossom.core.player.BlossomPlayer
import io.github.velosako3.blossom.core.model.SourceKind
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.feature.home.HomeViewModel
import io.github.velosako3.blossom.feature.search.RecentSearchStore
import io.github.velosako3.blossom.feature.search.SearchViewModel
import io.github.velosako3.blossom.source.youtubemusic.YtmSource
import kotlinx.coroutines.CoroutineScope

/**
 * The app's object graph, built once per process by the platform entry point (no DI framework: a handful of
 * long-lived objects wired by hand). View models live here so they survive navigation.
 */
class AppGraph(
    val player: BlossomPlayer,
    val ytm: YtmSource,
    val prefs: PreferenceStore,
    val network: NetworkMonitor,
    val scope: CoroutineScope,
    /** Shares a link: the share sheet on Android, the clipboard on desktop. */
    val shareLink: (String) -> Unit,
) {
    val search = SearchViewModel(ytm, RecentSearchStore(prefs), scope)
    val home by lazy { HomeViewModel(ytm, scope) }
}

/** The public web link for a track, for "Share link". */
fun Track.shareUrl(): String? = when (id.kind) {
    SourceKind.YOUTUBE_MUSIC -> "https://music.youtube.com/watch?v=${id.sourceId}"
    SourceKind.YOUTUBE -> "https://www.youtube.com/watch?v=${id.sourceId}"
    SourceKind.SOUNDCLOUD, SourceKind.UNTITLED, SourceKind.LOCAL -> null
}
