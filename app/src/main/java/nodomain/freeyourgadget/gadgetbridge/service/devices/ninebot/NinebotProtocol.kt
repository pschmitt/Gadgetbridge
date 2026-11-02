/*  Copyright (C) 2026 fds2610

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.ninebot

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.LabeledEntry

/**
 * Frame layout of the Ninebot BLE-UART protocol, before encryption (see [NinebotCrypto]):
 * `5A A5 <len> <src> <dst> <cmd> <index> <data...>`, where `len` is the number of data bytes. An encrypted
 * frame carries 6 more bytes (MAC / checksum and counter), i.e. is `len + 13` bytes long.
 */
object NinebotProtocol {
    const val ID_CTRL = 0x20
    const val ID_BLE = 0x21
    const val ID_BATT = 0x22
    const val ID_PC = 0x3D
    const val ID_PHONE = 0x3E

    const val CMD_READ = 0x01
    const val CMD_WRITE = 0x02
    const val CMD_WRITE_NOREPLY = 0x03
    const val CMD_READ_ACK = 0x04
    const val CMD_WRITE_ACK = 0x05
    const val CMD_INIT = 0x5B
    const val CMD_PING = 0x5C
    const val CMD_PAIR = 0x5D

    /** Registers of the controller ([ID_CTRL]). */
    const val CTRL_SERIAL = 0x10
    const val CTRL_FIRMWARE = 0x1A
    const val CTRL_ALARM = 0x1C
    const val CTRL_STATUS = 0x1D
    const val CTRL_BATTERY = 0x22
    const val CTRL_RANGE = 0x25
    const val CTRL_ODOMETER = 0x29
    const val CTRL_TEMPERATURE = 0x3E
    const val CTRL_POWER_OFF = 0x79
    /** Riding mode, 0 = Dynamic, 1 = Eco, 2 = Sport, checked on the F2 Pro. */
    const val CTRL_MODE = 0x75
    const val CTRL_KERS = 0x7B
    const val CTRL_CRUISE = 0x7C
    const val CTRL_TAILLIGHT = 0x7D

    /** Registers of the BLE module ([ID_BLE]). */
    const val BLE_CODE_PROTECTION = 0x1C
    const val BLE_CODE = 0x1D
    const val BLE_UNLOCK = 0x1E
    const val BLE_LOCK_STATE = 0x4D

    /** Registers of the battery ([ID_BATT]). */
    const val BATT_CHARGE_LIMIT = 0x3F

    /** Length of the start of the frame (`5A A5 <len>`). */
    const val HEADER_SIZE = 3

    /** Number of bytes added by [NinebotCrypto] to the frame. */
    const val TRAILER_SIZE = 6

    private val MAGIC = byteArrayOf(0x5A, 0xA5.toByte())

    /** A decoded frame. */
    class Message(val src: Int, val dst: Int, val cmd: Int, val index: Int, val data: ByteArray) {
        /** Little endian value of the first [size] data bytes. */
        fun uint(size: Int = data.size): Int {
            var result = 0
            for (i in 0 until minOf(size, data.size, 4)) {
                result = result or ((data[i].toInt() and 0xff) shl (8 * i))
            }
            return result
        }

        override fun toString() =
            "Message(src=0x%02x, dst=0x%02x, cmd=0x%02x, index=0x%02x, data=%s)".format(
                src, dst, cmd, index, data.joinToString("") { "%02x".format(it) }
            )
    }

    /** Builds a clear frame from the phone to [dst]. */
    fun build(dst: Int, cmd: Int, index: Int, data: ByteArray = ByteArray(0)): ByteArray {
        return MAGIC + byteArrayOf(data.size.toByte(), ID_PHONE.toByte(), dst.toByte(), cmd.toByte(), index.toByte()) + data
    }

    fun read(dst: Int, register: Int, size: Int): ByteArray =
        build(dst, CMD_READ, register, byteArrayOf(size.toByte()))

    /** The original app writes the battery with an answer and everything else without one. */
    fun write(dst: Int, register: Int, value: Int, size: Int = 2, reply: Boolean = dst == ID_BATT): ByteArray =
        build(
            dst, if (reply) CMD_WRITE else CMD_WRITE_NOREPLY, register,
            ByteArray(size) { (value shr (8 * it)).toByte() }
        )

    /** Parses a clear frame, null if it is not one. */
    fun parse(frame: ByteArray): Message? {
        if (frame.size < HEADER_SIZE + 4 || frame[0] != MAGIC[0] || frame[1] != MAGIC[1]) return null
        val len = frame[2].toInt() and 0xff
        if (frame.size < HEADER_SIZE + 4 + len) return null
        return Message(
            src = frame[3].toInt() and 0xff,
            dst = frame[4].toInt() and 0xff,
            cmd = frame[5].toInt() and 0xff,
            index = frame[6].toInt() and 0xff,
            data = frame.copyOfRange(7, 7 + len),
        )
    }

    /**
     * Collects the notification chunks (20 bytes at a time) to complete encrypted frames. The length of a
     * frame is taken from its header.
     */
    class Reassembler {
        private val buffer = java.io.ByteArrayOutputStream()

        /** Returns the complete frame once [chunk] finishes one, otherwise null. */
        fun feed(chunk: ByteArray): ByteArray? {
            if (chunk.size >= 2 && chunk[0] == MAGIC[0] && chunk[1] == MAGIC[1]) {
                buffer.reset()
            }
            if (buffer.size() == 0 && (chunk.size < 2 || chunk[0] != MAGIC[0] || chunk[1] != MAGIC[1])) {
                return null
            }
            buffer.write(chunk)
            val bytes = buffer.toByteArray()
            if (bytes.size < HEADER_SIZE) return null
            val total = (bytes[2].toInt() and 0xff) + HEADER_SIZE + 4 + TRAILER_SIZE
            if (bytes.size < total) return null
            buffer.reset()
            return bytes.copyOf(total)
        }

        fun reset() = buffer.reset()
    }
}

/** Behavior of the kinetic energy recovery system (`CTRL 0x7B`). */
enum class NinebotKers(val code: Int, override val label: Int) : LabeledEntry {
    WEAK(0, R.string.ninebot_kers_weak),
    MEDIUM(1, R.string.ninebot_kers_medium),
    STRONG(2, R.string.ninebot_kers_strong),
    ;

    companion object {
        fun fromCode(code: Int): NinebotKers? = entries.find { it.code == code }
    }
}

/** Riding mode of the scooter (`CTRL 0x75`). */
enum class NinebotRideMode(val code: Int, override val label: Int, val shortName: String) : LabeledEntry {
    DYNAMIC(0, R.string.ninebot_mode_dynamic, "D"),
    ECO(1, R.string.ninebot_mode_eco, "Eco"),
    SPORT(2, R.string.ninebot_mode_sport, "S"),
    ;

    companion object {
        fun fromCode(code: Int): NinebotRideMode? = entries.find { it.code == code }
    }
}

/** Charge limit of the battery (`BATT 0x3F`). */
enum class NinebotChargeLimit(val percent: Int, override val label: Int) : LabeledEntry {
    LIMIT_95(95, R.string.ninebot_charge_limit_95),
    LIMIT_100(100, R.string.ninebot_charge_limit_100),
    ;

    companion object {
        fun fromPercent(percent: Int): NinebotChargeLimit? = entries.find { it.percent == percent }
    }
}
