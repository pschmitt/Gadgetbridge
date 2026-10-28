/*  Copyright (C) 2015-2024 Andreas Shimokawa, Carsten Pfeiffer, Daniele
    Gobbetti, Dikay900, José Rebelo, ozkanpakdil, Pavel Elagin, Petr Vaněk, Q-er, a0z

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
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_BREATHS_PER_MIN;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_CELSIUS;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_PERCENTAGE;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.Chart;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import kotlin.jvm.functions.Function1;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.HeartRateUtils;
import nodomain.freeyourgadget.gadgetbridge.model.SleepSession;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartLegendView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.sleep.SleepStagesChartData;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartValueFormat;
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.GaugeDrawer;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutValueFormatter;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummarySimpleEntry;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.databinding.FragmentSleepchartBinding;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.RespiratoryRateSample;
import nodomain.freeyourgadget.gadgetbridge.model.SleepScoreSample;
import nodomain.freeyourgadget.gadgetbridge.model.Spo2Sample;
import nodomain.freeyourgadget.gadgetbridge.model.TemperatureSample;
import nodomain.freeyourgadget.gadgetbridge.util.Accumulator;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;
import nodomain.freeyourgadget.gadgetbridge.util.SleepRangeUtils;


public class SleepDailyFragment extends SleepFragment<SleepDailyFragment.MyChartsData> {
    protected static final Logger LOG = LoggerFactory.getLogger(SleepDailyFragment.class);

    private static final String PREF_DAY_CHART = "chart_sleep_day_chart";
    private static final String DAY_CHART_HYPNOGRAM = "hypnogram";
    private static final String DAY_CHART_LEGACY = "legacy";

    private FragmentSleepchartBinding binding;

    Prefs prefs = GBApplication.getPrefs();
    private final boolean SHOW_CHARTS_AVERAGE = prefs.getBoolean("charts_show_average", true);
    private final int sleepLinesLimit = prefs.getInt("chart_sleep_lines_limit", 6);

    @Override
    protected boolean isSingleDay() {
        return true;
    }

    @Override
    protected MyChartsData refreshInBackground(ChartsHost chartsHost, DBHandler db, GBDevice device) {
        final SleepRangeUtils.SleepRangeMode rangeMode = SleepRangeUtils.getMode();
        final int[] range = SleepRangeUtils.getSleepRange(getTSEnd(), rangeMode);

        final List<SleepSession> sleepSessions = device.getDeviceCoordinator()
                .getSleepSessionProvider(device, db.getDaoSession())
                .getSleepSessionsEndingIn(range[0], range[1]);

        // In ROLLING_24H mode, or when there is nothing to anchor on, the chart spans the whole
        // requested range. Otherwise, it spans exactly the sessions found in that range, so the chart
        // always matches the displayed sleep totals.
        final int samplesStart;
        final int samplesEnd;
        if (rangeMode == SleepRangeUtils.SleepRangeMode.ROLLING_24H || sleepSessions.isEmpty()) {
            samplesStart = range[0];
            samplesEnd = range[1];
        } else {
            samplesStart = (int) (sleepSessions.get(0).getStartTime() / 1000L);
            samplesEnd = (int) (sleepSessions.get(sleepSessions.size() - 1).getEndTime() / 1000L);
        }

        final List<ActivitySample> samples = (List<ActivitySample>) getSamples(db, device, samplesStart, samplesEnd);
        ensureStartAndEndSamples(samples, samplesStart, samplesEnd);

        List<? extends SleepScoreSample> sleepScoreSamples = new ArrayList<>();
        if (supportsSleepScore()) {
            sleepScoreSamples = getSleepScoreSamples(db, device, getTSStart(), getTSEnd());
        }
        MySleepChartsData mySleepChartsData = buildSleepChartsData(sleepSessions, sleepScoreSamples);

        final StageSamples stageSamples = stageSamples(device, samples, samples);
        Triple<Float, Integer, Integer> hrData = calculateHrData(samples);
        Triple<Float, Float, Float> intensityData = calculateIntensityData(samples);

        final SleepStagesChartData.Overlay overlay = samples.isEmpty() ? null : buildOverlay(db, device, samples, stageSamples, hrData);

        final DeviceChartsProvider chartsProvider = device.getDeviceCoordinator().getChartsProvider();
        final Map<String, ActivitySummarySimpleEntry> customStats = chartsProvider.getDailySleepStats(requireContext(), db, device, getTSStart(), getTSEnd());

        return new MyChartsData(
                mySleepChartsData,
                stageSamples,
                hrData.getLeft(),
                hrData.getMiddle(),
                hrData.getRight(),
                intensityData.getLeft(),
                intensityData.getMiddle(),
                intensityData.getRight(),
                overlay,
                customStats
        );
    }

    private SleepStagesChartData.Overlay buildOverlay(final DBHandler db, final GBDevice device,
                                                      final List<? extends ActivitySample> samples,
                                                      final StageSamples stageSamples,
                                                      final Triple<Float, Integer, Integer> hrData) {
        final long tsStart = samples.get(0).getTimestamp() * 1000L;
        final long tsEnd = samples.get(samples.size() - 1).getTimestamp() * 1000L;
        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        final WorkoutValueFormatter formatter = new WorkoutValueFormatter();
        switch (currentOverlay) {
            case HEART_RATE: {
                final List<ChartPoint> points = new ArrayList<>();
                for (int i = 0; i < stageSamples.hrSeconds.length; i++) {
                    if (stageSamples.heartRates[i] > 0) {
                        points.add(new ChartPoint(stageSamples.hrSeconds[i], stageSamples.heartRates[i], null));
                    }
                }
                double hrMinimum = HeartRateUtils.getInstance().getMinHeartRate();
                double hrMaximum = HeartRateUtils.getInstance().getMaxHeartRate();
                if (hrData.getMiddle() != 0 || hrData.getRight() != 0) {
                    hrMinimum = hrData.getMiddle() / 2.0;
                    hrMaximum = hrData.getRight() + hrData.getMiddle() / 2.0;
                }
                final int average = Math.round(hrData.getLeft());
                return new SleepStagesChartData.Overlay(
                        HEARTRATE_LABEL, HEARTRATE_COLOR, points, stageSamples.hrMaxGapSeconds, hrMinimum, hrMaximum,
                        SHOW_CHARTS_AVERAGE && average > 0 ? (double) average : null, Color.RED, ChartValueFormat.INTEGER,
                        value -> formatter.formatValue(Math.round(value), UNIT_BPM)
                );
            }
            case SPO2: {
                final List<ChartPoint> points = new ArrayList<>();
                for (final Spo2Sample sample : coordinator.getSpo2SampleProvider(device, db.getDaoSession()).getAllSamples(tsStart, tsEnd)) {
                    points.add(new ChartPoint(sample.getTimestamp() / 1000.0, sample.getSpo2(), null));
                }
                return overlay(R.string.menuitem_spo2, ContextCompat.getColor(requireContext(), R.color.spo2_sleep_overlay_color),
                        points, 68, 100, ChartValueFormat.INTEGER, value -> formatter.formatValue(Math.round(value), UNIT_PERCENTAGE));
            }
            case TEMPERATURE: {
                final List<ChartPoint> points = new ArrayList<>();
                for (final TemperatureSample sample : coordinator.getTemperatureSampleProvider(device, db.getDaoSession()).getAllSamples(tsStart, tsEnd)) {
                    points.add(new ChartPoint(sample.getTimestamp() / 1000.0, sample.getTemperature(), null));
                }
                return overlay(R.string.menuitem_temperature, CHART_TEXT_COLOR,
                        points, 28, 45, ChartValueFormat.ONE_DECIMAL, value -> formatter.formatValue(value, UNIT_CELSIUS));
            }
            case RESPIRATORY_RATE: {
                final List<ChartPoint> points = new ArrayList<>();
                final Accumulator accumulator = new Accumulator();
                for (final RespiratoryRateSample sample : coordinator.getRespiratoryRateSampleProvider(device, db.getDaoSession()).getAllSamples(tsStart, tsEnd)) {
                    points.add(new ChartPoint(sample.getTimestamp() / 1000.0, sample.getRespiratoryRate(), null));
                    accumulator.add(sample.getRespiratoryRate());
                }
                if (points.isEmpty()) {
                    return null;
                }
                return overlay(R.string.respiratoryrate, ContextCompat.getColor(requireContext(), R.color.respiratory_rate_color),
                        points, accumulator.getMin() / 2, 1.5d * accumulator.getMax(), ChartValueFormat.ONE_DECIMAL,
                        value -> formatter.formatValue(value, UNIT_BREATHS_PER_MIN));
            }
            default:
                return null;
        }
    }

    private SleepStagesChartData.Overlay overlay(final int labelRes, final int color, final List<ChartPoint> points,
                                                 final double minimum, final double maximum, final ChartValueFormat format,
                                                 final Function1<Double, String> valueText) {
        double interval = Double.MAX_VALUE;
        for (int i = 1; i < points.size(); i++) {
            final double delta = points.get(i).getX() - points.get(i - 1).getX();
            if (delta > 0 && delta < interval) {
                interval = delta;
            }
        }
        final Double maxGap = interval == Double.MAX_VALUE ? null : 2 * interval;
        return new SleepStagesChartData.Overlay(getString(labelRes), color, points, maxGap, minimum, maximum, null, Color.RED, format, valueText);
    }

    private MySleepChartsData buildSleepChartsData(List<SleepSession> sleepSessions, List<? extends SleepScoreSample> sleepScoreSamples) {
        long lightSleepDuration = 0;
        long deepSleepDuration = 0;
        long remSleepDuration = 0;
        long awakeSleepDuration = 0;
        long totalSeconds = 0;
        for (SleepSession sleepSession : sleepSessions) {
            lightSleepDuration += sleepSession.getLightSleepDuration();
            deepSleepDuration += sleepSession.getDeepSleepDuration();
            remSleepDuration += sleepSession.getRemSleepDuration();
            awakeSleepDuration += sleepSession.getAwakeSleepDuration();
            // Includes SLEEP_ANY, which the per-stage breakdown above does not.
            totalSeconds += sleepSession.getTotalSleepDuration();
        }

        int sleepScore = 0;
        if (!sleepScoreSamples.isEmpty()) {
            sleepScore = sleepScoreSamples.get(sleepScoreSamples.size() - 1).getSleepScore();
        }

        return new MySleepChartsData(sleepSessions, totalSeconds, awakeSleepDuration, remSleepDuration, deepSleepDuration, lightSleepDuration, sleepScore);
    }

    protected void sleepStagesGaugeUpdate(MySleepChartsData pieData) {
        int[] colors = new int[]{
                ContextCompat.getColor(GBApplication.getContext(), R.color.chart_light_sleep_light),
                ContextCompat.getColor(GBApplication.getContext(), R.color.chart_deep_sleep_light),
                ContextCompat.getColor(GBApplication.getContext(), R.color.chart_rem_sleep_light),
                ContextCompat.getColor(GBApplication.getContext(), R.color.chart_awake_sleep_light),
        };
        long total = pieData.getTotalSleep() + pieData.getTotalAwake();
        float[] segments = new float[]{
                pieData.getTotalLight() > 0 ? (float) pieData.getTotalLight() / total : 0,
                pieData.getTotalDeep() > 0 ? (float) pieData.getTotalDeep() / total : 0,
                pieData.getTotalRem() > 0 ? (float) pieData.getTotalRem() / total : 0,
                pieData.getTotalAwake() > 0 ? (float) pieData.getTotalAwake() / total : 0,
        };
        final int width = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                300,
                GBApplication.getContext().getResources().getDisplayMetrics()
        );
        String lowerText = "";
        if (supportsSleepScore()) {
            lowerText = GBApplication.getContext().getString(R.string.sleep_score_value, pieData.getSleepScore());
        }
        binding.sleepStagesGauge.setImageBitmap(GaugeDrawer.drawCircleGaugeSegmented(
                width,
                width / 15,
                colors,
                segments,
                true,
                String.valueOf(timeStringFormat(pieData.getTotalSleep())),
                lowerText,
                getContext()
        ));
    }

    private String timeStringFormat(long seconds) {
        return DateTimeUtils.formatDurationHoursMinutes(seconds, TimeUnit.SECONDS);
    }

    private StatTileData buildSleepStageTile(final int colorRes, final int labelRes, final String value) {
        return new StatTileData(value, getString(labelRes), null, null, null, null, ContextCompat.getColor(requireContext(), colorRes));
    }

    @Override
    protected void updateChartsnUIThread(MyChartsData mcd) {
        MySleepChartsData pieData = mcd.getPieData();

        Date date = new Date((long) this.getTSEnd() * 1000);
        String formattedDate = new SimpleDateFormat("E, MMM dd").format(date);
        binding.sleepDate.setText(formattedDate);

        sleepStagesGaugeUpdate(pieData);

        final boolean hasSleepSessions = !pieData.sleepSessions.isEmpty();
        final List<StatTileData> legendStats = new ArrayList<>();
        legendStats.add(buildSleepStageTile(R.color.chart_deep_sleep_dark, R.string.sleep_colored_stats_deep,
                hasSleepSessions ? timeStringFormat(pieData.getTotalDeep()) : "-"));
        legendStats.add(buildSleepStageTile(R.color.chart_light_sleep_dark, R.string.sleep_colored_stats_light,
                hasSleepSessions ? timeStringFormat(pieData.getTotalLight()) : "-"));
        if (showRemSleep(pieData)) {
            legendStats.add(buildSleepStageTile(R.color.chart_rem_sleep_dark, R.string.sleep_colored_stats_rem,
                    hasSleepSessions ? timeStringFormat(pieData.getTotalRem()) : "-"));
        }
        if (showAwakeSleep(pieData)) {
            legendStats.add(buildSleepStageTile(R.color.chart_awake_sleep_dark, R.string.abstract_chart_fragment_kind_awake_sleep,
                    hasSleepSessions ? timeStringFormat(pieData.getTotalAwake()) : "-"));
        }
        binding.sleepLegendStatsContainer.removeAllViews();
        StatTileGridUtilKt.addStatTileGrid(binding.sleepLegendStatsContainer, requireContext(), legendStats, 0);
        binding.sleepchartInfo.setText(buildYouSleptText(pieData));
        binding.sleepchartInfo.setMovementMethod(new ScrollingMovementMethod());

        updateStagesChart(mcd);
        int heartRateMin = mcd.getHeartRateAxisMin();
        int heartRateMax = mcd.getHeartRateAxisMax();
        int heartRateAvg = Math.round(mcd.getHeartRateAverage());
        float intensityTotal = mcd.getIntensityTotal();

        // Build stats grid programmatically
        binding.sleepStatsContainer.removeAllViews();
        final WorkoutValueFormatter workoutValueFormatter = new WorkoutValueFormatter();
        final List<StatTileData> stats = new ArrayList<>();

        if (supportsHeartrate(getChartsHost().getDevice())) {
            stats.add(new StatTileData(
                    heartRateMin > 0 ? workoutValueFormatter.formatValue(heartRateMin, UNIT_BPM) : getString(R.string.stats_empty_value),
                    getString(R.string.minHR)
            ));

            stats.add(new StatTileData(
                    heartRateMax > 0 ? workoutValueFormatter.formatValue(heartRateMax, UNIT_BPM) : getString(R.string.stats_empty_value),
                    getString(R.string.maxHR)
            ));

            stats.add(new StatTileData(
                    heartRateAvg > 0 ? workoutValueFormatter.formatValue(heartRateAvg, UNIT_BPM) : getString(R.string.stats_empty_value),
                    getString(R.string.averageHR)
            ));
        }

        if (intensityTotal > 0) {
            stats.add(new StatTileData(
                    new DecimalFormat("###.#").format(intensityTotal),
                    getString(R.string.movement_intensity)
            ));
        }

        for (Map.Entry<String, ActivitySummarySimpleEntry> e : mcd.getCustomStats().entrySet()) {
            final ActivitySummarySimpleEntry entry = e.getValue();
            stats.add(new StatTileData(workoutValueFormatter.formatValue(entry.getValue(), entry.getUnit()), e.getKey()));
        }

        StatTileGridUtilKt.addStatTileGrid(binding.sleepStatsContainer, requireContext(), stats, 0);
    }

    private void updateStagesChart(final MyChartsData mcd) {
        final StageSamples activitySamples = mcd.getStageSamples();
        final SleepStagesChartData.Overlay overlay = mcd.getOverlay();
        final boolean legacy = DAY_CHART_LEGACY.equals(GBApplication.getPrefs().getString(PREF_DAY_CHART, DAY_CHART_HYPNOGRAM));
        final StageSamples samples;
        if (legacy) {
            samples = activitySamples;
        } else {
            final SleepStagesChartData.SessionSamples stages = SleepStagesChartData.sessionSamples(
                    mcd.getPieData().getSleepSessions(), this::getIndexOfActivity, getIndexOfActivity(ActivityKind.ACTIVITY));
            samples = new StageSamples(stages.getSeconds(), stages.getStages(), new double[stages.getSeconds().length],
                    activitySamples.hrSeconds, activitySamples.heartRates, activitySamples.hrMaxGapSeconds);
        }
        final ChartSpec baseSpec = legacy
                ? stagesSpec(samples.withHeartRate(new long[0], new int[0]), 0, false, 0, 0)
                : hypnogramSpec(samples);
        final ChartSpec spec = SleepStagesChartData.withOverlay(baseSpec, overlay);
        binding.sleepchart.setSelectionContent(sleepSelection(samples, !legacy, overlay));
        binding.sleepchart.setSpec(spec);

        final List<ChartSeries> legend = new ArrayList<>();
        if (!spec.isEmpty()) {
            legend.add(ChartLegendView.squareItem(getString(R.string.sleep_colored_stats_light), akLightSleep.color));
            legend.add(ChartLegendView.squareItem(getString(R.string.sleep_colored_stats_deep), akDeepSleep.color));
            if (showRemSleep(mcd.getPieData())) {
                legend.add(ChartLegendView.squareItem(getString(R.string.sleep_colored_stats_rem), akRemSleep.color));
            }
            if (showAwakeSleep(mcd.getPieData())) {
                legend.add(ChartLegendView.squareItem(getString(R.string.abstract_chart_fragment_kind_awake_sleep), akAwakeSleep.color));
            }
            if (spec.getEndYAxis() != null) {
                legend.add(ChartLegendView.lineItem(overlay.getLabel(), overlay.getColor()));
            }
            if (!spec.getLimitLines().isEmpty()) {
                legend.add(ChartLegendView.lineItem(HEARTRATE_AVERAGE_LABEL, Color.RED));
            }
        }
        binding.sleepchartLegend.setSeries(legend);
    }

    private boolean showRemSleep(final MySleepChartsData pieData) {
        return supportsRemSleep(getChartsHost().getDevice()) || pieData.getTotalRem() > 0;
    }

    private boolean showAwakeSleep(final MySleepChartsData pieData) {
        return supportsAwakeSleep(getChartsHost().getDevice()) || pieData.getTotalAwake() > 0;
    }

    private Triple<Float, Integer, Integer> calculateHrData(List<? extends ActivitySample> samples) {
        if (samples.isEmpty()) {
            return Triple.of(0f, 0, 0);
        }

        final Accumulator accumulator = new Accumulator();
        HeartRateUtils heartRateUtilsInstance = HeartRateUtils.getInstance();
        for (ActivitySample sample : samples) {
            if (ActivityKind.isSleep(sample.getKind())) {
                int heartRate = sample.getHeartRate();
                if (heartRateUtilsInstance.isValidHeartRateValue(heartRate)) {
                    accumulator.add(heartRate);
                }
            }
        }
        if (accumulator.getCount() == 0) {
            return Triple.of(0f, 0, 0);
        }

        return Triple.of((float) accumulator.getAverage(), (int) accumulator.getMin(), (int) accumulator.getMax());
    }

    private Triple<Float, Float, Float> calculateIntensityData(List<? extends ActivitySample> samples) {
        if (samples.isEmpty()) {
            return Triple.of(0f, 0f, 0f);
        }

        final Accumulator accumulator = new Accumulator();
        for (ActivitySample s : samples) {
            if (s.getKind() == ActivityKind.LIGHT_SLEEP || s.getKind() == ActivityKind.DEEP_SLEEP) {
                float intensity = s.getIntensity();
                accumulator.add(intensity);
            }
        }
        if (accumulator.getCount() == 0) {
            return Triple.of(0f, 0f, 0f);
        }

        return Triple.of((float) accumulator.getSum(), (float) accumulator.getMin(), (float) accumulator.getMax());
    }

    private String buildYouSleptText(MySleepChartsData pieData) {
        final StringBuilder result = new StringBuilder();
        if (!pieData.getSleepSessions().isEmpty()) {
            for (SleepSession sleepSession : pieData.getSleepSessions()) {
                if (result.length() > 0) {
                    result.append("  |  ");
                }
                String from = DateTimeUtils.timeToString(new Date(sleepSession.getStartTime()));
                String to = DateTimeUtils.timeToString(new Date(sleepSession.getEndTime()));
                result.append(String.format("%s - %s", from, to));
            }
        }
        return result.toString();
    }

    private enum OverlayType {
        NONE,
        HEART_RATE,
        SPO2,
        TEMPERATURE,
        RESPIRATORY_RATE,
    }

    private OverlayType currentOverlay = OverlayType.NONE;

    public boolean supportsSpO2(GBDevice device) {
        DeviceCoordinator coordinator = device.getDeviceCoordinator();
        return coordinator.supportsSpo2(device);
    }

    public boolean supportsTemperature(GBDevice device) {
        DeviceCoordinator coordinator = device.getDeviceCoordinator();
        return coordinator.supportsTemperatureMeasurement(device);
    }

    public boolean supportsSleepRespiratoryRate(GBDevice device) {
        DeviceCoordinator coordinator = device.getDeviceCoordinator();
        return coordinator.supportsSleepRespiratoryRate(device);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        binding = FragmentSleepchartBinding.inflate(inflater, container, false);
        View rootView = binding.getRoot();

        rootView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> getChartsHost().enableSwipeRefresh(scrollY == 0));

        ChipGroup chipGroup = binding.sleepChartOverlayGroup;

        chipGroup.setOnCheckedStateChangeListener((group, list) -> {
            currentOverlay = list.isEmpty() ? OverlayType.NONE : (OverlayType) group.findViewById(list.get(0)).getTag();
            refresh();
        });

        if (supportsHeartrate(getChartsHost().getDevice())) {
            Chip hrChip = (Chip) inflater.inflate(R.layout.layout_chart_chip, chipGroup, false);
            hrChip.setText(ContextCompat.getString(GBApplication.getContext(), R.string.heart_rate));
            hrChip.setChipIconVisible(true);
            hrChip.setChipIconResource(R.drawable.ic_heartrate);
            hrChip.setTag(OverlayType.HEART_RATE);
            chipGroup.addView(hrChip);
        }

        if (supportsSpO2(getChartsHost().getDevice())) {
            Chip spo2Chip = (Chip) inflater.inflate(R.layout.layout_chart_chip, chipGroup, false);
            spo2Chip.setText(ContextCompat.getString(GBApplication.getContext(), R.string.menuitem_spo2));
            spo2Chip.setChipIconVisible(true);
            spo2Chip.setChipIconResource(R.drawable.ic_spo2);
            spo2Chip.setTag(OverlayType.SPO2);
            chipGroup.addView(spo2Chip);
        }

        if (supportsTemperature(getChartsHost().getDevice())) {
            Chip tempChip = (Chip) inflater.inflate(R.layout.layout_chart_chip, chipGroup, false);
            tempChip.setText(ContextCompat.getString(GBApplication.getContext(), R.string.menuitem_temperature));
            tempChip.setChipIconVisible(true);
            tempChip.setChipIconResource(R.drawable.ic_temperature);
            tempChip.setTag(OverlayType.TEMPERATURE);
            chipGroup.addView(tempChip);
        }

        if (supportsSleepRespiratoryRate(getChartsHost().getDevice())) {
            Chip tempChip = (Chip) inflater.inflate(R.layout.layout_chart_chip, chipGroup, false);
            tempChip.setText(ContextCompat.getString(GBApplication.getContext(), R.string.respiratoryrate));
            tempChip.setChipIconVisible(true);
            tempChip.setChipIconResource(R.drawable.ic_pulmonology);
            tempChip.setTag(OverlayType.RESPIRATORY_RATE);
            chipGroup.addView(tempChip);
        }

        binding.sleepchartInfo.setMaxLines(sleepLinesLimit);

        binding.sleepchart.setZoomable(true);
        binding.sleepchart.dismissSelectionOnTapOutside(rootView);

        // refresh immediately instead of use refreshIfVisible(), for perceived performance
        refresh();

        return rootView;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action != null && action.equals(ChartsHost.REFRESH)) {
            refresh();
        } else {
            super.onReceive(context, intent);
        }
    }

    @Override
    protected void setupLegend(Chart<?> chart) {
    }

    @Override
    protected void renderCharts() {
        binding.sleepchart.invalidate();
    }

    protected static class MySleepChartsData extends ChartsData {
        private final long totalSleep;
        private final long totalAwake;
        private final long totalRem;
        private final long totalDeep;
        private final long totalLight;
        private final int sleepScore;
        private final List<SleepSession> sleepSessions;

        public MySleepChartsData(List<SleepSession> sleepSessions, long totalSleep, long totalAwake, long totalRem, long totalDeep, long totalLight, int sleepScore) {
            this.sleepSessions = sleepSessions;
            this.totalAwake = totalAwake;
            this.totalSleep = totalSleep;
            this.totalRem = totalRem;
            this.totalDeep = totalDeep;
            this.totalLight = totalLight;
            this.sleepScore = sleepScore;
        }

        public long getTotalSleep() {
            return totalSleep;
        }

        public long getTotalAwake() {
            return totalAwake;
        }

        public long getTotalRem() {
            return totalRem;
        }

        public long getTotalDeep() {
            return totalDeep;
        }

        public long getTotalLight() {
            return totalLight;
        }

        public int getSleepScore() {
            return sleepScore;
        }

        public List<SleepSession> getSleepSessions() {
            return sleepSessions;
        }
    }

    protected static class MyChartsData extends ChartsData {
        private final StageSamples stageSamples;
        private final MySleepChartsData pieData;
        private final float heartRateAverage;
        private final int heartRateAxisMax;
        private final int heartRateAxisMin;
        private final float intensityAxisMax;
        private final float intensityAxisMin;
        private final float intensityTotal;

        private final Map<String, ActivitySummarySimpleEntry> customStats;

        private final SleepStagesChartData.Overlay overlay;

        public MyChartsData(MySleepChartsData pieData,
                            StageSamples stageSamples,
                            float heartRateAverage,
                            int heartRateAxisMin,
                            int heartRateAxisMax,
                            float intensityTotal,
                            float intensityAxisMin,
                            float intensityAxisMax,
                            SleepStagesChartData.Overlay overlay,
                            Map<String, ActivitySummarySimpleEntry> customStats) {
            this.pieData = pieData;
            this.stageSamples = stageSamples;
            this.heartRateAverage = heartRateAverage;
            this.heartRateAxisMax = heartRateAxisMax;
            this.heartRateAxisMin = heartRateAxisMin;
            this.intensityTotal = intensityTotal;
            this.intensityAxisMin = intensityAxisMin;
            this.intensityAxisMax = intensityAxisMax;
            this.overlay = overlay;
            this.customStats = customStats;
        }

        public MySleepChartsData getPieData() {
            return pieData;
        }

        public StageSamples getStageSamples() {
            return stageSamples;
        }

        public float getHeartRateAverage() {
            return heartRateAverage;
        }

        public int getHeartRateAxisMax() {
            return heartRateAxisMax;
        }

        public int getHeartRateAxisMin() {
            return heartRateAxisMin;
        }

        public float getIntensityAxisMax() {
            return intensityAxisMax;
        }

        public float getIntensityAxisMin() {
            return intensityAxisMin;
        }

        public float getIntensityTotal() {
            return intensityTotal;
        }

        public SleepStagesChartData.Overlay getOverlay() {
            return overlay;
        }

        public Map<String, ActivitySummarySimpleEntry> getCustomStats() {
            return customStats;
        }
    }
}
