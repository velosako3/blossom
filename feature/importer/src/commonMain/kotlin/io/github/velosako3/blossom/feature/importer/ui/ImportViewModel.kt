// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.importer.ui

import io.github.velosako3.blossom.core.database.LibraryRepository
import io.github.velosako3.blossom.core.model.PlaylistId
import io.github.velosako3.blossom.feature.importer.DetectedLink
import io.github.velosako3.blossom.feature.importer.ImportJobId
import io.github.velosako3.blossom.feature.importer.ImportJobRunner
import io.github.velosako3.blossom.feature.importer.ImportJobState
import io.github.velosako3.blossom.feature.importer.ImportProgress
import io.github.velosako3.blossom.feature.importer.ImportRouter
import io.github.velosako3.blossom.feature.importer.ImportTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant

sealed interface ImportUi {
    data object Idle : ImportUi
    data object NotALink : ImportUi
    data class Detected(val link: DetectedLink) : ImportUi
    /** A playlist with the same source already exists: update it, or make a copy. */
    data class DuplicateFound(val link: DetectedLink, val existing: PlaylistId, val existingName: String) : ImportUi
    data class InProgress(val link: DetectedLink, val job: ImportJobId, val progress: ImportProgress?, val startedAt: Instant) : ImportUi
    data class Done(val link: DetectedLink, val playlistId: PlaylistId, val progress: ImportProgress, val startedAt: Instant) : ImportUi
    data class Failed(val link: DetectedLink, val failure: io.github.velosako3.blossom.feature.importer.ImportFailure? = null) : ImportUi
}

/**
 * Paste (or share) a link → detected → imported into a new playlist named after the source, or into an existing one.
 * A source that was imported before offers "Update existing" (adds only new tracks) or "Create a copy".
 */
class ImportViewModel(
    private val runner: ImportJobRunner,
    private val repo: LibraryRepository,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
    /** Test hook: every target an import is started with. */
    private val onStart: (ImportTarget) -> Unit = {},
) {
    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text
    private val _state = MutableStateFlow<ImportUi>(ImportUi.Idle)
    val state: StateFlow<ImportUi> = _state
    private var job: Job? = null
    /** Set synchronously on start, so a double tap can't start two imports. */
    @OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
    private val starting = kotlin.concurrent.atomics.AtomicBoolean(false)

    fun onTextChange(value: String) {
        _text.value = value
        if (_state.value is ImportUi.InProgress) return
        _state.value = when {
            value.isBlank() -> ImportUi.Idle
            else -> ImportRouter.detect(value)?.let(ImportUi::Detected) ?: ImportUi.NotALink
        }
    }

    /** The link to offer from the clipboard ("Import link from clipboard?"), if it is one. */
    fun clipboardOffer(clipboard: String?): DetectedLink? = clipboard?.let(ImportRouter::detect)

    /** Starts the detected link: into [into] if given, else a new playlist named [name] (or after the source). */
    fun start(name: String? = null, into: PlaylistId? = null) {
        val link = (_state.value as? ImportUi.Detected)?.link ?: return
        if (!starting.compareAndSet(false, true)) return
        scope.launch {
            if (into == null) {
                val same = repo.playlists().first().firstOrNull { it.origin?.url == link.canonicalUrl }
                if (same != null) { _state.value = ImportUi.DuplicateFound(link, same.id, same.name); starting.store(false); return@launch }
            }
            run(link, if (into != null) ImportTarget.Existing(into) else ImportTarget.NewPlaylist(name?.takeIf { it.isNotBlank() }))
        }
    }

    fun updateExisting() {
        val d = _state.value as? ImportUi.DuplicateFound ?: return
        scope.launch { run(d.link, ImportTarget.UpdateExisting(d.existing)) }
    }

    fun createCopy() {
        val d = _state.value as? ImportUi.DuplicateFound ?: return
        scope.launch { run(d.link, ImportTarget.NewPlaylist(null)) }
    }

    /** "Refresh from source" on an imported playlist. */
    fun refresh(playlist: PlaylistId, sourceUrl: String) {
        val link = ImportRouter.detect(sourceUrl) ?: return
        scope.launch { run(link, ImportTarget.UpdateExisting(playlist)) }
    }

    /** Back to an empty screen. A running import carries on in the background (it is resumable). */
    fun reset() { job?.cancel(); starting.store(false); _text.value = ""; _state.value = ImportUi.Idle }

    private suspend fun run(link: DetectedLink, target: ImportTarget) {
        onStart(target)
        val started = clock.now()
        val id = runner.start(link, target)
        _state.value = ImportUi.InProgress(link, id, null, started)
        job?.cancel()
        job = scope.launch {
            runner.progress(id).takeWhile { p ->
                _state.value = when (p.state) {
                    ImportJobState.DONE -> ImportUi.Done(link, p.playlistId!!, p, started)
                    ImportJobState.FAILED -> ImportUi.Failed(link, p.failure)
                    else -> ImportUi.InProgress(link, id, p, started)
                }
                p.state != ImportJobState.DONE && p.state != ImportJobState.FAILED
            }.collect {}
        }
    }
}
