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

import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries
import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro.Stratos4ProWorkoutParser.buildTrack
import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro.Stratos4ProWorkoutParser.deriveSpeed
import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro.Stratos4ProWorkoutParser.interpolate
import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro.Stratos4ProWorkoutParser.nearest
import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro.Stratos4ProWorkoutParser.parseDetailPage
import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro.Stratos4ProWorkoutParser.parseList
import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro.Stratos4ProWorkoutParser.parseSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TreeMap

class Stratos4ProWorkoutParserTest {
    private val t = 1_750_000_000L
    private fun b32(v: Long) = be32Bytes(v.toInt()).toHex()

    @Test
    fun list() {
        assertEquals(
            listOf(WorkoutListItem(5, 3), WorkoutListItem(6, 0)),
            parseList(h("0002  0005 0003 00000000  0006 0000 00000000"))
        )
    }

    @Test
    fun summary() {
        val p = h("01020005  0304${b32(t)}  0404${b32(t + 600)}  130101  0502012c  0902000f")
        val s = parseSummary(p)!!
        assertEquals(5, s.id)
        assertEquals(t, s.start)
        assertEquals(t + 600, s.end)
        assertEquals(1, s.type)
        val d = s.toSummaryData()
        assertEquals(300, d.getNumber(ActivitySummaryEntries.CALORIES_BURNT, 0).toInt())
        assertEquals(1.5, d.getNumber(ActivitySummaryEntries.SPEED_AVG, 0).toDouble(), 1e-9)
        assertFalse(d.has(ActivitySummaryEntries.DISTANCE_METERS))
    }

    @Test
    fun summaryRejectsBadTimes() {
        assertNull(parseSummary(h("01020005  0304${b32(t)}  0404${b32(t)}")))
        assertNull(parseSummary(h("0304${b32(t)}  0404${b32(t + 1)}")))
        assertEquals(255, parseSummary(h("01020005  0304${b32(t)}  0404${b32(t + 1)}"))!!.type)
    }

    @Test
    fun detailPagesContinueTheSampleCount() {
        val w = WorkoutSeries()
        // Mask 88 40: bit 4 HR (1 byte), bit 15 distance delta (2 bytes). Interval 10 s.
        val page = parseDetailPage(w, h("0005 0000 0a ${b32(t)}  8840 64 0005  8840 0a 0007"))!!
        assertEquals(2, page.samples)
        assertEquals(mapOf(t to 100), page.hr)
        assertEquals(mapOf(t to 5, t + 10 to 12), w.distance)
        // Every page repeats the start time.
        parseDetailPage(w, h("0005 0001 0a ${b32(t)}  8840 50 0001"))
        assertEquals(mapOf(t to 100, t + 20 to 80), w.hr)
        assertEquals(13, w.distance[t + 20])
    }

    @Test
    fun detailElevationIsSigned() {
        val w = WorkoutSeries()
        parseDetailPage(w, h("0005 0000 01 ${b32(t)}  10 ff9c"))
        assertEquals(-100, w.elevation[t])
    }

    @Test
    fun track() {
        val w = WorkoutSeries()
        w.elevation[t + 5] = 123
        w.hr[t + 4] = 99
        val lat = 452_500_000
        val lon = -15_000_000
        val gps = h("${b32(t)} ${b32(t + 60)} 00000000  0704${b32(lat.toLong())} 0804${b32(lon.toLong())} 0d0105")
        val track = buildTrack(w, t, "Run", gps)!!
        val p = track.allPoints.single()
        assertEquals((t + 5) * 1000L, p.time.time)
        assertEquals(45.25, p.location!!.latitude, 1e-9)
        assertEquals(-1.5, p.location!!.longitude, 1e-9)
        assertEquals(12.3, p.location!!.altitude, 1e-9)
        assertEquals(99, p.heartRate)
        assertEquals("Run", track.name)

        // No position before the first time step: no track.
        assertNull(buildTrack(w, t, "Run", h("${b32(t)} ${b32(t + 60)} 00000000  0d0105")))
        assertNull(buildTrack(w, t, "Run", h("${b32(t)} ${b32(t + 60)} 00000000")))
    }

    @Test
    fun speedFromDistanceSkipsGaps() {
        val d = TreeMap(mapOf(0L to 0, 10L to 50, 400L to 60))
        assertEquals(mapOf(10L to 500), deriveSpeed(d))
    }

    @Test
    fun nearestAndInterpolate() {
        val s = TreeMap(mapOf(100L to 1, 200L to 2))
        assertEquals(1, nearest(s, 140))
        assertEquals(2, nearest(s, 160))
        assertNull(nearest(s, 400))
        val lin = TreeMap(mapOf(0L to 0, 100L to 100))
        assertEquals(25.0, interpolate(lin, 25)!!, 1e-9)
        assertEquals(100.0, interpolate(lin, 150)!!, 1e-9)
        assertNull(interpolate(lin, 300))
    }

    @Test
    fun detailDerivedStats() {
        val w = WorkoutSeries()
        val d = ActivitySummaryData()
        assertFalse(Stratos4ProWorkoutParser.addDetailDerivedStats(d, w))
        w.cadence[1] = 80
        w.cadence[2] = 90
        assertTrue(Stratos4ProWorkoutParser.addDetailDerivedStats(d, w))
        assertEquals(90, d.getNumber(ActivitySummaryEntries.CADENCE_MAX, 0).toInt())
    }

    /** Real `aa 04` bodies, workout 8. */
    @Test
    fun kmSplits() {
        assertEquals(1 to 740, Stratos4ProWorkoutParser.parseKmSplit(h("0008 00 01 00 02e4 0000 000a")))
        assertNull(Stratos4ProWorkoutParser.parseKmSplit(h("0008 12 00 00 0000 0000 0000")))
        assertNull(Stratos4ProWorkoutParser.parseKmSplit(h("0008 00 01 00")))
        val w = WorkoutSeries()
        w.splits.add(1 to 740)
        val d = ActivitySummaryData()
        assertTrue(Stratos4ProWorkoutParser.addDetailDerivedStats(d, w))
        assertTrue(d.has("laps_header"))
        assertTrue(d.has("lap_1"))
    }

    @Test
    fun sportTypes() {
        assertEquals(ActivityKind.OUTDOOR_RUNNING, Stratos4ProWorkoutParser.sportTypeToActivityKind(1))
        assertEquals(ActivityKind.EXERCISE, Stratos4ProWorkoutParser.sportTypeToActivityKind(9999))
        assertTrue(Stratos4ProWorkoutParser.isGpsSport(1))
        assertFalse(Stratos4ProWorkoutParser.isGpsSport(3))
    }
}
