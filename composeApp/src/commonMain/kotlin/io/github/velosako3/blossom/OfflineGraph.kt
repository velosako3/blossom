// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom

import io.github.velosako3.blossom.core.database.BlossomDatabase
import io.github.velosako3.blossom.core.database.SqlLibraryRepository
import io.github.velosako3.blossom.core.offline.DownloadManager
import io.github.velosako3.blossom.core.offline.KeyVault
import io.github.velosako3.blossom.core.offline.OfflineSettings
import io.github.velosako3.blossom.core.offline.SqlOfflineStore
import io.github.velosako3.blossom.core.platform.NetworkMonitor
import io.github.velosako3.blossom.core.platform.SecureStore
import io.github.velosako3.blossom.core.player.StreamResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import java.io.File

/** The offline cache in [dir], downloading through the player's [resolver] at the chosen quality. */
fun offlineStore(
    db: BlossomDatabase,
    dir: File,
    secure: SecureStore,
    resolver: StreamResolver,
    network: NetworkMonitor,
    settings: StateFlow<OfflineSettings>,
    scope: CoroutineScope,
): SqlOfflineStore {
    val manager = DownloadManager(db, dir, KeyVault(secure), { t, fresh -> if (fresh) resolver.invalidate(t.id); resolver.resolve(t.id, settings.value.quality) }, OkHttpClient(), network, settings, scope)
    return SqlOfflineStore(db, manager, SqlLibraryRepository(db), scope)
}
