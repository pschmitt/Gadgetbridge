package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit

import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.Event
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.EventType
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.SleepLevel
import nodomain.freeyourgadget.gadgetbridge.util.sleep.EstimatedSleepSession
import nodomain.freeyourgadget.gadgetbridge.util.sleep.EstimatedStage
import nodomain.freeyourgadget.gadgetbridge.util.sleep.EstimatedStageSpan
import nodomain.freeyourgadget.gadgetbridge.util.sleep.SleepMinuteKind
import nodomain.freeyourgadget.gadgetbridge.util.sleep.SleepStageEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.PI
import kotlin.math.sin

class RawSleepStreamTest {

    private val start = 1_700_000_000L

    @Test
    fun rawAndLevelRows_takeConsecutiveSlotsInFileOrder() {
        val stream = RawSleepStream()
        stream.onInfo(start, 60)
        stream.onRaw(52f, 0f)
        stream.onRaw(null, 1.5f)
        stream.onLevel(null, SleepLevel.AWAKE.num)
        stream.onRaw(53f, null)

        val minutes = stream.minutes()

        assertEquals(listOf(0L, 60L, 120L, 180L), minutes.map { it.time.epochSecond - start })
        assertEquals(
            listOf(SleepMinuteKind.RAW, SleepMinuteKind.RAW, SleepMinuteKind.AWAKE, SleepMinuteKind.RAW),
            minutes.map { it.kind },
        )
        assertEquals(52f, minutes[0].heartRate)
        assertNull(minutes[1].heartRate)
        assertEquals(1.5f, minutes[1].movement, 0f)
        assertEquals("a row without movement counts as still", 0f, minutes[3].movement, 0f)
    }

    @Test
    fun aLevelRowWithItsOwnTimestamp_keepsIt() {
        val stream = RawSleepStream()
        stream.onInfo(start, 60)
        stream.onLevel(start + 30, SleepLevel.UNMEASURABLE.num)
        stream.onRaw(52f, 0f)

        assertEquals(listOf(30L, 60L), stream.minutes().map { it.time.epochSecond - start })
        assertEquals(SleepMinuteKind.UNMEASURABLE, stream.minutes()[0].kind)
    }

    @Test
    fun aStagedLevelRow_takesASlotButIsNoMinute() {
        val stream = RawSleepStream()
        stream.onInfo(start, 60)
        stream.onLevel(null, SleepLevel.LIGHT.num)
        stream.onRaw(52f, 0f)

        assertEquals(listOf(60L), stream.minutes().map { it.time.epochSecond - start })
    }

    @Test
    fun rowsBeforeTheInfo_areCountedButNotPlaced() {
        val stream = RawSleepStream()
        stream.onRaw(52f, 0f)
        stream.onLevel(start, SleepLevel.AWAKE.num)
        stream.onInfo(start, 60)
        stream.onRaw(52f, 0f)

        assertEquals(2, stream.rawCount)
        assertEquals(listOf(0L), stream.minutes().map { it.time.epochSecond - start })
    }

    @Test
    fun theSampleLength_spacesTheSlots_andFallsBackToAMinute() {
        val twoMinutes = RawSleepStream().apply { onInfo(start, 120); onRaw(52f, 0f); onRaw(52f, 0f) }
        val unset = RawSleepStream().apply { onInfo(start, null); onRaw(52f, 0f); onRaw(52f, 0f) }
        val invalid = RawSleepStream().apply { onInfo(start, 0); onRaw(52f, 0f); onRaw(52f, 0f) }

        assertEquals(120L, twoMinutes.minutes()[1].time.epochSecond - start)
        assertEquals(60L, unset.minutes()[1].time.epochSecond - start)
        assertEquals(60L, invalid.minutes()[1].time.epochSecond - start)
    }

    @Test
    fun stageSamples_sitAtTheEndOfEachSpan() {
        val t = Instant.ofEpochSecond(start)
        val night = session(
            span(t, 0, 20, EstimatedStage.LIGHT),
            span(t, 20, 50, EstimatedStage.DEEP),
            span(t, 50, 55, EstimatedStage.AWAKE),
            span(t, 55, 60, EstimatedStage.UNKNOWN),
            span(t, 60, 90, EstimatedStage.REM),
        )

        val samples = RawSleepStream.stageSamples(night)

        assertEquals(listOf(20L, 50L, 55L, 60L, 90L), samples.map { (it.timestamp / 1000 - start) / 60 })
        assertEquals(
            listOf(SleepLevel.LIGHT, SleepLevel.DEEP, SleepLevel.AWAKE, SleepLevel.UNMEASURABLE, SleepLevel.REM).map { it.num },
            samples.map { it.stage },
        )
    }

    @Test
    fun sleepEvents_startAtTheOnsetAndStopAtTheEnd() {
        val t = Instant.ofEpochSecond(start)
        val night = session(span(t, 0, 200, EstimatedStage.LIGHT))

        val events = RawSleepStream.sleepEvents(night)

        assertEquals(listOf(night.onset.toEpochMilli(), night.end.toEpochMilli()), events.map { it.timestamp })
        assertTrue(events.all { it.event == Event.DETECT_SLEEP.num })
        assertEquals(listOf(EventType.START.num, EventType.STOP.num), events.map { it.eventType })
        assertTrue("marked as estimated", events.all { it.data == RawSleepStream.ESTIMATED_SLEEP_DATA })
    }

    @Test
    fun aNightThroughTheStream_estimatesAndTilesItsStageSamples() {
        val stream = RawSleepStream()
        stream.onInfo(start, 60)
        repeat(30) { index -> stream.onRaw(64f, 8f + (index * 5) % 8) }
        repeat(450) { index -> stream.onRaw(nightHeartRate(index), 0f) }
        repeat(60) { index -> stream.onRaw(64f, 8f + (index * 5) % 8) }

        val night = SleepStageEstimator.estimate(stream.minutes())
        assertNotNull(night)
        night!!
        val samples = RawSleepStream.stageSamples(night)

        assertEquals(night.end.toEpochMilli(), samples.last().timestamp)
        assertTrue(samples.zipWithNext().all { (a, b) -> a.timestamp < b.timestamp })
        assertTrue(samples.any { it.stage == SleepLevel.DEEP.num })
        assertTrue(samples.any { it.stage == SleepLevel.REM.num })
    }

    private fun nightHeartRate(index: Int): Float {
        val settling = if (index < 60) (60 - index) / 8f else 0f
        return 52f + settling + 4f * sin(2 * PI * index / 90.0).toFloat() + ((index * 7) % 3 - 1) * 0.5f
    }

    private fun span(origin: Instant, fromMinute: Long, toMinute: Long, stage: EstimatedStage) =
        EstimatedStageSpan(origin.plusSeconds(fromMinute * 60), origin.plusSeconds(toMinute * 60), stage)

    private fun session(vararg spans: EstimatedStageSpan) = EstimatedSleepSession(
        onset = spans.first().start,
        end = spans.last().end,
        inProgress = false,
        stages = spans.toList(),
        sleepMinutes = 0,
        awakeMinutes = 0,
        unknownMinutes = 0,
        deepThreshold = 0.6f,
        remThreshold = 0.6f,
    )
}
