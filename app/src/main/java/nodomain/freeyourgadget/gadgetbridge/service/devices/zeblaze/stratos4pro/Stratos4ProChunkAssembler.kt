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

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream

/**
 * Reassembles chunked replies. Synchronized: frames come in on the BLE thread, the GPS watchdog
 * cancels from the main thread.
 */
internal class Stratos4ProChunkAssembler {
    /** Partially received payloads, see [key]. */
    private val buffers = HashMap<Int, ByteArrayOutputStream>()

    /** Keys whose orphan chunks were already logged (or cancelled on purpose): one warning each. */
    private val orphaned = HashSet<Int>()

    /**
     * Chunked payload: `01 CMD MODE FIELD <idx> <slice>`, closed by index [CHUNK_END]. Chunk 0 has
     * a length prefix whose width depends on the command ([dataStart]). Returns the payload on the
     * closing frame, else null.
     *
     * [bareEndIsEmpty]: a close without chunk 0 means "nothing". Opt-in: for lists an empty result
     * would read as "none".
     */
    @Synchronized
    fun collect(value: ByteArray, dataStart: Int, bareEndIsEmpty: Boolean = false): ByteArray? {
        if (value.size < 5) return null
        val key = key(value[1], value[2], value[3], wide = false)
        when (value[4].toInt() and 0xff) {
            0 -> {
                if (value.size < dataStart) return null
                orphaned.remove(key)
                buffers[key] = ByteArrayOutputStream().apply {
                    write(value, dataStart, value.size - dataStart)
                }
            }

            CHUNK_END -> {
                val done = buffers.remove(key) ?: return if (bareEndIsEmpty) ByteArray(0) else null
                return done.toByteArray()
            }

            else -> buffers[key]?.write(value, 5, value.size - 5) ?: orphan(key, value)
        }
        return null
    }

    /**
     * As [collect] for files (`c6 aa 04`, `e8`, `e1 aa 01`): 2-byte index at 4-5, end `fd fd`.
     * Chunk 0's 2-byte total length (bytes 6-7) stays in the payload as a header.
     */
    @Synchronized
    fun collectFile(value: ByteArray): ByteArray? {
        if (value.size < 6) return null
        val key = key(value[1], value[2], value[3], wide = true)
        when (u16(value, 4)) {
            0 -> {
                orphaned.remove(key)
                buffers[key] = ByteArrayOutputStream().apply { write(value, 6, value.size - 6) }
            }

            // A bare terminator without chunk 0 = no such file (e.g. a workout without track):
            // empty payload, else the caller waits forever.
            CHUNK_END_WIDE -> return (buffers.remove(key) ?: ByteArrayOutputStream()).toByteArray()

            else -> buffers[key]?.write(value, 6, value.size - 6) ?: orphan(key, value)
        }
        return null
    }

    /** Drop a half-received file, e.g. a GPS transfer given up on. */
    @Synchronized
    fun cancelFile(cmd: Byte, mode: Byte, field: Byte) {
        val key = key(cmd, mode, field, wide = true)
        buffers.remove(key)
        // The rest of the file still arrives; expected, so do not warn about it.
        orphaned.add(key)
    }

    @Synchronized
    fun clear() {
        buffers.clear()
        orphaned.clear()
    }

    /** A chunk whose chunk 0 never came: dropped, logged once per transfer. */
    private fun orphan(key: Int, value: ByteArray) {
        if (orphaned.add(key)) {
            LOG.warn("chunk without a start, dropping the transfer: {}", value.copyOf(minOf(value.size, 8)).toHex())
        }
    }

    /**
     * Includes the mode: live `c6 ac 01` minutes arrive while a `c6 aa 01` frame is being
     * assembled. [wide] keeps the two index formats apart.
     */
    private fun key(cmd: Byte, mode: Byte, field: Byte, wide: Boolean): Int =
        (if (wide) 0x1000000 else 0) or ((cmd.toInt() and 0xff) shl 16) or
            ((mode.toInt() and 0xff) shl 8) or (field.toInt() and 0xff)

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(Stratos4ProChunkAssembler::class.java)
    }
}
