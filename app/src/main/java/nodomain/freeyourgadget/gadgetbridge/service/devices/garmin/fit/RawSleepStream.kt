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

import nodomain.freeyourgadget.gadgetbridge.entities.GarminEstimatedSleepStageSample
import nodomain.freeyourgadget.gadgetbridge.entities.GarminEventSample
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.Event
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.EventType
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.SleepLevel
import nodomain.freeyourgadget.gadgetbridge.util.sleep.EstimatedSleepSession
import nodomain.freeyourgadget.gadgetbridge.util.sleep.EstimatedStage
import nodomain.freeyourgadget.gadgetbridge.util.sleep.SleepMinute
import nodomain.freeyourgadget.gadgetbridge.util.sleep.SleepMinuteKind
import java.time.Instant

/**
 * The minute stream of a sleep file from a watch that does not stage sleep (#4048).
 *
 * After the sleep data info, every raw row and every awake or unmeasurable sleep level row takes
 * the next slot, one sample length apart. Raw rows carry no timestamp of their own, so their
 * order in the file is what places them.
 */
class RawSleepStream {
    private var startSeconds: Long? = null
    private var stepSeconds = DEFAULT_SAMPLE_SECONDS
    private var slot = 0
    private val minutes = mutableListOf<SleepMinute>()

    /** Raw rows seen, including any before the sleep data info. */
    var rawCount = 0
        private set

    fun onInfo(timestampSeconds: Long?, sampleLengthSeconds: Int?) {
        if (timestampSeconds == null) {
            return
        }
        startSeconds = timestampSeconds
        stepSeconds = sampleLengthSeconds?.takeIf { it in 1..MAX_SAMPLE_SECONDS }?.toLong() ?: DEFAULT_SAMPLE_SECONDS
        slot = 0
    }

    fun onRaw(heartRate: Float?, movement: Float?) {
        rawCount++
        val start = startSeconds ?: return
        minutes += SleepMinute(slotTime(start), SleepMinuteKind.RAW, movement ?: 0f, heartRate)
        slot++
    }

    fun onLevel(timestampSeconds: Long?, level: Int) {
        val start = startSeconds ?: return
        val kind = when (level) {
            SleepLevel.AWAKE.num -> SleepMinuteKind.AWAKE
            SleepLevel.UNMEASURABLE.num -> SleepMinuteKind.UNMEASURABLE
            else -> null
        }
        if (kind != null) {
            val time = timestampSeconds?.let(Instant::ofEpochSecond) ?: slotTime(start)
            minutes += SleepMinute(time, kind)
        }
        slot++
    }

    fun minutes(): List<SleepMinute> = minutes.toList()

    private fun slotTime(startSeconds: Long): Instant = Instant.ofEpochSecond(startSeconds + slot * stepSeconds)

    companion object {
        private const val DEFAULT_SAMPLE_SECONDS = 60L
        private const val MAX_SAMPLE_SECONDS = 3600

        /**
         * The data of an estimated sleep event. The watch's own sleep events carry a Garmin epoch,
         * or nothing when the file leaves the field out, and the light sleep faked by earlier
         * versions carries -1.
         */
        const val ESTIMATED_SLEEP_DATA = -2L

        /**
         * Stage rows as Garmin stores them, each at the end of the span it covers, see
         * GarminActivitySampleProvider#overlaySleep, but in a table of their own so the
         * watch's own stages are never overwritten or deleted.
         */
        @JvmStatic
        fun stageSamples(session: EstimatedSleepSession): List<GarminEstimatedSleepStageSample> =
            session.stages.map { span ->
                GarminEstimatedSleepStageSample().apply {
                    timestamp = span.end.toEpochMilli()
                    stage = levelOf(span.stage).num
                }
            }

        /** Sleep start at the onset and stop at the end, as a watch that stages sleep sends them, marked as estimated. */
        @JvmStatic
        fun sleepEvents(session: EstimatedSleepSession): List<GarminEventSample> =
            listOf(
                sleepEvent(session.onset, EventType.START),
                sleepEvent(session.end, EventType.STOP),
            )

        private fun sleepEvent(time: Instant, type: EventType) = GarminEventSample().apply {
            timestamp = time.toEpochMilli()
            setEvent(Event.DETECT_SLEEP)
            setEventType(type)
            data = ESTIMATED_SLEEP_DATA
        }

        private fun levelOf(stage: EstimatedStage): SleepLevel = when (stage) {
            EstimatedStage.AWAKE -> SleepLevel.AWAKE
            EstimatedStage.LIGHT -> SleepLevel.LIGHT
            EstimatedStage.DEEP -> SleepLevel.DEEP
            EstimatedStage.REM -> SleepLevel.REM
            EstimatedStage.UNKNOWN -> SleepLevel.UNMEASURABLE
        }
    }
}
