package nodomain.freeyourgadget.gadgetbridge.devices.garmin

import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.devices.GarminEstimatedSleepStageSampleProvider
import nodomain.freeyourgadget.gadgetbridge.entities.GarminEventSample
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.RawSleepStream
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.Event
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.EventType
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import nodomain.freeyourgadget.gadgetbridge.util.sleep.EstimatedSleepSession
import nodomain.freeyourgadget.gadgetbridge.util.sleep.EstimatedStage
import nodomain.freeyourgadget.gadgetbridge.util.sleep.EstimatedStageSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * An estimated night, stored the way FitImporter stores it, reads back through the provider the
 * charts use: each stage covers its span, and nothing outside the night is sleep.
 */
class GarminEstimatedSleepTest : TestBase() {

    private val start = Instant.parse("2024-03-04T22:00:00Z")
    private val sleepKinds = setOf(ActivityKind.LIGHT_SLEEP, ActivityKind.DEEP_SLEEP, ActivityKind.REM_SLEEP, ActivityKind.AWAKE_SLEEP)

    private fun at(minute: Long): Instant = start.plusSeconds(minute * 60)

    private fun span(from: Long, to: Long, stage: EstimatedStage) = EstimatedStageSpan(at(from), at(to), stage)

    private fun fakedEvent(minute: Long, type: EventType) = GarminEventSample().apply {
        timestamp = at(minute).toEpochMilli()
        setEvent(Event.DETECT_SLEEP)
        setEventType(type)
        data = -1L
    }

    @Test
    fun anEstimatedNight_replacesTheFakedOneAndShowsItsStages() {
        val device = createDummyGDevice("00:00:00:00:20")
        DBHelper.getDevice(device, daoSession)
        val events = GarminEventSampleProvider(device, daoSession)
        // An earlier import faked light sleep from minute 0 to 180 for this file
        assertTrue(events.persistSamples(listOf(fakedEvent(0, EventType.START), fakedEvent(180, EventType.STOP)), null))

        val night = EstimatedSleepSession(
            onset = at(30),
            end = at(120),
            inProgress = false,
            stages = listOf(
                span(30, 60, EstimatedStage.LIGHT),
                span(60, 90, EstimatedStage.DEEP),
                span(90, 120, EstimatedStage.REM),
            ),
            sleepMinutes = 90,
            awakeMinutes = 0,
            unknownMinutes = 0,
            deepThreshold = 0.6f,
            remThreshold = 0.6f,
        )
        events.deleteFakedSleepEvents(at(0).toEpochMilli(), at(180).toEpochMilli())
        assertTrue(GarminEstimatedSleepStageSampleProvider(device, daoSession).persistSamples(RawSleepStream.stageSamples(night), null))
        assertTrue(events.persistSamples(RawSleepStream.sleepEvents(night), null))

        val samples = GarminActivitySampleProvider(device, daoSession)
            .getAllActivitySamples(at(0).epochSecond.toInt(), at(200).epochSecond.toInt())
        fun kindAt(minute: Long) = samples.single { it.timestamp.toLong() == at(minute).epochSecond }.kind

        assertFalse(kindAt(10) in sleepKinds)
        assertEquals(ActivityKind.LIGHT_SLEEP, kindAt(45))
        assertEquals(ActivityKind.DEEP_SLEEP, kindAt(75))
        assertEquals(ActivityKind.REM_SLEEP, kindAt(105))
        assertFalse("the faked light sleep up to minute 180 is gone", kindAt(150) in sleepKinds)

        // A chart that ends mid-night still reads the stage running past its end
        val partial = GarminActivitySampleProvider(device, daoSession)
            .getAllActivitySamples(at(0).epochSecond.toInt(), at(100).epochSecond.toInt())
        assertEquals(ActivityKind.REM_SLEEP, partial.single { it.timestamp.toLong() == at(95).epochSecond }.kind)
    }
}
