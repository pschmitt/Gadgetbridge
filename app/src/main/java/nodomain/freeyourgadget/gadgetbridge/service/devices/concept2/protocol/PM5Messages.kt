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
 * Data blocks the PM5 sends on its rowing characteristics, decoded by [PM5MessageDecoder].
 * Field units are in the property names. Times called "elapsed" count from the start of the
 * piece, as shown on the monitor.
 */
sealed interface PM5Message {
    /** Multiplexed id 0x31. */
    data class GeneralStatus(
        val elapsedSeconds: Double,
        val distanceMeters: Double,
        val workoutType: PM5WorkoutType?,
        val intervalType: PM5IntervalType?,
        val workoutState: PM5WorkoutState?,
        val rowingState: PM5RowingState?,
        val strokeState: PM5StrokeState?,
        val totalWorkDistanceMeters: Int,
        /** Hundredths of a second for a time target, otherwise meters, calories or watt-minutes as [durationType] says. */
        val workoutDurationRaw: Int,
        val durationType: PM5DurationType?,
        val dragFactor: Int,
    ) : PM5Message {
        /**
         * Whether a rest between intervals is running: the interval type turns to a rest kind
         * for its length, and the workout state reads interval rest, then rest ending into the
         * next work interval.
         */
        val isResting: Boolean
            get() = intervalType == PM5IntervalType.REST || intervalType == PM5IntervalType.REST_UNDEFINED ||
                workoutState == PM5WorkoutState.INTERVAL_REST ||
                workoutState == PM5WorkoutState.INTERVAL_REST_END_TO_WORK_TIME ||
                workoutState == PM5WorkoutState.INTERVAL_REST_END_TO_WORK_DISTANCE

        /** The target of the piece in seconds, or null when it is not a time target. */
        val workoutDurationSeconds: Double?
            get() = if (durationType == PM5DurationType.TIME) workoutDurationRaw / 100.0 else null
    }

    /** Multiplexed id 0x32. */
    data class AdditionalStatus1(
        val elapsedSeconds: Double,
        val speedMetersPerSecond: Double,
        val strokeRate: Int,
        val heartRate: Int?,
        /** Seconds per 500 m, per 1000 m on the BikeErg; see [PM5ErgKind.paceMeters]. */
        val currentPaceSeconds: Double,
        /** Seconds per 500 m, per 1000 m on the BikeErg; see [PM5ErgKind.paceMeters]. */
        val averagePaceSeconds: Double,
        val restDistanceMeters: Int,
        val restTimeSeconds: Double,
        /** Only in the multiplexed layout. */
        val averagePowerWatts: Int?,
        /** Absent on firmware older than interface revision 1.26. */
        val ergMachineType: Int?,
    ) : PM5Message

    /** Multiplexed id 0x33. */
    data class AdditionalStatus2(
        val elapsedSeconds: Double,
        val intervalCount: Int,
        /** Only in the individual characteristic layout; the multiplexed layout moves it to 0x32. */
        val averagePowerWatts: Int?,
        val totalCalories: Int,
        /** Seconds per 500 m, per 1000 m on the BikeErg; see [PM5ErgKind.paceMeters]. */
        val splitAveragePaceSeconds: Double,
        val splitAveragePowerWatts: Int,
        val splitAverageCaloriesPerHour: Int,
        val lastSplitTimeSeconds: Double,
        val lastSplitDistanceMeters: Int,
    ) : PM5Message

    /** Multiplexed id 0x35, sent once per stroke. */
    data class StrokeData(
        val elapsedSeconds: Double,
        val distanceMeters: Double,
        val driveLengthMeters: Double,
        val driveTimeSeconds: Double,
        val recoveryTimeSeconds: Double,
        val strokeDistanceMeters: Double,
        val peakDriveForceLbf: Double,
        val averageDriveForceLbf: Double,
        /** Only in the individual characteristic layout; the multiplexed layout moves it to 0x36. */
        val workPerStrokeJoules: Double?,
        val strokeCount: Int,
    ) : PM5Message

    /** Multiplexed id 0x36, sent once per stroke. */
    data class AdditionalStrokeData(
        val elapsedSeconds: Double,
        val strokePowerWatts: Int,
        val strokeCaloriesPerHour: Int,
        val strokeCount: Int,
        val projectedWorkTimeSeconds: Int,
        val projectedWorkDistanceMeters: Int,
        /** Only in the multiplexed layout. */
        val workPerStrokeJoules: Double?,
    ) : PM5Message

    /** Multiplexed id 0x37, sent when a split or interval ends. */
    data class SplitData(
        val elapsedSeconds: Double,
        val distanceMeters: Double,
        val splitTimeSeconds: Double,
        val splitDistanceMeters: Int,
        val restTimeSeconds: Int,
        val restDistanceMeters: Int,
        val intervalType: PM5IntervalType?,
        val number: Int,
    ) : PM5Message

    /** Multiplexed id 0x38, follows [SplitData] for the same split. */
    data class AdditionalSplitData(
        val elapsedSeconds: Double,
        val averageStrokeRate: Int,
        val workHeartRate: Int?,
        val restHeartRate: Int?,
        /** Seconds per 500 m, per 1000 m on the BikeErg; see [PM5ErgKind.paceMeters]. */
        val averagePaceSeconds: Double,
        val totalCalories: Int,
        val averageCaloriesPerHour: Int,
        val speedMetersPerSecond: Double,
        val powerWatts: Int,
        val averageDragFactor: Int,
        val number: Int,
        /** Absent on firmware older than interface revision 1.27. */
        val ergMachineType: Int?,
    ) : PM5Message

    /** Multiplexed id 0x39. Sent at the end of a piece, and again about a minute later with [recoveryHeartRate]. */
    data class WorkoutSummary(
        val logDate: PM5LogDate,
        val elapsedSeconds: Double,
        val distanceMeters: Double,
        val averageStrokeRate: Int,
        val endingHeartRate: Int?,
        val averageHeartRate: Int?,
        val minHeartRate: Int?,
        val maxHeartRate: Int?,
        val averageDragFactor: Int,
        val recoveryHeartRate: Int?,
        val workoutType: PM5WorkoutType?,
        /** Seconds per 500 m, per 1000 m on the BikeErg. Only in the individual characteristic layout; the multiplexed layout moves it to 0x3C. */
        val averagePaceSeconds: Double?,
    ) : PM5Message

    /** Multiplexed id 0x3A. */
    data class AdditionalSummary(
        val logDate: PM5LogDate,
        /** Only in the individual characteristic layout. */
        val intervalType: PM5IntervalType?,
        /** Meters or seconds, as the workout type says. */
        val splitSize: Int,
        val splitCount: Int,
        val totalCalories: Int,
        val averagePowerWatts: Int,
        val totalRestDistanceMeters: Int,
        val intervalRestTimeSeconds: Int,
        val averageCaloriesPerHour: Int,
    ) : PM5Message

    /** Multiplexed id 0x3C, only sent multiplexed. */
    data class AdditionalSummary2(
        val logDate: PM5LogDate,
        /** Seconds per 500 m, per 1000 m on the BikeErg; see [PM5ErgKind.paceMeters]. */
        val averagePaceSeconds: Double,
        val gameId: Int,
        val verified: Boolean,
        val gameScore: Int,
        val ergMachineType: Int,
    ) : PM5Message

    /** Multiplexed id 0x3B, the heart rate belt the monitor is paired with. */
    data class HeartRateBeltInfo(
        val manufacturerId: Int,
        val deviceType: Int,
        val beltId: Long,
    ) : PM5Message

    /** An id this decoder does not know, or a block shorter than its layout. */
    class Unknown(val id: Int, val payload: ByteArray) : PM5Message {
        override fun toString(): String = "Unknown(id=0x%02x, %d bytes)".format(id, payload.size)
    }
}

/**
 * The two 16-bit "Log Entry Date" and "Log Entry Time" words that stamp the logbook entry of a piece.
 * The summary blocks of one piece carry the same pair.
 *
 * Date bits 0-3 are the month, bits 4-8 the day, bits 9-15 the year since 2000. The time word holds
 * the minute in its low byte and the hour (24 h) in its high byte. The clock is the monitor's local
 * time without a zone and without seconds.
 */
data class PM5LogDate(val date: Int, val time: Int) {
    val year: Int get() = 2000 + ((date shr 9) and 0x7f)
    val month: Int get() = date and 0x0f
    val day: Int get() = (date shr 4) and 0x1f
    val hour: Int get() = (time shr 8) and 0xff
    val minute: Int get() = time and 0xff

    /** Null when the monitor clock was never set or the words do not form a valid date. */
    fun toLocalDateTime(): java.time.LocalDateTime? = try {
        java.time.LocalDateTime.of(year, month, day, hour, minute)
    } catch (e: java.time.DateTimeException) {
        null
    }
}
