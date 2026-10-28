/*  Copyright (C) 2026 Dany Mestas

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.concept2.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** Decodes a real 100 m row and checks the blocks agree with each other. */
class PM5RecordingDecodeTest {
    private val messages = PM5Recording.row100m().map { PM5MessageDecoder.decodeCharacteristic(it.shortId, it.value) }

    @Test
    fun everyBlockDecodes() {
        assertFalse(messages.any { it is PM5Message.Unknown })
        assertEquals(299 - 4, messages.size)
    }

    @Test
    fun statusTimeAndDistanceNeverGoBack() {
        val status = messages.filterIsInstance<PM5Message.GeneralStatus>()
        status.zipWithNext().forEach { (a, b) ->
            assertTrue(b.elapsedSeconds >= a.elapsedSeconds)
            assertTrue(b.distanceMeters >= a.distanceMeters)
        }
        val last = status.last()
        assertEquals(PM5WorkoutState.WORKOUT_LOGGED, last.workoutState)
        assertEquals(PM5DurationType.DISTANCE, last.durationType)
        assertEquals(100, last.workoutDurationRaw)
    }

    @Test
    fun oldFirmwareOmitsMachineType() {
        val status = messages.filterIsInstance<PM5Message.AdditionalStatus1>()
        assertTrue(status.all { it.ergMachineType == null && it.averagePowerWatts == null })
        val rowing = status.filter { it.speedMetersPerSecond > 0 && it.currentPaceSeconds > 0 }
        assertTrue(rowing.size > 50)
        rowing.forEach {
            // pace is rounded to hundredths of a second, so the two agree to within a percent
            assertEquals(500.0 / it.currentPaceSeconds, it.speedMetersPerSecond, it.speedMetersPerSecond * 0.01)
            assertNull(it.heartRate)
        }
    }

    @Test
    fun strokesCountUp() {
        val strokes = messages.filterIsInstance<PM5Message.StrokeData>()
        assertEquals((1..10).toList(), strokes.map { it.strokeCount }.distinct().filter { it > 0 })
        val extra = messages.filterIsInstance<PM5Message.AdditionalStrokeData>()
        assertEquals(10, extra.last().strokeCount)
    }

    @Test
    fun summaryMatchesFinalStatusAndSplit() {
        val summary = messages.filterIsInstance<PM5Message.WorkoutSummary>().single()
        val additional = messages.filterIsInstance<PM5Message.AdditionalSummary>().single()
        val split = messages.filterIsInstance<PM5Message.SplitData>().single()
        val splitExtra = messages.filterIsInstance<PM5Message.AdditionalSplitData>().single()
        val lastStatus = messages.filterIsInstance<PM5Message.GeneralStatus>().last()

        assertEquals(LocalDateTime.of(2016, 3, 6, 11, 36), summary.logDate.toLocalDateTime())
        assertEquals(summary.logDate, additional.logDate)
        assertEquals(25.0, summary.elapsedSeconds, 1e-9)
        assertEquals(100.0, summary.distanceMeters, 1e-9)
        assertEquals(lastStatus.elapsedSeconds, summary.elapsedSeconds, 1e-9)
        assertEquals(lastStatus.distanceMeters, summary.distanceMeters, 1e-9)
        assertEquals(24, summary.averageStrokeRate)
        assertEquals(125.0, summary.averagePaceSeconds!!, 1e-9)
        assertEquals(PM5WorkoutType.FIXED_DISTANCE_SPLITS, summary.workoutType)
        assertNull(summary.averageHeartRate)

        assertEquals(PM5IntervalType.DISTANCE, additional.intervalType)
        assertEquals(100, additional.splitSize)
        assertEquals(1, additional.splitCount)
        assertEquals(6, additional.totalCalories)
        assertEquals(179, additional.averagePowerWatts)

        assertEquals(25.0, split.splitTimeSeconds, 1e-9)
        assertEquals(100, split.splitDistanceMeters)
        assertEquals(24, splitExtra.averageStrokeRate)
        assertEquals(summary.averagePaceSeconds!!, splitExtra.averagePaceSeconds, 1e-9)
        assertEquals(additional.averagePowerWatts, splitExtra.powerWatts)
        assertEquals(4.0, splitExtra.speedMetersPerSecond, 1e-9)
        assertNull(splitExtra.ergMachineType)
    }

    @Test
    fun statusRepeatsTheLastSplitTime() {
        val split = messages.filterIsInstance<PM5Message.SplitData>().single()
        val after = messages.filterIsInstance<PM5Message.AdditionalStatus2>().filter { it.lastSplitTimeSeconds > 0 }
        assertTrue(after.isNotEmpty())
        // the status block carries hundredths, so it shows 25.05 s where the split block rounds to 25.0 s
        after.forEach { assertEquals(split.splitTimeSeconds, it.lastSplitTimeSeconds, 0.1) }
    }
}
