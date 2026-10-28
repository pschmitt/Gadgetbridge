/*  Copyright (C) 2026 Manuel Marcatili

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
package nodomain.freeyourgadget.gadgetbridge.util.sleep

import java.time.Instant

/** What a watch that does not stage sleep said about one minute. [code] is what gets stored. */
enum class SleepMinuteKind(val code: Int) {
    /** Heart rate and movement, left for the phone to classify. */
    RAW(0),

    /** The watch was sure the wearer was awake. */
    AWAKE(1),

    /** Not worn, or no signal. */
    UNMEASURABLE(2),
    ;

    companion object {
        @JvmStatic
        fun fromCode(code: Int): SleepMinuteKind? = entries.firstOrNull { it.code == code }
    }
}

/** The estimator's input for one minute. Only movement and heart rate are used. */
data class SleepMinute(
    val time: Instant,
    val kind: SleepMinuteKind,
    /** Movement count for the minute; zero when still. Ignored unless [kind] is RAW. */
    val movement: Float = 0f,
    /** Beats per minute, or null when the row carried none. */
    val heartRate: Float? = null,
)
