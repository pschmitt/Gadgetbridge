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

import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro.Stratos4ProHistoryParser.parseHistoryFrame
import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro.Stratos4ProHistoryParser.parseLiveHeartRates
import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro.Stratos4ProHistoryParser.parseLiveMinute
import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro.Stratos4ProHistoryParser.parseMotionValues
import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro.Stratos4ProHistoryParser.parseSleepFile
import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro.Stratos4ProHistoryParser.parseStressFile
import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro.Stratos4ProHistoryParser.parseStressReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Stratos4ProHistoryParserTest {
    private val t = 1_750_000_000L
    private val ts = be32Bytes(t.toInt()).toHex()

    @Test
    fun motionValuesOneByteMask() {
        // bits 1 steps, 2 kcal, 3 distance (2 bytes)
        val v = parseMotionValues(h("07 0a 05 0102"), 0)!!
        assertEquals(10, v.steps)
        assertEquals(5, v.calories)
        assertEquals(258, v.distance)
        assertEquals(5, v.length)
        // Activity sample units: metres -> cm, kcal -> cal.
        assertEquals(25_800, v.distanceCm)
        assertEquals(5_000, v.activeCalories)
    }

    @Test
    fun motionValuesTwoByteMaskAndHeartRate() {
        // byte 0: 0x80 more, 0x40 bit 7 dynamic HR, 0x08 bit 4 resting HR; byte 1: 0x08 bit 12 average
        val v = parseMotionValues(h("c8 08 3c 5a 46"), 0)!!
        assertEquals(60, v.restingHr)
        assertEquals(90, v.dynamicHr)
        assertEquals(70, v.hrAvg)
        assertEquals(90, v.heartRate)
        assertEquals(5, v.length)
        assertEquals(60, parseMotionValues(h("c8 08 3c 00 46"), 0)!!.heartRate)
        assertEquals(0, parseMotionValues(h("c8 08 3c 5a 00"), 0)!!.heartRate)
    }

    @Test
    fun motionValuesUnknownFieldIsTwoBytes() {
        // bit 11 (byte 1, 0x04): not decoded, still skipped
        assertEquals(4, parseMotionValues(h("80 04 1234"), 0)!!.length)
    }

    @Test
    fun motionValuesRejectsBadInput() {
        assertNull(parseMotionValues(h("80 80 80 80 00"), 0)) // mask > 4 bytes
        assertNull(parseMotionValues(h("04 01"), 0)) // distance cut short
        assertNull(parseMotionValues(h("01"), 1))
    }

    @Test
    fun historyFrame() {
        val f = parseHistoryFrame(h("0003 $ts 0000 010a 0002 0114 05a1 0101"))!!
        assertEquals(3, f.index)
        assertEquals(t, f.start)
        // The item at offset 1441 min is dropped.
        assertEquals(listOf(t to 10, t + 120 to 20), f.minutes.map { it.first to it.second.steps })
    }

    @Test
    fun liveMinuteIsCutToTheMinute() {
        val (time, v) = parseLiveMinute(h("05 ${be32Bytes((t + 17).toInt()).toHex()} 01 07"))!!
        assertEquals(t + 17 - (t + 17) % 60, time)
        assertEquals(7, v.steps)
    }

    @Test
    fun sleepFile() {
        val r = parseSleepFile(h("0000 $ts 001e 01  $ts 0000 02  $ts 0005 07  ${be32Bytes((t + 1800).toInt()).toHex()} 000a 03"))
        assertEquals(listOf(SleepRecord(t, 30, 1), SleepRecord(t + 1800, 10, 3)), r)
        assertEquals(0, parseSleepFile(h("0000")).size)
    }

    @Test
    fun stressFile() {
        // One day at 720 min: two slots, the second empty.
        assertEquals(listOf(86400L to 50), parseStressFile(h("00015180 02d0 32 00")))
        assertEquals(0, parseStressFile(h("00015180 0000 32")).size)
    }

    @Test
    fun stressReading() {
        assertEquals(t to 50, parseStressReading(h("01c6ac0205 $ts 32")))
        assertNull(parseStressReading(h("01c6ac0205 $ts 00")))
        assertNull(parseStressReading(h("01c6ac0204 $ts")))
    }

    @Test
    fun liveHeartRatesDropImplausible() {
        assertEquals(listOf(70 to t), parseLiveHeartRates(h("01e1ac030a 46 $ts 0a $ts")))
    }
}
