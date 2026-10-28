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

/** Payloads are assembled field by field from table 4 of the public PM5 interface definition. */
class PM5MessageDecoderTest {
    private fun decode(hex: String): PM5Message? = PM5MessageDecoder.decode(hex(hex))

    @Test
    fun generalStatus() {
        val m = decode("31 393000 d71100 03 ff 01 01 02 d00700 d00700 80 78") as PM5Message.GeneralStatus
        assertEquals(123.45, m.elapsedSeconds, 1e-9)
        assertEquals(456.7, m.distanceMeters, 1e-9)
        assertEquals(PM5WorkoutType.FIXED_DISTANCE_SPLITS, m.workoutType)
        assertEquals(PM5IntervalType.NONE, m.intervalType)
        assertEquals(PM5WorkoutState.WORKOUT_ROW, m.workoutState)
        assertEquals(PM5RowingState.ACTIVE, m.rowingState)
        assertEquals(PM5StrokeState.DRIVING, m.strokeState)
        assertEquals(2000, m.totalWorkDistanceMeters)
        assertEquals(2000, m.workoutDurationRaw)
        assertEquals(PM5DurationType.DISTANCE, m.durationType)
        assertNull(m.workoutDurationSeconds)
        assertEquals(120, m.dragFactor)
    }

    @Test
    fun generalStatusTimeTarget() {
        // 30:00 piece: 180000 hundredths of a second
        val m = decode("31 000000 000000 05 ff 00 00 00 000000 20bf02 00 00") as PM5Message.GeneralStatus
        assertEquals(PM5WorkoutState.WAIT_TO_BEGIN, m.workoutState)
        assertEquals(PM5DurationType.TIME, m.durationType)
        assertEquals(1800.0, m.workoutDurationSeconds!!, 1e-9)
    }

    @Test
    fun largestElapsedTime() {
        val m = decode("31 ffffff ffffff 00 ff 01 01 00 000000 000000 00 00") as PM5Message.GeneralStatus
        assertEquals(167772.15, m.elapsedSeconds, 1e-9)
        assertEquals(1677721.5, m.distanceMeters, 1e-9)
    }

    @Test
    fun additionalStatus1() {
        val m = decode("32 393000 1b10 1a 8e 622f e52e 0000 000000 cb00 00") as PM5Message.AdditionalStatus1
        assertEquals(123.45, m.elapsedSeconds, 1e-9)
        assertEquals(4.123, m.speedMetersPerSecond, 1e-9)
        assertEquals(26, m.strokeRate)
        assertEquals(142, m.heartRate)
        assertEquals(121.30, m.currentPaceSeconds, 1e-9)
        assertEquals(120.05, m.averagePaceSeconds, 1e-9)
        assertEquals(0, m.restDistanceMeters)
        assertEquals(0.0, m.restTimeSeconds, 1e-9)
        assertEquals(203, m.averagePowerWatts)
        assertEquals(0, m.ergMachineType)
    }

    @Test
    fun heartRateWithoutBelt() {
        val m = decode("32 393000 1b10 1a ff 622f e52e 0000 000000 cb00 00") as PM5Message.AdditionalStatus1
        assertNull(m.heartRate)
    }

    @Test
    fun additionalStatus2() {
        val m = decode("33 393000 02 2300 ae2e d200 b603 e02e00 f40100") as PM5Message.AdditionalStatus2
        assertEquals(2, m.intervalCount)
        assertEquals(35, m.totalCalories)
        assertEquals(119.50, m.splitAveragePaceSeconds, 1e-9)
        assertEquals(210, m.splitAveragePowerWatts)
        assertEquals(950, m.splitAverageCaloriesPerHour)
        assertEquals(120.0, m.lastSplitTimeSeconds, 1e-9)
        assertEquals(500, m.lastSplitDistanceMeters)
    }

    @Test
    fun strokeData() {
        val m = decode("35 393000 d71100 91 50 9600 0104 0d07 ea03 3900") as PM5Message.StrokeData
        assertEquals(456.7, m.distanceMeters, 1e-9)
        assertEquals(1.45, m.driveLengthMeters, 1e-9)
        assertEquals(0.80, m.driveTimeSeconds, 1e-9)
        assertEquals(1.50, m.recoveryTimeSeconds, 1e-9)
        assertEquals(10.25, m.strokeDistanceMeters, 1e-9)
        assertEquals(180.5, m.peakDriveForceLbf, 1e-9)
        assertEquals(100.2, m.averageDriveForceLbf, 1e-9)
        assertEquals(57, m.strokeCount)
    }

    @Test
    fun additionalStrokeData() {
        val m = decode("36 393000 cb00 e803 3900 e00100 d00700 4112") as PM5Message.AdditionalStrokeData
        assertEquals(203, m.strokePowerWatts)
        assertEquals(1000, m.strokeCaloriesPerHour)
        assertEquals(57, m.strokeCount)
        assertEquals(480, m.projectedWorkTimeSeconds)
        assertEquals(2000, m.projectedWorkDistanceMeters)
        assertEquals(467.3, m.workPerStrokeJoules!!, 1e-9)
    }

    @Test
    fun splitData() {
        val m = decode("37 393000 d71100 b00400 f40100 3c00 0000 01 01") as PM5Message.SplitData
        assertEquals(120.0, m.splitTimeSeconds, 1e-9)
        assertEquals(500, m.splitDistanceMeters)
        assertEquals(60, m.restTimeSeconds)
        assertEquals(0, m.restDistanceMeters)
        assertEquals(PM5IntervalType.DISTANCE, m.intervalType)
        assertEquals(1, m.number)
    }

    @Test
    fun additionalSplitData() {
        val m = decode("38 393000 19 96 78 b004 1e00 8403 4710 cb00 79 01 00") as PM5Message.AdditionalSplitData
        assertEquals(25, m.averageStrokeRate)
        assertEquals(150, m.workHeartRate)
        assertEquals(120, m.restHeartRate)
        assertEquals(120.0, m.averagePaceSeconds, 1e-9)
        assertEquals(30, m.totalCalories)
        assertEquals(900, m.averageCaloriesPerHour)
        assertEquals(4.167, m.speedMetersPerSecond, 1e-9)
        assertEquals(203, m.powerWatts)
        assertEquals(121, m.averageDragFactor)
        assertEquals(1, m.number)
        assertEquals(0, m.ergMachineType)
    }

    @Test
    fun additionalSplitDataWithoutMachineType() {
        val m = decode("38 393000 19 96 78 b004 1e00 8403 4710 cb00 79 01") as PM5Message.AdditionalSplitData
        assertEquals(1, m.number)
        assertNull(m.ergMachineType)
    }

    @Test
    fun workoutSummary() {
        val m = decode("39 3412 1e0a 80bb00 204e00 1a a0 91 5f ac 78 00 02") as PM5Message.WorkoutSummary
        assertEquals(PM5LogDate(0x1234, 0x0a1e), m.logDate)
        assertEquals(480.0, m.elapsedSeconds, 1e-9)
        assertEquals(2000.0, m.distanceMeters, 1e-9)
        assertEquals(26, m.averageStrokeRate)
        assertEquals(160, m.endingHeartRate)
        assertEquals(145, m.averageHeartRate)
        assertEquals(95, m.minHeartRate)
        assertEquals(172, m.maxHeartRate)
        assertEquals(120, m.averageDragFactor)
        assertNull(m.recoveryHeartRate)
        assertEquals(PM5WorkoutType.FIXED_DISTANCE_NO_SPLITS, m.workoutType)
    }

    @Test
    fun workoutSummaryWithRecoveryHeartRate() {
        val m = decode("39 3412 1e0a 80bb00 204e00 1a a0 91 5f ac 78 6e 02") as PM5Message.WorkoutSummary
        assertEquals(110, m.recoveryHeartRate)
    }

    @Test
    fun additionalSummary() {
        val m = decode("3a 3412 1e0a f401 04 7800 cb00 000000 0000 e803") as PM5Message.AdditionalSummary
        assertEquals(PM5LogDate(0x1234, 0x0a1e), m.logDate)
        assertEquals(500, m.splitSize)
        assertEquals(4, m.splitCount)
        assertEquals(120, m.totalCalories)
        assertEquals(203, m.averagePowerWatts)
        assertEquals(0, m.totalRestDistanceMeters)
        assertEquals(0, m.intervalRestTimeSeconds)
        assertEquals(1000, m.averageCaloriesPerHour)
    }

    @Test
    fun additionalSummary2() {
        val m = decode("3c 3412 1e0a b004 13 2a00 00") as PM5Message.AdditionalSummary2
        assertEquals(120.0, m.averagePaceSeconds, 1e-9)
        assertEquals(3, m.gameId)
        assertTrue(m.verified)
        assertEquals(42, m.gameScore)
        assertEquals(0, m.ergMachineType)

        val unverified = decode("3c 3412 1e0a b004 00 0000 00") as PM5Message.AdditionalSummary2
        assertFalse(unverified.verified)
    }

    @Test
    fun heartRateBeltInfo() {
        val m = decode("3b 01 02 78563412") as PM5Message.HeartRateBeltInfo
        assertEquals(1, m.manufacturerId)
        assertEquals(2, m.deviceType)
        assertEquals(0x12345678L, m.beltId)
    }

    @Test
    fun unknownIdIsKeptRaw() {
        val m = decode("5a 0102") as PM5Message.Unknown
        assertEquals(0x5a, m.id)
        assertEquals(2, m.payload.size)
    }

    @Test
    fun truncatedBlockIsUnknown() {
        val m = decode("31 3930") as PM5Message.Unknown
        assertEquals(0x31, m.id)
    }

    @Test
    fun emptyNotification() {
        assertNull(PM5MessageDecoder.decode(ByteArray(0)))
    }

    @Test
    fun logDate() {
        assertEquals(java.time.LocalDateTime.of(2014, 4, 25, 16, 9), PM5LogDate(0x1d94, 0x1009).toLocalDateTime())
        assertEquals(java.time.LocalDateTime.of(2022, 1, 31, 14, 1), PM5LogDate(0x2df1, 0x0e01).toLocalDateTime())
        assertNull(PM5LogDate(0, 0).toLocalDateTime())
    }

    @Test
    fun multiplexedSummaryHasNoPace() {
        val m = decode("39 3412 1e0a 80bb00 204e00 1a a0 91 5f ac 78 00 02") as PM5Message.WorkoutSummary
        assertNull(m.averagePaceSeconds)
    }

    @Test
    fun ergKind() {
        assertEquals(PM5ErgKind.ROWER, PM5ErgKind.fromMachineType(0))
        assertEquals(PM5ErgKind.ROWER, PM5ErgKind.fromMachineType(32))
        assertEquals(PM5ErgKind.SKI, PM5ErgKind.fromMachineType(128))
        assertEquals(PM5ErgKind.BIKE, PM5ErgKind.fromMachineType(192))
        assertEquals(PM5ErgKind.BIKE, PM5ErgKind.fromMachineType(226))
        assertEquals(PM5ErgKind.UNKNOWN, PM5ErgKind.fromMachineType(64))
    }

    @Test
    fun uuids() {
        assertEquals("ce060080-43e5-11e4-916c-0800200c9a66", PM5Uuids.MULTIPLEXED.toString())
        assertEquals("ce060030-43e5-11e4-916c-0800200c9a66", PM5Uuids.SERVICE_ROWING.toString())
    }

    companion object {
        fun hex(hex: String): ByteArray {
            val cleaned = hex.replace(" ", "")
            require(cleaned.length % 2 == 0) { "Hex string must have even length" }
            return ByteArray(cleaned.length / 2) { i -> cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        }
    }
}
