/*  Copyright (C) 2026 Vladimir Tasic

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro

import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import nodomain.freeyourgadget.gadgetbridge.activities.audiorecordings.AudioRecordingsActivity
import nodomain.freeyourgadget.gadgetbridge.database.repository.AudioRecordingsRepository
import nodomain.freeyourgadget.gadgetbridge.entities.AudioRecording
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.zip.CRC32

/**
 * Voice recorder files over the file service `f9`: list [RECORDING_DIR], then read each recording
 * not in the database yet, one at a time. Read-only: the service's delete (`ab 04`) and cancel
 * write (`ab 07`, which unlinks the last path it was given) are never sent.
 */
internal class Stratos4ProRecordings(support: Stratos4ProSupport) : Stratos4ProFeature(support) {
    private var list: FileListCollector? = null

    /** Name and timestamp of the recordings left to read. */
    private val pending = ArrayDeque<Pair<String, Long>>()
    private var reading: Pair<String, Long>? = null
    private var stream: FileReadStream? = null
    private var part: File? = null

    @Volatile
    private var busy = false

    /** No frame for [TIMEOUT_MS]: the watch dropped the request or the link stalled. */
    private val watchdog = Runnable {
        LOG.warn("recordings: no reply, stopping")
        synchronized(this@Stratos4ProRecordings) { finish() }
    }

    fun isBusy(): Boolean = busy

    /** A read cut by a disconnect is gone on the watch too. */
    @Synchronized
    fun onConnect() {
        if (busy) finish()
    }

    @Synchronized
    fun fetch() {
        if (busy) return
        busy = true
        list = FileListCollector()
        kick()
        queueFrames("list recordings", fileRequest(MODE_GET, FILE_LIST, RECORDING_DIR))
    }

    @Synchronized
    fun handle(value: ByteArray) {
        if (!busy || value.size < 6) {
            LOG.debug("file service: {}", value.toHex())
            return
        }
        kick()
        try {
            when {
                value[2] == MODE_GET && value[3] == FILE_LIST && value.size >= 7 -> list?.add(value)?.let { onList(it) }
                value[2] == MODE_SET && value[3] == FILE_READ -> onReadReply(value)
                value[2] == MODE_REPORT && value[3] == PUSH_FILE_DATA && value.size >= 9 -> stream?.let { if (it.add(value)) onReadEnd(it) }
                else -> LOG.debug("file service: {}", value.toHex())
            }
        } catch (e: IOException) {
            LOG.error("recordings: cannot save", e)
            finish()
        }
    }

    private fun onList(names: List<String>) {
        list = null
        for (name in names) {
            val time = recordingTime(name) ?: continue
            if (AudioRecordingsRepository.getByTimestamp(device, time) == null) pending.add(name to time)
        }
        LOG.info("recordings: {} files on the watch, {} new", names.size, pending.size)
        readNext()
    }

    private fun readNext() {
        val next = pending.removeFirstOrNull() ?: return finish()
        reading = next
        queueFrames("read recording", fileRequest(MODE_SET, FILE_READ, "$RECORDING_DIR/${next.first}"))
    }

    /** `01 f9 ab 05 09 00 <size:4> <crc32:4>`, or `01 f9 ab 05 01 <status>` if it cannot open the file. */
    private fun onReadReply(value: ByteArray) {
        val name = reading?.first ?: return
        if (value.size < 14 || value[5] != 0.toByte()) {
            LOG.warn("recordings: cannot read {}: {}", name, value.toHex())
            return readNext()
        }
        val dir = File(device.deviceCoordinator.getWritableExportDirectory(device, true), "voicememo")
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("cannot create $dir")
        val file = File(dir, "$name.part")
        part = file
        stream = FileReadStream(be32(value, 6), be32(value, 10), FileOutputStream(file))
    }

    private fun onReadEnd(s: FileReadStream) {
        val (name, time) = reading ?: return
        val file = part ?: return
        s.out.close()
        stream = null
        part = null
        val target = File(file.parentFile, name)
        if (!s.complete() || !file.renameTo(target)) {
            LOG.warn("recordings: {} failed: {}", name, s)
            file.delete()
            return readNext()
        }
        AudioRecordingsRepository.insertOrReplace(device, AudioRecording().apply {
            recordingId = UUID.randomUUID().toString()
            timestamp = time
            label = name.removeSuffix(".mp3")
            path = target.path
        })
        LOG.info("recordings: saved {}, {} bytes", name, target.length())
        readNext()
    }

    private fun kick() {
        handler.removeCallbacks(watchdog)
        handler.postDelayed(watchdog, TIMEOUT_MS)
    }

    private fun finish() {
        handler.removeCallbacks(watchdog)
        stream?.out?.close()
        part?.delete()
        stream = null
        part = null
        list = null
        reading = null
        pending.clear()
        busy = false
        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(AudioRecordingsActivity.ACTION_FETCH_FINISH))
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(Stratos4ProRecordings::class.java)

        /** `/NAND:/Recording` */
        internal const val RECORDING_DIR = "D:/Recording"
        private const val TIMEOUT_MS = 10_000L
    }
}

/**
 * `f9 aa 02` list: frames `01 f9 aa 02 <len> <idx:2> <entries>`, closed by
 * `01 f9 aa 02 06 fd fd <crc32:4>` over the whole data frames, headers included.
 */
internal class FileListCollector {
    private val crc = CRC32()
    private val body = ByteArrayOutputStream()

    /** The file names at the end frame (none on a CRC mismatch), else null. */
    fun add(frame: ByteArray): List<String>? {
        if (frame.size == 11 && u8(frame, 4) == 6 && u16(frame, 5) == 0xfdfd) {
            return if (be32(frame, 7) == crc.value) parseFileNames(body.toByteArray()) else emptyList()
        }
        crc.update(frame)
        body.write(frame, 7, frame.size - 7)
        return null
    }
}

/** List entries `<namelen+5> <type: 1 file, 0 folder> <size:4> <name>`; the file names. */
internal fun parseFileNames(body: ByteArray): List<String> {
    val names = ArrayList<String>()
    var off = 0
    while (off + 6 <= body.size) {
        val len = u8(body, off) - 5
        if (len < 0 || off + 6 + len > body.size) break
        if (body[off + 1] == 1.toByte()) names.add(String(body, off + 6, len, Charsets.UTF_8))
        off += 6 + len
    }
    return names
}

/**
 * One `f9 ab 05` read: frames `01 f9 ac 02 <len> <idx:4> <data>` from idx 0, closed by
 * `01 f9 ac 02 04 ff ff ff fd`. The watch streams without acks or resends, so a gap fails the file.
 */
internal class FileReadStream(private val size: Long, private val crc: Long, val out: OutputStream) {
    private val actual = CRC32()
    private var received = 0L
    private var next = 0L
    private var gap = false

    /** True at the end frame. */
    fun add(frame: ByteArray): Boolean {
        val idx = be32(frame, 5)
        if (idx == 0xfffffffdL) return true
        if (idx != next++) gap = true
        if (!gap) {
            out.write(frame, 9, frame.size - 9)
            actual.update(frame, 9, frame.size - 9)
            received += frame.size - 9
        }
        return false
    }

    fun complete(): Boolean = !gap && received == size && actual.value == crc

    override fun toString() = "$received/$size bytes, crc32 ${actual.value}/$crc, gap $gap"
}

/** `YYYYMMDD_HHMMSS.mp3`, the recorder's names in watch local time: epoch ms, null for other names. */
internal fun recordingTime(name: String): Long? {
    if (!name.matches(Regex("\\d{8}_\\d{6}\\.mp3"))) return null
    val format = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).apply { isLenient = false }
    return try {
        format.parse(name.substring(0, 15))?.time
    } catch (e: ParseException) {
        null
    }
}
