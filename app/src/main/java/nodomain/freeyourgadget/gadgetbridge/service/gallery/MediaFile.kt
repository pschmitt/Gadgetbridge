package nodomain.freeyourgadget.gadgetbridge.service.gallery

/**
 * One media file (a photo or a video) on a device.
 *
 * @property id the identifier of the file in its [GallerySource]
 * @property size the size of the original version, in bytes
 * @property time the capture time in milliseconds since the epoch, from the device clock in the time zone of the phone
 * @property duration the duration of a video in seconds, 0 for a photo
 */
data class MediaFile(
    val id: String,
    val name: String,
    val type: MediaType,
    val size: Long,
    val time: Long,
    val duration: Int,
    val favorite: Boolean,
)

enum class MediaType {
    PHOTO,
    VIDEO,
}
