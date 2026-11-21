// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.library.local

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.MediaStore
import io.github.velosako3.blossom.core.model.Track
import io.github.velosako3.blossom.core.model.TrackId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android local library: MediaStore audio (needs READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE) plus folders the user
 * picked through the system picker (SAF tree URIs). Paths are content URIs. Tags come from MediaStore columns and,
 * for picked folders, MediaMetadataRetriever (jaudiotagger needs desktop-only APIs).
 */
class MediaStoreLibrary(
    private val context: Context,
    private val extraTrees: StateFlow<List<String>>,
    private val scope: CoroutineScope,
    private val hasPermission: () -> Boolean,
) : LocalLibrary {
    private val files = MutableStateFlow<List<ScannedFile>?>(null)

    init {
        context.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { scope.launch { rescan() } }
        })
        scope.launch { extraTrees.collect { rescan() } }
    }

    override fun tracks(): Flow<List<Track>> = files.filterNotNull().map { l -> l.map { it.track }.sortedBy { it.title.lowercase() } }

    override suspend fun rescan() = withContext(Dispatchers.IO) {
        val store = if (hasPermission()) runCatching { mediaStore() }.getOrDefault(emptyList()) else emptyList()
        val picked = extraTrees.value.flatMap { runCatching { tree(Uri.parse(it)) }.getOrDefault(emptyList()) }
        files.value = (store + picked).distinctBy { it.track.id }
    }

    override fun pathFor(id: TrackId): String? = files.value?.firstOrNull { it.track.id == id }?.path

    private fun mediaStore(): List<ScannedFile> {
        val cols = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_MODIFIED, MediaStore.Audio.Media.SIZE,
        )
        val out = mutableListOf<ScannedFile>()
        context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(6) ?: continue
                if (name.substringAfterLast('.', "").lowercase() !in SUPPORTED_EXTENSIONS) continue
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
                val artist = c.getString(2)?.takeUnless { it == MediaStore.UNKNOWN_STRING }
                val art = "content://media/external/audio/albumart/${c.getLong(4)}"
                val album = c.getString(3)?.takeUnless { it == MediaStore.UNKNOWN_STRING }
                out += ScannedFile(localTrack(c.getString(1), artist, album, c.getLong(5), name, art), uri.toString(), c.getLong(7), c.getLong(8))
            }
        }
        return out
    }

    private fun tree(treeUri: Uri): List<ScannedFile> {
        val out = mutableListOf<ScannedFile>()
        fun walk(docId: String) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_LAST_MODIFIED, DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0); val name = c.getString(1) ?: continue
                    if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) { walk(id); continue }
                    if (name.substringAfterLast('.', "").lowercase() !in SUPPORTED_EXTENSIONS) continue
                    val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                    val r = MediaMetadataRetriever()
                    val track = try {
                        r.setDataSource(context, uri)
                        val base = localTrack(
                            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE), r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM), r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0,
                            name, null,
                        )
                        // Embedded artwork, cached once per track in the app's cache folder.
                        val art = r.embeddedPicture?.let { bytes ->
                            val f = java.io.File(java.io.File(context.cacheDir, "artwork").apply { mkdirs() }, base.id.sourceId + ".img")
                            if (!f.isFile) f.writeBytes(bytes)
                            Uri.fromFile(f).toString()
                        }
                        base.copy(artworkUrl = art)
                    } catch (_: Exception) { localTrack(null, null, null, 0, name, null) } finally { r.release() }
                    out += ScannedFile(track, uri.toString(), c.getLong(3), c.getLong(4))
                }
            }
        }
        walk(DocumentsContract.getTreeDocumentId(treeUri))
        return out
    }
}
