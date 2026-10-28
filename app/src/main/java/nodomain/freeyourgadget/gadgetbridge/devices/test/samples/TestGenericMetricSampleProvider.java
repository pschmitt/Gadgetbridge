package nodomain.freeyourgadget.gadgetbridge.devices.test.samples;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import nodomain.freeyourgadget.gadgetbridge.devices.GenericMetricSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.test.TestDeviceRand;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericMetricSample;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericTrainingLoadAcuteSample;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericTrainingLoadChronicSample;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.MetricSample;

public class TestGenericMetricSampleProvider extends GenericMetricSampleProvider {
    private static final long DAY_MILLIS = AbstractTestSampleProvider.DAY_MILLIS;
    private static final long SAMPLE_TIME_MILLIS = 7 * 60 * 60 * 1000L;
    private static final long LOOKBACK_MILLIS = 30 * DAY_MILLIS;

    private static final double RACE_DISTANCE_5K = 5000;
    private static final double RACE_DISTANCE_10K = 10000;
    private static final double RACE_DISTANCE_HALF_MARATHON = 21097.5;
    private static final double RACE_DISTANCE_FULL_MARATHON = 42195;
    private static final double RIEGEL_CONSTANT = 1.06;

    private final Set<MetricSample.Metric> metrics;

    public TestGenericMetricSampleProvider(@NonNull final GBDevice device, @NonNull final DaoSession session) {
        super(device, session);
        this.metrics = device.getDeviceCoordinator().supportsMetrics(device);
    }

    @NonNull
    @Override
    public List<GenericMetricSample> getAllSamples(@NonNull final MetricSample.Metric metric, final long timestampFrom, final long timestampTo) {
        if (!metrics.contains(metric)) {
            return Collections.emptyList();
        }

        final List<GenericMetricSample> samples = new ArrayList<>();

        switch (metric) {
            case GENERIC_TRAINING_LOAD_ACUTE:
                for (final GenericTrainingLoadAcuteSample sample : new TestTrainingLoadAcuteSampleProvider().getAllSamples(timestampFrom, timestampTo)) {
                    samples.add(newSample(metric, sample.getTimestamp(), sample.getValue()));
                }
                return samples;
            case GENERIC_TRAINING_LOAD_CHRONIC:
                for (final GenericTrainingLoadChronicSample sample : new TestTrainingLoadChronicSampleProvider().getAllSamples(timestampFrom, timestampTo)) {
                    samples.add(newSample(metric, sample.getTimestamp(), sample.getValue()));
                }
                return samples;
            default:
                break;
        }

        for (long day = AbstractTestSampleProvider.startOfDay(timestampFrom); day < timestampTo; day += DAY_MILLIS) {
            final long ts = day + SAMPLE_TIME_MILLIS;
            if (ts < timestampFrom || ts >= timestampTo) {
                continue;
            }
            samples.add(newSample(metric, ts, getValue(metric, ts)));
        }

        return samples;
    }

    @Nullable
    @Override
    public MetricSample getLatestSampleBefore(@NonNull final MetricSample.Metric metric, final long until) {
        return getLatestSample(metric, until - LOOKBACK_MILLIS, until + 1);
    }

    @Nullable
    @Override
    public GenericMetricSample getLatestSample(@NonNull final MetricSample.Metric metric) {
        final long now = System.currentTimeMillis();
        return getLatestSample(metric, now - LOOKBACK_MILLIS, now);
    }

    @Nullable
    @Override
    public GenericMetricSample getLatestSample(@NonNull final MetricSample.Metric metric, final long tsFromMillis, final long tsToMillis) {
        final List<GenericMetricSample> samples = getAllSamples(metric, tsFromMillis, tsToMillis);
        return !samples.isEmpty() ? samples.get(samples.size() - 1) : null;
    }

    private static GenericMetricSample newSample(final MetricSample.Metric metric, final long timestamp, final double score) {
        final GenericMetricSample sample = new GenericMetricSample();
        sample.setTimestamp(timestamp);
        sample.setMetric(metric, score);
        return sample;
    }

    private static double getValue(final MetricSample.Metric metric, final long ts) {
        switch (metric) {
            case GARMIN_ENDURANCE_SCORE -> {
                return TestDeviceRand.randInt(ts, 4000, 7000);
            }
            case GARMIN_FUNCTIONAL_THRESHOLD_POWER -> {
                return TestDeviceRand.randInt(ts, 180, 280);
            }
            case GARMIN_HILL_ENDURANCE, GARMIN_HILL_STRENGTH -> {
                return TestDeviceRand.randInt(ts, 20, 60);
            }
            case GARMIN_HILL_SCORE -> {
                return TestDeviceRand.randInt(ts, 30, 70);
            }
            case GARMIN_MET_MAX_VO2, GENERIC_MAXIMUM_OXYGEN_UPTAKE -> {
                return TestDeviceRand.randInt(ts, 38, 55);
            }
            case GARMIN_RUNNING_LACTATE_THRESHOLD_POWER -> {
                return TestDeviceRand.randInt(ts, 250, 350);
            }
            case GENERIC_RESTING_METABOLIC_RATE -> {
                return TestDeviceRand.randInt(ts, 1500, 1800);
            }
            case GENERIC_RACE_PREDICTOR_5K -> {
                return getRaceTime(ts, RACE_DISTANCE_5K);
            }
            case GENERIC_RACE_PREDICTOR_10K -> {
                return getRaceTime(ts, RACE_DISTANCE_10K);
            }
            case GENERIC_RACE_PREDICTOR_HALF_MARATHON -> {
                return getRaceTime(ts, RACE_DISTANCE_HALF_MARATHON);
            }
            case GENERIC_RACE_PREDICTOR_FULL_MARATHON -> {
                return getRaceTime(ts, RACE_DISTANCE_FULL_MARATHON);
            }
            case GARMIN_RECOVERY_TIME -> {
                return TestDeviceRand.randInt(ts, 0, 2880);
            }
            case DAILY_TOTAL_ASCENT, DAILY_TOTAL_DESCENT -> {
                return TestDeviceRand.randInt(ts, 0, 400);
            }
            default -> {
                return TestDeviceRand.randInt(ts, 1, 100);
            }
        }
    }

    /**
     * The predicted race time in seconds. The 5km time follows a slow trend, and the other distances
     * use Riegel's equation.
     */
    private static double getRaceTime(final long ts, final double distanceMeters) {
        final long dayIndex = (ts - TestDeviceRand.BASE_TIMESTAMP) / DAY_MILLIS;
        final double time5k = 1380 - 60 * Math.sin(dayIndex * 2 * Math.PI / 90) + TestDeviceRand.randInt(ts, -15, 15);
        return Math.round(time5k * Math.pow(distanceMeters / RACE_DISTANCE_5K, RIEGEL_CONSTANT));
    }
}
