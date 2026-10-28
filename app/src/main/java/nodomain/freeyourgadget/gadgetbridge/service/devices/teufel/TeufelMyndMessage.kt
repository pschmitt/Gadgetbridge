package nodomain.freeyourgadget.gadgetbridge.service.devices.teufel

import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * RFComm Packet Format:
 * | Start (0xFF) | Version (0x01) | Flags | Length | Vendor ID (2B) | Command ID (2B) | Payload... | Checksum |
 *
 * - Flags bit 0: checksum present
 * - Length: payload length in bytes
 * - Checksum: XOR of all other bytes in the packet
 */
data class TeufelMyndMessage(
    val commandId: Short,
    val payload: ByteArray
) {
    constructor(command: TeufelMyndCommand, payload: ByteArray = byteArrayOf()) :
            this(command.commandId, payload)

    val isAck: Boolean get() = TeufelMyndCommand.isAck(commandId)

    val originalCommand: TeufelMyndCommand?
        get() = if (isAck) {
            TeufelMyndCommand.fromAckCommandId(commandId)
        } else {
            TeufelMyndCommand.fromCommandId(commandId)
        }

    fun encode(): ByteArray {
        val packetSize = HEADER_SIZE + payload.size + 1 // +1 for checksum byte
        val buf = ByteBuffer.allocate(packetSize).order(ByteOrder.BIG_ENDIAN)

        buf.put(PACKET_START)
        buf.put(PROTOCOL_VERSION)
        buf.put(FLAG_CHECKSUM)
        buf.put(payload.size.toByte())
        buf.putShort(VENDOR_ID)
        buf.putShort(commandId)
        buf.put(payload)

        // Checksum: XOR of all bytes except the checksum byte itself
        val bytes = buf.array()
        var check: Byte = 0
        for (i in 0 until bytes.size - 1) {
            check = (check.toInt() xor bytes[i].toInt()).toByte()
        }
        buf.put(check)

        return buf.array()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TeufelMyndMessage) return false

        if (commandId != other.commandId) return false
        if (!payload.contentEquals(other.payload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = commandId.toInt()
        result = 31 * result + payload.contentHashCode()
        return result
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(TeufelMyndMessage::class.java)

        const val PACKET_START: Byte = 0xFF.toByte()
        const val PROTOCOL_VERSION: Byte = 0x01
        const val FLAG_CHECKSUM: Byte = 0x01
        const val VENDOR_ID: Short = 0x2CC2.toShort()
        const val STATUS_SUCCESS: Byte = 0x00

        // start(1) + version(1) + flags(1) + length(1) + vendorId(2) + commandId(2), the checksum is optional
        const val HEADER_SIZE = 8

        /**
         * Try to parse a complete packet from the buffer.
         * Returns null and keeps the buffer position when the buffer does not yet contain a complete packet.
         * Returns null and moves the buffer position forward when the processed data was not a valid packet.
         */
        fun tryParse(buf: ByteBuffer): TeufelMyndMessage? {
            if (buf.remaining() < HEADER_SIZE) return null

            buf.mark()

            val start = buf.get()
            if (start != PACKET_START) {
                // Not a valid start, don't reset - caller will skip this byte
                return null
            }

            val version = buf.get()
            val flags = buf.get()
            val hasChecksum = (flags.toInt() and 0x01) != 0
            val length = buf.get().toInt() and 0xFF

            // remaining: vendor id + command id + payload + checksum
            if (buf.remaining() < 4 + length + (if (hasChecksum) 1 else 0)) {
                buf.reset()
                return null
            }

            val vendorId = buf.getShort()
            val commandId = buf.getShort()
            val payload = ByteArray(length)
            buf.get(payload)

            if (hasChecksum) {
                val receivedCheck = buf.get()
                // Verify: XOR all bytes except checksum
                val packetBytes = ByteArray(HEADER_SIZE + length)
                val tempBuf = ByteBuffer.wrap(packetBytes).order(ByteOrder.BIG_ENDIAN)
                tempBuf.put(start)
                tempBuf.put(version)
                tempBuf.put(flags)
                tempBuf.put(length.toByte())
                tempBuf.putShort(vendorId)
                tempBuf.putShort(commandId)
                tempBuf.put(payload)

                var expectedCheck: Byte = 0
                for (b in packetBytes) {
                    expectedCheck = (expectedCheck.toInt() xor b.toInt()).toByte()
                }

                if (receivedCheck != expectedCheck) {
                    LOG.warn(
                        "Got invalid checksum {}, expected {}",
                        String.format("0x%02x", receivedCheck),
                        String.format("0x%02x", expectedCheck),
                    )
                    return null
                }
            }

            if (vendorId != VENDOR_ID) {
                LOG.warn("Got unexpected vendor ID {}", String.format("0x%04x", vendorId))
            }

            return TeufelMyndMessage(
                commandId = commandId,
                payload = payload
            )
        }
    }
}
