/*  Copyright (C) 2026 Gadgetbridge contributors

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
package nodomain.freeyourgadget.gadgetbridge.util.healthconnect.syncers

import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Power
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.MetricSample
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneOffset
import kotlin.reflect.KClass

/**
 * Syncs the resting metabolic rate the devices report as [MetricSample.Metric.GENERIC_RESTING_METABOLIC_RATE].
 * The metrics table holds every metric, so the others are passed over here.
 */
internal object BasalMetabolicRateSyncer : AbstractTimeSampleSyncer<MetricSample, BasalMetabolicRateRecord>() {
    override val logger: Logger = LoggerFactory.getLogger(BasalMetabolicRateSyncer::class.java)
    override val recordClass: KClass<BasalMetabolicRateRecord> = BasalMetabolicRateRecord::class

    override fun getSampleProvider(
        gbDevice: GBDevice,
        daoSession: DaoSession
    ): TimeSampleProvider<out MetricSample>? {
        return gbDevice.deviceCoordinator.getMetricsSampleProvider(gbDevice, daoSession)
    }

    override fun convertSample(
        sample: MetricSample,
        offset: ZoneOffset,
        metadata: Metadata,
        deviceName: String
    ): BasalMetabolicRateRecord? {
        if (sample.metric != MetricSample.Metric.GENERIC_RESTING_METABOLIC_RATE) {
            return null
        }
        val kcalPerDay = sample.metricScore
        if (kcalPerDay !in 1.0..10000.0 || !kcalPerDay.isFinite()) {
            logger.skipOutOfRange(deviceName, "BasalMetabolicRate", kcalPerDay, "1..10000 kcal/day")
            return null
        }

        return BasalMetabolicRateRecord(
            time = Instant.ofEpochMilli(sample.timestamp),
            zoneOffset = offset,
            basalMetabolicRate = Power.kilocaloriesPerDay(kcalPerDay),
            metadata = metadata
        )
    }
}
