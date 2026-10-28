package nodomain.freeyourgadget.gadgetbridge.devices.garmin

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.export.FitExporter
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivityPoint
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrack
import nodomain.freeyourgadget.gadgetbridge.model.GPSCoordinate
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.FitFile
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import nodomain.freeyourgadget.gadgetbridge.util.elevation.ElevationTiles
import nodomain.freeyourgadget.gadgetbridge.util.elevation.HgtResolution
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Date

/**
 * A Garmin workout whose barometer climbed 49 m over terrain an imported tile says is flat: the
 * stored summary takes the ascent and altitude range of the tile, not of the watch.
 */
class GarminWorkoutParserElevationTest : TestBase() {

    @get:Rule
    val tmp = TemporaryFolder()

    private val start = 1_777_747_544L
    private val points = 100

    private lateinit var watch: GBDevice

    @Before
    fun clearTiles() {
        ElevationTiles.resetForTests()
        val library = ElevationTiles.library(getContext())
        library.scan().forEach { library.deleteTile(it.key) }
        watch = createDummyGDevice("00:00:00:00:53")
        setEnabled(true)
    }

    private fun setEnabled(enabled: Boolean) {
        GBApplication.getDeviceSpecificSharedPrefs(watch.address).edit()
            .putBoolean(DeviceSettingsPreferenceConst.PREF_ELEVATION_CORRECTION, enabled).commit()
    }

    @After
    fun cleanUp() {
        clearTiles()
    }

    private fun importFlatTile(value: Int) {
        val resolution = HgtResolution.THREE_ARC_SECOND
        val buffer = ByteBuffer.allocate(resolution.byteSize.toInt()).order(ByteOrder.BIG_ENDIAN)
        repeat(resolution.samplesPerSide * resolution.samplesPerSide) { buffer.putShort(value.toShort()) }
        ElevationTiles.library(getContext()).importTiles("N45E007.hgt", ByteArrayInputStream(buffer.array()))
        ElevationTiles.tiles(getContext())
    }

    /** Parses the workout the way an import does, from the FIT file of a hike rising 0.5 m a second. */
    private fun parseHike(): ActivitySummaryData {
        val summary = BaseActivitySummary().apply {
            startTime = Date(start * 1000L)
            endTime = Date((start + points - 1) * 1000L)
            activityKind = ActivityKind.HIKING.code
        }
        val track = ActivityTrack().apply {
            setCurrentSegmentInfo(ActivityTrack.SegmentInfo(ActivityTrack.SegmentIntensity.ACTIVE))
            for (i in 0 until points) {
                addTrackPoint(ActivityPoint(Date((start + i) * 1000L)).apply {
                    location = GPSCoordinate(7.5, 45.5 + i * 0.0001, 480.0 + i * 0.5)
                })
            }
        }
        val file = tmp.newFile("hike.fit")
        FitExporter().performExport(track, summary, ActivitySummaryData(), file)

        val parser = GarminWorkoutParser(getContext(), watch)
        FitFile.parseIncoming(file).records.forEach { parser.handleRecord(it) }
        return parser.updateSummary(BaseActivitySummary().apply {
            startTime = summary.startTime
            endTime = summary.endTime
        })
    }

    private fun ActivitySummaryData.meters(key: String) = getNumber(key, -1).toDouble()

    @Test
    fun withoutTiles_theWatchsAscentIsKept() {
        val data = parseHike()

        assertEquals(49.0, data.meters(ActivitySummaryEntries.TOTAL_ASCENT), 2.0)
    }

    @Test
    fun withACoveringTile_theTilesAscentAndAltitudesReplaceTheWatchs() {
        importFlatTile(100)

        val data = parseHike()

        assertEquals(0.0, data.meters(ActivitySummaryEntries.TOTAL_ASCENT), 0.0)
        assertEquals(0.0, data.meters(ActivitySummaryEntries.TOTAL_DESCENT), 0.0)
        assertEquals(100.0, data.meters(ActivitySummaryEntries.ALTITUDE_MIN), 0.0)
        assertEquals(100.0, data.meters(ActivitySummaryEntries.ALTITUDE_MAX), 0.0)
    }

    @Test
    fun withTheDevicesSwitchOff_theWatchsAscentIsKept() {
        importFlatTile(100)
        setEnabled(false)

        val data = parseHike()

        assertEquals(49.0, data.meters(ActivitySummaryEntries.TOTAL_ASCENT), 2.0)
    }
}

