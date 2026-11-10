/*  Copyright (C) 2026 Pedro

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.soundpeats

/**
 * Frame format used by Soundpeats earbuds over the RFCOMM control channel, as observed on the wire.
 *
 * <pre>
 * offset  0    1    2    3      4..5      6..7      8..
 *        0xFF 0x04 0x00 len   vendorId  command   payload
 * </pre>
 *
 * The frame carries no checksum. Commands are big-endian. Responses from the device echo the
 * command with the high bit set (0x0310 -> 0x8310), see [SoundpeatsCommands.responseFor].
 */
object SoundpeatsPacket {
    const val SOF = 0xFF
    const val VERSION = 0x04
    const val FLAGS = 0x00
    const val HEADER_LENGTH = 8

    /** Vendor id used by regular commands. */
    const val VENDOR_DEFAULT = 0x000A

    /** Vendor id used by the equalizer commands (see [SoundpeatsCommands.MODIFY_EQ_V1]). */
    const val VENDOR_EQ = 0x001D

    data class Decoded(val command: Int, val payload: ByteArray, val vendorId: Int) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Decoded) return false
            return command == other.command && vendorId == other.vendorId && payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int =
            (command * 31 + vendorId) * 31 + payload.contentHashCode()
    }

    @JvmStatic
    fun vendorFor(command: Int): Int = when (command) {
        SoundpeatsCommands.MODIFY_EQ_V1, SoundpeatsCommands.MODIFY_EQ_V2 -> VENDOR_EQ
        else -> VENDOR_DEFAULT
    }

    @JvmStatic
    @JvmOverloads
    fun encode(command: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        require(command in 0..0xFFFF) { "command out of range: $command" }
        require(payload.size <= 0xFF) { "payload too long: ${payload.size}" }

        val frame = ByteArray(HEADER_LENGTH + payload.size)
        val vendor = vendorFor(command)

        frame[0] = SOF.toByte()
        frame[1] = VERSION.toByte()
        frame[2] = FLAGS.toByte()
        frame[3] = payload.size.toByte()
        frame[4] = ((vendor shr 8) and 0xFF).toByte()
        frame[5] = (vendor and 0xFF).toByte()
        frame[6] = ((command shr 8) and 0xFF).toByte()
        frame[7] = (command and 0xFF).toByte()
        payload.copyInto(frame, HEADER_LENGTH)

        return frame
    }

    /**
     * Accumulates incoming bytes and splits them into complete frames.
     *
     * The read callback of an RFCOMM socket can deliver arbitrary fragments, so partial frames must
     * be retained until the announced payload length is available.
     */
    class Parser {
        private var buffer = ByteArray(0)

        fun feed(bytes: ByteArray): List<Decoded> {
            buffer += bytes

            val frames = mutableListOf<Decoded>()
            while (true) {
                // Resynchronize on the start byte
                val start = buffer.indexOfFirst { it == SOF.toByte() }
                if (start < 0) {
                    buffer = ByteArray(0)
                    break
                }
                if (start > 0) {
                    buffer = buffer.copyOfRange(start, buffer.size)
                }
                if (buffer.size < HEADER_LENGTH) {
                    break
                }

                val payloadLength = buffer[3].toInt() and 0xFF
                val total = HEADER_LENGTH + payloadLength
                if (buffer.size < total) {
                    break
                }

                val vendorId = ((buffer[4].toInt() and 0xFF) shl 8) or (buffer[5].toInt() and 0xFF)
                val command = ((buffer[6].toInt() and 0xFF) shl 8) or (buffer[7].toInt() and 0xFF)
                val payload = buffer.copyOfRange(HEADER_LENGTH, total)

                frames.add(Decoded(command, payload, vendorId))
                buffer = buffer.copyOfRange(total, buffer.size)
            }

            return frames
        }
    }
}
