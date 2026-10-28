package nodomain.freeyourgadget.gadgetbridge.service.gallery

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The folder `DCIM/Gadgetbridge/<device name>` in the phone's media store, into where the
 * originals are downloaded.
 */
class GalleryMediaStore(private val context: Context, deviceName: String) {
    /**
     * A new file in the media store. It is not visible to other apps until [commit].
     */
    interface Target {
        val output: OutputStream

        /**
         * Makes the file visible, and returns its media store URI, or null when it is not known.
         */
        @Throws(IOException::class)
        fun commit(): Uri?

        /**
         * Deletes the partial file.
         */
        fun delete()
    }

    private val folder = deviceName.replace(INVALID_CHARS, "_")
    private val relativePath = "${Environment.DIRECTORY_DCIM}/$ROOT_FOLDER/$folder/"

    /**
     * Creates the [Target] for the original of [file], or returns null when the file is already in the folder.
     */
    @Throws(IOException::class)
    fun create(file: MediaFile): Target? {
        val name = displayName(file)
        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase(Locale.ROOT))
            ?: if (file.type == MediaType.VIDEO) "video/mp4" else "image/jpeg"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            createScoped(file, name, mimeType)
        } else {
            createLegacy(file, name, mimeType)
        }
    }

    /**
     * Returns the IDs of the files whose originals are in the folder.
     */
    fun findDownloaded(files: List<MediaFile>): Set<String> {
        if (files.isEmpty()) {
            return emptySet()
        }
        val found = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val names = files.mapTo(LinkedHashSet()) { displayName(it) }.toList()
            val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val rows = queryRows(context.contentResolver, collection, names).filter { !it.pending }
            files.filter { file -> rows.any { it.name == displayName(file) && matches(file, it.size) } }
        } else {
            files.filter { legacyOriginal(it) != null }
        }
        return found.mapTo(HashSet()) { it.id }
    }

    /**
     * Returns the URI of the original of [file] in the folder, or null when it is not in the folder.
     */
    fun findOriginal(file: MediaFile): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return legacyOriginal(file)?.let { Uri.fromFile(it) }
        }
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        return queryRows(context.contentResolver, collection, listOf(displayName(file)))
            .firstOrNull { !it.pending && matches(file, it.size) }
            ?.uri
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createScoped(file: MediaFile, name: String, mimeType: String): Target? {
        val resolver = context.contentResolver
        val collection = if (file.type == MediaType.VIDEO) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }

        for (row in queryRows(resolver, collection, listOf(name))) {
            if (row.pending) {
                // Only the rows for this app are visible while they are pending
                LOG.debug("Deleting the pending row {} of {}", row.uri, name)
                resolver.delete(row.uri, null, null)
            } else if (matches(file, row.size)) {
                LOG.info("Skipping {}: {} is in the media store with {} bytes", file.id, row.uri, row.size)
                return null
            } else {
                LOG.debug("{} is in the media store with {} bytes, not {}", row.uri, row.size, file.size)
            }
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (file.time > 0) {
                put(MediaStore.MediaColumns.DATE_TAKEN, file.time)
            }
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("The media store did not create $name")
        val stream = try {
            resolver.openOutputStream(uri) ?: throw IOException("The media store did not open $uri")
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        LOG.debug("Created the pending row {} for {} in {}", uri, name, relativePath)

        return object : Target {
            override val output = stream

            override fun commit(): Uri {
                val update = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                if (resolver.update(uri, update, null, null) != 1) {
                    throw IOException("The media store did not update $uri")
                }
                return uri
            }

            override fun delete() {
                try {
                    stream.close()
                } catch (e: IOException) {
                    LOG.warn("Failed to close {}", uri, e)
                }
                val deleted = resolver.delete(uri, null, null)
                LOG.debug("Deleted {} rows for the pending {}", deleted, uri)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun queryRows(resolver: ContentResolver, collection: Uri, names: List<String>): List<Row> {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.IS_PENDING,
        )
        val placeholders = names.joinToString(",") { "?" }
        val selection =
            "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} IN ($placeholders)"
        val selectionArgs = arrayOf(relativePath, *names.toTypedArray())
        val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
            }
            resolver.query(collection, projection, queryArgs, null)
        } else {
            @Suppress("DEPRECATION")
            val pendingCollection = MediaStore.setIncludePending(collection)
            resolver.query(pendingCollection, projection, selection, selectionArgs, null)
        }
        return cursor?.use {
            val rows = ArrayList<Row>()
            while (it.moveToNext()) {
                rows.add(
                    Row(
                        ContentUris.withAppendedId(collection, it.getLong(0)),
                        it.getString(1),
                        it.getLong(2),
                        it.getInt(3) != 0
                    )
                )
            }
            rows
        } ?: emptyList()
    }

    private fun createLegacy(file: MediaFile, name: String, mimeType: String): Target? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            throw IOException("No permission to write to the external storage")
        }
        val directory = legacyDirectory()
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Failed to create $directory")
        }
        val target = File(directory, name)
        if (target.isFile && matches(file, target.length())) {
            LOG.info("Skipping {}: {} exists with {} bytes", file.id, target, target.length())
            return null
        }
        val partial = File(directory, "$name$PARTIAL_SUFFIX")
        val stream = FileOutputStream(partial)
        LOG.debug("Created {}", partial)

        return object : Target {
            override val output = stream

            override fun commit(): Uri? {
                if (target.exists() && !target.delete()) {
                    throw IOException("Failed to delete $target")
                }
                if (!partial.renameTo(target)) {
                    throw IOException("Failed to rename $partial to $target")
                }
                LOG.debug("Renamed {} to {}", partial, target)
                if (file.time > 0 && !target.setLastModified(file.time)) {
                    LOG.warn("Failed to set the time of {}", target)
                }
                val scanned = CountDownLatch(1)
                var uri: Uri? = null
                MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf(mimeType)) { _, result ->
                    uri = result
                    scanned.countDown()
                }
                if (!scanned.await(SCAN_TIMEOUT_S, TimeUnit.SECONDS)) {
                    LOG.warn("The media scanner did not scan {}", target)
                }
                return uri
            }

            override fun delete() {
                try {
                    stream.close()
                } catch (e: IOException) {
                    LOG.warn("Failed to close {}", partial, e)
                }
                if (partial.exists() && !partial.delete()) {
                    LOG.warn("Failed to delete {}", partial)
                }
            }
        }
    }

    private fun legacyOriginal(file: MediaFile): File? =
        File(legacyDirectory(), displayName(file)).takeIf { it.isFile && matches(file, it.length()) }

    @Suppress("DEPRECATION")
    private fun legacyDirectory() =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "$ROOT_FOLDER/$folder")

    private fun displayName(file: MediaFile) = file.name.replace(INVALID_CHARS, "_")

    /**
     * Returns true when a stored file with [size] bytes is the original of [file].
     */
    private fun matches(file: MediaFile, size: Long) = file.size <= 0 || size == file.size

    private class Row(val uri: Uri, val name: String?, val size: Long, val pending: Boolean)

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(GalleryMediaStore::class.java)

        private const val ROOT_FOLDER = "Gadgetbridge"
        private const val SCAN_TIMEOUT_S = 10L
        private const val PARTIAL_SUFFIX = ".part"

        // The media store replaces these characters, therefore a name with them would not match its row
        private val INVALID_CHARS = Regex("[\\x00-\\x1f\\x7f\"*/:<>?\\\\|]")
    }
}
