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

// Enumerations from Appendix A of the public PM5 Bluetooth interface definition.

enum class PM5WorkoutType(val code: Int) {
    JUST_ROW_NO_SPLITS(0),
    JUST_ROW_SPLITS(1),
    FIXED_DISTANCE_NO_SPLITS(2),
    FIXED_DISTANCE_SPLITS(3),
    FIXED_TIME_NO_SPLITS(4),
    FIXED_TIME_SPLITS(5),
    FIXED_TIME_INTERVAL(6),
    FIXED_DISTANCE_INTERVAL(7),
    VARIABLE_INTERVAL(8),
    VARIABLE_UNDEFINED_REST_INTERVAL(9),
    FIXED_CALORIE(10),
    FIXED_WATT_MINUTES(11),
    FIXED_CALORIE_INTERVAL(12),
    FIXED_WATT_MINUTE_INTERVAL(13);

    val isInterval: Boolean
        get() = when (this) {
            FIXED_TIME_INTERVAL, FIXED_DISTANCE_INTERVAL, VARIABLE_INTERVAL,
            VARIABLE_UNDEFINED_REST_INTERVAL, FIXED_CALORIE_INTERVAL, FIXED_WATT_MINUTE_INTERVAL -> true
            else -> false
        }

    companion object {
        @JvmStatic
        fun fromCode(code: Int): PM5WorkoutType? = entries.firstOrNull { it.code == code }
    }
}

enum class PM5IntervalType(val code: Int) {
    TIME(0),
    DISTANCE(1),
    REST(2),
    TIME_REST_UNDEFINED(3),
    DISTANCE_REST_UNDEFINED(4),
    REST_UNDEFINED(5),
    CALORIE(6),
    CALORIE_REST_UNDEFINED(7),
    WATT_MINUTE(8),
    WATT_MINUTE_REST_UNDEFINED(9),
    NONE(255);

    companion object {
        @JvmStatic
        fun fromCode(code: Int): PM5IntervalType? = entries.firstOrNull { it.code == code }
    }
}

enum class PM5WorkoutState(val code: Int) {
    WAIT_TO_BEGIN(0),
    WORKOUT_ROW(1),
    COUNTDOWN_PAUSE(2),
    INTERVAL_REST(3),
    INTERVAL_WORK_TIME(4),
    INTERVAL_WORK_DISTANCE(5),
    INTERVAL_REST_END_TO_WORK_TIME(6),
    INTERVAL_REST_END_TO_WORK_DISTANCE(7),
    INTERVAL_WORK_TIME_TO_REST(8),
    INTERVAL_WORK_DISTANCE_TO_REST(9),
    WORKOUT_END(10),
    TERMINATE(11),
    WORKOUT_LOGGED(12),
    REARM(13);

    /** The monitor is counting a piece: anything between the first stroke and the end. */
    val isInProgress: Boolean
        get() = code in WORKOUT_ROW.code..INTERVAL_WORK_DISTANCE_TO_REST.code

    val isFinished: Boolean
        get() = this == WORKOUT_END || this == TERMINATE || this == WORKOUT_LOGGED

    companion object {
        @JvmStatic
        fun fromCode(code: Int): PM5WorkoutState? = entries.firstOrNull { it.code == code }
    }
}

enum class PM5RowingState(val code: Int) {
    INACTIVE(0),
    ACTIVE(1);

    companion object {
        @JvmStatic
        fun fromCode(code: Int): PM5RowingState? = entries.firstOrNull { it.code == code }
    }
}

enum class PM5StrokeState(val code: Int) {
    WAITING_FOR_MIN_SPEED(0),
    WAITING_FOR_ACCELERATION(1),
    DRIVING(2),
    DWELLING_AFTER_DRIVE(3),
    RECOVERY(4);

    companion object {
        @JvmStatic
        fun fromCode(code: Int): PM5StrokeState? = entries.firstOrNull { it.code == code }
    }
}

enum class PM5DurationType(val code: Int) {
    TIME(0x00),
    CALORIES(0x40),
    DISTANCE(0x80),
    WATTS(0xC0);

    companion object {
        @JvmStatic
        fun fromCode(code: Int): PM5DurationType? = entries.firstOrNull { it.code == code }
    }
}

/** Which kind of erg an "Erg Machine Type" byte describes, grouping the spec's model variants. */
enum class PM5ErgKind {
    ROWER,
    SKI,
    BIKE,
    UNKNOWN;

    /** Metres the monitor's pace fields count time over: 1000 on the BikeErg, 500 on the others. */
    val paceMeters: Int get() = if (this == BIKE) 1000 else 500

    /** [monitorPace], as the monitor sends it for this erg, in seconds per 500 m. */
    fun pacePer500m(monitorPace: Double): Double = monitorPace * 500 / paceMeters

    companion object {
        @JvmStatic
        fun fromMachineType(code: Int): PM5ErgKind = when (code) {
            in 0..63 -> ROWER // static, slides and dynamic rowers
            128, 143 -> SKI
            in 192..207 -> BIKE
            224 -> ROWER // multi-erg row
            225 -> SKI // multi-erg ski
            226 -> BIKE // multi-erg bike
            else -> UNKNOWN
        }

        /**
         * The erg a monitor names after its serial when it advertises, as in "PM5 430070439 Row";
         * null when the name gives none.
         */
        @JvmStatic
        fun fromDeviceName(name: String?): PM5ErgKind? {
            val words = name?.split(' ')?.drop(2) ?: return null
            return when {
                words.any { it.equals("Row", ignoreCase = true) } -> ROWER
                words.any { it.equals("Ski", ignoreCase = true) } -> SKI
                words.any { it.equals("Bike", ignoreCase = true) } -> BIKE
                else -> null
            }
        }

        /**
         * The erg from the machine type the monitor reports, which firmware before interface
         * revision 1.26 leaves out of its status, else from [named], else a rower.
         */
        @JvmStatic
        fun of(machineType: Int?, named: PM5ErgKind?): PM5ErgKind {
            val reported = machineType?.let { fromMachineType(it) }
            return reported?.takeIf { it != UNKNOWN } ?: named ?: reported ?: ROWER
        }
    }
}
