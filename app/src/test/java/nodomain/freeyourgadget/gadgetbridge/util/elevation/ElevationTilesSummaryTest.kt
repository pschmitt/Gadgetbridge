package nodomain.freeyourgadget.gadgetbridge.util.elevation

import nodomain.freeyourgadget.gadgetbridge.model.ActivityPoint
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries
import nodomain.freeyourgadget.gadgetbridge.model.GPSCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Date

/** The corrected profile's figures replace the device's, under whichever keys the device used. */
class ElevationTilesSummaryTest {

    private fun points(vararg altitudes: Double) = altitudes.mapIndexed { index, altitude ->
        ActivityPoint(Date(index * 1000L)).apply { location = GPSCoordinate(7.5, 45.5, altitude) }
    }

    private fun ActivitySummaryData.meters(key: String) = getNumber(key, -1).toDouble()

    @Test
    fun theDevicesAscentKeys_areOverwritten() {
        val data = ActivitySummaryData().apply {
            add(ActivitySummaryEntries.TOTAL_ASCENT, 999, ActivitySummaryEntries.UNIT_METERS)
            add(ActivitySummaryEntries.ASCENT_METERS, 999, ActivitySummaryEntries.UNIT_METERS)
            add(ActivitySummaryEntries.TOTAL_DESCENT, 888, ActivitySummaryEntries.UNIT_METERS)
        }

        ElevationTiles.applyToSummary(data, points(100.0, 130.0))

        assertEquals(30.0, data.meters(ActivitySummaryEntries.TOTAL_ASCENT), 0.0)
        assertEquals(30.0, data.meters(ActivitySummaryEntries.ASCENT_METERS), 0.0)
        assertEquals("a flat descent is stored as 0, not left at the device's figure", 0.0, data.meters(ActivitySummaryEntries.TOTAL_DESCENT), 0.0)
        assertFalse(data.has(ActivitySummaryEntries.DESCENT_METERS))
    }

    @Test
    fun withoutDeviceAscent_theTotalsAreAdded() {
        val data = ActivitySummaryData()

        ElevationTiles.applyToSummary(data, points(130.0, 100.0))

        assertEquals(0.0, data.meters(ActivitySummaryEntries.TOTAL_ASCENT), 0.0)
        assertEquals(30.0, data.meters(ActivitySummaryEntries.TOTAL_DESCENT), 0.0)
    }

    @Test
    fun theAltitudeRange_followsTheCorrectedPoints() {
        val data = ActivitySummaryData().apply {
            add(ActivitySummaryEntries.ALTITUDE_MAX, 4000, ActivitySummaryEntries.UNIT_METERS)
        }

        ElevationTiles.applyToSummary(data, points(100.0, 130.0, 160.0))

        assertEquals(100.0, data.meters(ActivitySummaryEntries.ALTITUDE_MIN), 0.0)
        assertEquals(160.0, data.meters(ActivitySummaryEntries.ALTITUDE_MAX), 0.0)
        assertEquals(130.0, data.meters(ActivitySummaryEntries.ALTITUDE_AVG), 0.0)
    }
}
