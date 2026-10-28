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

import org.junit.Assert.assertEquals
import org.junit.Test

/** Records from a real `d1 aa 04` log. */
class Stratos4ProBatteryLogTest {
    private val now = 0x6b000000L

    @Test
    fun levelsAndVoltage() {
        val log = Stratos4ProBatteryLog.parse(
            h(
                "00000032" +
                    "fd6ab09d061004010050" + // module 16 key 01: 80 %
                    "fd6ab0a1660504020050" + // module 5 key 02: 80 %
                    "fd6ab0a1660504041014" + // module 5 key 04: 4116 mV
                    "fd6ab0d66a0e040e0001" + // module 14, ignored
                    "fd6ab0a1661304030000" // module 19, ignored
            ),
            now
        )
        assertEquals(5, log.records)
        assertEquals(mapOf(0x6ab09d06L to 80, 0x6ab0a166L to 80), log.levels)
        assertEquals(mapOf(0x6ab0a166L to 4116), log.millivolts)
    }

    @Test
    fun moduleSixteenWinsOnSameSecond() {
        val log = Stratos4ProBatteryLog.parse(h("00000014" + "fd6ab0a1660504020051" + "fd6ab0a1661004010050"), now)
        assertEquals(mapOf(0x6ab0a166L to 80), log.levels)
    }

    @Test
    fun skipsJunk() {
        val log = Stratos4ProBatteryLog.parse(
            h(
                "00000000" +
                    "00" + // stray byte, resync on the next fd
                    "fd000000001004010050" + // timestamp 0
                    "fd6ab0a16610040100c8" + // level 200
                    "fd6ab0a1660504040064" + // 100 mV
                    "fd6ab0a1661004010037" + // 55 %
                    "fd6ab0a166" // truncated
            ),
            now
        )
        assertEquals(3, log.records)
        assertEquals(mapOf(0x6ab0a166L to 55), log.levels)
        assertEquals(emptyMap<Long, Int>(), log.millivolts)
    }
}
