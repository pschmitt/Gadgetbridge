/*  Copyright (C) 2026 Francesco Colasuonno

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
    along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.magic_touch

import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder
import java.util.UUID

/**
 * Transport frames of the Xiaomi BLE StandardAuth / encrypted operation
 * channel for `xiaomi.magic_touch.jj02`, all raw bytes in one place
 * (behavior matches Mi Home HCI snoops; see the phone <-> device
 * flow diagram in [MagicTouchJj02Support]).
 *
 * Device->phone frames are `00 00 <opcode> [<sub-opcode> <payload...>]`.
 * Fixed-length frames must match exactly; variable-length frames carry a
 * payload appended after their frame prefix. [decode] matches the longest
 * known device frame and returns the packet together with its payload, so
 * no raw byte comparisons are scattered across the support code.
 */
enum class MagicTouchPacket(
    private val fromDevice: Boolean,
    val frame: ByteArray,
    private val exact: Boolean,
) {
    // ------------------------------------------------------------------
    // Device -> phone
    // ------------------------------------------------------------------

    /** Key-exchange pre-key push: `00 00 04 <index> <key part>`. We echo
     * each push with [PREKEY_ECHO]; the second PDU (index 0x01) completes
     * the exchange. */
    PREKEY_PUSH(true, byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x04.toByte()), false),

    /** Device random push, 16-byte payload (login step). */
    DEVICE_RANDOM(true, byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x02.toByte(), 0x0d.toByte()), false),

    /** Device confirm push, 32-byte payload (login step). */
    DEVICE_CONFIRM(true, byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x02.toByte(), 0x0c.toByte()), false),

    /** `00 00 01 01` -- device ready to receive a frame. */
    RCV_RDY(true, byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x01.toByte()), true),

    /** `00 00 01 00` -- device confirmed receiving a frame. */
    RCV_OK(true, byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte()), true),

    /** Decrypted MiOT report: `00 00 02 00 <u16LE counter> <CT || tag>`. */
    REPORT(true, byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x02.toByte(), 0x00.toByte()), false),

    /** Device keepalive; answer with [PONG] or the device stops talking. */
    PING(true, byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x05.toByte(), 0x01.toByte(), 0x00.toByte()), true),

    /** Login success on AUTH_CTRL: `21 00 00 00`. */
    LOGIN_OK(true, byteArrayOf(0x21.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()), true),

    /** Login failure on AUTH_CTRL: `22 00 00 00`. */
    LOGIN_FAIL(true, byteArrayOf(0x22.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()), true),

    // ------------------------------------------------------------------
    // Phone -> device (encoded via [frame], never decoded)
    // ------------------------------------------------------------------

    /** Reply to a device pre-key push (same payload, swapped opcode). */
    PREKEY_ECHO(false, byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x05.toByte()), false),

    /** Start the StandardAuth login (single byte, written to AUTH_CTRL). */
    AUTH_START(false, byteArrayOf(0xa4.toByte()), true),

    /** Fixed LOGIN_START frame from a HCI snoop: `24 00 00 00`. */
    LOGIN_START(false, byteArrayOf(0x24.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte()), true),

    /** 6-byte SEND frames; 7-byte variants are silently ignored. */
    SEND_KEY(false, byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0b.toByte(), 0x01.toByte(), 0x00.toByte()), true),
    SEND_INFO(false, byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0a.toByte(), 0x01.toByte(), 0x00.toByte()), true),

    /** Open the encrypted command channel: `00 00 00 00 01 00`. */
    OPEN_CMD(false, byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte()), true),

    /** Data-frame prefix before the payload (`01 00`). */
    PREFIX_DATA(false, byteArrayOf(0x01.toByte(), 0x00.toByte()), false),

    /** Generic acknowledgement: `00 00 03 00`. */
    ACK(false, byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x03.toByte(), 0x00.toByte()), true),

    /** Keepalive answer: `00 00 05 04 01 00`. */
    PONG(false, byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x05.toByte(), 0x04.toByte(), 0x01.toByte(), 0x00.toByte()), true);

    /** Payload bytes of [value] following this packet's frame prefix. */
    fun payloadOf(value: ByteArray): ByteArray = value.copyOfRange(frame.size, value.size)

    companion object {
        /**
         * Longest prefix first, so e.g. the 6-byte [PING] is matched before
         * shorter `00 00 ...` prefixes. Only device frames are decodable,
         * like SoundcorePacket's direction check.
         */
        private val DECODE_ORDER: List<MagicTouchPacket> = entries
            .filter { it.fromDevice }
            .sortedByDescending { it.frame.size }

        /**
         * Decode a raw notification into the matching packet and its payload
         * (the bytes after the frame prefix), or null when it matches no
         * known frame. Payload-carrying [exact] frames are rejected as
         * unknown, matching the device behavior.
         */
        fun decode(value: ByteArray): Pair<MagicTouchPacket, ByteArray>? {
            for (pkt in DECODE_ORDER) {
                if (startsWith(value, pkt.frame)) {
                    val payload = pkt.payloadOf(value)
                    if (pkt.exact && payload.isNotEmpty()) {
                        return null
                    }
                    return pkt to payload
                }
            }
            return null
        }

        private fun startsWith(value: ByteArray, prefix: ByteArray): Boolean {
            if (value.size < prefix.size) return false
            for (i in prefix.indices) {
                if (value[i] != prefix[i]) return false
            }
            return true
        }
    }
}

/**
 * Write a packet frame, optionally followed by payload bytes, to the given
 * characteristic. Frames whose payload/characteristic vary per context
 * (e.g. ACK, PREFIX_DATA) pass them from the call site, since a single
 * frame can go out on more than one characteristic.
 */
fun TransactionBuilder.write(
    characteristic: UUID,
    packet: MagicTouchPacket,
    payload: ByteArray = ByteArray(0),
): TransactionBuilder = write(characteristic, *(packet.frame + payload))
