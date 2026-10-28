package nodomain.freeyourgadget.gadgetbridge.util.elevation

import nodomain.freeyourgadget.gadgetbridge.model.ActivityPoint
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrack
import nodomain.freeyourgadget.gadgetbridge.model.GPSCoordinate
import nodomain.freeyourgadget.gadgetbridge.util.elevation.ElevationCorrection.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/** Tile altitudes replace the track's only when every positioned point is covered; the gain follows. */
class ElevationCorrectionTest {

    private val start = Date(1_780_000_000_000L)

    private fun point(index: Int, altitude: Double, position: Boolean = true) = ActivityPoint(Date(start.time + index * 1000L)).apply {
        if (position) {
            location = GPSCoordinate(24.0 + index * 0.001, 59.0 + index * 0.001, altitude, 4.0, 6.0, 7.0)
        } else {
            this.altitude = altitude
        }
    }

    private fun track(vararg points: ActivityPoint) = ActivityTrack().apply { addTrackPoints(points.toList()) }

    /** 100 m per 0.001 degree of latitude above 59 N. */
    private val slope = ElevationLookup { latitude, _ -> (latitude - 59.0) * 100_000 }

    @Test
    fun everyCoveredPoint_takesTheTileAltitude() {
        val track = track(point(0, 10.0), point(1, 12.0), point(2, 10.0))

        assertEquals(Outcome.CORRECTED, ElevationCorrection.correct(track, slope))

        val points = track.allPoints
        assertEquals(listOf(0L, 100L, 200L), points.map { Math.round(it.altitude) })
        assertEquals(listOf(0L, 100L, 200L), points.map { Math.round(it.location!!.altitude) })
        assertEquals(listOf(0L, 1000L, 2000L), points.map { it.time.time - start.time })
    }

    @Test
    fun theVerticalAccuracy_goesWithTheReplacedAltitude() {
        val track = track(point(0, 10.0))

        ElevationCorrection.correct(track) { _, _ -> 5.0 }

        val location = track.allPoints.single().location!!
        assertFalse(location.hasVdop())
        assertEquals(4.0, location.hdop, 0.0)
        assertEquals(7.0, location.pdop, 0.0)
    }

    @Test
    fun oneUncoveredPoint_leavesTheWholeTrackUntouched() {
        val track = track(point(0, 10.0), point(1, 12.0), point(2, 14.0))

        assertEquals(Outcome.NOT_COVERED, ElevationCorrection.correct(track) { latitude, _ -> if (latitude > 59.0015) null else 5.0 })

        assertEquals(listOf(10.0, 12.0, 14.0), track.allPoints.map { it.altitude })
    }

    @Test
    fun aTrackWithoutPositions_isNotCorrected() {
        val indoor = track(point(0, 10.0, position = false), point(1, 12.0, position = false))

        assertEquals(Outcome.NO_POSITIONS, ElevationCorrection.correct(indoor, slope))
        assertEquals(Outcome.NO_POSITIONS, ElevationCorrection.correct(track(), slope))
        assertEquals(listOf(10.0, 12.0), indoor.allPoints.map { it.altitude })
    }

    @Test
    fun pointsWithoutAPosition_loseTheirSensorAltitude() {
        val placeholder = ActivityPoint(Date(start.time + 3000L)).apply { location = GPSCoordinate(0.0, 0.0, 480.0) }
        val track = track(point(0, 10.0), point(1, 480.0, position = false), point(2, 10.0), placeholder)

        assertEquals(Outcome.CORRECTED, ElevationCorrection.correct(track, slope))

        val points = track.allPoints
        assertEquals(0L, Math.round(points[0].altitude))
        assertEquals(GPSCoordinate.UNKNOWN_ALTITUDE, points[1].altitude, 0.0)
        assertEquals(200L, Math.round(points[2].altitude))
        assertEquals("a 0,0 placeholder is not a position", GPSCoordinate.UNKNOWN_ALTITUDE, points[3].altitude, 0.0)
    }

    @Test
    fun theElevationChange_followsTheCorrectedProfile() {
        val track = track(point(0, 0.0), point(1, 0.0))

        ElevationCorrection.correct(track) { latitude, _ -> if (latitude < 59.0005) 100.0 else 130.0 }

        val change = ElevationCorrection.elevationChange(track)
        assertEquals(30.0, change.gain, 1e-9)
        assertEquals(0.0, change.loss, 1e-9)
    }

    // The slope rule: the model is trusted only while the ground within the fix's horizontal error is close to level.

    /** A plane rising [metersPerMeter] northwards from 59 N. */
    private fun plane(metersPerMeter: Double) = ElevationLookup { latitude, _ -> (latitude - 59.0) * 111_320 * metersPerMeter }

    @Test
    fun aGentleSlope_isCorrected() {
        // The fixes report 4 m of accuracy; on a 1-in-2 slope that moves the model by 2 m
        val track = track(point(0, 10.0), point(1, 12.0), point(2, 14.0))

        assertEquals(Outcome.CORRECTED, ElevationCorrection.correct(track, plane(0.5)))
    }

    @Test
    fun aSteepSlope_keepsTheDevicesAltitude() {
        // The fixes report 4 m of accuracy; on a 5-in-1 slope that moves the model by 20 m, more than the 15 m allowed
        val track = track(point(0, 10.0), point(1, 12.0), point(2, 14.0))

        assertEquals(Outcome.TOO_STEEP, ElevationCorrection.correct(track, plane(5.0)))
        assertEquals(listOf(10.0, 12.0, 14.0), track.allPoints.map { it.altitude })
    }

    @Test
    fun aPoorFix_widensTheErrorItIsJudgedWith() {
        // The same 1-in-2 slope, but the fixes report 40 m of accuracy: 20 m of spread
        val track = track(point(0, 10.0), point(1, 12.0), point(2, 14.0))
        for (p in track.allPoints) {
            val l = p.location!!
            p.location = GPSCoordinate(l.longitude, l.latitude, l.altitude, 40.0, l.vdop, l.pdop)
        }

        assertEquals(Outcome.TOO_STEEP, ElevationCorrection.correct(track, plane(0.5)))
    }

    @Test
    fun theSlopeIsJudgedByTheMedianPoint_notTheWorstOrTheAverage() {
        // Two points on the flat and one on a cliff spreading 80 m, enough to pull the average over the limit: the cliff is the minority
        val cliff = ElevationLookup { latitude, _ -> if (latitude > 59.0015) (latitude - 59.0) * 111_320 * 20.0 else 100.0 }
        val track = track(point(0, 10.0), point(1, 12.0), point(2, 14.0))

        assertEquals(Outcome.CORRECTED, ElevationCorrection.correct(track, cliff))
    }

    @Test
    fun aShiftedPositionOffTheTiles_countsAsNotCovered() {
        // The track sits on the very edge of coverage
        val edge = ElevationLookup { latitude, _ -> if (latitude >= 59.0) 100.0 else null }
        val track = track(point(0, 10.0))

        assertEquals(Outcome.NOT_COVERED, ElevationCorrection.correct(track, edge))
    }
}

