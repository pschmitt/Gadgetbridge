/*  Copyright (C) 2025 José Rebelo

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
package nodomain.freeyourgadget.gadgetbridge.devices.gloryfit

import nodomain.freeyourgadget.gadgetbridge.devices.AbstractSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.GenericHeartRateSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.GloryFitStepsSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.entities.GenericActivitySample
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample
import org.slf4j.Logger
import org.slf4j.LoggerFactory

open class GloryFitActivitySampleProvider(private val device: GBDevice, private val session: DaoSession) :
    SampleProvider<GenericActivitySample> {

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(GloryFitActivitySampleProvider::class.java)
        private const val SLEEP_LOOK_BACK_SECONDS = 12 * 60 * 60
    }

    private val stepsProvider: GloryFitStepsSampleProvider = GloryFitStepsSampleProvider(device, session)
    private val heartRateProvider: GenericHeartRateSampleProvider = GenericHeartRateSampleProvider(device, session)

    override fun normalizeType(rawType: Int): ActivityKind {
        return ActivityKind.fromCode(rawType)
    }

    override fun toRawActivityKind(activityKind: ActivityKind): Int {
        return activityKind.code
    }

    override fun normalizeIntensity(rawIntensity: Int): Float {
        return rawIntensity.toFloat()
    }

    override fun getAllActivitySamples(timestampFrom: Int, timestampTo: Int): MutableList<GenericActivitySample> {
        val byTimestamp: MutableMap<Int, GenericActivitySample> = mutableMapOf()
        val ret: MutableList<GenericActivitySample> = mutableListOf()

        val stepsSamples = stepsProvider.getAllSamples(timestampFrom * 1000L - 2 * 86400L, timestampTo * 1000L)
        for (stepsSample in stepsSamples) {
            val activitySample = GenericActivitySample()
            activitySample.provider = this
            activitySample.timestamp = (stepsSample.timestamp / 1000L).toInt()
            activitySample.steps = stepsSample.totalSteps
            ret.add(activitySample)
            byTimestamp[activitySample.timestamp] = activitySample
        }
        val hrSamples = heartRateProvider.getAllSamples(timestampFrom * 1000L - 2 * 86400L, timestampTo * 1000L)
        for (hrSample in hrSamples) {
            val timestamp = (hrSample.timestamp / 1000L).toInt()
            if (byTimestamp.contains(timestamp)) {
                byTimestamp[timestamp]!!.heartRate = hrSample.heartRate
            } else {
                val activitySample = GenericActivitySample()
                activitySample.provider = this
                activitySample.timestamp = timestamp
                activitySample.heartRate = hrSample.heartRate
                ret.add(activitySample)
                byTimestamp[activitySample.timestamp] = activitySample
            }
        }

        // TODO fill gaps?

        overlaySleep(ret, timestampFrom, timestampTo)

        return ret
            .filter { sample -> sample.timestamp in timestampFrom..timestampTo }
            .sortedBy { sample -> sample.timestamp }
            .toMutableList()
    }

    override fun getAllActivitySamplesHighRes(
        timestampFrom: Int,
        timestampTo: Int
    ): MutableList<GenericActivitySample> {
        return getAllActivitySamples(timestampFrom, timestampTo)
    }

    override fun hasHighResData(): Boolean {
        return false
    }

    override fun getActivitySamples(timestampFrom: Int, timestampTo: Int): MutableList<GenericActivitySample> {
        return getAllActivitySamples(timestampFrom, timestampTo)
            .filter { sample -> sample.kind == ActivityKind.ACTIVITY }
            .toMutableList()
    }

    override fun addGBActivitySample(activitySample: GenericActivitySample) {
        throw UnsupportedOperationException("Read-only sample provider")
    }

    override fun addGBActivitySamples(activitySamples: List<GenericActivitySample>) {
        throw UnsupportedOperationException("Read-only sample provider")
    }

    override fun createActivitySample(): GenericActivitySample {
        return GenericActivitySample()
    }

    override fun getLatestActivitySample(): GenericActivitySample? {
        // TODO getLatestActivitySample
        LOG.warn("getLatestActivitySample not implemented")
        return null
    }

    override fun getLatestActivitySample(until: Int): GenericActivitySample? {
        // TODO getLatestActivitySample
        LOG.warn("getLatestActivitySample(until) not implemented")
        return null
    }

    override fun getFirstActivitySample(): GenericActivitySample? {
        // TODO getFirstActivitySample
        LOG.warn("getFirstActivitySample not implemented")
        return null
    }

    override fun getFirstActivitySample(after: Int): GenericActivitySample? {
        // TODO getFirstActivitySample
        LOG.warn("getFirstActivitySample(after) not implemented")
        return null
    }

    fun overlaySleep(samples: MutableList<GenericActivitySample>, timestampFrom: Int, timestampTo: Int) {
        val sleepSessionProvider = GloryFitSleepSessionProvider(device, session)
        val sleepSessions = sleepSessionProvider.getSleepSessions(timestampFrom - SLEEP_LOOK_BACK_SECONDS, timestampTo)

        if (sleepSessions.isEmpty()) {
            return
        }

        for (sample in samples) {
            val sleepType = AbstractSampleProvider.sleepKindAt(sleepSessions, sample.timestamp * 1000L)
            if (sleepType != null) {
                sample.rawKind = sleepType.code
                sample.rawIntensity = ActivitySample.NOT_MEASURED
            }
        }
    }
}
