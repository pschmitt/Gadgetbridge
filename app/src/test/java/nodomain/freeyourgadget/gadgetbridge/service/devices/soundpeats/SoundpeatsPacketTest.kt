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

import nodomain.freeyourgadget.gadgetbridge.devices.soundpeats.SoundpeatsAncMode
import nodomain.freeyourgadget.gadgetbridge.devices.soundpeats.SoundpeatsCustomEqStore
import nodomain.freeyourgadget.gadgetbridge.devices.soundpeats.SoundpeatsEqImport
import nodomain.freeyourgadget.gadgetbridge.devices.soundpeats.SoundpeatsEqPresets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundpeatsPacketTest {
    @Test
    fun testEncodeHeader() {
        val payload = byteArrayOf(0x01, 0x02, 0x03)
        val encoded = SoundpeatsPacket.encode(SoundpeatsCommands.SET_NOISE_CANCELLATION_MODE, payload)

        assertEquals(SoundpeatsPacket.HEADER_LENGTH + payload.size, encoded.size)
        assertEquals(0xFF.toByte(), encoded[0])
        assertEquals(0x04.toByte(), encoded[1])
        assertEquals(0x00.toByte(), encoded[2])
        assertEquals(payload.size.toByte(), encoded[3])
        // vendor 0x000A, big endian
        assertEquals(0x00.toByte(), encoded[4])
        assertEquals(0x0A.toByte(), encoded[5])
        // command 0x0311, big endian
        assertEquals(0x03.toByte(), encoded[6])
        assertEquals(0x11.toByte(), encoded[7])
        assertArrayEquals(payload, encoded.copyOfRange(8, encoded.size))
    }

    @Test
    fun testEncodeEmptyPayload() {
        val encoded = SoundpeatsPacket.encode(SoundpeatsCommands.GET_LEFT_BATTERY)

        assertEquals(SoundpeatsPacket.HEADER_LENGTH, encoded.size)
        assertEquals(0x00.toByte(), encoded[3])
        assertEquals(0x03.toByte(), encoded[6])
        assertEquals(0x06.toByte(), encoded[7])
    }

    @Test
    fun testEqualizerUsesAlternateVendor() {
        val encoded = SoundpeatsPacket.encode(SoundpeatsCommands.MODIFY_EQ_V2, byteArrayOf(0x01))

        assertEquals(0x00.toByte(), encoded[4])
        assertEquals(0x1D.toByte(), encoded[5])
        assertEquals(0x0E.toByte(), encoded[6])
        assertEquals(0x02.toByte(), encoded[7])
    }

    @Test
    fun testParseSingleFrame() {
        val parser = SoundpeatsPacket.Parser()
        val frame = SoundpeatsPacket.encode(
            SoundpeatsCommands.responseFor(SoundpeatsCommands.GET_LEFT_BATTERY),
            byteArrayOf(0x00, 0x64)
        )

        val decoded = parser.feed(frame)

        assertEquals(1, decoded.size)
        assertEquals(0x8306, decoded[0].command)
        assertEquals(SoundpeatsPacket.VENDOR_DEFAULT, decoded[0].vendorId)
        assertArrayEquals(byteArrayOf(0x00, 0x64), decoded[0].payload)
    }

    @Test
    fun testParseFragmentedFrame() {
        val parser = SoundpeatsPacket.Parser()
        val frame = SoundpeatsPacket.encode(
            SoundpeatsCommands.responseFor(SoundpeatsCommands.GET_FIRMWARE_VERSION),
            "V0.0.22".toByteArray()
        )

        assertTrue(parser.feed(frame.copyOfRange(0, 3)).isEmpty())
        assertTrue(parser.feed(frame.copyOfRange(3, 6)).isEmpty())

        val decoded = parser.feed(frame.copyOfRange(6, frame.size))

        assertEquals(1, decoded.size)
        assertEquals(0x8309, decoded[0].command)
        assertEquals("V0.0.22", String(decoded[0].payload))
    }

    @Test
    fun testParseMultipleFrames() {
        val parser = SoundpeatsPacket.Parser()
        val first = SoundpeatsPacket.encode(
            SoundpeatsCommands.responseFor(SoundpeatsCommands.GET_LEFT_BATTERY),
            byteArrayOf(0x00, 0x64)
        )
        val second = SoundpeatsPacket.encode(
            SoundpeatsCommands.responseFor(SoundpeatsCommands.GET_RIGHT_BATTERY),
            byteArrayOf(0x00, 0x32)
        )

        val decoded = parser.feed(first + second)

        assertEquals(2, decoded.size)
        assertEquals(100, decoded[0].payload[1].toInt())
        assertEquals(50, decoded[1].payload[1].toInt())
    }

    @Test
    fun testResynchronizesAfterGarbage() {
        val parser = SoundpeatsPacket.Parser()
        val frame = SoundpeatsPacket.encode(SoundpeatsCommands.responseFor(SoundpeatsCommands.GET_CASE_BATTERY), byteArrayOf(0x00, 0x50))

        val decoded = parser.feed(byteArrayOf(0x12, 0x34, 0x56) + frame)

        assertEquals(1, decoded.size)
        assertEquals(0x8323, decoded[0].command)
        assertEquals(80, decoded[0].payload[1].toInt())
    }
}
