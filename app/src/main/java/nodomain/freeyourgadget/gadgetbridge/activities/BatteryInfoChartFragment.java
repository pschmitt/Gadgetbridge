/*  Copyright (C) 2021-2026 José Rebelo, Petr Vaněk

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
package nodomain.freeyourgadget.gadgetbridge.activities;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.battery.BatteryChartData;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartSelection;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartValueFormat;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutValueFormatter;
import nodomain.freeyourgadget.gadgetbridge.database.DBAccess;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.databinding.FragmentBatteryChartBinding;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;
import nodomain.freeyourgadget.gadgetbridge.util.preferences.DevicePrefs;

public class BatteryInfoChartFragment extends AbstractGBFragment {
    private static final Logger LOG = LoggerFactory.getLogger(BatteryInfoChartFragment.class);
    private static final String PREF_SELECTED_METRICS_PREFIX = "chart_battery_selected_metrics_";
    private static final String STATE_SELECTED_METRICS = "selectedMetrics";

    private FragmentBatteryChartBinding binding;

    private int startTime;
    private int endTime;
    private GBDevice gbDevice;
    private int batteryIndex;
    private Set<BatteryMetric> selectedMetrics = EnumSet.of(BatteryMetric.LEVEL);
    private boolean selectedMetricsInitialized;
    private RefreshTask refreshTask;
    private final WorkoutValueFormatter valueFormatter = new WorkoutValueFormatter();

    public void setDateAndGetData(final GBDevice gbDevice, final int batteryIndex, final long startTime, final long endTime) {
        this.startTime = (int) startTime;
        this.endTime = (int) endTime;
        this.gbDevice = gbDevice;
        this.batteryIndex = batteryIndex;
        ensureSelectedMetricsLoaded();
        try {
            startRefreshTask();
        } catch (final Exception e) {
            LOG.debug("Unable to fill charts data right now:", e);
        }
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        binding = FragmentBatteryChartBinding.inflate(inflater, container, false);

        if (savedInstanceState != null) {
            final String[] saved = savedInstanceState.getStringArray(STATE_SELECTED_METRICS);
            if (saved != null && saved.length > 0) {
                selectedMetrics = parseMetrics(saved);
                selectedMetricsInitialized = true;
            }
        } else {
            ensureSelectedMetricsLoaded();
        }

        setupChart();

        if (gbDevice != null) {
            startRefreshTask();
        }

        return binding.getRoot();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArray(STATE_SELECTED_METRICS, metricsToNames(selectedMetrics));
    }

    @Override
    public void onDestroyView() {
        if (refreshTask != null) {
            refreshTask.cancel(true);
            refreshTask = null;
        }
        binding = null;
        super.onDestroyView();
    }

    @Override
    public String getTitle() {
        return "";
    }

    private void setupChart() {
        binding.batteryChart.setZoomable(true);
        binding.batteryChart.dismissSelectionOnTapOutside(binding.getRoot());
    }

    private void startRefreshTask() {
        if (refreshTask != null) {
            refreshTask.cancel(true);
        }
        refreshTask = new RefreshTask("Visualizing battery data", getActivity(), gbDevice, batteryIndex, startTime, endTime, selectedMetrics);
        refreshTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void onMetricToggled(final BatteryMetric metric, final boolean checked, final Chip chip) {
        if (!checked && selectedMetrics.size() <= 1) {
            // keep at least one metric selected; revert the chip
            chip.setChecked(true);
            Toast.makeText(requireContext(), R.string.charts_at_least_one_item, Toast.LENGTH_SHORT).show();
            return;
        }

        final Set<BatteryMetric> updated = EnumSet.copyOf(selectedMetrics);
        if (checked) {
            updated.add(metric);
        } else {
            updated.remove(metric);
        }
        selectedMetrics = updated;
        saveSelectedMetrics(selectedMetrics);
        startRefreshTask();
    }

    private void rebuildChips(final Set<BatteryMetric> availableMetrics) {
        if (binding == null) {
            return;
        }
        binding.batteryChartChipGroup.removeAllViews();

        if (availableMetrics.size() <= 1) {
            // single chip, hide it
            binding.batteryChartChipGroup.setVisibility(View.GONE);
            return;
        }
        binding.batteryChartChipGroup.setVisibility(View.VISIBLE);

        for (final BatteryMetric metric : BatteryMetric.values()) {
            if (!availableMetrics.contains(metric)) {
                continue;
            }
            final Chip chip = new Chip(requireContext());
            chip.setText(getString(metric.labelResId));
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setChecked(selectedMetrics.contains(metric));
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> onMetricToggled(metric, isChecked, (Chip) buttonView));
            binding.batteryChartChipGroup.addView(chip);
        }
    }

    private void ensureSelectedMetricsLoaded() {
        if (!selectedMetricsInitialized && gbDevice != null) {
            selectedMetrics = loadSelectedMetrics();
            selectedMetricsInitialized = true;
        }
    }

    private Set<BatteryMetric> loadSelectedMetrics() {
        if (gbDevice == null) {
            return EnumSet.of(BatteryMetric.LEVEL);
        }
        final DevicePrefs prefs = GBApplication.getDevicePrefs(gbDevice);
        final String csv = prefs.getString(PREF_SELECTED_METRICS_PREFIX + batteryIndex, null);
        if (csv == null || csv.trim().isEmpty()) {
            return EnumSet.of(BatteryMetric.LEVEL);
        }
        final Set<BatteryMetric> parsed = parseMetrics(csv.split(","));
        return parsed.isEmpty() ? EnumSet.of(BatteryMetric.LEVEL) : parsed;
    }

    private void saveSelectedMetrics(final Set<BatteryMetric> metrics) {
        if (gbDevice == null) {
            return;
        }
        final StringBuilder sb = new StringBuilder();
        for (final BatteryMetric metric : metrics) {
            //noinspection SizeReplaceableByIsEmpty isEmpty requires SDK 35
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(metric.name());
        }
        GBApplication.getDevicePrefs(gbDevice).getPreferences().edit()
                .putString(PREF_SELECTED_METRICS_PREFIX + batteryIndex, sb.toString())
                .apply();
    }

    private static Set<BatteryMetric> parseMetrics(final String[] names) {
        final Set<BatteryMetric> metrics = new LinkedHashSet<>();
        for (final String name : names) {
            try {
                metrics.add(BatteryMetric.valueOf(name.trim()));
            } catch (final IllegalArgumentException ignored) {
                // metric no longer exists, skip it
            }
        }
        return metrics;
    }

    private static String[] metricsToNames(final Set<BatteryMetric> metrics) {
        final String[] names = new String[metrics.size()];
        int i = 0;
        for (final BatteryMetric metric : metrics) {
            names[i++] = metric.name();
        }
        return names;
    }

    private String formatMetricValue(final BatteryMetric metric, final double value, final boolean showUnit) {
        return valueFormatter.formatValue(value, metric.uomKey, showUnit).trim();
    }

    private String formatMetricRange(final BatteryMetric metric, final double min, final double max) {
        if (min >= max) {
            return formatMetricValue(metric, min, true);
        }
        return formatMetricValue(metric, min, false) + "\u2013" + formatMetricValue(metric, max, true);
    }

    private int lineColor(final BatteryMetric metric) {
        if (metric == BatteryMetric.LEVEL) {
            return MaterialColors.getColor(requireContext(), R.attr.textColorPrimary, Color.GRAY);
        }
        return ContextCompat.getColor(requireContext(), metric.colorResId);
    }

    private ChartSelection selection(final double x,
                                     final List<BatteryMetric> metrics,
                                     final List<BatteryChartData.Metric> chartMetrics,
                                     final boolean singleDay,
                                     final int tsFrom,
                                     final int tsTo) {
        final Date date = new Date(Math.round(x) * 1000L);
        final String title = singleDay
                ? DateFormat.getTimeFormat(requireContext()).format(date)
                : DateTimeUtils.formatDateTime(date);
        final List<ChartSelection.Row> rows = new ArrayList<>();
        final StringBuilder description = new StringBuilder(title).append('.');
        for (int i = 0; i < chartMetrics.size(); i++) {
            final BatteryChartData.Metric chartMetric = chartMetrics.get(i);
            final int index = BatteryChartData.nearestSample(chartMetric.getSeconds(), x, tsFrom, tsTo);
            if (index < 0) {
                continue;
            }
            final String text = formatMetricValue(metrics.get(i), chartMetric.getValues()[index], true);
            rows.add(new ChartSelection.Row(chartMetric.getColor(), text));
            description.append(' ').append(getString(metrics.get(i).labelResId)).append(' ').append(text).append('.');
        }
        return new ChartSelection(title, rows, description.toString());
    }

    /**
     * A small header of (icon, value) pairs - one per metric ever recorded for this battery
     * index, showing its overall most recent value - so the current readings are always visible,
     * regardless of which chips or time window are currently selected. Each pair is tinted to match
     * its line/chip.
     * <p>
     * {@link BatteryMetric#LEVEL} is deliberately skipped: it's already shown by the large
     * percentage label above this fragment, and repeating it here just duplicates that label.
     *
     * @return whether any (icon, value) pair was actually added
     */
    private boolean populateLatestValues(final LinearLayout container, final Set<BatteryMetric> availableMetrics, final Map<BatteryMetric, BatteryMetric.Sample> latestSampleByMetric) {
        container.removeAllViews();
        final LayoutInflater inflater = LayoutInflater.from(requireContext());
        boolean addedAny = false;
        for (final BatteryMetric metric : BatteryMetric.values()) {
            if (metric == BatteryMetric.LEVEL || !availableMetrics.contains(metric)) {
                continue;
            }
            final BatteryMetric.Sample latest = latestSampleByMetric.get(metric);
            if (latest == null) {
                continue;
            }
            final int metricColor = ContextCompat.getColor(requireContext(), metric.colorResId);

            final View item = inflater.inflate(R.layout.item_battery_metric_value, container, false);
            final ImageView icon = item.findViewById(R.id.battery_metric_value_icon);
            icon.setImageResource(metric.iconResId);
            icon.setColorFilter(metricColor);

            final TextView value = item.findViewById(R.id.battery_metric_value_text);
            value.setText(formatMetricValue(metric, latest.value(), true));
            value.setTextColor(metricColor);

            container.addView(item);
            addedAny = true;
        }
        return addedAny;
    }

    @SuppressLint("StaticFieldLeak")
    private class RefreshTask extends DBAccess {
        private final GBDevice device;
        private final int batteryIndex;
        private final int tsFrom;
        private final int tsTo;
        private final Set<BatteryMetric> requestedMetrics;

        private final Set<BatteryMetric> availableMetrics = EnumSet.noneOf(BatteryMetric.class);
        private Set<BatteryMetric> loadedMetrics = EnumSet.noneOf(BatteryMetric.class);
        private final Map<BatteryMetric, List<BatteryMetric.Sample>> samplesByMetric = new EnumMap<>(BatteryMetric.class);
        private final Map<BatteryMetric, BatteryMetric.Sample> latestSampleByMetric = new EnumMap<>(BatteryMetric.class);

        RefreshTask(final String task, final Context context, final GBDevice device, final int batteryIndex, final int tsFrom, final int tsTo, final Set<BatteryMetric> requestedMetrics) {
            super(task, context, false);
            this.device = device;
            this.batteryIndex = batteryIndex;
            this.tsFrom = tsFrom;
            this.tsTo = tsTo;
            this.requestedMetrics = EnumSet.copyOf(requestedMetrics);
        }

        @Override
        protected void doInBackground(final DBHandler handler) {
            for (final BatteryMetric metric : BatteryMetric.values()) {
                if (metric == BatteryMetric.LEVEL || metric.hasSamples(handler, device, batteryIndex)) {
                    availableMetrics.add(metric);
                    final BatteryMetric.Sample latest = metric.loadLatestSample(handler, device, batteryIndex);
                    if (latest != null) {
                        latestSampleByMetric.put(metric, latest);
                    }
                }
            }

            loadedMetrics = EnumSet.copyOf(requestedMetrics);
            loadedMetrics.retainAll(availableMetrics);
            if (loadedMetrics.isEmpty()) {
                loadedMetrics = EnumSet.of(BatteryMetric.LEVEL);
            }

            for (final BatteryMetric metric : loadedMetrics) {
                samplesByMetric.put(metric, metric.loadSamples(handler, device, batteryIndex, tsFrom * 1000L, tsTo * 1000L));
            }
        }

        @Override
        protected void onPostExecute(final Object o) {
            super.onPostExecute(o);
            if (getTaskError() != null || binding == null) {
                return;
            }

            if (!loadedMetrics.equals(selectedMetrics)) {
                selectedMetrics = loadedMetrics;
                saveSelectedMetrics(selectedMetrics);
            }

            rebuildChips(availableMetrics);
            renderChart();
        }

        private void renderChart() {
            final List<BatteryMetric> plottedMetrics = new ArrayList<>();
            final List<BatteryChartData.Metric> chartMetrics = new ArrayList<>();
            for (final BatteryMetric metric : BatteryMetric.values()) {
                final List<BatteryMetric.Sample> samples = samplesByMetric.get(metric);
                if (!loadedMetrics.contains(metric) || samples == null || samples.isEmpty()) {
                    continue;
                }
                final long[] seconds = new long[samples.size()];
                final double[] values = new double[samples.size()];
                double min = Double.MAX_VALUE;
                double max = -Double.MAX_VALUE;
                for (int i = 0; i < samples.size(); i++) {
                    seconds[i] = samples.get(i).timestampSeconds();
                    values[i] = samples.get(i).value();
                    min = Math.min(min, values[i]);
                    max = Math.max(max, values[i]);
                }
                final boolean level = metric == BatteryMetric.LEVEL;
                plottedMetrics.add(metric);
                chartMetrics.add(new BatteryChartData.Metric(
                        metric.name(),
                        getString(R.string.generic_metric_chart_label_with_unit, getString(metric.labelResId), formatMetricRange(metric, min, max)),
                        lineColor(metric),
                        seconds,
                        values,
                        metric.minAxisSpan,
                        level ? Double.valueOf(0) : null,
                        level ? Double.valueOf(100) : null,
                        value -> formatMetricValue(metric, value, false)
                ));
            }

            // the header shows "live" readings, which are meaningless once the device has
            // disconnected and can no longer be trusted to be current
            final boolean deviceConnected = device.isConnected();
            final boolean hasLatestValues = deviceConnected
                    && populateLatestValues(binding.batteryChartLatestValues, availableMetrics, latestSampleByMetric);
            if (!deviceConnected) {
                binding.batteryChartLatestValues.removeAllViews();
            }
            binding.batteryChartLatestValues.setVisibility(hasLatestValues ? View.VISIBLE : View.GONE);

            final ChartSpec spec = BatteryChartData.spec(tsFrom, tsTo, chartMetrics);
            final boolean singleDay = spec.getXAxis().getFormat() == ChartValueFormat.TIME_OF_DAY;
            binding.batteryChart.setSelectionContent(x -> selection(x, plottedMetrics, chartMetrics, singleDay, tsFrom, tsTo));
            binding.batteryChart.setSpec(spec);
            binding.batteryChartLegend.setSeries(spec.getSeries());
        }
    }
}
