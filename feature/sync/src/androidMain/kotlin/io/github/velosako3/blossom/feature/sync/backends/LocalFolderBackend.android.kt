// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.backends

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import io.github.velosako3.blossom.feature.sync.RemoteFile
import io.github.velosako3.blossom.feature.sync.SyncBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A folder the user picked through the system picker (a SAF tree; for example one Syncthing keeps in step). A write
 * goes to a hidden temp document that replaces the target, so a half-written file is never under the real name.
 */
class SafFolderBackend(private val context: Context, private val tree: Uri) : SyncBackend {
    override val id = "saf:$tree"
    private val resolver get() = context.contentResolver
    private val rootId = DocumentsContract.getTreeDocumentId(tree)

    private data class Doc(val id: String, val name: String, val dir: Boolean, val size: Long, val modified: Long)

    private fun children(parentId: String): List<Doc> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        return resolver.query(uri, cols, null, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) add(Doc(c.getString(0), c.getString(1), c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR, c.getLong(3), c.getLong(4)))
            }
        }.orEmpty()
    }

    /** The document at [path] (segments under the tree root), creating folders on the way if [create]. */
    private fun find(path: List<String>, create: Boolean): Doc? {
        var current = Doc(rootId, "", true, 0, 0)
        for (segment in path) {
            val next = children(current.id).firstOrNull { it.name == segment }
                ?: if (create) {
                    val uri = DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, current.id), DocumentsContract.Document.MIME_TYPE_DIR, segment)
                        ?: throw IOException("can't create $segment")
                    Doc(DocumentsContract.getDocumentId(uri), segment, true, 0, 0)
                } else return null
            current = next
        }
        return current
    }

    override suspend fun list(prefix: String): List<RemoteFile> = withContext(Dispatchers.IO) {
        val parts = prefix.trim('/').split('/').filter { it.isNotEmpty() }
        val startParts = generateSequence(parts) { if (it.isEmpty()) null else it.dropLast(1) }.firstOrNull { find(it, false)?.dir == true } ?: emptyList()
        val start = find(startParts, false) ?: return@withContext emptyList()
        val out = mutableListOf<RemoteFile>()
        fun walk(doc: Doc, path: String) {
            for (c in children(doc.id)) {
                val p = if (path.isEmpty()) c.name else "$path/${c.name}"
                if (c.dir) walk(c, p)
                else if (!c.name.startsWith(TEMP) && p.startsWith(prefix.trim('/'))) out += RemoteFile(p, c.size, Instant.fromEpochMilliseconds(c.modified), null)
            }
        }
        walk(start, startParts.joinToString("/"))
        out
    }

    override suspend fun read(path: String): ByteArray = withContext(Dispatchers.IO) {
        val doc = find(path.trim('/').split('/'), false) ?: throw FileNotFoundException(path)
        resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, doc.id))?.use { it.readBytes() } ?: throw FileNotFoundException(path)
    }

    override suspend fun write(path: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val parts = path.trim('/').split('/')
        val dir = find(parts.dropLast(1), true)!!
        val dirUri = DocumentsContract.buildDocumentUriUsingTree(tree, dir.id)
        val temp = DocumentsContract.createDocument(resolver, dirUri, "application/octet-stream", "$TEMP${Uuid.random()}") ?: throw IOException("can't write $path")
        resolver.openOutputStream(temp, "wt")?.use { it.write(bytes) } ?: throw IOException("can't write $path")
        // SAF has no atomic replace: drop the old file, then give the complete temp file its name.
        children(dir.id).firstOrNull { it.name == parts.last() }?.let { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, it.id)) }
        DocumentsContract.renameDocument(resolver, temp, parts.last()) ?: throw IOException("can't rename to $path")
        Unit
    }

    private companion object { const val TEMP = ".blossom-tmp-" }
}
