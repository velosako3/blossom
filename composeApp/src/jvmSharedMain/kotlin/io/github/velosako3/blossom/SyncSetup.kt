// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import io.github.velosako3.blossom.core.platform.SecureStore
import io.github.velosako3.blossom.feature.sync.SyncBackend
import io.github.velosako3.blossom.feature.sync.lan.PeerDiscovery
import io.github.velosako3.blossom.feature.sync.ui.BackendConfig
import io.github.velosako3.blossom.feature.sync.ui.BackendKind

/** What each platform gives sync: its backends (with sign-in), the default device name and LAN discovery. */
class SyncSetup(
    val secure: SecureStore,
    val defaultName: String,
    /** Backends offered here; one without a configured client ID is left out. */
    val kinds: List<BackendKind>,
    /** Builds the backend; may open a sign-in page only when `interactive` (the user just chose it). */
    val connect: suspend (BackendConfig, interactive: Boolean) -> SyncBackend?,
    val discovery: (() -> PeerDiscovery)? = null,
)
