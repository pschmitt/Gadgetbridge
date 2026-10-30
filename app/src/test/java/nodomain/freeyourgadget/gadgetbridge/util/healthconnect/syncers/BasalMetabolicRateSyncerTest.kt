package nodomain.freeyourgadget.gadgetbridge.util.healthconnect.syncers

import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import nodomain.freeyourgadget.gadgetbridge.entities.GenericMetricSample
import nodomain.freeyourgadget.gadgetbridge.model.MetricSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

class BasalMetabolicRateSyncerTest {

    private fun sample(metric: MetricSample.Metric, score: Double): MetricSample =
        GenericMetricSample().apply {
            timestamp = 1_700_000_000_000L
            setMetric(metric, score)
        }

    private fun metadata(): Metadata =
        Metadata.autoRecorded(Device(manufacturer = "Test", model = "TestModel", type = Device.TYPE_WATCH))

    private fun convert(sample: MetricSample): BasalMetabolicRateRecord? =
        BasalMetabolicRateSyncer.convertSample(sample, ZoneOffset.UTC, metadata(), "test-device")

    @Test
    fun restingMetabolicRateBecomesRecord() {
        val record = convert(sample(MetricSample.Metric.GENERIC_RESTING_METABOLIC_RATE, 1850.0))

        assertNotNull(record)
        assertEquals(1_700_000_000_000L, record!!.time.toEpochMilli())
        assertEquals(1850.0, record.basalMetabolicRate.inKilocaloriesPerDay, 0.01)
    }

    @Test
    fun otherMetricsAreSkipped() {
        assertNull(convert(sample(MetricSample.Metric.GENERIC_MAXIMUM_OXYGEN_UPTAKE, 50.0)))
    }

    @Test
    fun zeroIsSkipped() {
        assertNull(convert(sample(MetricSample.Metric.GENERIC_RESTING_METABOLIC_RATE, 0.0)))
    }
}
