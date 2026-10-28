package nodomain.freeyourgadget.gadgetbridge.service.devices.dji.gallery

import nodomain.freeyourgadget.gadgetbridge.kaitai.DumlGeneral
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.TreeMap

/**
 * The file list that a DJI camera sends in General 0x27 messages.
 */
object DjiFileList {
    private val LOG: Logger = LoggerFactory.getLogger(DjiFileList::class.java)

    private const val LIST_HEADER_SIZE = 8
    private const val RECORD_HEADER_SIZE = 18
    private const val TAG_RECORD_LENGTH = 0x19
    private const val TAG_PATH = 0x1a
    private const val TAG_UNKNOWN = 0x1b
    private const val TAG_END = 0x03
    private const val PATH_FULL = 0x01
    private const val MEDIA_TYPE_VIDEO = 1

    private val NAME_TIME = Regex("^DJI_(\\d{14})_")
    private val NAME_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    /**
     * One record of the file list.
     *
     * @property fatTime the file time in FAT date and time format
     * @property path the path of the Original, without extension
     * @property thumbnailPath the path of the Thumbnail, without extension
     */
    data class Record(
        val fileId: Long,
        val fatTime: Long,
        val size: Long,
        val durationS: Int,
        val path: String,
        val thumbnailPath: String,
        val favorite: Boolean,
        val video: Boolean,
        val name: String,
    )

    /**
     * The data fragments of one transfer.
     */
    class Transfer(private val transferId: Int) {
        private val fragments = TreeMap<Long, ByteArray>()

        /**
         * Adds a FileTransferDataRequest message of this transfer, and ignores other messages. Returns true at the end
         * of the transfer.
         */
        @Synchronized
        fun onMessage(message: DumlGeneral.FileTransferDataRequest): Boolean {
            if (message.magic() != DjiFileCommands.MAGIC || message.transferId() != transferId) {
                return false
            }
            when (message.msgType()) {
                DjiFileCommands.MSG_DATA -> {
                    LOG.debug(
                        "Received fragment {} of transfer {}, {} bytes",
                        message.fragmentIndex(),
                        transferId,
                        message.data().size
                    )
                    fragments[message.fragmentIndex()] = message.data()
                }

                DjiFileCommands.MSG_END -> {
                    LOG.debug("Received end of transfer {}", transferId)
                    return true
                }
            }
            return false
        }

        /**
         * The concatenated fragments. Throws when a fragment is missing.
         */
        @Synchronized
        @Throws(IOException::class)
        fun data(): ByteArray {
            val out = ByteArrayOutputStream()
            var expected = 0L
            for ((index, fragment) in fragments) {
                if (index != expected) {
                    throw IOException("Fragment $expected of transfer $transferId is missing")
                }
                out.write(fragment)
                expected++
            }
            return out.toByteArray()
        }
    }

    /**
     * Parses the concatenated data of a transfer.
     */
    @Throws(IOException::class)
    fun parseRecords(data: ByteArray): List<Record> {
        try {
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val count = buf.getInt(0)
            val records = ArrayList<Record>()
            var start = LIST_HEADER_SIZE
            repeat(count) { index ->
                val record = parseRecord(data, buf, start) ?: throw IOException("Bad record $index at $start")
                records.add(record.first)
                start += record.second
            }
            return records
        } catch (e: IndexOutOfBoundsException) {
            throw IOException("Truncated file list", e)
        }
    }

    /**
     * Returns the capture time from a file name such as DJI_20260827185020_0118_D.JPG, in the time
     * zone of the phone, or null when the name has no time.
     */
    fun nameTime(name: String): Long? {
        val digits = NAME_TIME.find(name)?.groupValues?.get(1) ?: return null
        return try {
            LocalDateTime.parse(digits, NAME_TIME_FORMAT).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: DateTimeException) {
            null
        }
    }

    /**
     * Returns the time of a FAT date and time value, in the time zone of the phone, or null when
     * the value is not a valid time.
     */
    fun fatTime(fatTime: Long): Long? {
        return try {
            LocalDateTime.of(
                ((fatTime shr 25) and 0x7f).toInt() + 1980,
                ((fatTime shr 21) and 0x0f).toInt(),
                ((fatTime shr 16) and 0x1f).toInt(),
                ((fatTime shr 11) and 0x1f).toInt(),
                ((fatTime shr 5) and 0x3f).toInt(),
                ((fatTime and 0x1f) * 2).toInt()
            ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: DateTimeException) {
            null
        }
    }

    /**
     * Returns the record at [start] and its length, or null when it is not valid.
     */
    private fun parseRecord(data: ByteArray, buf: ByteBuffer, start: Int): Pair<Record, Int>? {
        var recordLength = 0
        var path: String? = null
        var thumbnailPath: String? = null
        var p = start + RECORD_HEADER_SIZE
        while ((data[p].toInt() and 0xff) in TAG_RECORD_LENGTH..TAG_UNKNOWN) {
            val tag = data[p].toInt() and 0xff
            val valueStart = p + 5
            val valueEnd = p + 1 + buf.getInt(p + 1)
            if (valueEnd < valueStart) {
                return null
            }
            when (tag) {
                TAG_RECORD_LENGTH -> recordLength = (buf.getShort(valueStart).toInt() and 0xffff) + RECORD_HEADER_SIZE
                TAG_PATH -> {
                    val value = cString(data, valueStart + 1, valueEnd)
                    if (data[valueStart].toInt() == PATH_FULL) {
                        path = value
                    } else {
                        thumbnailPath = value
                    }
                }
            }
            p = valueEnd
        }
        if (recordLength <= 0 || data[p].toInt() != TAG_END || path == null || thumbnailPath == null) {
            return null
        }
        val end = start + recordLength
        var name: String? = null
        for (i in p until end - 1) {
            if (data[i].toInt() == 0x0d && data[i + 1].toInt() == 0x1d) {
                name = cString(data, i + 2, end)
                break
            }
        }
        if (name == null) {
            return null
        }
        val record = Record(
            fileId = buf.getInt(start + 8).toLong() and 0xffffffffL,
            fatTime = buf.getInt(start).toLong() and 0xffffffffL,
            size = buf.getInt(start + 4).toLong() and 0xffffffffL,
            durationS = buf.getShort(start + 12).toInt() and 0xffff,
            path = path,
            thumbnailPath = thumbnailPath,
            favorite = data[p + 1].toInt() != 0,
            video = data[p + 2].toInt() == MEDIA_TYPE_VIDEO,
            name = name,
        )
        return record to recordLength
    }

    private fun cString(data: ByteArray, from: Int, to: Int): String {
        var end = from
        while (end < to && data[end].toInt() != 0) {
            end++
        }
        return String(data, from, end - from, Charsets.US_ASCII)
    }
}
