/*  Copyright (C) 2026 Vladimir Tasic

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
package nodomain.freeyourgadget.gadgetbridge.devices.zeblaze.stratos4pro

import nodomain.freeyourgadget.gadgetbridge.devices.GenericHeartRateSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.GenericSleepStageSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.Stratos4ProStepsSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.generic_hr.GenericHeartRateActivitySampleProvider
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.entities.GenericActivitySample
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.util.RangeMap
import java.util.TreeMap

/**
 * Per-minute steps, distance and kcal from [Stratos4ProStepsSampleProvider], merged with heart
 * rate and sleep stages from the generic tables.
 */
class Stratos4ProActivitySampleProvider(device: GBDevice, session: DaoSession) :
    GenericHeartRateActivitySampleProvider(device, session) {

    private val stepsProvider = Stratos4ProStepsSampleProvider(device, session)
    private val hrProvider = GenericHeartRateSampleProvider(device, session)
    private val sleepProvider = GenericSleepStageSampleProvider(device, session)

    // Minute data: a high-res query would return the same samples again.
    override fun hasHighResData(): Boolean = false

    override fun getAllActivitySamples(timestampFrom: Int, timestampTo: Int): MutableList<GenericActivitySample> {
        val byTimestamp = TreeMap<Int, GenericActivitySample>()
        fun at(millis: Long) = byTimestamp.getOrPut((millis / 1000L).toInt()) {
            GenericActivitySample().also {
                it.provider = this
                it.timestamp = (millis / 1000L).toInt()
            }
        }
        for (s in stepsProvider.getAllSamples(timestampFrom * 1000L, timestampTo * 1000L)) {
            at(s.timestamp).apply {
                steps = s.steps
                distanceCm = s.distanceCm
                activeCalories = s.activeCalories
            }
        }
        for (hr in hrProvider.getAllSamples(timestampFrom * 1000L, timestampTo * 1000L)) {
            at(hr.timestamp).heartRate = hr.heartRate
        }
        overlaySleep(byTimestamp.values, timestampFrom, timestampTo)
        return byTimestamp.values.toMutableList()
    }

    /** Marks the samples that fall in a sleep stage, including one that started before the range. */
    private fun overlaySleep(samples: Collection<GenericActivitySample>, timestampFrom: Int, timestampTo: Int) {
        val stages = RangeMap<Long, ActivityKind>(RangeMap.Mode.LOWER_BOUND)
        val before = listOfNotNull(sleepProvider.getLastSampleBefore(timestampFrom * 1000L))
        for (stage in before + sleepProvider.getAllSamples(timestampFrom * 1000L, timestampTo * 1000L)) {
            stages.put(stage.timestamp, sleepKind(stage.stage))
            stages.put(stage.timestamp + stage.duration * 60_000L, ActivityKind.UNKNOWN)
        }
        if (stages.isEmpty) return
        for (sample in samples) {
            val kind = stages.get(sample.timestamp * 1000L)
            if (kind != null && kind != ActivityKind.UNKNOWN) sample.rawKind = kind.code
        }
    }

    private fun sleepKind(stage: Int): ActivityKind = when (stage) {
        1 -> ActivityKind.DEEP_SLEEP
        2 -> ActivityKind.LIGHT_SLEEP
        3 -> ActivityKind.AWAKE_SLEEP
        4 -> ActivityKind.REM_SLEEP
        else -> ActivityKind.UNKNOWN
    }

    // First / latest over both tables: HR is measured on minutes without steps.
    override fun getLatestActivitySample(): GenericActivitySample? {
        return getLatestActivitySample((System.currentTimeMillis() / 1000L).toInt())
    }

    override fun getLatestActivitySample(until: Int): GenericActivitySample? {
        val latest = listOfNotNull(
            stepsProvider.getLatestSample(until * 1000L)?.timestamp,
            hrProvider.getLatestSample(until * 1000L)?.timestamp,
        ).maxOrNull() ?: return null
        return sampleAt(latest)
    }

    override fun getFirstActivitySample(): GenericActivitySample? {
        val earliest = listOfNotNull(
            stepsProvider.firstSample?.timestamp,
            hrProvider.firstSample?.timestamp,
        ).minOrNull() ?: return null
        return sampleAt(earliest)
    }

    override fun getFirstActivitySample(after: Int): GenericActivitySample? {
        val earliest = listOfNotNull(
            stepsProvider.getNextSampleAfter(after * 1000L)?.timestamp,
            hrProvider.getNextSampleAfter(after * 1000L)?.timestamp,
        ).minOrNull() ?: return null
        return sampleAt(earliest)
    }

    /** Merged sample at the second a steps / HR row sits on (both tables use whole seconds). */
    private fun sampleAt(timestampMillis: Long): GenericActivitySample? {
        val ts = (timestampMillis / 1000L).toInt()
        return getAllActivitySamples(ts, ts).firstOrNull()
    }
}
