package nodomain.freeyourgadget.gadgetbridge.util.elevation

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivityPoint
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrack
import nodomain.freeyourgadget.gadgetbridge.model.GPSCoordinate
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Date

/** A track is corrected only for a device whose switch is on, for an activity on the ground, under an imported tile. */
class ElevationTilesTest : TestBase() {

    private lateinit var watch: GBDevice
    private lateinit var otherWatch: GBDevice

    @Before
    fun resetTiles() {
        ElevationTiles.resetForTests()
        ElevationTiles.library(getContext()).scan().forEach { ElevationTiles.library(getContext()).deleteTile(it.key) }
        watch = createDummyGDevice("00:00:00:00:51")
        otherWatch = createDummyGDevice("00:00:00:00:52")
        // Removed rather than switched off, so the tests see the default
        for (device in listOf(watch, otherWatch)) {
            GBApplication.getDeviceSpecificSharedPrefs(device.address).edit()
                .remove(DeviceSettingsPreferenceConst.PREF_ELEVATION_CORRECTION).commit()
        }
    }

    @After
    fun cleanUp() {
        ElevationTiles.resetForTests()
        ElevationTiles.library(getContext()).scan().forEach { ElevationTiles.library(getContext()).deleteTile(it.key) }
    }

    private fun setEnabled(device: GBDevice, enabled: Boolean) {
        GBApplication.getDeviceSpecificSharedPrefs(device.address).edit()
            .putBoolean(DeviceSettingsPreferenceConst.PREF_ELEVATION_CORRECTION, enabled).commit()
    }

    private fun importFlatTile(value: Int) {
        val resolution = HgtResolution.THREE_ARC_SECOND
        val buffer = ByteBuffer.allocate(resolution.byteSize.toInt()).order(ByteOrder.BIG_ENDIAN)
        repeat(resolution.samplesPerSide * resolution.samplesPerSide) { buffer.putShort(value.toShort()) }
        ElevationTiles.library(getContext()).importTiles("N45E007.hgt", ByteArrayInputStream(buffer.array()))
        // As the settings screen does after an import
        ElevationTiles.tiles(getContext())
    }

    private fun track() = ActivityTrack().apply {
        addTrackPoint(ActivityPoint(Date(0)).apply { location = GPSCoordinate(7.5, 45.5, 480.0) })
        addTrackPoint(ActivityPoint(Date(1000)).apply { location = GPSCoordinate(7.6, 45.6, 485.0) })
    }

    @Test
    fun offByDefault_theTrackKeepsItsAltitudes() {
        importFlatTile(100)
        val track = track()

        assertFalse(ElevationTiles.isActive(getContext(), watch))
        assertFalse(ElevationTiles.correct(getContext(), watch, ActivityKind.HIKING, track))
        assertEquals(480.0, track.allPoints[0].altitude, 0.0)
    }

    @Test
    fun switchedOnForADevice_itsTracksTakeTheTileAltitudes() {
        importFlatTile(100)
        setEnabled(watch, true)
        val track = track()

        assertTrue(ElevationTiles.correct(getContext(), watch, ActivityKind.HIKING, track))
        assertEquals(listOf(100.0, 100.0), track.allPoints.map { it.altitude })
    }

    @Test
    fun theSwitchIsPerDevice() {
        importFlatTile(100)
        setEnabled(watch, true)
        val other = track()

        assertTrue(ElevationTiles.isActive(getContext(), watch))
        assertFalse(ElevationTiles.isActive(getContext(), otherWatch))
        assertFalse(ElevationTiles.correct(getContext(), otherWatch, ActivityKind.HIKING, other))
        assertEquals(480.0, other.allPoints[0].altitude, 0.0)
    }

    @Test
    fun withoutTiles_nothingIsCorrected() {
        setEnabled(watch, true)
        val track = track()

        assertFalse(ElevationTiles.isActive(getContext(), watch))
        assertFalse(ElevationTiles.correct(getContext(), watch, ActivityKind.HIKING, track))
    }

    @Test
    fun activitiesOffTheGround_keepTheirAltitudes() {
        importFlatTile(100)
        setEnabled(watch, true)

        for (kind in listOf(ActivityKind.SWIMMING_OPENWATER, ActivityKind.SCUBA_DIVING, ActivityKind.PARAGLIDING, ActivityKind.STAIRS, ActivityKind.SAILING)) {
            val track = track()
            assertFalse("$kind", ElevationTiles.correct(getContext(), watch, kind, track))
            assertEquals("$kind", 480.0, track.allPoints[0].altitude, 0.0)
        }
        for (kind in listOf(ActivityKind.HIKING, ActivityKind.CYCLING, ActivityKind.RUNNING)) {
            assertTrue("$kind", ElevationTiles.correct(getContext(), watch, kind, track()))
        }
        assertTrue("an unknown kind is corrected", ElevationTiles.correct(getContext(), watch, null, track()))
    }

    @Test
    fun deletingTheLastTile_stopsCorrecting() {
        importFlatTile(100)
        setEnabled(watch, true)
        ElevationTiles.deleteTile(getContext(), HgtTileKey(45, 7))

        assertFalse(ElevationTiles.isActive(getContext(), watch))
        assertFalse(ElevationTiles.correct(getContext(), watch, ActivityKind.HIKING, track()))
    }
}
