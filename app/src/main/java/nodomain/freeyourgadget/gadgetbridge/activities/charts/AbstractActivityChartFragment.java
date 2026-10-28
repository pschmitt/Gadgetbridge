/*  Copyright (C) 2023-2024 Daniel Dakhno, José Rebelo, a0z

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
package nodomain.freeyourgadget.gadgetbridge.activities.charts;

import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_BPM;

import android.graphics.Color;
import android.text.format.DateFormat;
import android.util.TypedValue;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;

import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import de.greenrobot.dao.query.QueryBuilder;
import kotlin.jvm.functions.Function1;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.HeartRateUtils;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartDataBuilder;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartSelection;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.sleep.SleepStagesChartData;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutValueFormatter;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.AbstractActivitySample;
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary;
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummaryDao;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryParser;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;

public abstract class AbstractActivityChartFragment<D extends ChartsData> extends AbstractChartFragment<D> {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractActivityChartFragment.class);

    public static final float Y_VALUE_DEEP_SLEEP = 0.01f;

    public boolean supportsHeartrate(GBDevice device) {
        DeviceCoordinator coordinator = device.getDeviceCoordinator();
        return coordinator.supportsHeartRateMeasurement(device);
    }

    public boolean supportsRemSleep(GBDevice device) {
        DeviceCoordinator coordinator = device.getDeviceCoordinator();
        return coordinator.supportsRemSleep(device);
    }

    public boolean supportsAwakeSleep(GBDevice device) {
        DeviceCoordinator coordinator = device.getDeviceCoordinator();
        return coordinator.supportsAwakeSleep(device);
    }

    protected static final class ActivityConfig {
        public final ActivityKind type;
        public final String label;
        public final Integer color;

        public ActivityConfig(ActivityKind kind, String label, Integer color) {
            this.type = kind;
            this.label = label;
            this.color = color;
        }
    }

    protected ActivityConfig akActivity;
    protected ActivityConfig akLightSleep;
    protected ActivityConfig akDeepSleep;
    protected ActivityConfig akRemSleep;
    protected ActivityConfig akAwakeSleep;
    protected ActivityConfig akNotWorn;

    protected int BACKGROUND_COLOR;
    protected int DESCRIPTION_COLOR;
    protected int CHART_TEXT_COLOR;
    protected int HEARTRATE_COLOR;
    protected int HEARTRATE_FILL_COLOR;
    protected int AK_ACTIVITY_COLOR;
    protected int AK_DEEP_SLEEP_COLOR;
    protected int AK_REM_SLEEP_COLOR;
    protected int AK_AWAKE_SLEEP_COLOR;
    protected int AK_LIGHT_SLEEP_COLOR;
    protected int AK_NOT_WORN_COLOR;

    protected String HEARTRATE_LABEL;
    protected String HEARTRATE_AVERAGE_LABEL;

    @Override
    protected void init() {
        TypedValue runningColor = new TypedValue();
        BACKGROUND_COLOR = GBApplication.getBackgroundColor(getContext());
        DESCRIPTION_COLOR = GBApplication.getTextColor(getContext());
        CHART_TEXT_COLOR = GBApplication.getSecondaryTextColor(getContext());
        HEARTRATE_COLOR = ContextCompat.getColor(getContext(), GBApplication.getPrefs().getChartsHeartRateColor().getColorRes());
        HEARTRATE_FILL_COLOR = ContextCompat.getColor(getContext(), R.color.chart_heartrate_fill);

        getContext().getTheme().resolveAttribute(R.attr.chart_activity, runningColor, true);
        AK_ACTIVITY_COLOR = runningColor.data;
        getContext().getTheme().resolveAttribute(R.attr.chart_deep_sleep, runningColor, true);
        AK_DEEP_SLEEP_COLOR = runningColor.data;
        getContext().getTheme().resolveAttribute(R.attr.chart_light_sleep, runningColor, true);
        AK_LIGHT_SLEEP_COLOR = runningColor.data;
        getContext().getTheme().resolveAttribute(R.attr.chart_rem_sleep, runningColor, true);
        AK_REM_SLEEP_COLOR = runningColor.data;
        getContext().getTheme().resolveAttribute(R.attr.chart_awake_sleep, runningColor, true);
        AK_AWAKE_SLEEP_COLOR = runningColor.data;
        getContext().getTheme().resolveAttribute(R.attr.chart_not_worn, runningColor, true);
        AK_NOT_WORN_COLOR = runningColor.data;

        HEARTRATE_LABEL = getContext().getString(R.string.charts_legend_heartrate);
        HEARTRATE_AVERAGE_LABEL = getContext().getString(R.string.charts_legend_heartrate_average);

        akActivity = new ActivityConfig(ActivityKind.ACTIVITY, getString(R.string.abstract_chart_fragment_kind_activity), AK_ACTIVITY_COLOR);
        akLightSleep = new ActivityConfig(ActivityKind.LIGHT_SLEEP, getString(R.string.abstract_chart_fragment_kind_light_sleep), AK_LIGHT_SLEEP_COLOR);
        akDeepSleep = new ActivityConfig(ActivityKind.DEEP_SLEEP, getString(R.string.abstract_chart_fragment_kind_deep_sleep), AK_DEEP_SLEEP_COLOR);
        akRemSleep = new ActivityConfig(ActivityKind.REM_SLEEP, getString(R.string.abstract_chart_fragment_kind_rem_sleep), AK_REM_SLEEP_COLOR);
        akAwakeSleep = new ActivityConfig(ActivityKind.AWAKE_SLEEP, getString(R.string.abstract_chart_fragment_kind_awake_sleep), AK_AWAKE_SLEEP_COLOR);
        akNotWorn = new ActivityConfig(ActivityKind.NOT_WORN, getString(R.string.abstract_chart_fragment_kind_not_worn), AK_NOT_WORN_COLOR);
    }

    @Override
    protected boolean isSingleDay() {
        return false;
    }

    protected int getColorFor(ActivityKind activityKind) {
        return switch (activityKind) {
            case DEEP_SLEEP -> akDeepSleep.color;
            case LIGHT_SLEEP -> akLightSleep.color;
            case REM_SLEEP -> akRemSleep.color;
            case AWAKE_SLEEP -> akAwakeSleep.color;
            case NOT_WORN -> akNotWorn.color;
            default -> akActivity.color;
        };
    }

    protected SampleProvider<? extends AbstractActivitySample> getProvider(DBHandler db, GBDevice device) {
        DeviceCoordinator coordinator = device.getDeviceCoordinator();
        return coordinator.getSampleProvider(device, db.getDaoSession());
    }

    /**
     * Returns all kinds of samples for the given device.
     * To be called from a background thread.
     */
    protected List<? extends ActivitySample> getAllSamples(DBHandler db, GBDevice device, int tsFrom, int tsTo) {
        SampleProvider<? extends ActivitySample> provider = getProvider(db, device);
        if (provider == null) {
            LOG.error("Activity sample provider for all samples is null for {}", device);
            return new LinkedList<>();
        }
        return provider.getAllActivitySamples(tsFrom, tsTo);
    }

    protected List<? extends ActivitySample> getAllSamplesHighRes(DBHandler db, GBDevice device, int tsFrom, int tsTo) {
        SampleProvider<? extends ActivitySample> provider = getProvider(db, device);
        if (provider == null) {
            LOG.error("Activity sample provider for high res samples is null for {}", device);
            return new LinkedList<>();
        }
        // Only retrieve if the provider signals it has high-res data, otherwise it is useless
        if (provider.hasHighResData())
            return provider.getAllActivitySamplesHighRes(tsFrom, tsTo);
        return null;
    }

    protected List<? extends AbstractActivitySample> getActivitySamples(DBHandler db, GBDevice device, int tsFrom, int tsTo) {
        SampleProvider<? extends AbstractActivitySample> provider = getProvider(db, device);
        if (provider == null) {
            LOG.error("Activity sample provider for activity samples is null for {}", device);
            return new LinkedList<>();
        }
        return provider.getActivitySamples(tsFrom, tsTo);
    }

    protected static float chartValueOf(final ActivitySample sample) {
        final ActivityKind type = sample.getKind();
        if (type == ActivityKind.NOT_WORN) {
            return Y_VALUE_DEEP_SLEEP;
        }
        if (ActivityKind.isSleep(type) && sample.getIntensity() < 0) {
            return switch (type) {
                case SLEEP_ANY, AWAKE_SLEEP -> 0.25f;
                case DEEP_SLEEP -> 0.10f;
                case LIGHT_SLEEP -> 0.15f;
                case REM_SLEEP -> 0.20f;
                default -> Y_VALUE_DEEP_SLEEP;
            };
        }
        return sample.getIntensity();
    }

    /**
     * Per sample: time, stage index (see {@link #getIndexOfActivity}) and chart value, plus heart rate samples.
     */
    protected static final class StageSamples extends ChartsData {
        public final long[] seconds;
        public final int[] stages;
        public final double[] values;
        public final long[] hrSeconds;
        public final int[] heartRates;
        public final double hrMaxGapSeconds;

        public StageSamples(final long[] seconds, final int[] stages, final double[] values,
                            final long[] hrSeconds, final int[] heartRates, final double hrMaxGapSeconds) {
            this.seconds = seconds;
            this.stages = stages;
            this.values = values;
            this.hrSeconds = hrSeconds;
            this.heartRates = heartRates;
            this.hrMaxGapSeconds = hrMaxGapSeconds;
        }

        public StageSamples withHeartRate(final long[] hrSeconds, final int[] heartRates) {
            return new StageSamples(seconds, stages, values, hrSeconds, heartRates, hrMaxGapSeconds);
        }
    }

    protected StageSamples stageSamples(final GBDevice device,
                                        final List<? extends ActivitySample> samples,
                                        final List<? extends ActivitySample> hrSamples) {
        final int n = samples.size();
        final long[] seconds = new long[n];
        final int[] stages = new int[n];
        final double[] values = new double[n];
        for (int i = 0; i < n; i++) {
            final ActivitySample sample = samples.get(i);
            seconds[i] = sample.getTimestamp();
            stages[i] = getIndexOfActivity(sample.getKind());
            values[i] = chartValueOf(sample);
        }
        final int hrCount = supportsHeartrate(device) ? hrSamples.size() : 0;
        final long[] hrSeconds = new long[hrCount];
        final int[] heartRates = new int[hrCount];
        final HeartRateUtils heartRateUtils = HeartRateUtils.getInstance();
        for (int i = 0; i < hrCount; i++) {
            final ActivitySample sample = hrSamples.get(i);
            hrSeconds[i] = sample.getTimestamp();
            if (sample.getKind() != ActivityKind.NOT_WORN && heartRateUtils.isValidHeartRateValue(sample.getHeartRate())) {
                heartRates[i] = sample.getHeartRate();
            }
        }
        final int maxGapSeconds = 60 * device.getDeviceCoordinator().getMaxHeartRateMeasurementsGapMinutes(device);
        return new StageSamples(seconds, stages, values, hrSeconds, heartRates, maxGapSeconds);
    }

    private ActivityConfig[] stageConfigs() {
        return new ActivityConfig[]{akDeepSleep, akLightSleep, akRemSleep, akAwakeSleep, akNotWorn, akActivity};
    }

    /**
     * Activity per stage as solid areas, with heart rate on the end axis.
     */
    protected ChartSpec stagesSpec(final StageSamples samples, final int hrAverage, final boolean showHrAverage,
                                   final double hrMinimum, final double hrMaximum) {
        final ActivityConfig[] configs = stageConfigs();
        final String[] labels = new String[configs.length];
        final int[] colors = new int[configs.length];
        for (int i = 0; i < configs.length; i++) {
            labels[i] = configs[i].label;
            colors[i] = configs[i].color;
        }
        return SleepStagesChartData.daySpec(
                samples.seconds, samples.stages, samples.values, getIndexOfActivity(ActivityKind.NOT_WORN),
                labels, colors, CHART_TEXT_COLOR,
                samples.hrSeconds, samples.heartRates, samples.hrMaxGapSeconds,
                hrAverage, showHrAverage, HEARTRATE_LABEL, HEARTRATE_COLOR, Color.RED,
                hrMinimum, hrMaximum
        );
    }

    /**
     * Tooltip for a time on a {@link #stagesSpec} chart: the stage then, and the heart rate.
     */
    protected Function1<Double, ChartSelection> stagesSelection(final StageSamples samples) {
        final ActivityConfig[] configs = stageConfigs();
        final WorkoutValueFormatter formatter = new WorkoutValueFormatter();
        final java.text.DateFormat timeFormat = DateFormat.getTimeFormat(requireContext());
        return x -> {
            final long time = Math.round(x);
            final String title = timeFormat.format(new Date(time * 1000L));
            final List<ChartSelection.Row> rows = new ArrayList<>();
            final StringBuilder description = new StringBuilder(title).append('.');
            final int found = Arrays.binarySearch(samples.seconds, time);
            final int i = found >= 0 ? found : -found - 2;
            if (i >= 0) {
                final ActivityConfig config = configs[samples.stages[i]];
                rows.add(new ChartSelection.Row(config.color, config.label));
                description.append(' ').append(config.label).append('.');
            }
            final int hr = Arrays.binarySearch(samples.hrSeconds, time);
            if (hr >= 0 && samples.heartRates[hr] > 0) {
                final String rate = formatter.formatValue(samples.heartRates[hr], UNIT_BPM);
                rows.add(new ChartSelection.Row(HEARTRATE_COLOR, rate));
                description.append(' ').append(HEARTRATE_LABEL).append(' ').append(rate).append('.');
            }
            return new ChartSelection(title, rows, description.toString());
        };
    }

    /**
     * Deep, light, REM and awake sleep as rows of a hypnogram.
     */
    protected ChartSpec hypnogramSpec(final StageSamples samples) {
        final ActivityConfig[] configs = stageConfigs();
        final int[] colors = new int[getIndexOfActivity(ActivityKind.AWAKE_SLEEP) + 1];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = configs[i].color;
        }
        return SleepStagesChartData.hypnogramSpec(samples.seconds, samples.stages, colors);
    }

    /**
     * Tooltip for a time on a sleep chart: the stage then, with its stretch for {@code showStretch}, and the overlay value.
     */
    protected Function1<Double, ChartSelection> sleepSelection(final StageSamples samples, final boolean showStretch,
                                                               final SleepStagesChartData.Overlay overlay) {
        final ActivityConfig[] configs = stageConfigs();
        final String[] labels = new String[configs.length];
        final int[] colors = new int[configs.length];
        for (int i = 0; i < configs.length; i++) {
            labels[i] = configs[i].label;
            colors[i] = configs[i].color;
        }
        labels[getIndexOfActivity(ActivityKind.DEEP_SLEEP)] = getString(R.string.sleep_colored_stats_deep);
        labels[getIndexOfActivity(ActivityKind.LIGHT_SLEEP)] = getString(R.string.sleep_colored_stats_light);
        labels[getIndexOfActivity(ActivityKind.REM_SLEEP)] = getString(R.string.sleep_colored_stats_rem);
        final java.text.DateFormat timeFormat = DateFormat.getTimeFormat(requireContext());
        return SleepStagesChartData.selection(
                samples.seconds, samples.stages, labels, colors, showStretch, overlay,
                time -> timeFormat.format(new Date(time * 1000L)),
                duration -> DateTimeUtils.formatDurationHoursMinutes(duration, TimeUnit.SECONDS)
        );
    }

    protected int getIndexOfActivity(ActivityKind kind) {
        return switch (kind) {
            case DEEP_SLEEP -> 0;
            case LIGHT_SLEEP -> 1;
            case REM_SLEEP -> 2;
            case AWAKE_SLEEP -> 3;
            case NOT_WORN -> 4;
            default -> 5; // treated as ActivityKind.ACTIVITY
        };
    }

    protected LineDataSet createHeartrateSet(List<Entry> values, String label) {
        LineDataSet set1 = new LineDataSet(values, label);
        set1.setLineWidth(ChartDataBuilder.LINE_WIDTH_DP);
        set1.setColor(HEARTRATE_COLOR);
        set1.setMode(LineDataSet.Mode.HORIZONTAL_BEZIER);
        set1.setCubicIntensity(0.1f);
        set1.setDrawCirclesEnabled(false);
        set1.setDrawValuesEnabled(true);
        set1.setValueTextColor(CHART_TEXT_COLOR);
        set1.setAxisDependency(YAxis.AxisDependency.RIGHT);
        return set1;
    }

    /**
     * Implement this to supply the samples to be displayed.
     */
    protected abstract List<? extends ActivitySample> getSamples(DBHandler db, GBDevice device, int tsFrom, int tsTo);

    /**
     * Implement this to supply high-resolution data
     */
    protected List<? extends ActivitySample> getSamplesHighRes(DBHandler db, GBDevice device, int tsFrom, int tsTo) {
        throw new NotImplementedException("High resolution samples have not been implemented for this chart.");
    }

    protected List<? extends ActivitySample> getSamples(DBHandler db, GBDevice device) {
        int tsStart = getTSStart();
        int tsEnd = getTSEnd();
        List<ActivitySample> samples = (List<ActivitySample>) getSamples(db, device, tsStart, tsEnd);
        ensureStartAndEndSamples(samples, tsStart, tsEnd);
//        List<ActivitySample> samples2 = new ArrayList<>();
//        int min = Math.min(samples.size(), 10);
//        int min = Math.min(samples.size(), 10);
//        for (int i = 0; i < min; i++) {
//            samples2.add(samples.get(i));
//        }
//        return samples2;
        return samples;
    }

    protected List<BaseActivitySummary> getAllWorkouts(DBHandler db, GBDevice device) {
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(getTSEnd() * 1000L); //we need today initially, which is the end of the time range
        day.set(Calendar.HOUR_OF_DAY, 0); //and we set time for the start and end of the same day
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        final int tsFrom = (int) (day.getTimeInMillis() / 1000);
        final int tsTo = tsFrom + 24 * 60 * 60 - 1;
        BaseActivitySummaryDao summaryDao = db.getDaoSession().getBaseActivitySummaryDao();
        Device dbDevice = DBHelper.findDevice(device, db.getDaoSession());
        QueryBuilder<BaseActivitySummary> qb = summaryDao.queryBuilder();
        qb.where(BaseActivitySummaryDao.Properties.DeviceId.eq(Objects.requireNonNull(dbDevice).getId()));
        qb.where(BaseActivitySummaryDao.Properties.StartTime.gt(new Date(tsFrom * 1000L)));
        qb.where(BaseActivitySummaryDao.Properties.EndTime.lt(new Date(tsTo * 1000L)));
        qb.orderAsc(BaseActivitySummaryDao.Properties.StartTime);
        final List<BaseActivitySummary> summaries = qb.build().list();
        final ActivitySummaryParser summaryParser = device.getDeviceCoordinator().getActivitySummaryParser(device, requireContext());
        for (BaseActivitySummary summary : summaries) {
            summaryParser.parseBinaryData(summary, false);
        }
        return summaries;
    }

    protected List<? extends ActivitySample> getSamplesHighRes(DBHandler db, GBDevice device) {
        int tsStart = getTSStart();
        int tsEnd = getTSEnd();
        return getSamplesHighRes(db, device, tsStart, tsEnd);
    }

    protected void ensureStartAndEndSamples(List<ActivitySample> samples, int tsStart, int tsEnd) {
        if (samples == null || samples.isEmpty()) {
            return;
        }
        ActivitySample lastSample = samples.get(samples.size() - 1);
        if (lastSample.getTimestamp() < tsEnd) {
            samples.add(createTrailingActivitySample(lastSample, tsEnd));
        }

        ActivitySample firstSample = samples.get(0);
        if (firstSample.getTimestamp() > tsStart) {
            samples.add(0, createTrailingActivitySample(firstSample, tsStart));
        }
    }

    private ActivitySample createTrailingActivitySample(ActivitySample referenceSample, int timestamp) {
        TrailingActivitySample sample = new TrailingActivitySample();
        if (referenceSample instanceof AbstractActivitySample) {
            AbstractActivitySample reference = (AbstractActivitySample) referenceSample;
            sample.setUserId(reference.getUserId());
            sample.setDeviceId(reference.getDeviceId());
            sample.setProvider(reference.getProvider());
        }
        sample.setTimestamp(timestamp);
        return sample;
    }

    @Nullable
    @Override
    public ChartDataRange getAvailableDataRange(final GBDevice device, final DBHandler db) {
        return ChartDataRange.ofActivitySamples(device.getDeviceCoordinator().getSampleProvider(device, db.getDaoSession()));
    }
}
