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

/**
 * Decodes PM5 rowing data blocks, laid out as in the public interface definition. All values are
 * little-endian.
 *
 * A block can arrive on its own characteristic (0x0031 to 0x003B) or behind its id on the
 * multiplexed characteristic (0x0080). The two layouts of one id differ: multiplexed 0x32 gains
 * average power, which 0x33 loses; multiplexed 0x36 gains work per stroke, which 0x35 loses;
 * multiplexed 0x39 loses average pace (it moves to 0x3C, which only exists multiplexed); and
 * multiplexed 0x3A loses the interval type byte.
 */
object PM5MessageDecoder {
    const val ID_GENERAL_STATUS = 0x31
    const val ID_ADDITIONAL_STATUS_1 = 0x32
    const val ID_ADDITIONAL_STATUS_2 = 0x33
    const val ID_STROKE_DATA = 0x35
    const val ID_ADDITIONAL_STROKE_DATA = 0x36
    const val ID_SPLIT_DATA = 0x37
    const val ID_ADDITIONAL_SPLIT_DATA = 0x38
    const val ID_WORKOUT_SUMMARY = 0x39
    const val ID_ADDITIONAL_SUMMARY = 0x3A
    const val ID_HEART_RATE_BELT_INFO = 0x3B
    const val ID_ADDITIONAL_SUMMARY_2 = 0x3C

    /** Multiplexed only while its own characteristic is not subscribed. */
    const val ID_FORCE_CURVE = 0x3D

    // Shortest block each layout accepts. The trailing machine type byte of 0x32 arrived with
    // interface revision 1.26 and that of 0x38 with 1.27, so older firmware omits them.
    private val MIN_LENGTH_MULTIPLEXED = mapOf(
        ID_GENERAL_STATUS to 19,
        ID_ADDITIONAL_STATUS_1 to 18,
        ID_ADDITIONAL_STATUS_2 to 18,
        ID_STROKE_DATA to 18,
        ID_ADDITIONAL_STROKE_DATA to 17,
        ID_SPLIT_DATA to 18,
        ID_ADDITIONAL_SPLIT_DATA to 18,
        ID_WORKOUT_SUMMARY to 18,
        ID_ADDITIONAL_SUMMARY to 18,
        ID_HEART_RATE_BELT_INFO to 6,
        ID_ADDITIONAL_SUMMARY_2 to 10,
    )

    private val MIN_LENGTH_CHARACTERISTIC = mapOf(
        ID_GENERAL_STATUS to 19,
        ID_ADDITIONAL_STATUS_1 to 16,
        ID_ADDITIONAL_STATUS_2 to 20,
        ID_STROKE_DATA to 20,
        ID_ADDITIONAL_STROKE_DATA to 15,
        ID_SPLIT_DATA to 18,
        ID_ADDITIONAL_SPLIT_DATA to 18,
        ID_WORKOUT_SUMMARY to 20,
        ID_ADDITIONAL_SUMMARY to 19,
        ID_HEART_RATE_BELT_INFO to 6,
    )

    /** Decodes one notification of the multiplexed characteristic, id byte included. */
    @JvmStatic
    fun decode(value: ByteArray): PM5Message? {
        if (value.isEmpty()) {
            return null
        }
        return decodeBlock(value[0].toInt() and 0xff, value.copyOfRange(1, value.size), true)
    }

    /**
     * Decodes the value of an individual rowing characteristic, where [shortId] is the low byte
     * of its 16-bit id (0x31 for 0x0031).
     */
    @JvmStatic
    fun decodeCharacteristic(shortId: Int, value: ByteArray): PM5Message =
        decodeBlock(shortId, value, false)

    private fun decodeBlock(id: Int, data: ByteArray, multiplexed: Boolean): PM5Message {
        val minLength = (if (multiplexed) MIN_LENGTH_MULTIPLEXED else MIN_LENGTH_CHARACTERISTIC)[id]
        if (minLength == null || data.size < minLength) {
            return PM5Message.Unknown(id, data)
        }
        val r = Reader(data)
        return when (id) {
            ID_GENERAL_STATUS -> PM5Message.GeneralStatus(
                elapsedSeconds = r.u24() / 100.0,
                distanceMeters = r.u24() / 10.0,
                workoutType = PM5WorkoutType.fromCode(r.u8()),
                intervalType = PM5IntervalType.fromCode(r.u8()),
                workoutState = PM5WorkoutState.fromCode(r.u8()),
                rowingState = PM5RowingState.fromCode(r.u8()),
                strokeState = PM5StrokeState.fromCode(r.u8()),
                totalWorkDistanceMeters = r.u24(),
                workoutDurationRaw = r.u24(),
                durationType = PM5DurationType.fromCode(r.u8()),
                dragFactor = r.u8(),
            )

            ID_ADDITIONAL_STATUS_1 -> PM5Message.AdditionalStatus1(
                elapsedSeconds = r.u24() / 100.0,
                speedMetersPerSecond = r.u16() / 1000.0,
                strokeRate = r.u8(),
                heartRate = heartRate(r.u8()),
                currentPaceSeconds = r.u16() / 100.0,
                averagePaceSeconds = r.u16() / 100.0,
                restDistanceMeters = r.u16(),
                restTimeSeconds = r.u24() / 100.0,
                averagePowerWatts = if (multiplexed) r.u16() else null,
                ergMachineType = r.u8OrNull(),
            )

            ID_ADDITIONAL_STATUS_2 -> {
                val elapsed = r.u24() / 100.0
                val intervalCount = r.u8()
                val averagePower = if (multiplexed) null else r.u16()
                PM5Message.AdditionalStatus2(
                    elapsedSeconds = elapsed,
                    intervalCount = intervalCount,
                    averagePowerWatts = averagePower,
                    totalCalories = r.u16(),
                    splitAveragePaceSeconds = r.u16() / 100.0,
                    splitAveragePowerWatts = r.u16(),
                    splitAverageCaloriesPerHour = r.u16(),
                    // the interface definition gives 0.1 s, but the monitor sends hundredths: a
                    // 25.0 s split arrives as 2505 here and as 250 in the split block
                    lastSplitTimeSeconds = r.u24() / 100.0,
                    lastSplitDistanceMeters = r.u24(),
                )
            }

            ID_STROKE_DATA -> PM5Message.StrokeData(
                elapsedSeconds = r.u24() / 100.0,
                distanceMeters = r.u24() / 10.0,
                driveLengthMeters = r.u8() / 100.0,
                driveTimeSeconds = r.u8() / 100.0,
                recoveryTimeSeconds = r.u16() / 100.0,
                strokeDistanceMeters = r.u16() / 100.0,
                peakDriveForceLbf = r.u16() / 10.0,
                averageDriveForceLbf = r.u16() / 10.0,
                workPerStrokeJoules = if (multiplexed) null else r.u16() / 10.0,
                strokeCount = r.u16(),
            )

            ID_ADDITIONAL_STROKE_DATA -> PM5Message.AdditionalStrokeData(
                elapsedSeconds = r.u24() / 100.0,
                strokePowerWatts = r.u16(),
                strokeCaloriesPerHour = r.u16(),
                strokeCount = r.u16(),
                projectedWorkTimeSeconds = r.u24(),
                projectedWorkDistanceMeters = r.u24(),
                workPerStrokeJoules = if (multiplexed) r.u16() / 10.0 else null,
            )

            ID_SPLIT_DATA -> PM5Message.SplitData(
                elapsedSeconds = r.u24() / 100.0,
                distanceMeters = r.u24() / 10.0,
                splitTimeSeconds = r.u24() / 10.0,
                splitDistanceMeters = r.u24(),
                restTimeSeconds = r.u16(),
                restDistanceMeters = r.u16(),
                intervalType = PM5IntervalType.fromCode(r.u8()),
                number = r.u8(),
            )

            ID_ADDITIONAL_SPLIT_DATA -> PM5Message.AdditionalSplitData(
                elapsedSeconds = r.u24() / 100.0,
                averageStrokeRate = r.u8(),
                workHeartRate = heartRate(r.u8()),
                restHeartRate = heartRate(r.u8()),
                averagePaceSeconds = r.u16() / 10.0,
                totalCalories = r.u16(),
                averageCaloriesPerHour = r.u16(),
                speedMetersPerSecond = r.u16() / 1000.0,
                powerWatts = r.u16(),
                averageDragFactor = r.u8(),
                number = r.u8(),
                ergMachineType = r.u8OrNull(),
            )

            ID_WORKOUT_SUMMARY -> PM5Message.WorkoutSummary(
                logDate = PM5LogDate(r.u16(), r.u16()),
                elapsedSeconds = r.u24() / 100.0,
                distanceMeters = r.u24() / 10.0,
                averageStrokeRate = r.u8(),
                endingHeartRate = heartRate(r.u8()),
                averageHeartRate = heartRate(r.u8()),
                minHeartRate = heartRate(r.u8()),
                maxHeartRate = heartRate(r.u8()),
                averageDragFactor = r.u8(),
                recoveryHeartRate = heartRate(r.u8()),
                workoutType = PM5WorkoutType.fromCode(r.u8()),
                averagePaceSeconds = if (multiplexed) null else r.u16() / 10.0,
            )

            ID_ADDITIONAL_SUMMARY -> {
                val logDate = PM5LogDate(r.u16(), r.u16())
                val intervalType = if (multiplexed) null else PM5IntervalType.fromCode(r.u8())
                PM5Message.AdditionalSummary(
                    logDate = logDate,
                    intervalType = intervalType,
                    splitSize = r.u16(),
                    splitCount = r.u8(),
                    totalCalories = r.u16(),
                    averagePowerWatts = r.u16(),
                    totalRestDistanceMeters = r.u24(),
                    intervalRestTimeSeconds = r.u16(),
                    averageCaloriesPerHour = r.u16(),
                )
            }

            ID_HEART_RATE_BELT_INFO -> PM5Message.HeartRateBeltInfo(
                manufacturerId = r.u8(),
                deviceType = r.u8(),
                beltId = r.u32(),
            )

            ID_ADDITIONAL_SUMMARY_2 -> {
                val logDate = PM5LogDate(r.u16(), r.u16())
                val averagePace = r.u16() / 10.0
                val gameByte = r.u8()
                PM5Message.AdditionalSummary2(
                    logDate = logDate,
                    averagePaceSeconds = averagePace,
                    gameId = gameByte and 0x0f,
                    verified = (gameByte and 0xf0) != 0,
                    gameScore = r.u16(),
                    ergMachineType = r.u8(),
                )
            }

            else -> PM5Message.Unknown(id, data)
        }
    }

    /** 255 marks a missing heart rate in the status blocks and 0 an unset one in the summary. */
    private fun heartRate(value: Int): Int? = if (value == 0 || value == 255) null else value

    private class Reader(private val data: ByteArray) {
        private var pos = 0

        fun u8(): Int = data[pos++].toInt() and 0xff

        fun u8OrNull(): Int? = if (pos < data.size) u8() else null

        fun u16(): Int = u8() or (u8() shl 8)

        fun u24(): Int = u8() or (u8() shl 8) or (u8() shl 16)

        fun u32(): Long = (u16().toLong()) or (u16().toLong() shl 16)
    }
}
