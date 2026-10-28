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
package nodomain.freeyourgadget.gadgetbridge.devices.garmin

import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.devices.GarminEstimatedSleepStageSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.GarminRawSleepSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.GarminSleepStageSampleProvider
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.FileType
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.FitFile
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.FitImporter
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.RawSleepStream
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.RecordData
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.Event
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.EventType
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.SleepLevel
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.AbstractFitSleepDataRaw
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitEvent
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitFileId
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitSleepDataInfo
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitSleepLevel
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import nodomain.freeyourgadget.gadgetbridge.util.sleep.SleepMinute
import nodomain.freeyourgadget.gadgetbridge.util.sleep.SleepMinuteKind
import nodomain.freeyourgadget.gadgetbridge.util.sleep.SleepStageEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Sleep files as a watch that does not stage sleep writes them (#4048), built in memory, run
 * through the importer, and read back the way the charts read them. This covers what the unit
 * tests of the parts cannot: the raw row decoder, the stream assembly, the file type switch, and
 * the overlay of the estimate onto activity samples.
 */
class GarminRawSleepFitImportTest : TestBase() {

    private val offset: ZoneOffset = ZoneOffset.ofHours(2)
    private val bedtime: Instant = LocalDate.of(2024, 3, 4).atTime(22, 30).toInstant(offset)
    private val sleepKinds =
        setOf(ActivityKind.LIGHT_SLEEP, ActivityKind.DEEP_SLEEP, ActivityKind.REM_SLEEP, ActivityKind.AWAKE_SLEEP)
    private var nextAddress = 0x50
    private var nextFile = 0

    private fun device(): GBDevice =
        createDummyGDevice("00:00:00:00:%02X".format(nextAddress++)).also { DBHelper.getDevice(it, daoSession) }

    /** A night's heart rate in quarter beats, which a half-precision float carries exactly. */
    private fun nightHeartRate(index: Int): Float {
        val settling = if (index < 60) (60 - index) / 8f else 0f
        val raw = 52f + settling + 4f * sin(2 * PI * index / 90.0).toFloat() + ((index * 7) % 3 - 1) * 0.5f
        return (raw * 4).roundToInt() / 4f
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

    /** Cuts minutes into files at local midnight and after every [syncEvery] minutes. */
    @Suppress("SameParameterValue")
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

    /** IEEE 754 half precision, truncating; the values used here are exact. */
    private fun halfOf(value: Float): Int {
        val bits = java.lang.Float.floatToIntBits(value)
        val sign = (bits ushr 16) and 0x8000
        if (value == 0f) return sign
        val exponent = ((bits ushr 23) and 0xFF) - 127 + 15
        require(exponent in 1..30) { "$value is out of the half range used here" }
        return sign or (exponent shl 10) or ((bits and 0x7FFFFF) ushr 13)
    }

    /** The twenty bytes of a raw row: ten little-endian halves, movement eighth, heart rate ninth. */
    private fun rawRow(movement: Float, heartRate: Float?): Array<Any?> {
        val halves = IntArray(10)
        halves[0] = halfOf(1f)
        halves[8] = halfOf(movement)
        halves[9] = halfOf(heartRate ?: 0f)
        return halves.flatMap { listOf(it and 0xFF, (it ushr 8) and 0xFF) }.toTypedArray()
    }

    /**
     * One sleep file: a file id, the sleep data info at the first minute, a row per minute, and
     * any [stages] and sleep [events] a staging watch would add.
     */
    private fun sleepFile(
        minutes: List<SleepMinute>,
        stages: List<Pair<Instant, SleepLevel>> = emptyList(),
        events: List<Pair<Instant, EventType>> = emptyList(),
    ): File {
        val start = minutes.first().time.epochSecond
        val records = mutableListOf<RecordData>()
        records += FitFileId.Builder()
            .setType(FileType.FILETYPE.SLEEP)
            .setManufacturer(1)
            .setProduct(2)
            .setSerialNumber(3L)
            .setTimeCreated(start)
            .build(0)
        records += FitSleepDataInfo.Builder()
            .setTimestamp(start)
            .setSampleLength(60)
            .setLocalTimestamp(start + offset.totalSeconds)
            .build(1)
        for ((time, kind, movement, heartRate) in minutes) {
            records += when (kind) {
                SleepMinuteKind.RAW -> AbstractFitSleepDataRaw.Builder()
                    .setFieldByNumber(0, *rawRow(movement, heartRate)).build(2)

                SleepMinuteKind.AWAKE -> FitSleepLevel.Builder().setTimestamp(time.epochSecond)
                    .setSleepLevel(SleepLevel.AWAKE).build(3)

                SleepMinuteKind.UNMEASURABLE -> FitSleepLevel.Builder().setTimestamp(time.epochSecond)
                    .setSleepLevel(SleepLevel.UNMEASURABLE).build(3)
            }
        }
        for ((time, level) in stages) {
            records += FitSleepLevel.Builder().setTimestamp(time.epochSecond).setSleepLevel(level).build(3)
        }
        for ((time, type) in events) {
            records += FitEvent.Builder().setTimestamp(time.epochSecond).setEvent(Event.DETECT_SLEEP).setEventType(type)
                .build(4)
        }
        val file = File(app.cacheDir, "sleep-${nextFile++}.fit")
        file.writeBytes(FitFile(records).outgoingMessage)
        return file
    }

    private fun estimatedEvents(device: GBDevice): List<Pair<Long, Int>> =
        GarminEventSampleProvider(device, daoSession).getSleepEvents(0, Long.MAX_VALUE)
            .filter { it.data == RawSleepStream.ESTIMATED_SLEEP_DATA }
            .sortedBy { it.timestamp }
            .map { it.timestamp to (it.eventType ?: -1) }

    /** The kind the charts show at [time], read over the whole night as a chart would. */
    private fun kindAt(device: GBDevice, time: Instant): ActivityKind {
        val from = bedtime.minusSeconds(3600).epochSecond.toInt()
        val to = bedtime.plusSeconds(12 * 3600).epochSecond.toInt()
        val samples = GarminActivitySampleProvider(device, daoSession).getAllActivitySamples(from, to)
        return samples.first { it.timestamp.toLong() == time.epochSecond }.kind
    }

    @Test
    fun aNightCutIntoFiles_importedInAnyOrder_readsBackAsTheEstimatedSleep() {
        val device = device()
        val minutes = night(bedtime)
        val cut = files(minutes, syncEvery = 47).map { sleepFile(it) }
        assertTrue("the night spans several files", cut.size > 5)

        val importer = FitImporter(app, device)
        for (file in cut.shuffled(kotlin.random.Random(5))) {
            importer.importFile(file, false)
        }

        val expected = SleepStageEstimator.estimateAll(minutes).single()
        assertEquals(
            listOf(
                expected.onset.toEpochMilli() to EventType.START.num,
                expected.end.toEpochMilli() to EventType.STOP.num
            ),
            estimatedEvents(device),
        )
        assertEquals(
            expected.stages.size,
            GarminEstimatedSleepStageSampleProvider(device, daoSession).getAllSamples(0, Long.MAX_VALUE).size
        )
        assertTrue(
            "the watch's own stage table is left alone",
            GarminSleepStageSampleProvider(device, daoSession).getAllSamples(0, Long.MAX_VALUE).isEmpty()
        )
        assertEquals(
            minutes.size,
            GarminRawSleepSampleProvider(device, daoSession).getAllSamples(0, Long.MAX_VALUE).size
        )

        // What the charts read: sleep inside the session, none before or after it
        assertFalse(kindAt(device, bedtime) in sleepKinds)
        assertTrue(kindAt(device, expected.onset.plusSeconds(3600)) in sleepKinds)
        assertTrue(kindAt(device, expected.end.minusSeconds(60)) in sleepKinds)
        assertFalse(kindAt(device, expected.end.plusSeconds(30 * 60)) in sleepKinds)
    }

    @Test
    fun aFileWithTheWatchsOwnStages_keepsThemAndIsNotEstimated() {
        val device = device()
        val minutes = night(bedtime)
        val stages = listOf(
            bedtime.plusSeconds(60 * 60) to SleepLevel.LIGHT,
            bedtime.plusSeconds(120 * 60) to SleepLevel.DEEP,
            bedtime.plusSeconds(480 * 60) to SleepLevel.AWAKE,
        )

        val events = listOf(
            bedtime to EventType.START,
            bedtime.plusSeconds(480 * 60) to EventType.STOP,
        )

        FitImporter(app, device).importFile(sleepFile(minutes, stages, events), false)

        assertEquals(
            stages.map { it.first.toEpochMilli() },
            GarminSleepStageSampleProvider(device, daoSession).getAllSamples(0, Long.MAX_VALUE).map { it.timestamp })
        assertTrue("no estimate next to real stages", estimatedEvents(device).isEmpty())
        assertEquals(
            "raw minutes of a staging watch are not kept",
            0,
            GarminRawSleepSampleProvider(device, daoSession).getAllSamples(0, Long.MAX_VALUE).size
        )
        assertEquals(ActivityKind.DEEP_SLEEP, kindAt(device, bedtime.plusSeconds(90 * 60)))
    }
}
