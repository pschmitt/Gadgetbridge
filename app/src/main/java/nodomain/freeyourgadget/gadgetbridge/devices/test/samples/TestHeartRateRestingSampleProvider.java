package nodomain.freeyourgadget.gadgetbridge.devices.test.samples;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.devices.test.TestDeviceRand;
import nodomain.freeyourgadget.gadgetbridge.model.HeartRateSample;

public class TestHeartRateRestingSampleProvider extends AbstractTestSampleProvider<HeartRateSample> {
    @NonNull
    @Override
    public List<HeartRateSample> getAllSamples(final long timestampFrom, final long timestampTo) {
        final List<HeartRateSample> samples = new ArrayList<>();

        for (long day = startOfDay(timestampFrom); day <= timestampTo; day += DAY_MILLIS) {
            final long ts = day + 8 * 60 * 60 * 1000L;
            if (ts >= timestampFrom && ts <= timestampTo) {
                samples.add(createSample(ts));
            }
        }

        return samples;
    }

    @Nullable
    @Override
    public HeartRateSample getLatestSample() {
        return getLatestSample(System.currentTimeMillis());
    }

    @Nullable
    @Override
    public HeartRateSample getFirstSample() {
        return createSample(TestDeviceRand.BASE_TIMESTAMP);
    }

    private static HeartRateSample createSample(final long timestamp) {
        final int heartRate = TestDeviceRand.randInt(startOfDay(timestamp), 50, 70);
        return new HeartRateSample() {
            @Override
            public long getTimestamp() {
                return timestamp;
            }

            @Override
            public int getHeartRate() {
                return heartRate;
            }
        };
    }
}
