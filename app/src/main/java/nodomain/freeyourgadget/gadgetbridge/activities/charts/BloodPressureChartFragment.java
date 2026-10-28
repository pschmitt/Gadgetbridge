/*
    Copyright (C) 2026 Christian Breiteneder

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
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package nodomain.freeyourgadget.gadgetbridge.activities.charts;

import android.graphics.Color;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.github.mikephil.charting.charts.Chart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.HeartRateUtils;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.bloodpressure.BloodPressureChartData;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartLegendView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartSelection;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.GbChartView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericBloodPressureSample;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.BloodPressureSample;
import nodomain.freeyourgadget.gadgetbridge.util.Accumulator;
import nodomain.freeyourgadget.gadgetbridge.util.BloodPressureExportHelper;

public class BloodPressureChartFragment extends AbstractChartFragment<BloodPressureChartFragment.BloodPressureChartsData> {
    protected static final Logger LOG = LoggerFactory.getLogger(BloodPressureChartFragment.class);

    static int DATA_INVALID = -1;

    private TextView mDateView;
    private GbChartView mChart;
    private ChartLegendView mLegend;
    private LinearLayout mStatsContainer;
    private LinearLayout mManualMeasurements;
    private LinearLayout mManualMeasurementsList;
    private BloodPressureChartsData currentData;

    @Override
    protected void init() {
    }

    @Override
    public View onCreateView(final LayoutInflater inflater,
                             final ViewGroup container,
                             final Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_blood_pressure_chart, container, false);

        mDateView = rootView.findViewById(R.id.date_view);
        mChart = rootView.findViewById(R.id.blood_pressure_line_chart);
        mLegend = rootView.findViewById(R.id.blood_pressure_chart_legend);
        mStatsContainer = rootView.findViewById(R.id.bp_stats_container);
        mManualMeasurements = rootView.findViewById(R.id.manualMeasurements);
        mManualMeasurementsList = rootView.findViewById(R.id.manualMeasurementsList);

        mManualMeasurements.setVisibility(View.GONE);
        mChart.setZoomable(true);
        final View scrollView = rootView.findViewById(R.id.bp_scroll_view);
        scrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) ->
            getChartsHost().enableSwipeRefresh(scrollY == 0)
        );
        mChart.dismissSelectionOnTapOutside(scrollView);

        FloatingActionButton exportFab = rootView.findViewById(R.id.bp_export_fab);
        exportFab.setOnClickListener(v -> showExportDialog());

        refresh();
        return rootView;
    }

    @Override
    protected BloodPressureChartsData refreshInBackground(final ChartsHost chartsHost, final DBHandler db, final GBDevice device) {
        Calendar day = Calendar.getInstance();
        day.setTime(getEndDate());
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        int startTs = (int) (day.getTimeInMillis() / 1000);
        int endTs = startTs + 24 * 60 * 60 - 1;
        return fetchBloodPressureData(db, device, startTs, endTs);
    }

    static int getPulseRate(final BloodPressureSample sample) {
        if (sample instanceof GenericBloodPressureSample) {
            final Integer pulseRate = ((GenericBloodPressureSample) sample).getPulseRate();
            if (pulseRate != null) {
                return pulseRate;
            }
        }
        return DATA_INVALID;
    }

    @Override
    protected void updateChartsnUIThread(BloodPressureChartsData data) {
        currentData = data;
        mManualMeasurementsList.removeAllViews();
        mManualMeasurements.setVisibility(View.GONE);

        final String emptyValue = requireContext().getString(R.string.stats_empty_value);
        final String average = (data.systolicAvg > 0 && data.diastolicAvg > 0)
            ? getString(R.string.blood_pressure_avg_format, data.systolicAvg, data.diastolicAvg)
            : emptyValue;

        final List<StatTileData> stats = new ArrayList<>();
        stats.add(new StatTileData(data.systolicLast > 0 ? String.valueOf(data.systolicLast) : emptyValue, getString(R.string.blood_pressure_systolic)));
        stats.add(new StatTileData(data.diastolicLast > 0 ? String.valueOf(data.diastolicLast) : emptyValue, getString(R.string.blood_pressure_diastolic)));
        stats.add(new StatTileData(average, getString(R.string.hr_average)));
        stats.add(new StatTileData(String.valueOf(data.measurementCount), getString(R.string.blood_pressure_measurement_count)));
        mStatsContainer.removeAllViews();
        StatTileGridUtilKt.addStatTileGrid(mStatsContainer, requireContext(), stats, 0);

        Date date = new Date((long) getTSEnd() * 1000);
        String formattedDate = new SimpleDateFormat("E, MMM dd", Locale.getDefault()).format(date);
        mDateView.setText(formattedDate);

        final int systolicColor = ContextCompat.getColor(requireContext(), R.color.blood_pressure_systolic_color);
        final int diastolicColor = ContextCompat.getColor(requireContext(), R.color.blood_pressure_diastolic_color);
        final int heartRateColor = ContextCompat.getColor(requireContext(), R.color.chart_line_heart_rate);
        final String pressureUnit = getString(R.string.unit_millimetre_of_mercury);
        final String pulseUnit = getString(R.string.bpm);

        final int n = data.samples.size();
        final long[] seconds = new long[n];
        final int[] systolic = new int[n];
        final int[] diastolic = new int[n];
        final int[] pulse = new int[n];
        for (int i = 0; i < n; i++) {
            final BloodPressureSample sample = data.samples.get(i);
            seconds[i] = sample.getTimestamp() / 1000L;
            systolic[i] = sample.getBpSystolic();
            diastolic[i] = sample.getBpDiastolic();
            pulse[i] = getPulseRate(sample);
        }

        final String[] labels = {
            getString(R.string.blood_pressure_systolic),
            getString(R.string.blood_pressure_diastolic),
            getString(R.string.heart_rate)
        };
        final ChartSpec spec = BloodPressureChartData.daySpec(
            data.startTs, seconds, systolic, diastolic, pulse, data.systolicAvg,
            GBApplication.getPrefs().getBoolean("charts_show_average", true), Color.GRAY,
            labels, new int[]{systolicColor, diastolicColor, heartRateColor}, pressureUnit, pulseUnit,
            HeartRateUtils.getInstance().getMinHeartRate(), HeartRateUtils.getInstance().getMaxHeartRate()
        );
        mChart.setSelectionContent(x -> {
            final long time = Math.round(x);
            final String title = DateFormat.getTimeFormat(requireContext()).format(new Date(time * 1000L));
            final List<ChartSelection.Row> rows = new ArrayList<>();
            final StringBuilder description = new StringBuilder(title).append('.');
            for (int i = 0; i < n; i++) {
                if (seconds[i] != time) {
                    continue;
                }
                if (systolic[i] > 0 || diastolic[i] > 0) {
                    final String pressure = (systolic[i] > 0 ? String.valueOf(systolic[i]) : "\u2013") + "/"
                        + (diastolic[i] > 0 ? String.valueOf(diastolic[i]) : "\u2013") + " " + pressureUnit;
                    rows.add(new ChartSelection.Row(systolic[i] > 0 ? systolicColor : diastolicColor, pressure));
                    description.append(' ').append(pressure).append('.');
                }
                if (pulse[i] > 0) {
                    final String rate = pulse[i] + " " + pulseUnit;
                    rows.add(new ChartSelection.Row(heartRateColor, rate));
                    description.append(' ').append(labels[2]).append(' ').append(rate).append('.');
                }
                break;
            }
            return new ChartSelection(title, rows, description.toString());
        });
        mChart.setSpec(spec);

        final List<ChartSeries> legendSeries = new ArrayList<>(spec.getSeries());
        if (!spec.getLimitLines().isEmpty()) {
            legendSeries.add(ChartLegendView.lineItem(getString(R.string.hr_average), Color.GRAY));
        }
        mLegend.setSeries(legendSeries.size() > 1 ? legendSeries : Collections.emptyList());
    }

    @Override
    public String getTitle() {
        return requireContext().getString(R.string.blood_pressure);
    }

    private void showExportDialog() {
        if (currentData == null || currentData.samples.isEmpty()) {
            return;
        }
        String dateLabel = mDateView.getText().toString();
        String[] options = {"PDF", "CSV"};
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.appmanager_app_share)
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    BloodPressureExportHelper.exportPdf(requireContext(), currentData.samples, dateLabel, mChart.toLightBitmap());
                } else {
                    BloodPressureExportHelper.exportCsv(requireContext(), currentData.samples);
                }
            })
            .show();
    }

    @Override
    protected void setupLegend(final Chart<?> chart) {
    }

    @Override
    protected void renderCharts() {
        mChart.invalidate();
    }

    private List<? extends BloodPressureSample> getSamples(final DBHandler db, final GBDevice device, int startTs, int endTs) {
        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        final TimeSampleProvider<? extends BloodPressureSample> sampleProvider = coordinator.getBloodPressureSampleProvider(device, db.getDaoSession());
        return sampleProvider.getAllSamples(startTs * 1000L, endTs * 1000L);
    }

    private BloodPressureChartsData fetchBloodPressureData(DBHandler db, GBDevice device, int startTs, int endTs) {
        List<? extends BloodPressureSample> samples = getSamples(db, device, startTs, endTs);

        final Accumulator systolicAccumulator = new Accumulator();
        final Accumulator diastolicAccumulator = new Accumulator();
        int systolicLast = DATA_INVALID;
        int diastolicLast = DATA_INVALID;

        for (final BloodPressureSample sample : samples) {
            if (sample.getBpSystolic() > 0) {
                systolicAccumulator.add(sample.getBpSystolic());
                systolicLast = sample.getBpSystolic();
            }
            if (sample.getBpDiastolic() > 0) {
                diastolicAccumulator.add(sample.getBpDiastolic());
                diastolicLast = sample.getBpDiastolic();
            }
        }

        final int systolicAvg = systolicAccumulator.getCount() > 0 ? (int) Math.round(systolicAccumulator.getAverage()) : DATA_INVALID;
        final int diastolicAvg = diastolicAccumulator.getCount() > 0 ? (int) Math.round(diastolicAccumulator.getAverage()) : DATA_INVALID;
        final int measurementCount = samples.size();

        return new BloodPressureChartsData(samples, startTs, systolicAvg, diastolicAvg, systolicLast, diastolicLast, measurementCount);
    }

    protected static class BloodPressureChartsData extends ChartsData {
        public List<? extends BloodPressureSample> samples;
        public final int startTs;
        public final int systolicAvg;
        public final int diastolicAvg;
        public final int systolicLast;
        public final int diastolicLast;
        public final int measurementCount;

        public BloodPressureChartsData(List<? extends BloodPressureSample> samples, int startTs, int systolicAvg, int diastolicAvg,
                                       int systolicLast, int diastolicLast, int measurementCount) {
            this.samples = samples;
            this.startTs = startTs;
            this.systolicAvg = systolicAvg;
            this.diastolicAvg = diastolicAvg;
            this.systolicLast = systolicLast;
            this.diastolicLast = diastolicLast;
            this.measurementCount = measurementCount;
        }
    }

    @Nullable
    @Override
    public ChartDataRange getAvailableDataRange(final GBDevice device, final DBHandler db) {
        return ChartDataRange.ofSamples(device.getDeviceCoordinator().getBloodPressureSampleProvider(device, db.getDaoSession()));
    }
}
