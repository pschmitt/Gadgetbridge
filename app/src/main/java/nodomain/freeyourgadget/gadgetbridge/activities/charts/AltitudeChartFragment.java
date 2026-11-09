/*  Copyright (C) 2026 Davide Gessa

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
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.Chart;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.altitude.AltitudeChartData;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartSelection;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.GbChartView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.AltitudeSample;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;

public class AltitudeChartFragment extends AbstractChartFragment<AltitudeChartFragment.AltitudeChartsData> {
    private int totalDays;

    private GbChartView chart;
    private TextView textTimeSpan;
    private TextView textAltitudeLatest;
    private TextView textAltitudeMin;
    private TextView textAltitudeMax;

    @Override
    public String getTitle() {
        return getString(R.string.menuitem_altitude);
    }

    @Override
    protected void init() {
        if (GBApplication.getPrefs().getBoolean("charts_range", true))
            totalDays = 30;
        else
            totalDays = 7;
    }

    @Override
    protected boolean isSingleDay() {
        return false;
    }

    @Override
    protected AltitudeChartsData refreshInBackground(ChartsHost chartsHost, DBHandler db, GBDevice device) {
        final long tsStart = getTSStart() * 1000L;
        final long tsEnd = getTSEnd() * 1000L;

        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        final TimeSampleProvider<? extends AltitudeSample> provider = coordinator.getAltitudeSampleProvider(device, db.getDaoSession());
        final List<? extends AltitudeSample> samples = provider.getAllSamples(tsStart, tsEnd);
        return new AltitudeChartsData(samples);
    }

    @Override
    protected void renderCharts() {
        chart.invalidate();
    }

    @Override
    protected void setupLegend(Chart<?> chart) {}

    @Override
    protected void updateChartsnUIThread(AltitudeChartsData chartsData) {
        updateChart(chartsData.samples);
        textTimeSpan.setText(DateTimeUtils.formatDaysUntil(totalDays, getTSEnd()));

        final List<? extends AltitudeSample> samples = chartsData.samples;
        if (samples.isEmpty()) {
            textAltitudeLatest.setText(getString(R.string.stats_empty_value));
            textAltitudeMin.setText(getString(R.string.stats_empty_value));
            textAltitudeMax.setText(getString(R.string.stats_empty_value));
            return;
        }

        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (final AltitudeSample sample : samples) {
            min = Math.min(min, sample.getAltitude());
            max = Math.max(max, sample.getAltitude());
        }
        final AltitudeSample latest = samples.get(samples.size() - 1);
        textAltitudeLatest.setText(formatAltitude(latest.getAltitude()));
        textAltitudeMin.setText(formatAltitude(min));
        textAltitudeMax.setText(formatAltitude(max));
    }

    private void updateChart(final List<? extends AltitudeSample> samples) {
        final int n = samples.size();
        final long[] seconds = new long[n];
        final double[] altitudes = new double[n];
        for (int i = 0; i < n; i++) {
            final AltitudeSample sample = samples.get(i);
            seconds[i] = sample.getTimestamp() / 1000L;
            altitudes[i] = sample.getAltitude();
        }
        final int color = ContextCompat.getColor(requireContext(), R.color.accent);
        final String label = getString(R.string.menuitem_altitude);
        final ChartSpec spec = AltitudeChartData.spec(getTSStart(), getTSEnd(), seconds, altitudes, label, color);
        chart.setSelectionContent(x -> {
            final long time = Math.round(x);
            final String title = DateTimeUtils.formatDateTime(new Date(time * 1000L));
            for (int i = 0; i < n; i++) {
                if (seconds[i] == time) {
                    final String text = formatAltitude((int) altitudes[i]);
                    return new ChartSelection(title,
                            Collections.singletonList(new ChartSelection.Row(color, text)),
                            title + ". " + label + ' ' + text + '.');
                }
            }
            return new ChartSelection(title, Collections.emptyList(), title + ".");
        });
        chart.setSpec(spec);
    }

    @Override
    protected int getTSStart() {
        return DateTimeUtils.shiftDays(getTSEnd(), -totalDays + 1);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_altitude_chart, container, false);

        chart = rootView.findViewById(R.id.altitude_chart);
        textTimeSpan = rootView.findViewById(R.id.altitude_time_span_text);
        textAltitudeLatest = rootView.findViewById(R.id.altitude_latest_text);
        textAltitudeMin = rootView.findViewById(R.id.altitude_min_text);
        textAltitudeMax = rootView.findViewById(R.id.altitude_max_text);

        chart.setZoomable(true);
        chart.dismissSelectionOnTapOutside(rootView);

        refresh();

        return rootView;
    }

    private String formatAltitude(final int altitude) {
        return getString(R.string.altitude_value_format, altitude);
    }

    protected static class AltitudeChartsData extends ChartsData {
        private final List<? extends AltitudeSample> samples;

        public AltitudeChartsData(final List<? extends AltitudeSample> samples) {
            this.samples = samples;
        }
    }

    @Nullable
    @Override
    public ChartDataRange getAvailableDataRange(final GBDevice device, final DBHandler db) {
        return ChartDataRange.ofSamples(device.getDeviceCoordinator().getAltitudeSampleProvider(device, db.getDaoSession()));
    }
}
