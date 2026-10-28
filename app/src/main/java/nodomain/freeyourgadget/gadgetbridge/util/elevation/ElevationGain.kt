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
package nodomain.freeyourgadget.gadgetbridge.util.elevation

/**
 * Cumulative ascent and descent from an altitude profile, with noise filtered
 * out. Summing every positive step banks small errors thousands of times.
 * Two filters: an exponential moving average, then hysteresis, so a change is
 * banked only once the smoothed altitude moves [MIN_STEP_METERS] from the last
 * accepted reference. Simulated flat routes report about 15 m.
 */
object ElevationGain {

    /** EMA weight. Heavier smoothing lags and under-reports sparse tracks. */
    private const val SMOOTHING_ALPHA = 0.3

    /** How far the smoothed altitude must move before it counts. Above smoothed GPS noise. */
    private const val MIN_STEP_METERS = 5.0

    data class Change(val gain: Double, val loss: Double)

    /** Ascent and descent in meters, the descent as a positive number. Nulls and non-finite values are skipped. */
    @JvmStatic
    fun fromAltitudes(altitudes: Iterable<Double?>): Change {
        var smoothed: Double? = null
        var reference: Double? = null
        var lastAltitude: Double? = null
        var gain = 0.0
        var loss = 0.0

        for (altitude in altitudes) {
            if (altitude == null || !altitude.isFinite()) continue
            lastAltitude = altitude
            val nextSmoothed = smoothed?.let { it + (altitude - it) * SMOOTHING_ALPHA } ?: altitude
            smoothed = nextSmoothed
            val currentReference = reference
            if (currentReference == null) {
                reference = nextSmoothed
                continue
            }
            val delta = nextSmoothed - currentReference
            if (delta >= MIN_STEP_METERS) {
                gain += delta
                reference = nextSmoothed
            } else if (delta <= -MIN_STEP_METERS) {
                loss += -delta
                reference = nextSmoothed
            }
            // Anything smaller is noise: the reference does not move.
        }

        // Settle the smoothing lag against the final raw altitude: on a short
        // track the trailing tail is a large share of the whole.
        val finalReference = reference
        if (lastAltitude != null && finalReference != null) {
            val residual = lastAltitude - finalReference
            if (residual >= MIN_STEP_METERS) {
                gain += residual
            } else if (residual <= -MIN_STEP_METERS) {
                loss += -residual
            }
        }

        return Change(gain = gain, loss = loss)
    }
}
