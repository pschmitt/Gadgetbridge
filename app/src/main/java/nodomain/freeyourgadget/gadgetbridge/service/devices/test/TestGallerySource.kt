package nodomain.freeyourgadget.gadgetbridge.service.devices.test

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.devices.test.TestDeviceConst
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.gallery.GalleryCapability
import nodomain.freeyourgadget.gadgetbridge.service.gallery.GalleryFilter
import nodomain.freeyourgadget.gadgetbridge.service.gallery.GalleryPage
import nodomain.freeyourgadget.gadgetbridge.service.gallery.GallerySource
import nodomain.freeyourgadget.gadgetbridge.service.gallery.GalleryStream
import nodomain.freeyourgadget.gadgetbridge.service.gallery.MediaFile
import nodomain.freeyourgadget.gadgetbridge.service.gallery.MediaType
import nodomain.freeyourgadget.gadgetbridge.service.gallery.Variant
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * A [GallerySource] that reads the photos and videos in a folder chosen by the user, and all its subfolders.
 * Favorites and deletions are kept in memory only.
 * The [MediaFile] IDs and the page keys are the document IDs.
 */
class TestGallerySource(private val context: Context, private val device: GBDevice) : GallerySource {
    override val capabilities: Set<GalleryCapability> = EnumSet.of(
        GalleryCapability.FAVORITE,
        GalleryCapability.DELETE,
        GalleryCapability.FILTER_TYPE,
        GalleryCapability.FILTER_FAVORITE,
        GalleryCapability.SEEK,
    )

    private class Entry(val uri: Uri, val file: MediaFile)

    // The files of the last scan, newest first
    @Volatile
    private var entries: List<Entry> = emptyList()

    private val uris = ConcurrentHashMap<String, Uri>()
    private val durations = ConcurrentHashMap<String, Int>()
    private val favorites: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val deleted: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override suspend fun acquire() {
    }

    override fun release() {
    }

    override suspend fun listPage(filter: GalleryFilter, after: String?): GalleryPage = withContext(Dispatchers.IO) {
        if (after == null) {
            entries = scan()
        }
        val start = if (after == null) 0 else entries.indexOfFirst { it.file.id == after } + 1
        val matching = entries.asSequence()
            .drop(start)
            .filter { !deleted.contains(it.file.id) && matches(it.file, filter) }
            .take(PAGE_SIZE + 1)
            .toList()
        val files = matching.take(PAGE_SIZE).map { entry ->
            entry.file.copy(
                duration = if (entry.file.type == MediaType.VIDEO) duration(entry) else 0,
                favorite = favorites.contains(entry.file.id),
            )
        }
        val next = if (matching.size > PAGE_SIZE) files.last().id else null
        LOG.debug("Listed {} files for {} after {}, next {}", files.size, filter, after, next)
        GalleryPage(files, next)
    }

    private fun scan(): List<Entry> {
        val folder = GBApplication.getDeviceSpecificSharedPrefs(device.address)
            .getString(TestDeviceConst.PREF_TEST_GALLERY_FOLDER, null)
            ?: throw IOException("No gallery folder is selected")
        val tree = DocumentFile.fromTreeUri(context, folder.toUri())
        if (tree == null || !tree.canRead()) {
            throw IOException("Cannot read the gallery folder $folder")
        }
        val scanned = listFilesRecursively(tree).mapNotNull { document ->
            val mimeType = document.type ?: return@mapNotNull null
            val type = when {
                mimeType.startsWith("image/") -> MediaType.PHOTO
                mimeType.startsWith("video/") -> MediaType.VIDEO
                else -> return@mapNotNull null
            }
            val id = DocumentsContract.getDocumentId(document.uri)
            Entry(
                document.uri,
                MediaFile(
                    id = id,
                    name = document.name ?: id,
                    type = type,
                    size = document.length(),
                    time = document.lastModified(),
                    duration = 0,
                    favorite = false,
                )
            )
        }.sortedByDescending { it.file.time }.toList()
        scanned.forEach { uris[it.file.id] = it.uri }
        LOG.debug("Found {} media files in {}", scanned.size, folder)
        return scanned
    }

    private fun listFilesRecursively(folder: DocumentFile): Sequence<DocumentFile> =
        folder.listFiles().asSequence().flatMap { document ->
            if (document.isDirectory) listFilesRecursively(document) else sequenceOf(document)
        }

    private fun matches(file: MediaFile, filter: GalleryFilter): Boolean = when (filter) {
        GalleryFilter.ALL -> true
        GalleryFilter.PHOTOS -> file.type == MediaType.PHOTO
        GalleryFilter.VIDEOS -> file.type == MediaType.VIDEO
        GalleryFilter.FAVORITES -> favorites.contains(file.id)
    }

    private fun duration(entry: Entry): Int = durations.getOrPut(entry.file.id) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, entry.uri)
            val millis = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            ((millis ?: 0L) / 1000).toInt()
        } catch (e: Exception) {
            LOG.debug("Failed to get the duration of {}", entry.file.name, e)
            0
        } finally {
            retriever.release()
        }
    }

    override fun open(file: MediaFile, variant: Variant, offset: Long, length: Long): GalleryStream {
        val uri = uris[file.id] ?: throw IOException("Unknown file ${file.id}")
        val available = (file.size - offset).coerceAtLeast(0)
        val streamLength = if (length >= 0) min(length, available) else available
        if (streamLength == 0L) {
            return GalleryStream(ByteArrayInputStream(ByteArray(0)), 0)
        }
        val descriptor = context.contentResolver.openAssetFileDescriptor(uri, "r")
            ?: throw IOException("Failed to open $uri")
        val input = descriptor.createInputStream()
        try {
            input.channel.position(descriptor.startOffset + offset)
        } catch (e: IOException) {
            input.close()
            throw e
        }
        LOG.debug("Opened {} bytes from {} at offset {}", streamLength, file.name, offset)
        return GalleryStream(input, streamLength)
    }

    override suspend fun setFavorite(files: List<MediaFile>, favorite: Boolean) {
        LOG.debug("Setting favorite={} for {}", favorite, files.joinToString { it.id })
        val ids = files.map { it.id }
        if (favorite) favorites.addAll(ids) else favorites.removeAll(ids.toSet())
    }

    override suspend fun delete(files: List<MediaFile>) {
        LOG.debug("Deleting {}", files.joinToString { it.id })
        deleted.addAll(files.map { it.id })
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(TestGallerySource::class.java)

        private const val PAGE_SIZE = 20
    }
}
