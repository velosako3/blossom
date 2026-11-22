// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.offline

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import io.github.velosako3.blossom.core.model.TrackId

/** Media3 reads saved tracks (`blossom-offline:///<track id>`) through this, decrypting chunk by chunk. */
@OptIn(UnstableApi::class)
class EncryptedDataSource(private val store: OfflineStore) : BaseDataSource(false) {
    private var cursor: DecryptingCursor? = null
    private var uri: Uri? = null
    private var remaining = 0L

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val id = TrackId.parse(dataSpec.uri.path.orEmpty().removePrefix("/"))
        val reader = try { store.openReader(id) } catch (e: CacheCorruptedException) {
            throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        } ?: throw DataSourceException(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
        val c = DecryptingCursor(reader)
        if (!c.seek(dataSpec.position)) { c.close(); throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE) }
        remaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else c.size - dataSpec.position
        cursor = c
        uri = dataSpec.uri
        transferStarted(dataSpec)
        return remaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val n = try {
            cursor!!.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
        } catch (e: CacheCorruptedException) {
            throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        }
        if (n < 0) return C.RESULT_END_OF_INPUT
        remaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        cursor?.close(); cursor = null
        if (uri != null) { uri = null; transferEnded() }
    }

    class Factory(private val store: OfflineStore) : DataSource.Factory {
        override fun createDataSource(): DataSource = EncryptedDataSource(store)
    }
}
