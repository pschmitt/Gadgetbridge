package nodomain.freeyourgadget.gadgetbridge.service.gallery

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/**
 * A [GallerySource] provides the [MediaFile]s of one device.
 */
interface GallerySource {
    val capabilities: Set<GalleryCapability>

    /**
     * Opens the connection to the device, if necessary. The connection is kept open until [release].
     * Each call must have one [release] call.
     */
    @Throws(IOException::class)
    suspend fun acquire()

    fun release()

    /**
     * Returns the [GalleryPage] that starts after the Page key [after], or the first Page when it is null.
     */
    @Throws(IOException::class)
    suspend fun listPage(filter: GalleryFilter, after: String?): GalleryPage

    /**
     * Opens [length] bytes of a variant of a Media File, from [offset]. A length of -1 means up to
     * the end of the file. Blocks until the device answers.
     * It does not open the connection: the caller must hold an [acquire] call, else it throws an [IOException].
     */
    @Throws(IOException::class)
    fun open(file: MediaFile, variant: Variant, offset: Long, length: Long): GalleryStream

    /**
     * Sets or clears the favorite flag of the files on the device. Returns after the device acks.
     * Throws a [GalleryActionException] when the device applied the change to only some of the files.
     */
    @Throws(IOException::class)
    suspend fun setFavorite(files: List<MediaFile>, favorite: Boolean)

    /**
     * Deletes the files from the device. Returns after the device acks.
     * Throws a [GalleryActionException] when the device deleted only some of the files.
     */
    @Throws(IOException::class)
    suspend fun delete(files: List<MediaFile>)
}

/**
 * A [GallerySource.setFavorite] or [GallerySource.delete] call that failed after the device applied
 * it to the files in [applied].
 */
class GalleryActionException(val applied: List<MediaFile>, message: String, cause: Throwable) :
    IOException(message, cause)

enum class GalleryCapability {
    FAVORITE,
    DELETE,
    FILTER_TYPE,
    FILTER_FAVORITE,
    SEEK,
}

enum class GalleryFilter {
    ALL,
    PHOTOS,
    VIDEOS,
    FAVORITES,
}

enum class Variant {
    THUMBNAIL,
    PREVIEW,
    ORIGINAL,
}

/**
 * The [MediaFile]s of one listing request. [next] is the Page key of the next Page, or null at the end.
 */
data class GalleryPage(val files: List<MediaFile>, val next: String?)

/**
 * The bytes of a variant of a [MediaFile], from [GallerySource.open].
 *
 * @property length the number of bytes in the stream, or -1 when the device does not report it
 */
open class GalleryStream(input: InputStream, val length: Long) : FilterInputStream(input)
