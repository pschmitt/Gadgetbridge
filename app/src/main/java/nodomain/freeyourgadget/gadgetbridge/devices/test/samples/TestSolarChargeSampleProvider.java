package nodomain.freeyourgadget.gadgetbridge.devices.test.samples;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.devices.test.TestDeviceRand;
import nodomain.freeyourgadget.gadgetbridge.model.SolarChargeSample;

public class TestSolarChargeSampleProvider extends AbstractTestSampleProvider<SolarChargeSample> {
    private static final int SUNRISE_HOUR = 7;
    private static final int SUNSET_HOUR = 20;
    private static final long HOUR_MILLIS = 60 * 60 * 1000L;
    private static final long SAMPLE_INTERVAL_MILLIS = 60 * 1000L;

    @NonNull
    @Override
    public List<SolarChargeSample> getAllSamples(final long timestampFrom, final long timestampTo) {
        final List<SolarChargeSample> samples = new ArrayList<>();

        final long firstTs = (timestampFrom + SAMPLE_INTERVAL_MILLIS - 1) / SAMPLE_INTERVAL_MILLIS * SAMPLE_INTERVAL_MILLIS;
        for (long ts = firstTs; ts < timestampTo; ts += SAMPLE_INTERVAL_MILLIS) {
            samples.add(new TestSolarChargeSample(ts));
        }

        return samples;
    }

    @Nullable
    @Override
    public SolarChargeSample getLatestSample() {
        return new TestSolarChargeSample(System.currentTimeMillis());
    }

    @Nullable
    @Override
    public SolarChargeSample getFirstSample() {
        return new TestSolarChargeSample(TestDeviceRand.BASE_TIMESTAMP);
    }

    /**
     * Follow daylight hours, zero at night. Simulates clouds changing every hour.
     */
    protected static class TestSolarChargeSample implements SolarChargeSample {
        private final long timestamp;
        private final float percent;

        public TestSolarChargeSample(final long timestamp) {
            this.timestamp = timestamp;

            final long millisOfDay = timestamp - startOfDay(timestamp);
            final double hour = (double) millisOfDay / HOUR_MILLIS;
            if (hour < SUNRISE_HOUR || hour >= SUNSET_HOUR) {
                this.percent = 0;
            } else {
                final double dayProgress = (hour - SUNRISE_HOUR) / (SUNSET_HOUR - SUNRISE_HOUR);
                final double daylight = Math.sin(dayProgress * Math.PI);
                final float clouds = TestDeviceRand.randFloat(timestamp - millisOfDay % HOUR_MILLIS, 0.4f, 1f);
                this.percent = clamp((float) (daylight * 100 * clouds), 0f, 100f);
            }
        }

        @Override
        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public float getPercent() {
            return percent;
        }

        @Override
        public long getGain() {
            return Math.round(percent / 100 * 4 * 1000L);
        }
    }
}
