package nodomain.freeyourgadget.gadgetbridge.service.devices.dji.gallery

import nodomain.freeyourgadget.gadgetbridge.kaitai.DumlCamera
import nodomain.freeyourgadget.gadgetbridge.kaitai.DumlGeneral
import nodomain.freeyourgadget.gadgetbridge.service.devices.dji.duml.messages.encodeToBytes
import nodomain.freeyourgadget.gadgetbridge.service.gallery.GalleryFilter

/**
 * The payloads of the DJI commands that list, favorite and delete MediaFiles.
 */
object DjiFileCommands {
    const val MAX_RECORDS = 45

    const val MAGIC = 0x4a
    const val MSG_LIST_REQUEST = 0x00
    const val MSG_DATA = 0x01
    const val MSG_END = 0x03
    const val MSG_TRANSFER_ACK = 0x04

    private const val FLAG_LAST = 0x1000
    private const val LIST_REQUEST_SIZE = 42
    private const val TRANSFER_ACK_SIZE = 14
    private const val LIST_REQUEST_UNKNOWN1 = 0x010d
    private const val LIST_REQUEST_UNKNOWN2 = 0x0100
    private const val LIST_REQUEST_RESERVED_SIZE = 13
    private const val FILTER_NONE = 0xffffffffL
    private const val FILTER_INCLUDE = 0x80000000L
    private const val FILTER_EXCLUDE = 0L

    /**
     * Request one page, from the File ID [cursor].
     */
    fun listRequest(transferId: Int, cursor: Long, filter: GalleryFilter): ByteArray {
        return DumlGeneral.FileListRequest().apply {
            setMagic(MAGIC)
            setMsgType(MSG_LIST_REQUEST)
            setLengthFlags(LIST_REQUEST_SIZE or FLAG_LAST)
            setTransferId(transferId and 0xffff)
            setFragmentIndex(0)
            setCursor(cursor)
            setMaxRecords(MAX_RECORDS)
            setUnknown1(LIST_REQUEST_UNKNOWN1)
            setFavoritesOnly(if (filter == GalleryFilter.FAVORITES) 1 else 0)
            setVideoFilter(
                when (filter) {
                    GalleryFilter.PHOTOS -> FILTER_EXCLUDE
                    GalleryFilter.VIDEOS -> FILTER_INCLUDE
                    else -> FILTER_NONE
                }
            )
            setPhotoFilter(
                when (filter) {
                    GalleryFilter.PHOTOS -> FILTER_INCLUDE
                    GalleryFilter.VIDEOS -> FILTER_EXCLUDE
                    else -> FILTER_NONE
                }
            )
            setUnknown2(LIST_REQUEST_UNKNOWN2)
            setReserved(ByteArray(LIST_REQUEST_RESERVED_SIZE))
        }.encodeToBytes()
    }

    /**
     * Confirm the end of a transfer.
     */
    fun transferAck(transferId: Int): ByteArray {
        return DumlGeneral.FileTransferAck().apply {
            setMagic(MAGIC)
            setMsgType(MSG_TRANSFER_ACK)
            setLengthFlags(TRANSFER_ACK_SIZE or FLAG_LAST)
            setTransferId(transferId and 0xffff)
            setFragmentIndex(0)
            setStatus(1)
        }.encodeToBytes()
    }

    /**
     * Sets or clears the Favorite flag of the provided files.
     */
    fun setFavorite(fileIds: List<Long>, favorite: Boolean, requestCounter: Long): ByteArray {
        return DumlCamera.FileStarFlagSetRequest().apply {
            setNumFileIds(fileIds.size)
            setFavorite(if (favorite) 1 else 0)
            setFileIds(ArrayList(fileIds))
            setRequestCounter(requestCounter)
            setReserved(0)
            setNumFileIdsAgain(fileIds.size.toLong())
        }.encodeToBytes()
    }

    /**
     * Deletes the files.
     */
    fun delete(fileIds: List<Long>, requestCounter: Long): ByteArray {
        return DumlGeneral.FileTransferDeleteRequest().apply {
            setNumFileIds(fileIds.size)
            setFileIds(ArrayList(fileIds))
            setRequestCounter(requestCounter)
            setReserved(0)
            setNumFileIdsAgain(fileIds.size.toLong())
        }.encodeToBytes()
    }
}
