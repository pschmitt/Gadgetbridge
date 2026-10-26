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

import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.magic_touch.MagicTouchMiotSpec
import nodomain.freeyourgadget.gadgetbridge.service.btle.BLETypeConversions

/**
 * MiOT payload layer carried inside the encrypted CMD / REPORT channels of
 * `xiaomi.magic_touch.jj02` (behavior matches Mi Home HCI snoops).
 *
 * Frames are `LL 20 <msgId LE> <properties>`; [buildCommand] wraps a property
 * payload in that header, and [parseShort] extracts the (siid, piid, value)
 * from short single-property frames on the report side. The support class
 * encrypts these with AES-CCM before they hit the wire, so this layer is
 * pure: no device state, only byte construction and parsing.
 */
object MagicTouchMiotCodec {
    /** MiOT property-access codes: set-properties = 00, get = 02. */
    const val OP_SET = 0x00
    const val OP_GET = 0x02

    /** Numeric property wire type (uint8); boolean = 00. */
    const val TYPE_UINT8 = 0x10

    /** Boolean wire type (heating, default beep); uint8 elsewhere. */
    const val TYPE_BOOL = 0x00

    /** siid-4 piids read by the vendor bulk get, fixed order. */
    private val BULK_GET_PIIDS = listOf(
        MagicTouchMiotSpec.PID_ON,
        MagicTouchMiotSpec.PID_MODE,
        MagicTouchMiotSpec.PID_DEFAULT_MODE,
        MagicTouchMiotSpec.PID_TARGET_TIME,
        MagicTouchMiotSpec.PID_COUNTDOWN,
        MagicTouchMiotSpec.PID_HEATING,
        MagicTouchMiotSpec.PID_HEAT_LEVEL,
        MagicTouchMiotSpec.PID_DEFAULT_BEEP_MODE,
        MagicTouchMiotSpec.PID_CUSTOM_NAME,
        MagicTouchMiotSpec.PID_MASSAGE_HEAD_INIT,
    )

    /**
     * Capability frame the vendor app sends first after login: `LL 20`
     * header with a **fixed** msg id 0x0002 (separate from the running
     * counter) and the 0xf0 capability/version marker as payload.
     */
    fun buildCapability(): ByteArray = buildCommand(2, byteArrayOf(0xf0.toByte()))

    /**
     * Wraps a miot payload in the common `LL 20 <msgId LE>` header. `LL` is
     * the total frame length (matches the vendor app on every command sent).
     */
    fun buildCommand(msgId: Int, payload: ByteArray): ByteArray {
        val frame = ByteArray(4 + payload.size)
        frame[0] = frame.size.toByte()
        frame[1] = 0x20.toByte()
        frame[2] = (msgId and 0xff).toByte()
        frame[3] = ((msgId shr 8) and 0xff).toByte()
        payload.copyInto(frame, 4)
        return frame
    }

    /**
     * Set-property template: `0c 20 <msgId LE> 00 01 <siid> <piid-lo>
     * <piid-hi=00> 01 <type> <value>` -- 12 bytes, value in the last byte.
     */
    fun buildSet(msgId: Int, piid: Int, typeByte: Int, value: Int): ByteArray = buildCommand(
        msgId,
        byteArrayOf(
            OP_SET.toByte(), 0x01.toByte(),
            MagicTouchMiotSpec.SIID_MASSAGER_CONTROL.toByte(),
            piid.toByte(), 0x00.toByte(),
            0x01.toByte(),
            typeByte.toByte(), value.toByte(),
        ),
    )

    /**
     * Bulk get-properties with the exact vendor list (fixed order): battery
     * level (2-byte LE piid) first, then the siid-4 piids with 1-byte ids.
     */
    fun buildBulkGet(msgId: Int): ByteArray {
        var payload = byteArrayOf(
            OP_GET.toByte(),
            (BULK_GET_PIIDS.size + 1).toByte(),
            MagicTouchMiotSpec.SIID_BATTERY.toByte(),
        ) + BLETypeConversions.fromUint16(MagicTouchMiotSpec.PID_BATTERY_LEVEL)
        for (piid in BULK_GET_PIIDS) {
            payload += byteArrayOf(
                MagicTouchMiotSpec.SIID_MASSAGER_CONTROL.toByte(),
                piid.toByte(),
                0x00.toByte(),
            )
        }
        return buildCommand(msgId, payload)
    }

    /**
     * Standalone battery-level get (siid 5/piid 1003, count 1). Unlike the
     * same piid embedded in the bulk get (which the device ignores), this is
     * answered immediately with a 14 B `…05 eb 03…<raw>` report.
     */
    fun buildBatteryLevelGet(msgId: Int): ByteArray = buildCommand(
        msgId,
        // get-properties, count=1: siid 5 + 2-byte LE piid 1003
        byteArrayOf(
            OP_GET.toByte(), 0x01.toByte(),
            MagicTouchMiotSpec.SIID_BATTERY.toByte(),
        ) + BLETypeConversions.fromUint16(MagicTouchMiotSpec.PID_BATTERY_LEVEL),
    )

    /** Short single-property frame: 12B/11B `LL 20 MM MM .. siid piid .. val`.
     * 12B layout matches the SET frames sent: siid=[6], piid=[7], val=[11];
     * 11B acks: siid=[6], piid=[7], val=[10]. Battery level uses a 2-byte LE
     * piid on [MagicTouchMiotSpec.SIID_BATTERY] (1003). Null if not a short frame. */
    fun parseShort(pt: ByteArray): Triple<Int, Int, Int>? {
        if (pt.size < 4 || pt[1] != 0x20.toByte()) return null
        if ((pt.size == 12 || pt.size == 14)
            && (pt[6].toInt() and 0xff) == MagicTouchMiotSpec.SIID_BATTERY
            && BLETypeConversions.toUint16(pt, 7) == MagicTouchMiotSpec.PID_BATTERY_LEVEL
        ) {
            return Triple(
                MagicTouchMiotSpec.SIID_BATTERY,
                MagicTouchMiotSpec.PID_BATTERY_LEVEL,
                pt[if (pt.size == 12) 11 else 13].toInt() and 0xff,
            )
        }
        return when (pt.size) {
            12 -> Triple(pt[6].toInt() and 0xff, pt[7].toInt() and 0xff, pt[11].toInt() and 0xff)
            11 -> Triple(pt[6].toInt() and 0xff, pt[7].toInt() and 0xff, pt[10].toInt() and 0xff)
            else -> null
        }
    }
}
