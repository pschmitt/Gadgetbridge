/*  Copyright (C) 2026 Thomas Kuehne

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
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.mikephil.charting.charts.Chart;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

import com.google.android.material.color.MaterialColors;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.metric.GenericMetricChartData;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartSelection;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.GbChartView;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutValueFormatter;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.charts.DefaultWorkoutCharts;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.GenericMetricSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.MetricSample;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;

public class GenericMetricChartFragment extends AbstractChartFragment<GenericMetricChartFragment.GenericMetricChartsData> {
    private static final String ARG_TOTAL_DAYS = "totalDays";
    private static final int DEFAULT_TOTAL_DAYS = 30;
    private static final String STATE_SELECTED_METRIC = "selectedMetric";

    private GbChartView chart;
    private Spinner metricSpinner;
    private TextView timeSpanText;
    private LinearLayout statsContainer;

    private int lineColor;

    private int totalDays;
    private List<MetricSample.Metric> metrics = Collections.emptyList();
    private MetricSample.Metric selectedMetric;
    private WorkoutValueFormatter valueFormatter;

    public static GenericMetricChartFragment newInstance(final int totalDays) {
        final GenericMetricChartFragment fragment = new GenericMetricChartFragment();
        final Bundle args = new Bundle();
        args.putInt(ARG_TOTAL_DAYS, totalDays);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public String getTitle() {
        return getString(R.string.generic_metrics);
    }

    @Override
    protected void init() {
        totalDays = getArguments() != null ? getArguments().getInt(ARG_TOTAL_DAYS, DEFAULT_TOTAL_DAYS) : DEFAULT_TOTAL_DAYS;
        lineColor = MaterialColors.getColor(requireContext(), R.attr.accent_color, getResources().getColor(R.color.accent));
        valueFormatter = new WorkoutValueFormatter();
    }

    @Override
    protected boolean isSingleDay() {
        return totalDays == 1;
    }

    @Override
    protected GenericMetricChartsData refreshInBackground(final ChartsHost chartsHost, final DBHandler db, final GBDevice device) {
        final MetricSample.Metric metric = selectedMetric;
        final Date rangeStart = DateTimeUtils.dayStart(new Date(getTSStart() * 1000L));
        final Date rangeEnd = DateTimeUtils.dayEnd(new Date(getTSEnd() * 1000L));

        if (metric == null) {
            return GenericMetricChartsData.empty(null, rangeStart, rangeEnd);
        }

        final List<? extends MetricSample> samples = GenericMetricSampleProvider.getMetricSamples(
                db,
                device,
                metric,
                rangeStart.getTime(),
                rangeEnd.getTime()
        );
        return createChartsData(metric, samples, rangeStart, rangeEnd);
    }

    @Override
    protected void renderCharts() {
        chart.invalidate();
    }

    @Override
    protected void setupLegend(final Chart<?> chart) {
    }

    @Override
    protected void updateChartsnUIThread(final GenericMetricChartsData chartsData) {
        if (chartsData.metric != selectedMetric) {
            return;
        }

        if (totalDays == 1) {
            timeSpanText.setText(DateTimeUtils.formatDate(getEndDate(), DateUtils.FORMAT_SHOW_WEEKDAY));
        } else {
            timeSpanText.setText(DateTimeUtils.formatDaysUntil(totalDays, getTSEnd()));
        }

        final String label = getMetricLabel(chartsData.metric);
        chart.setSelectionContent(x -> {
            final long time = Math.round(x);
            final String title = totalDays == 1
                    ? DateFormat.getTimeFormat(requireContext()).format(new Date(time * 1000L))
                    : DateTimeUtils.formatDateTime(new Date(time * 1000L));
            for (int i = 0; i < chartsData.seconds.length; i++) {
                if (chartsData.seconds[i] == time) {
                    final String text = formatMetricValue(chartsData.metric, chartsData.values[i], true);
                    return new ChartSelection(
                            title,
                            Collections.singletonList(new ChartSelection.Row(lineColor, text)),
                            title + ". " + label + " " + text + "."
                    );
                }
            }
            return new ChartSelection(title, Collections.emptyList(), title + ".");
        });
        chart.setSpec(GenericMetricChartData.spec(
                chartsData.startTs, chartsData.endTs, totalDays == 1, chartsData.seconds, chartsData.values, label, lineColor
        ));

        final String emptyValue = getString(R.string.stats_empty_value);
        final String minimumValue;
        final String maximumValue;
        final String averageValue;
        if (chartsData.hasData()) {
            minimumValue = formatMetricValue(chartsData.metric, chartsData.yMin, false);
            maximumValue = formatMetricValue(chartsData.metric, chartsData.yMax, false);
            averageValue = formatMetricValue(chartsData.metric, chartsData.averageValue, false);
        } else {
            minimumValue = emptyValue;
            maximumValue = emptyValue;
            averageValue = emptyValue;
        }

        final List<StatTileData> stats = new ArrayList<>();
        stats.add(new StatTileData(minimumValue, getString(R.string.hr_minimum)));
        stats.add(new StatTileData(maximumValue, getString(R.string.hr_maximum)));
        stats.add(new StatTileData(averageValue, getString(R.string.hr_average)));
        stats.add(new StatTileData(String.valueOf(chartsData.sampleCount), getString(R.string.generic_metric_samples)));
        statsContainer.removeAllViews();
        StatTileGridUtilKt.addStatTileGrid(statsContainer, requireContext(), stats, 0);
    }

    @Override
    protected int getTSStart() {
        return DateTimeUtils.shiftDays(getTSEnd(), -totalDays + 1);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_generic_metric_chart, container, false);

        rootView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            getChartsHost().enableSwipeRefresh(scrollY == 0);
        });

        metricSpinner = rootView.findViewById(R.id.generic_metric_spinner);
        timeSpanText = rootView.findViewById(R.id.generic_metric_time_span_text);
        chart = rootView.findViewById(R.id.generic_metric_chart);
        statsContainer = rootView.findViewById(R.id.generic_metric_stats_container);

        metrics = getAvailableMetrics(getChartsHost().getDevice());
        selectedMetric = getInitialMetric(savedInstanceState);

        setupMetricSpinner();
        chart.setZoomable(true);
        chart.dismissSelectionOnTapOutside(rootView);

        refresh();

        return rootView;
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (selectedMetric != null) {
            outState.putString(STATE_SELECTED_METRIC, selectedMetric.name());
        }
    }

    private List<MetricSample.Metric> getAvailableMetrics(final GBDevice device) {
        final Set<MetricSample.Metric> supportedMetrics = GenericMetricSampleProvider.supportsMetrics(device);
        final List<MetricSample.Metric> availableMetrics = new ArrayList<>();
        for (final MetricSample.Metric metric : MetricSample.Metric.values()) {
            if (metric != MetricSample.Metric.UNKNOWN && supportedMetrics.contains(metric)) {
                availableMetrics.add(metric);
            }
        }
        return availableMetrics;
    }

    private MetricSample.Metric getInitialMetric(final Bundle savedInstanceState) {
        if (metrics.isEmpty()) {
            return null;
        }

        if (savedInstanceState != null) {
            final String savedMetricName = savedInstanceState.getString(STATE_SELECTED_METRIC);
            if (savedMetricName != null) {
                try {
                    final MetricSample.Metric savedMetric = MetricSample.Metric.valueOf(savedMetricName);
                    if (metrics.contains(savedMetric)) {
                        return savedMetric;
                    }
                } catch (final IllegalArgumentException ignored) {
                    // Fall back to the first recorded metric.
                }
            }
        }

        return metrics.get(0);
    }

    private void setupMetricSpinner() {
        final List<MetricItem> metricItems = new ArrayList<>();
        for (final MetricSample.Metric metric : metrics) {
            metricItems.add(new MetricItem(metric, getMetricLabel(metric)));
        }

        final ArrayAdapter<MetricItem> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, metricItems);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        metricSpinner.setAdapter(adapter);
        metricSpinner.setEnabled(metricItems.size() > 1);
        metricSpinner.setPrompt(getString(R.string.generic_metric_select_metric));

        if (selectedMetric != null) {
            metricSpinner.setSelection(metrics.indexOf(selectedMetric));
        }

        metricSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(final AdapterView<?> parent, final View view, final int position, final long id) {
                final MetricSample.Metric metric = ((MetricItem) parent.getItemAtPosition(position)).metric;
                if (metric != selectedMetric) {
                    selectedMetric = metric;
                    refresh();
                }
            }

            @Override
            public void onNothingSelected(final AdapterView<?> parent) {
                // Keep the previous metric selected.
            }
        });
    }

    private GenericMetricChartsData createChartsData(final MetricSample.Metric metric, final List<? extends MetricSample> samples, final Date rangeStart, final Date rangeEnd) {
        if (samples.isEmpty()) {
            return GenericMetricChartsData.empty(metric, rangeStart, rangeEnd);
        }

        final long[] seconds = new long[samples.size()];
        final double[] values = new double[samples.size()];
        double yMin = Double.MAX_VALUE;
        double yMax = -Double.MAX_VALUE;
        double totalValue = 0;
        for (int i = 0; i < samples.size(); i++) {
            final MetricSample sample = samples.get(i);
            seconds[i] = sample.getTimestamp() / 1000L;
            values[i] = sample.getMetricScore();
            yMin = Math.min(yMin, values[i]);
            yMax = Math.max(yMax, values[i]);
            totalValue += values[i];
        }

        return new GenericMetricChartsData(
                metric, seconds, values, rangeStart.getTime() / 1000L, rangeEnd.getTime() / 1000L,
                yMin, yMax, totalValue / samples.size()
        );
    }

    private String formatMetricValue(final MetricSample.Metric metric, final double value, final boolean showUnit) {
        return valueFormatter.formatValue(value, metric.uomKey, showUnit).trim();
    }

    private String getMetricLabel(final MetricSample.Metric metric) {
        if (metric.labelResId == 0) {
            return metric.name();
        }
        return getString(metric.labelResId);
    }

    protected static class GenericMetricChartsData extends ChartsData {
        private final MetricSample.Metric metric;
        private final long[] seconds;
        private final double[] values;
        private final long startTs;
        private final long endTs;
        private final double yMin;
        private final double yMax;
        private final double averageValue;
        private final int sampleCount;

        private GenericMetricChartsData(final MetricSample.Metric metric, final long[] seconds, final double[] values, final long startTs, final long endTs, final double yMin, final double yMax, final double averageValue) {
            this.metric = metric;
            this.seconds = seconds;
            this.values = values;
            this.startTs = startTs;
            this.endTs = endTs;
            this.yMin = yMin;
            this.yMax = yMax;
            this.averageValue = averageValue;
            this.sampleCount = seconds.length;
        }

        private static GenericMetricChartsData empty(final MetricSample.Metric metric, final Date rangeStart, final Date rangeEnd) {
            return new GenericMetricChartsData(metric, new long[0], new double[0], rangeStart.getTime() / 1000L, rangeEnd.getTime() / 1000L, 0, 1, 0);
        }

        private boolean hasData() {
            return sampleCount > 0;
        }
    }

    private static class MetricItem {
        private final MetricSample.Metric metric;
        private final String label;

        private MetricItem(final MetricSample.Metric metric, final String label) {
            this.metric = metric;
            this.label = label;
        }

        @NonNull
        @Override
        public String toString() {
            return label;
        }
    }
}
