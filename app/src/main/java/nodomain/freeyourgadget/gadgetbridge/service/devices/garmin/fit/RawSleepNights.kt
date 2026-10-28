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
package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit

import android.content.Context
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.devices.GarminEstimatedSleepStageSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.GarminRawSleepSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.GarminEventSampleProvider
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.entities.GarminEstimatedSleepStageSampleDao
import nodomain.freeyourgadget.gadgetbridge.entities.GarminEventSample
import nodomain.freeyourgadget.gadgetbridge.entities.GarminEventSampleDao
import nodomain.freeyourgadget.gadgetbridge.entities.GarminRawSleepSample
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.Event
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.EventType
import nodomain.freeyourgadget.gadgetbridge.util.sleep.SleepMinute
import nodomain.freeyourgadget.gadgetbridge.util.sleep.SleepMinuteKind
import nodomain.freeyourgadget.gadgetbridge.util.sleep.SleepStageEstimator
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Sleep of watches that do not stage it (#4048), estimated per sleep episode.
 *
 * Such a watch writes its raw sleep stream around the clock, cut into a new file at
 * midnight and at every sync, so a night spans many files. Each file's minutes are
 * stored, and the stretch of the stored stream around them is estimated again: every
 * episode between two long breaks is stored as a sleep session, like the sessions a
 * staging watch reports, and the charts cut the days as they do for any other device.
 *
 * Nothing here depends on the calendar or on the order the files arrive in. The stretch
 * fetched around a file reaches a day each way, so it always holds the breaks on both
 * sides of any sleep the file touches, as long as no episode runs longer than about
 * seventeen hours without a break.
 */
object RawSleepNights {
    private val LOG = LoggerFactory.getLogger(RawSleepNights::class.java)

    private const val REACH_SECONDS = 24 * 3600L

    /** How far beyond the fetched stretch a stored minute still matters: a break's length. */
    private const val BREAK_MILLIS = 45 * 60_000L

    /**
     * Stores the minutes of one raw sleep file, then estimates again the stretch of the
     * stored stream they belong to. An episode without enough sleep loses any earlier
     * estimate and shows none.
     */
    @JvmStatic
    fun importMinutes(device: GBDevice, session: DaoSession, context: Context?, minutes: List<SleepMinute>) {
        if (minutes.isEmpty()) {
            return
        }
        val rawProvider = GarminRawSleepSampleProvider(device, session)
        rawProvider.persistSamples(minutes.map(::toSample), context)

        val from = minutes.minOf { it.time }.minusSeconds(REACH_SECONDS).toEpochMilli()
        val to = minutes.maxOf { it.time }.plusSeconds(REACH_SECONDS).toEpochMilli()
        val stored = rawProvider.getAllSamples(from, to).mapNotNull(::toMinute)
        val firstStored = stored.minOf { it.time }.toEpochMilli()
        val lastStored = stored.maxOf { it.time }.toEpochMilli()
        // Only a minute within a break's length of an edge matters, and a bounded range is cheap:
        // an open-ended "next sample" would scan every later row of every other device.
        val gapBefore = rawProvider.getAllSamples(firstStored - BREAK_MILLIS, firstStored - 1)
            .maxOfOrNull { it.timestamp }?.let { missingSlots(it, firstStored) }
        val gapAfter = rawProvider.getAllSamples(lastStored + 1, lastStored + BREAK_MILLIS)
            .minOfOrNull { it.timestamp }?.let { missingSlots(lastStored, it) }

        val settled = SleepStageEstimator.estimateSettled(stored, gapBefore, gapAfter)
        if (settled == null) {
            LOG.debug("No settled stretch around {} raw minutes from {}", minutes.size, minutes.first().time)
            return
        }
        removeEstimates(device, session, settled.zoneStart, settled.zoneEnd)
        val eventProvider = GarminEventSampleProvider(device, session)
        val stageProvider = GarminEstimatedSleepStageSampleProvider(device, session)
        eventProvider.deleteFakedSleepEvents(from, to)
        LOG.debug("Settled {} to {} from {} raw minutes: {} sleep episodes", settled.zoneStart, settled.zoneEnd, stored.size, settled.sessions.size)
        for (estimate in settled.sessions) {
            LOG.debug("Estimated sleep {} to {}, {} min asleep", estimate.onset, estimate.end, estimate.sleepMinutes)
            stageProvider.persistSamples(RawSleepStream.stageSamples(estimate), context)
            eventProvider.persistSamples(RawSleepStream.sleepEvents(estimate), context)
        }
    }

    /** Whole minute slots with no stored minute between two timestamps, as the estimator's grid counts them. */
    private fun missingSlots(earlierMillis: Long, laterMillis: Long): Int =
        maxOf(0L, laterMillis / 60_000L - earlierMillis / 60_000L - 1).toInt()

    /**
     * Deletes the estimates that reach into `[start, end)`: every estimated sleep (a START/STOP
     * pair of sleep events marked [RawSleepStream.ESTIMATED_SLEEP_DATA]) overlapping it, with its
     * stages. A pair is taken whole even when one of its events lies outside, so no half pair is
     * ever left behind.
     */
    private fun removeEstimates(device: GBDevice, session: DaoSession, start: Instant, end: Instant) {
        val dbDevice = DBHelper.findDevice(device, session) ?: return
        val dao = session.garminEventSampleDao
        fun estimated() = dao.queryBuilder().where(
            GarminEventSampleDao.Properties.DeviceId.eq(dbDevice.id),
            GarminEventSampleDao.Properties.Event.eq(Event.DETECT_SLEEP.num),
            GarminEventSampleDao.Properties.Data.eq(RawSleepStream.ESTIMATED_SLEEP_DATA),
        )
        val inside = estimated()
            .where(GarminEventSampleDao.Properties.Timestamp.gt(start.toEpochMilli()), GarminEventSampleDao.Properties.Timestamp.lt(end.toEpochMilli()))
            .orderAsc(GarminEventSampleDao.Properties.Timestamp)
            .list()
        val before = estimated().where(GarminEventSampleDao.Properties.Timestamp.le(start.toEpochMilli()))
            .orderDesc(GarminEventSampleDao.Properties.Timestamp).limit(1).list().firstOrNull()
        val after = estimated().where(GarminEventSampleDao.Properties.Timestamp.ge(end.toEpochMilli()))
            .orderAsc(GarminEventSampleDao.Properties.Timestamp).limit(1).list().firstOrNull()

        val startBefore = before?.takeIf { it.isType(EventType.START) }
        val stopAfter = after?.takeIf { it.isType(EventType.STOP) }
        val doomed = inside.toMutableList()
        if (inside.isEmpty()) {
            // A pair enclosing the whole stretch
            if (startBefore != null && stopAfter != null) {
                doomed += startBefore
                doomed += stopAfter
            }
        } else {
            if (startBefore != null && inside.first().isType(EventType.STOP)) doomed += startBefore
            if (stopAfter != null && inside.last().isType(EventType.START)) doomed += stopAfter
        }
        if (doomed.isEmpty()) {
            return
        }
        val low = doomed.minOf { it.timestamp }
        val high = doomed.maxOf { it.timestamp }
        // Stage samples sit at the end of the span they cover, so the first lies after the START.
        session.garminEstimatedSleepStageSampleDao.queryBuilder()
            .where(
                GarminEstimatedSleepStageSampleDao.Properties.DeviceId.eq(dbDevice.id),
                GarminEstimatedSleepStageSampleDao.Properties.Timestamp.gt(low),
                GarminEstimatedSleepStageSampleDao.Properties.Timestamp.le(high),
            )
            .buildDelete()
            .executeDeleteWithoutDetachingEntities()
        estimated()
            .where(GarminEventSampleDao.Properties.Timestamp.between(low, high))
            .buildDelete()
            .executeDeleteWithoutDetachingEntities()
    }

    private fun GarminEventSample.isType(type: EventType): Boolean = eventType == type.num

    private fun toSample(minute: SleepMinute) = GarminRawSleepSample().apply {
        timestamp = minute.time.toEpochMilli()
        kind = minute.kind.code
        if (minute.kind == SleepMinuteKind.RAW) {
            heartRate = minute.heartRate
            movement = minute.movement
        }
    }

    private fun toMinute(sample: GarminRawSleepSample): SleepMinute? {
        val kind = SleepMinuteKind.fromCode(sample.kind) ?: return null
        return SleepMinute(Instant.ofEpochMilli(sample.timestamp), kind, sample.movement ?: 0f, sample.heartRate)
    }
}
