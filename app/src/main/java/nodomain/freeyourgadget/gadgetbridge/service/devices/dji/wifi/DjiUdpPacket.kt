package nodomain.freeyourgadget.gadgetbridge.service.devices.dji.wifi

import nodomain.freeyourgadget.gadgetbridge.service.devices.dji.DecodeResult
import nodomain.freeyourgadget.gadgetbridge.service.devices.dji.duml.DumlCodec
import nodomain.freeyourgadget.gadgetbridge.service.devices.dji.duml.DumlPacket
import nodomain.freeyourgadget.gadgetbridge.util.GB

/**
 * A UDP datagram of the DJI Control Channel: the 8-byte envelope header and the body.
 *
 * Header layout:
 *   0-1  length (bits 0-14, LE) | flag (bit 15, always 1)
 *   2-3  session id (LE)
 *   4-5  sequence number (LE)
 *   6    packet type
 *   7    checksum, XOR of bytes 0-6
 */
class DjiUdpPacket private constructor(
    val type: Int,
    val sessionId: Int,
    val seq: Int,
    private val data: ByteArray,
) {
    /**
     * True for the reply of the camera that accepts a handshake.
     */
    val handshakeAccepted: Boolean
        get() = type == TYPE_HANDSHAKE && data.size > HEADER_SIZE && data[HEADER_SIZE].toInt() == 0x01

    /**
     * The last Command sequence number that the camera acked, from a Telemetry packet.
     */
    val ackedCommandSeq: Int?
        get() = if (type == TYPE_TELEMETRY && data.size >= TELEMETRY_HEADER_SIZE) u16(data, 26) else null

    /**
     * The DUML messages in the body. Decoding stops at the first message that is not valid.
     */
    fun dumlMessages(): List<DumlPacket> {
        val (from, to) = when (type) {
            TYPE_TELEMETRY -> {
                if (data.size < TELEMETRY_HEADER_SIZE) {
                    return emptyList()
                }
                TELEMETRY_HEADER_SIZE to minOf(data.size, TELEMETRY_HEADER_SIZE + u16(data, 32))
            }

            TYPE_RESPONSE, TYPE_COMMAND -> COMMAND_HEADER_SIZE to data.size
            else -> return emptyList()
        }
        if (from >= to) {
            return emptyList()
        }
        val bytes = data.copyOfRange(from, to)
        val messages = ArrayList<DumlPacket>()
        var offset = 0
        while (offset < bytes.size) {
            val result = DumlCodec.decodeOne(bytes, offset)
            if (result !is DecodeResult.Success) {
                break
            }
            messages.add(result.content)
            offset += result.bytesConsumed
        }
        return messages
    }

    override fun toString(): String {
        return "DjiUdpPacket(type=%d, session=0x%04x, seq=0x%04x, %s)".format(type, sessionId, seq, GB.hexdump(data))
    }

    companion object {
        const val TYPE_HANDSHAKE = 0
        const val TYPE_TELEMETRY = 1
        const val TYPE_VIDEO = 2
        const val TYPE_RESPONSE = 3
        const val TYPE_ACK = 4
        const val TYPE_COMMAND = 5

        private const val HEADER_SIZE = 8
        private const val HANDSHAKE_SIZE = 48
        private const val ACK_SIZE = 34
        private const val COMMAND_HEADER_SIZE = 20
        private const val TELEMETRY_HEADER_SIZE = 34
        private const val LENGTH_FLAG = 0x8000

        private val HANDSHAKE_BLOCKS = GB.hexStringToByteArray(
            "64006400c005140000640000019001c005140000640014006400c00514000064000101040102"
        )

        /**
         * Decodes a datagram. Returns null when it is too short or when the checksum does not match.
         */
        fun decode(datagram: ByteArray): DjiUdpPacket? {
            if (datagram.size < HEADER_SIZE || checksum(datagram) != (datagram[7].toInt() and 0xff)) {
                return null
            }
            return DjiUdpPacket(datagram[6].toInt() and 0xff, u16(datagram, 2), u16(datagram, 4), datagram)
        }

        fun handshake(sessionId: Int, isn: Int): ByteArray {
            val buf = ByteArray(HANDSHAKE_SIZE)
            putU16(buf, 8, isn)
            HANDSHAKE_BLOCKS.copyInto(buf, 10)
            putHeader(buf, sessionId, 0, TYPE_HANDSHAKE)
            return buf
        }

        /**
         * An Ack packet. The window fields acknowledge the Video and Response streams of the camera, and
         * report the Command stream of the app.
         */
        fun ack(sessionId: Int, videoSeq: Int, responseSeq: Int, ackedCommandSeq: Int, commandSeq: Int): ByteArray {
            val buf = ByteArray(ACK_SIZE)
            putU16(buf, 8, videoSeq)
            putU16(buf, 10, videoSeq)
            putU16(buf, 16, responseSeq)
            putU16(buf, 18, responseSeq)
            putU16(buf, 24, ackedCommandSeq)
            putU16(buf, 26, commandSeq)
            putHeader(buf, sessionId, 0, TYPE_ACK)
            return buf
        }

        /**
         * A Command packet that contains one encoded DUML message.
         */
        fun command(sessionId: Int, isn: Int, seq: Int, ackedCommandSeq: Int, duml: ByteArray): ByteArray {
            val buf = ByteArray(COMMAND_HEADER_SIZE + duml.size)
            putU16(buf, 8, ackedCommandSeq)
            putU16(buf, 10, seq)
            buf[16] = ((((seq - isn) and 0xffff) / 8) and 0xff).toByte()
            buf[17] = 0x01
            duml.copyInto(buf, COMMAND_HEADER_SIZE)
            putHeader(buf, sessionId, seq, TYPE_COMMAND)
            return buf
        }

        fun checksum(data: ByteArray): Int {
            var xor = 0
            for (i in 0 until 7) {
                xor = xor xor (data[i].toInt() and 0xff)
            }
            return xor
        }

        private fun putHeader(buf: ByteArray, sessionId: Int, seq: Int, type: Int) {
            putU16(buf, 0, buf.size or LENGTH_FLAG)
            putU16(buf, 2, sessionId)
            putU16(buf, 4, seq)
            buf[6] = type.toByte()
            buf[7] = checksum(buf).toByte()
        }

        private fun putU16(buf: ByteArray, offset: Int, value: Int) {
            buf[offset] = (value and 0xff).toByte()
            buf[offset + 1] = ((value shr 8) and 0xff).toByte()
        }

        private fun u16(buf: ByteArray, offset: Int): Int =
            (buf[offset].toInt() and 0xff) or ((buf[offset + 1].toInt() and 0xff) shl 8)
    }
}
