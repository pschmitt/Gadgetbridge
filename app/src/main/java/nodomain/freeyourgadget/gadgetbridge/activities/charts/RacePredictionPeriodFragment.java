/*  Copyright (C) 2026 a0z

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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.Chart;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.color.MaterialColors;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import kotlin.jvm.functions.Function1;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartLegendView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.DaySelections;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.GbChartView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.race.RacePredictionChartData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.GenericMetricSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.MetricSample;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;

public class RacePredictionPeriodFragment extends AbstractChartFragment<RacePredictionPeriodFragment.RacePredictionData> {
    private static final String ARG_TOTAL_DAYS = "totalDays";
    private static final String STATE_SELECTED_METRIC = "selectedMetric";
    private static final int DEFAULT_TOTAL_DAYS = 30;

    private static final MetricSample.Metric[] METRICS_IN_CHIP_ORDER = {
            MetricSample.Metric.GENERIC_RACE_PREDICTOR_5K,
            MetricSample.Metric.GENERIC_RACE_PREDICTOR_10K,
            MetricSample.Metric.GENERIC_RACE_PREDICTOR_HALF_MARATHON,
            MetricSample.Metric.GENERIC_RACE_PREDICTOR_FULL_MARATHON,
    };
    private static final MetricSample.Metric DEFAULT_METRIC = MetricSample.Metric.GENERIC_RACE_PREDICTOR_5K;

    private int totalDays;
    private GBDevice device;
    private MetricSample.Metric selectedMetric = DEFAULT_METRIC;

    private TextView dateView;
    private GbChartView raceChart;
    private ChartLegendView raceLegend;
    private ChipGroup metricChipGroup;
    private LinearLayout statsContainer;

    protected int LINE_COLOR;

    public static RacePredictionPeriodFragment newInstance(final int totalDays) {
        final RacePredictionPeriodFragment fragment = new RacePredictionPeriodFragment();
        final Bundle args = new Bundle();
        args.putInt(ARG_TOTAL_DAYS, totalDays);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public String getTitle() {
        return getString(R.string.menuitem_race_predictor);
    }

    @Override
    protected boolean isSingleDay() {
        return totalDays == 1;
    }

    @Override
    protected int getTSStart() {
        return DateTimeUtils.shiftDays(getTSEnd(), -totalDays + 1);
    }

    @Override
    protected void init() {
        totalDays = getArguments() != null ? getArguments().getInt(ARG_TOTAL_DAYS, DEFAULT_TOTAL_DAYS) : DEFAULT_TOTAL_DAYS;
        LINE_COLOR = MaterialColors.getColor(requireContext(), R.attr.accent_color, getResources().getColor(R.color.accent));
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_race_prediction_month, container, false);
        rootView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) ->
                getChartsHost().enableSwipeRefresh(scrollY == 0));

        device = getChartsHost().getDevice();
        dateView = rootView.findViewById(R.id.race_prediction_date_view);
        raceChart = rootView.findViewById(R.id.race_prediction_chart);
        raceChart.setZoomable(true);
        raceChart.dismissSelectionOnTapOutside(rootView);
        raceLegend = rootView.findViewById(R.id.race_prediction_chart_legend);
        metricChipGroup = rootView.findViewById(R.id.race_prediction_chip_group);
        statsContainer = rootView.findViewById(R.id.race_prediction_stats_container);

        if (savedInstanceState != null) {
            final String savedMetricName = savedInstanceState.getString(STATE_SELECTED_METRIC);
            if (savedMetricName != null) {
                try {
                    selectedMetric = MetricSample.Metric.valueOf(savedMetricName);
                } catch (final IllegalArgumentException ignored) {
                    selectedMetric = DEFAULT_METRIC;
                }
            }
        }

        setupMetricChips(inflater);
        refresh();

        return rootView;
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_SELECTED_METRIC, selectedMetric.name());
    }

    private void setupMetricChips(final LayoutInflater inflater) {
        metricChipGroup.removeAllViews();
        for (final MetricSample.Metric metric : METRICS_IN_CHIP_ORDER) {
            final Chip chip = (Chip) inflater.inflate(R.layout.layout_chart_chip, metricChipGroup, false);
            chip.setId(View.generateViewId());
            chip.setText(getString(metric.labelResId));
            chip.setTag(metric);
            metricChipGroup.addView(chip);
            chip.setChecked(metric == selectedMetric);
        }
        metricChipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return;
            }
            final Chip checkedChip = group.findViewById(checkedIds.get(0));
            final MetricSample.Metric metric = (MetricSample.Metric) checkedChip.getTag();
            if (metric != selectedMetric) {
                selectedMetric = metric;
                refresh();
            }
        });
    }

    @Override
    protected RacePredictionData refreshInBackground(final ChartsHost chartsHost, final DBHandler db, final GBDevice device) {
        final Calendar day = Calendar.getInstance();
        day.setTime(DateTimeUtils.dayStart(new Date(getTSEnd() * 1000L)));
        day.add(Calendar.DATE, -totalDays + 1);
        final long windowStartMillis = day.getTimeInMillis();

        final List<RacePredictionDay> days = new ArrayList<>(totalDays);
        for (int i = 0; i < totalDays; i++) {
            final long dayStartMillis = day.getTimeInMillis();
            final long dayEndMillis = DateTimeUtils.dayEnd(day.getTime()).getTime();
            Double value = null;
            final MetricSample sample = GenericMetricSampleProvider.getLatestMetricSample(db, device, selectedMetric, dayStartMillis, dayEndMillis);
            if (sample != null) {
                value = sample.getMetricScore();
            }
            days.add(new RacePredictionDay((Calendar) day.clone(), value, i));
            day.add(Calendar.DATE, 1);
        }

        if (days.get(0).value == null) {
            final MetricSample carryIn = GenericMetricSampleProvider.getLatestMetricSampleBefore(db, device, selectedMetric, windowStartMillis);
            if (carryIn != null) {
                days.set(0, new RacePredictionDay(days.get(0).day, carryIn.getMetricScore(), 0));
            }
        }

        final long asOfMillis = DateTimeUtils.dayEnd(new Date(getTSEnd() * 1000L)).getTime();
        final Double[] latestValues = new Double[METRICS_IN_CHIP_ORDER.length];
        final Double[] trendDeltas = new Double[METRICS_IN_CHIP_ORDER.length];
        for (int i = 0; i < METRICS_IN_CHIP_ORDER.length; i++) {
            final MetricSample latest = GenericMetricSampleProvider.getLatestMetricSampleBefore(db, device, METRICS_IN_CHIP_ORDER[i], asOfMillis);
            latestValues[i] = latest != null ? latest.getMetricScore() : null;
            if (latest != null) {
                final MetricSample baseline = GenericMetricSampleProvider.getLatestMetricSampleBefore(db, device, METRICS_IN_CHIP_ORDER[i], windowStartMillis);
                if (baseline != null) {
                    final double delta = latest.getMetricScore() - baseline.getMetricScore();
                    trendDeltas[i] = delta != 0 ? delta : null;
                }
            }
        }

        return new RacePredictionData(days, latestValues, trendDeltas, selectedMetric);
    }

    @Override
    protected void updateChartsnUIThread(final RacePredictionData data) {
        if (data.metric != selectedMetric) {
            // A chip was tapped again while a previous refresh for the old metric was still in flight.
            return;
        }

        dateView.setText(DateTimeUtils.formatDaysUntil(totalDays, getTSEnd()));

        final int n = data.days.size();
        final long[] epochDays = new long[n];
        final double[] seconds = new double[n];
        for (int i = 0; i < n; i++) {
            final RacePredictionDay raceDay = data.days.get(i);
            epochDays[i] = LocalDate.of(raceDay.day.get(Calendar.YEAR), raceDay.day.get(Calendar.MONTH) + 1, raceDay.day.get(Calendar.DAY_OF_MONTH)).toEpochDay();
            seconds[i] = raceDay.value != null ? raceDay.value : 0;
        }
        final String label = getString(data.metric.labelResId);
        final Function1<Integer, String> timeText = i -> seconds[i] > 0 ? formatSeconds(seconds[i]) : getString(R.string.stats_empty_value);
        raceChart.setSelectionContent(x -> DaySelections.of(
                epochDays, x, Collections.singletonList(label), Collections.singletonList(LINE_COLOR),
                Collections.singletonList(timeText), getString(R.string.stats_empty_value)
        ));
        final ChartSpec spec = RacePredictionChartData.spec(epochDays, seconds, label, LINE_COLOR);
        raceChart.setSpec(spec);
        raceLegend.setSeries(spec.getSeries());

        updateTiles(data.latestValues, data.trendDeltas);
    }

    private void updateTiles(@Nullable final Double[] latestValues, @Nullable final Double[] trendDeltas) {
        if (latestValues == null) {
            return;
        }

        final List<StatTileData> stats = new ArrayList<>();
        for (int i = 0; i < METRICS_IN_CHIP_ORDER.length; i++) {
            stats.add(buildStatTile(latestValues[i], trendDeltas != null ? trendDeltas[i] : null, METRICS_IN_CHIP_ORDER[i]));
        }

        statsContainer.removeAllViews();
        StatTileGridUtilKt.addStatTileGrid(statsContainer, requireContext(), stats, 0);
    }

    private StatTileData buildStatTile(@Nullable final Double value, @Nullable final Double deltaSeconds, final MetricSample.Metric metric) {
        final String tileValue = value != null ? formatSeconds(value) : getString(R.string.stats_empty_value);
        final String label = getString(metric.labelResId);

        if (deltaSeconds == null) {
            return new StatTileData(tileValue, label);
        }

        final boolean faster = deltaSeconds < 0;
        final int iconRes = faster ? R.drawable.ic_caret_down_solid : R.drawable.ic_caret_up_solid;
        final int color = ContextCompat.getColor(requireContext(), faster ? R.color.body_energy_level_color : R.color.body_energy_lost_color);

        return new StatTileData(tileValue, label, null, formatSeconds(Math.abs(deltaSeconds)), iconRes, color);
    }

    static String formatSeconds(final double seconds) {
        final long totalSeconds = Math.round(seconds);
        final long hours = totalSeconds / 3600;
        final long minutes = (totalSeconds % 3600) / 60;
        final long secs = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format(Locale.ROOT, "%02d:%02d", minutes, secs);
    }

    @Override
    protected void renderCharts() {
        raceChart.invalidate();
    }

    @Override
    protected void setupLegend(final Chart<?> chart) {
        // Legend is disabled — only one metric's line is ever shown, selected via the chips.
    }

    protected static class RacePredictionDay {
        final Calendar day;
        @Nullable
        final Double value;
        final int i;

        RacePredictionDay(final Calendar day, @Nullable final Double value, final int i) {
            this.day = day;
            this.value = value;
            this.i = i;
        }
    }

    protected static class RacePredictionData extends ChartsData {
        final List<RacePredictionDay> days;
        @Nullable
        final Double[] latestValues;
        @Nullable
        final Double[] trendDeltas;
        final MetricSample.Metric metric;

        RacePredictionData(final List<RacePredictionDay> days, @Nullable final Double[] latestValues, @Nullable final Double[] trendDeltas, final MetricSample.Metric metric) {
            this.days = days;
            this.latestValues = latestValues;
            this.trendDeltas = trendDeltas;
            this.metric = metric;
        }

        RacePredictionDay getDay(final int i) {
            return days.get(i);
        }
    }
}
