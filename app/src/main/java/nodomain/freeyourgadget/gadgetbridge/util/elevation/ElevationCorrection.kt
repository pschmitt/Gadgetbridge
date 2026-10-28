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

import nodomain.freeyourgadget.gadgetbridge.model.ActivityPoint
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrack
import nodomain.freeyourgadget.gadgetbridge.model.GPSCoordinate
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max

/** Elevation in meters at a position, or null when nothing covers it. */
fun interface ElevationLookup {
    fun elevationAt(latitude: Double, longitude: Double): Double?
}

/**
 * Replaces a track's altitudes with offline elevation model values.
 *
 * Every point with a position must be covered, or the whole track keeps the
 * altitudes it came with: a profile half from the tiles and half from a
 * drifting barometer would be worse than either. A point without a position
 * cannot be looked up, so its altitude is dropped rather than mixing the
 * sensor the correction replaces into the corrected profile.
 *
 * On steep terrain the horizontal error of a GPS fix becomes a large altitude
 * error: a few meters to the side of a slope is a different height. Each point
 * is therefore also looked up at its own horizontal error to the north, south,
 * east and west, and when the typical spread is too large the model is not
 * trusted over the device.
 */
object ElevationCorrection {

    /** Horizontal error assumed for a fix that reports none. */
    const val DEFAULT_HORIZONTAL_ERROR_METERS = 10.0

    /** Median altitude spread within the horizontal error above which the terrain is too steep. */
    const val MAX_SLOPE_SPREAD_METERS = 15.0

    private const val METERS_PER_DEGREE_LATITUDE = 111_320.0

    enum class Outcome {
        /** Every positioned point took the model's altitude. */
        CORRECTED,

        /** No point has a position, so there is nothing to look up. */
        NO_POSITIONS,

        /** At least one positioned point lies outside the imported tiles or on a void sample. */
        NOT_COVERED,

        /** The terrain changes too much within the horizontal error of the fixes. */
        TOO_STEEP,
    }

    @JvmStatic
    fun correct(track: ActivityTrack, lookup: ElevationLookup): Outcome = correct(track.allPoints, lookup)

    /** Corrects the points in place and says what happened; anything but [Outcome.CORRECTED] leaves them untouched. */
    @JvmStatic
    fun correct(points: List<ActivityPoint>, lookup: ElevationLookup): Outcome {
        val positioned = points.filter { it.hasPosition() }
        if (positioned.isEmpty()) {
            return Outcome.NO_POSITIONS
        }

        val altitudes = DoubleArray(positioned.size)
        val spreads = DoubleArray(positioned.size)
        for ((index, point) in positioned.withIndex()) {
            val location = point.location!!
            val altitude = lookup.elevationAt(location.latitude, location.longitude) ?: return Outcome.NOT_COVERED
            altitudes[index] = altitude
            spreads[index] = slopeSpread(location, altitude, lookup) ?: return Outcome.NOT_COVERED
        }
        spreads.sort()
        if (spreads[spreads.size / 2] > MAX_SLOPE_SPREAD_METERS) {
            return Outcome.TOO_STEEP
        }

        for ((index, point) in positioned.withIndex()) {
            val location = point.location!!
            // The vertical accuracy described the altitude being replaced
            point.location = GPSCoordinate(
                location.longitude,
                location.latitude,
                altitudes[index],
                location.hdop,
                GPSCoordinate.UNKNOWN_DOP,
                location.pdop
            )
            point.altitude = altitudes[index]
        }
        for (point in points) {
            if (!point.hasPosition()) {
                point.altitude = GPSCoordinate.UNKNOWN_ALTITUDE
                // getAltitude() falls back to the location, so a placeholder fix loses its altitude too
                point.location?.let { location ->
                    point.location = GPSCoordinate(
                        location.longitude,
                        location.latitude,
                        GPSCoordinate.UNKNOWN_ALTITUDE,
                        location.hdop,
                        location.vdop,
                        location.pdop
                    )
                }
            }
        }
        return Outcome.CORRECTED
    }

    /**
     * How much the model's altitude changes within the fix's horizontal error around a point,
     * or null when a shifted position is not covered.
     */
    private fun slopeSpread(location: GPSCoordinate, altitude: Double, lookup: ElevationLookup): Double? {
        // Garmin stores gps_accuracy, in meters, as the hdop
        val error = if (location.hasHdop() && location.hdop > 0) location.hdop else DEFAULT_HORIZONTAL_ERROR_METERS
        val latitudeStep = error / METERS_PER_DEGREE_LATITUDE
        val longitudeStep = error / (METERS_PER_DEGREE_LATITUDE * max(cos(Math.toRadians(location.latitude)), 0.01))
        var spread = 0.0
        for ((latitude, longitude) in listOf(
            location.latitude + latitudeStep to location.longitude,
            location.latitude - latitudeStep to location.longitude,
            location.latitude to location.longitude + longitudeStep,
            location.latitude to location.longitude - longitudeStep,
        )) {
            val shifted = lookup.elevationAt(latitude, longitude) ?: return null
            spread = max(spread, abs(shifted - altitude))
        }
        return spread
    }

    /** Total ascent and descent of the track as it now stands. */
    @JvmStatic
    fun elevationChange(track: ActivityTrack): ElevationGain.Change = elevationChange(track.allPoints)

    /** Total ascent and descent of the points as they now stand. */
    @JvmStatic
    fun elevationChange(points: List<ActivityPoint>): ElevationGain.Change =
        ElevationGain.fromAltitudes(points.map { it.knownAltitude() })

    /** Lowest, highest and mean altitude, or null when no point has one. */
    @JvmStatic
    fun altitudeRange(points: List<ActivityPoint>): DoubleArray? {
        val altitudes = points.mapNotNull { it.knownAltitude() }
        if (altitudes.isEmpty()) {
            return null
        }
        return doubleArrayOf(altitudes.min(), altitudes.max(), altitudes.average())
    }

    private fun ActivityPoint.knownAltitude(): Double? = altitude.takeIf { it > GPSCoordinate.UNKNOWN_ALTITUDE }

    /** A fix at exactly 0,0 is a placeholder some devices write before they have one. */
    private fun ActivityPoint.hasPosition(): Boolean {
        val location = location ?: return false
        return location.latitude != 0.0 || location.longitude != 0.0
    }
}
