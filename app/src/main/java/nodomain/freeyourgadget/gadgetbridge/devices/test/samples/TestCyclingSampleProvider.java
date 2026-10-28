package nodomain.freeyourgadget.gadgetbridge.devices.test.samples;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.devices.test.TestDeviceRand;
import nodomain.freeyourgadget.gadgetbridge.entities.CyclingSample;

/**
 * Up to one ride per day, in the evening. The distance is cumulative across days.
 */
public class TestCyclingSampleProvider extends AbstractTestSampleProvider<CyclingSample> {
    private static final long RIDE_START_MILLIS = 18 * 60 * 60 * 1000L;
    private static final int RIDE_MINUTES = 60;
    private static final long MINUTE_MILLIS = 60 * 1000L;
    private static final float WHEEL_CIRCUMFERENCE_METERS = 2.1f;
    private static final float MAX_DAILY_DISTANCE_METERS = 30000f;

    @NonNull
    @Override
    public List<CyclingSample> getAllSamples(final long timestampFrom, final long timestampTo) {
        final List<CyclingSample> samples = new ArrayList<>();

        for (long day = startOfDay(timestampFrom); day < timestampTo; day += DAY_MILLIS) {
            if (!TestDeviceRand.randBool(day, 0.6f)) {
                continue;
            }

            final long dayIndex = (day - TestDeviceRand.BASE_TIMESTAMP) / DAY_MILLIS;
            float distance = dayIndex * MAX_DAILY_DISTANCE_METERS;

            for (int minute = 0; minute <= RIDE_MINUTES; minute++) {
                final long ts = day + RIDE_START_MILLIS + minute * MINUTE_MILLIS;
                final float speed = minute == 0 ? 0 : TestDeviceRand.randFloat(ts, 4f, 8f);
                distance += speed * 60;
                if (ts < timestampFrom || ts >= timestampTo) {
                    continue;
                }
                samples.add(createSample(ts, distance, speed));
            }
        }

        return samples;
    }

    @Nullable
    @Override
    public CyclingSample getLatestSample() {
        return getLatestSample(System.currentTimeMillis());
    }

    @Nullable
    @Override
    public CyclingSample getFirstSample() {
        return createSample(TestDeviceRand.BASE_TIMESTAMP, 0, 0);
    }

    private static CyclingSample createSample(final long timestamp, final float distance, final float speed) {
        final CyclingSample sample = new CyclingSample();
        sample.setTimestamp(timestamp);
        sample.setDistance(distance);
        sample.setSpeed(speed);
        sample.setRevolutionCount(Math.round(distance / WHEEL_CIRCUMFERENCE_METERS));
        return sample;
    }
}
