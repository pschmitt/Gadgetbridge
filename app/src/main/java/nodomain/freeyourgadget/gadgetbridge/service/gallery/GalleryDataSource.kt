package nodomain.freeyourgadget.gadgetbridge.service.gallery

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.EOFException
import java.io.IOException
import kotlin.math.min

/**
 * A Media3 [DataSource] that reads the Preview of one [MediaFile] from a [GallerySource].
 * Each [open] sends one request for the range of the [DataSpec].
 * The caller must hold an acquire call of the [GallerySource].
 */
@OptIn(UnstableApi::class)
class GalleryDataSource(
    private val source: GallerySource,
    private val file: MediaFile,
) : BaseDataSource(true) {
    class Factory(private val source: GallerySource, private val file: MediaFile) : DataSource.Factory {
        override fun createDataSource(): DataSource = GalleryDataSource(source, file)
    }

    private var uri: Uri? = null
    private var stream: GalleryStream? = null
    private var bytesRemaining = 0L
    private var opened = false

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val input = source.open(file, Variant.PREVIEW, dataSpec.position, dataSpec.length)
        stream = input
        bytesRemaining = when {
            dataSpec.length != LENGTH_UNSET -> dataSpec.length
            input.length >= 0 -> input.length
            else -> LENGTH_UNSET
        }
        LOG.debug(
            "Opened the preview of {} from {}, requested length {}, {} bytes",
            file.id,
            dataSpec.position,
            dataSpec.length,
            bytesRemaining
        )
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) {
            return 0
        }
        if (bytesRemaining == 0L) {
            return C.RESULT_END_OF_INPUT
        }
        val input = stream ?: throw IOException("The data source is not open")
        val toRead = if (bytesRemaining == LENGTH_UNSET) length else min(length.toLong(), bytesRemaining).toInt()
        val read = input.read(buffer, offset, toRead)
        if (read == -1) {
            // An end of input before the known length must not set the content length in the cache
            if (bytesRemaining != LENGTH_UNSET) {
                throw EOFException("The preview of ${file.id} ended with $bytesRemaining bytes remaining")
            }
            return C.RESULT_END_OF_INPUT
        }
        if (bytesRemaining != LENGTH_UNSET) {
            bytesRemaining -= read
        }
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    @Throws(IOException::class)
    override fun close() {
        uri = null
        try {
            stream?.close()
        } finally {
            stream = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(GalleryDataSource::class.java)

        private const val LENGTH_UNSET = C.LENGTH_UNSET.toLong()
    }
}
