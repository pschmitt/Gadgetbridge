package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit

import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.devices.GarminEstimatedSleepStageSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.GarminSleepStageSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.GarminEventSampleProvider
import nodomain.freeyourgadget.gadgetbridge.entities.GarminEventSample
import nodomain.freeyourgadget.gadgetbridge.entities.GarminSleepStageSample
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.Event
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.EventType
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.SleepLevel
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import nodomain.freeyourgadget.gadgetbridge.util.sleep.EstimatedSleepSession
import nodomain.freeyourgadget.gadgetbridge.util.sleep.SleepMinute
import nodomain.freeyourgadget.gadgetbridge.util.sleep.SleepMinuteKind
import nodomain.freeyourgadget.gadgetbridge.util.sleep.SleepStageEstimator
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.sin

/**
 * A watch that does not stage sleep cuts its raw stream into a new file at midnight and at every
 * sync. Whatever the cut and the import order, the stored sleep is what one estimate of the whole
 * stream gives: every episode, none twice, none half.
 */
class RawSleepNightsTest : TestBase() {

    private val offset: ZoneOffset = ZoneOffset.ofHours(2)
    private val bedtime: Instant = LocalDate.of(2024, 3, 4).atTime(22, 30).toInstant(offset)
    private val march5: LocalDate = LocalDate.of(2024, 3, 5)
    private var nextAddress = 0x30

    private fun device(): GBDevice =
        createDummyGDevice("00:00:00:00:%02X".format(nextAddress++)).also { DBHelper.getDevice(it, daoSession) }

    private fun nightHeartRate(index: Int): Float {
        val settling = if (index < 60) (60 - index) / 8f else 0f
        return 52f + settling + 4f * sin(2 * PI * index / 90.0).toFloat() + ((index * 7) % 3 - 1) * 0.5f
    }

    /** Thirty restless minutes, [asleep] minutes asleep, then an hour of restlessness, from [start]. */
    private fun night(start: Instant, asleep: Int = 450): List<SleepMinute> =
        (0 until 30 + asleep + 60).map { i ->
            val time = start.plusSeconds(60L * i)
            if (i < 30 || i >= 30 + asleep) {
                SleepMinute(time, SleepMinuteKind.RAW, movement = 8f + (i * 5) % 8, heartRate = 64f)
            } else {
                SleepMinute(time, SleepMinuteKind.RAW, movement = 0f, heartRate = nightHeartRate(i - 30))
            }
        }

    /** Restless minutes, from [start]. */
    private fun restless(start: Instant, count: Int): List<SleepMinute> =
        (0 until count).map { i -> SleepMinute(start.plusSeconds(60L * i), SleepMinuteKind.RAW, movement = 6f + i % 5, heartRate = 80f) }

    /** Cuts minutes into files at local midnight and after every [syncEvery] minutes. */
    private fun files(minutes: List<SleepMinute>, syncEvery: Int): List<List<SleepMinute>> {
        val files = mutableListOf(mutableListOf<SleepMinute>())
        for (minute in minutes) {
            val local = minute.time.atOffset(offset).toLocalTime()
            if (files.last().size >= syncEvery || (local == LocalTime.MIDNIGHT && files.last().isNotEmpty())) {
                files += mutableListOf<SleepMinute>()
            }
            files.last() += minute
        }
        return files
    }

    private fun import(device: GBDevice, minutes: List<SleepMinute>) =
        RawSleepNights.importMinutes(device, daoSession, null, minutes)

    private fun sleepEvents(device: GBDevice): List<GarminEventSample> =
        GarminEventSampleProvider(device, daoSession).getSleepEvents(0, Long.MAX_VALUE).sortedBy { it.timestamp }

    private fun stageCount(device: GBDevice): Int =
        GarminEstimatedSleepStageSampleProvider(device, daoSession).getAllSamples(0, Long.MAX_VALUE).size

    /** The device holds exactly [expected]: a START and a STOP per session, and their stages. */
    private fun assertStored(expected: List<EstimatedSleepSession>, device: GBDevice) {
        assertEquals(
            expected.flatMap { listOf(it.onset.toEpochMilli() to EventType.START.num, it.end.toEpochMilli() to EventType.STOP.num) },
            sleepEvents(device).map { it.timestamp to it.eventType },
        )
        assertEquals(expected.sumOf { it.stages.size }, stageCount(device))
    }

    @Test
    fun aNightCutIntoFiles_isEstimatedOnceFromAllOfThem() {
        val device = device()
        val minutes = night(bedtime)
        val cut = files(minutes, syncEvery = 47)
        assertTrue("the night spans several files", cut.size > 5)

        for (file in cut.shuffled(kotlin.random.Random(3))) {
            import(device, file)
        }

        val expected = SleepStageEstimator.estimateAll(minutes)
        assertEquals(1, expected.size)
        assertStored(expected, device)
    }

    @Test
    fun reimportingTheFiles_changesNothing() {
        val device = device()
        val cut = files(night(bedtime), syncEvery = 60)
        cut.forEach { import(device, it) }
        val events = sleepEvents(device).map { it.timestamp to it.eventType }
        val stages = stageCount(device)
        assertEquals(2, events.size)

        cut.forEach { import(device, it) }

        assertEquals(events, sleepEvents(device).map { it.timestamp to it.eventType })
        assertEquals(stages, stageCount(device))
    }

    @Test
    fun aDaytimeFile_showsNoSleep() {
        val device = device()
        // Two restless hours in the morning: under the old fallback, light sleep throughout
        import(device, restless(march5.atTime(10, 0).toInstant(offset), 120))

        assertTrue(sleepEvents(device).isEmpty())
        assertEquals(0, stageCount(device))
    }

    @Test
    fun laterFilesOfTheNight_replaceTheEarlierEstimate() {
        val device = device()
        val minutes = night(bedtime)
        // Synced at 03:30 local, then again after waking
        val split = minutes.indexOfFirst { it.time >= march5.atTime(3, 30).toInstant(offset) }
        import(device, minutes.subList(0, split))
        val early = sleepEvents(device).maxOf { it.timestamp }

        import(device, minutes.subList(split, minutes.size))

        val expected = SleepStageEstimator.estimateAll(minutes)
        assertStored(expected, device)
        assertTrue(expected.single().end.toEpochMilli() > early)
    }

    @Test
    fun lightSleepFakedByAnEarlierImport_isRemoved() {
        val device = device()
        val events = GarminEventSampleProvider(device, daoSession)
        val fakedStart = bedtime.minusSeconds(3600)
        events.persistSamples(listOf(
            sleepEvent(fakedStart, EventType.START, -1L),
            sleepEvent(bedtime.plusSeconds(11 * 3600), EventType.STOP, -1L),
        ), null)

        val minutes = night(bedtime)
        import(device, minutes)

        assertStored(SleepStageEstimator.estimateAll(minutes), device)
    }

    @Test
    fun sleepTheWatchSent_isKept() {
        val device = device()
        val minutes = night(bedtime)
        val estimate = SleepStageEstimator.estimateAll(minutes).single()
        // The watch's own events carry no data when its file leaves the field out
        val watchEvents = listOf(
            sleepEvent(bedtime.plusSeconds(2 * 3600), EventType.START, null),
            sleepEvent(bedtime.plusSeconds(3 * 3600), EventType.STOP, null),
        )
        // One of the watch's own stages ends at the very instant an estimated one does
        val watchStages = listOf(estimate.stages.first().end, bedtime.plusSeconds(3 * 3600)).map { time ->
            GarminSleepStageSample().apply {
                timestamp = time.toEpochMilli()
                stage = SleepLevel.DEEP.num
            }
        }
        GarminEventSampleProvider(device, daoSession).persistSamples(watchEvents, null)
        GarminSleepStageSampleProvider(device, daoSession).persistSamples(watchStages, null)

        import(device, minutes)
        import(device, minutes)

        assertEquals(
            watchEvents.map { it.timestamp to it.eventType },
            sleepEvents(device).filter { it.data == null }.map { it.timestamp to it.eventType },
        )
        assertEquals(
            watchStages.map { it.timestamp to it.stage },
            GarminSleepStageSampleProvider(device, daoSession).getAllSamples(0, Long.MAX_VALUE).map { it.timestamp to it.stage },
        )
        assertEquals(
            listOf(estimate.onset.toEpochMilli(), estimate.end.toEpochMilli()),
            sleepEvents(device).filter { it.data == RawSleepStream.ESTIMATED_SLEEP_DATA }.map { it.timestamp },
        )
        assertEquals(estimate.stages.size, stageCount(device))
    }

    @Test
    fun twoSleepsInADay_areBothStored_whateverTheOrder() {
        // A night to 07:30, then a sleep from 13:00 to 16:20: one window of the charts, two sessions
        val minutes = night(bedtime) + night(march5.atTime(12, 30).toInstant(offset), asleep = 200)
        val expected = SleepStageEstimator.estimateAll(minutes)
        assertEquals(2, expected.size)

        for (seed in 1..3) {
            val device = device()
            files(minutes, syncEvery = 90).shuffled(kotlin.random.Random(seed)).forEach { import(device, it) }
            assertStored(expected, device)
        }
    }

    @Test
    fun aSleepAcrossTheEveningCutOff_isOneSession() {
        val device = device()
        // Asleep from 15:00 to 21:00, across the charts' evening cut-off
        val minutes = night(march5.atTime(14, 30).toInstant(offset), asleep = 360)
        files(minutes, syncEvery = 60).forEach { import(device, it) }

        val expected = SleepStageEstimator.estimateAll(minutes)
        val cutOff = march5.atTime(18, 0).toInstant(offset)
        assertTrue(expected.single().onset < cutOff && expected.single().end > cutOff)
        assertStored(expected, device)
    }

    @Test
    fun aLaterUnrelatedFile_doesNotFreezeAHalfNight() {
        val device = device()
        val minutes = night(bedtime)
        val split = minutes.indexOfFirst { it.time >= march5.atTime(2, 0).toInstant(offset) }
        val daytime = restless(march5.plusDays(1).atTime(10, 0).toInstant(offset), 120)

        import(device, minutes.subList(0, split))
        import(device, daytime)
        import(device, minutes.subList(split, minutes.size))

        assertStored(SleepStageEstimator.estimateAll(minutes + daytime), device)
    }

    @Test
    fun aFileStartingAtOnset_importedBeforeItsPredecessor_leavesNoHalfPair() {
        val device = device()
        val before = restless(bedtime.minusSeconds(3600), 90)
        val minutes = night(bedtime)

        import(device, minutes.subList(30, minutes.size))
        import(device, before + minutes.subList(0, 30))

        assertStored(SleepStageEstimator.estimateAll(before + minutes), device)
    }

    @Test
    fun theVenuSqFixture_cutLikeRealFiles_keepsItsThreeNights() {
        val (minutes, fixtureOffset) = fixtureMinutes()
        val expected = SleepStageEstimator.estimateAll(minutes)
        assertEquals(3, expected.size)

        // The corpus of real files from such watches has a median of 18 minutes per file
        val cut = mutableListOf(mutableListOf<SleepMinute>())
        var nextCut = 18
        for (minute in minutes) {
            val local = minute.time.atOffset(fixtureOffset).toLocalTime()
            if (cut.last().size >= nextCut || (local == LocalTime.MIDNIGHT && cut.last().isNotEmpty())) {
                cut += mutableListOf<SleepMinute>()
                nextCut = listOf(3, 18, 47, 120, 240)[cut.size % 5]
            }
            cut.last() += minute
        }

        val inOrder = device()
        cut.forEach { import(inOrder, it) }
        assertStored(expected, inOrder)

        val shuffled = device()
        cut.shuffled(kotlin.random.Random(7)).forEach { import(shuffled, it) }
        assertStored(expected, shuffled)
    }

    private fun sleepEvent(time: Instant, type: EventType, data: Long?) = GarminEventSample().apply {
        timestamp = time.toEpochMilli()
        setEvent(Event.DETECT_SLEEP)
        setEventType(type)
        this.data = data
    }

    private fun fixtureMinutes(): Pair<List<SleepMinute>, ZoneOffset> {
        val text = requireNotNull(javaClass.getResourceAsStream("/garmin/sleep/venu_sq_minutes.json")).use { it.readBytes().decodeToString() }
        val root = JSONObject(text)
        val origin = root.getLong("originEpochSeconds")
        val step = root.getLong("minuteSeconds")
        val rows = root.getJSONArray("minutes")
        var zone = ZoneOffset.UTC
        val minutes = List(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            zone = ZoneOffset.ofTotalSeconds(row.getInt("z"))
            val at = Instant.ofEpochSecond(origin + step * row.getLong("t"))
            when (row.getString("k")) {
                "R" -> SleepMinute(at, SleepMinuteKind.RAW, movement = row.optDouble("mv", 0.0).toFloat(),
                    heartRate = if (row.isNull("hr")) null else row.getDouble("hr").toFloat())
                "A" -> SleepMinute(at, SleepMinuteKind.AWAKE)
                else -> SleepMinute(at, SleepMinuteKind.UNMEASURABLE)
            }
        }
        return minutes to zone
    }
}
