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

import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro.Stratos4ProLights.Command
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Frames must match, byte for byte, what the watch accepted in the PC test (2026-09-26): a wrong
 * length field runs a cut or garbled command.
 */
class Stratos4ProLightsTest {
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    @Test
    fun whiteOn() {
        assertEquals(
            "1d10811900011600646267206d7777203430303638333063203130303430",
            hex(Stratos4ProLights.frame(Command.WHITE_ON))
        )
    }

    @Test
    fun whiteOff() {
        assertEquals(
            "1d10811500011200646267206d77772034303036383330632030",
            hex(Stratos4ProLights.frame(Command.WHITE_OFF))
        )
    }

    @Test
    fun redOn() {
        assertEquals(
            "1d10811b00011800646267206d77772034303036383231342034303030303030",
            hex(Stratos4ProLights.frame(Command.RED_ON))
        )
    }

    @Test
    fun redOff() {
        assertEquals(
            "1d10811b00011800646267206d77772034303036383232342034303030303030",
            hex(Stratos4ProLights.frame(Command.RED_OFF))
        )
    }

    @Test
    fun prepare() {
        assertEquals("1d108105000002000100", hex(Stratos4ProLights.PREPARE_FRAME))
    }

    @Test
    fun strobeParsing() {
        assertEquals(0, Stratos4ProLights.parseStrobeMs(null))
        assertEquals(0, Stratos4ProLights.parseStrobeMs(""))
        assertEquals(0, Stratos4ProLights.parseStrobeMs("0"))
        assertEquals(0, Stratos4ProLights.parseStrobeMs("abc"))
        assertEquals(100, Stratos4ProLights.parseStrobeMs("50"))
        assertEquals(500, Stratos4ProLights.parseStrobeMs(" 500 "))
        assertEquals(10000, Stratos4ProLights.parseStrobeMs("99999"))
    }

    @Test
    fun keepaliveParsing() {
        assertEquals(3, Stratos4ProLights.parseKeepaliveS(null))
        assertEquals(0, Stratos4ProLights.parseKeepaliveS(""))
        assertEquals(0, Stratos4ProLights.parseKeepaliveS("0"))
        assertEquals(0, Stratos4ProLights.parseKeepaliveS("abc"))
        assertEquals(0, Stratos4ProLights.parseKeepaliveS("-1"))
        assertEquals(5, Stratos4ProLights.parseKeepaliveS("5"))
        assertEquals(30, Stratos4ProLights.parseKeepaliveS(" 30 "))
        assertEquals(60, Stratos4ProLights.parseKeepaliveS("99"))
    }
}
