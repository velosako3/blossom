// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.library.local

import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.WatchService
import java.util.concurrent.TimeUnit

/**
 * Desktop local library: scans the user's folders, then watches them (java.nio WatchService, recursively) and
 * rescans within about a second of a change. Unchanged files are not re-read.
 */
class FolderLocalLibrary(
    private val folders: StateFlow<List<File>>,
    artworkDir: File,
    private val scope: CoroutineScope,
) : LocalLibrary {
    private val scanner = FolderScanner(artworkDir)
    private val files = MutableStateFlow<List<ScannedFile>?>(null)
    private val lock = Mutex()
    private var watchJob: Job? = null

    init {
        scope.launch(Dispatchers.IO) {
            folders.collect { roots ->
                watchJob?.cancel()
                val registered = kotlinx.coroutines.CompletableDeferred<Unit>()
                watchJob = scope.launch(Dispatchers.IO + kotlinx.coroutines.SupervisorJob()) { watch(roots, registered) }
                registered.await() // watch first, then scan: nothing copied in between is missed
                rescan()
            }
        }
    }

    override fun tracks(): Flow<List<Track>> = files.filterNotNull().map { list -> list.map { it.track }.sortedBy { it.title.lowercase() } }

    override suspend fun rescan() = lock.withLock {
        files.value = scanner.scan(folders.value.filter { it.isDirectory })
    }

    override fun pathFor(id: TrackId): String? = files.value?.firstOrNull { it.track.id == id }?.path?.takeIf { File(it).isFile }

    private suspend fun watch(roots: List<File>, registered: kotlinx.coroutines.CompletableDeferred<Unit>) {
        var service: WatchService? = null
        try {
            service = FileSystems.getDefault().newWatchService()
            fun register(dir: Path) = runCatching { dir.register(service, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY) }
            roots.filter { it.isDirectory }.forEach { root -> root.walkTopDown().filter { it.isDirectory }.forEach { register(it.toPath()) } }
            registered.complete(Unit)
            while (currentCoroutineContext().isActive) {
                val key = service.poll(500, TimeUnit.MILLISECONDS) ?: continue
                var changed = false
                key.pollEvents().forEach { event ->
                    changed = true
                    val child = (key.watchable() as Path).resolve(event.context() as Path)
                    if (event.kind() == ENTRY_CREATE && child.toFile().isDirectory) child.toFile().walkTopDown().filter { it.isDirectory }.forEach { register(it.toPath()) }
                }
                key.reset()
                if (changed) {
                    delay(400) // let copies finish and coalesce bursts
                    rescan()
                }
            }
        } catch (_: ClosedWatchServiceException) {
        } catch (e: java.io.IOException) {
            // No watching (e.g. inotify limit reached): the library still works, it just won't notice changes live.
        } finally {
            registered.complete(Unit)
            service?.close()
        }
    }
}
