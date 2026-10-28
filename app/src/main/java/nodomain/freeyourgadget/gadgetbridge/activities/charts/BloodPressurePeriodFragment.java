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

import android.os.Bundle;
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

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import kotlin.jvm.functions.Function1;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.HeartRateUtils;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.bloodpressure.BloodPressureChartData;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartLegendView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.DaySelections;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.GbChartView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.BloodPressureSample;
import nodomain.freeyourgadget.gadgetbridge.util.Accumulator;
import nodomain.freeyourgadget.gadgetbridge.util.BloodPressureExportHelper;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;

public class BloodPressurePeriodFragment extends AbstractChartFragment<BloodPressurePeriodFragment.BloodPressurePeriodData> {
    protected static final Logger LOG = LoggerFactory.getLogger(BloodPressurePeriodFragment.class);

    static int SEC_PER_DAY = 24 * 60 * 60;
    static int DATA_INVALID = -1;

    private int SYSTOLIC_COLOR;
    private int DIASTOLIC_COLOR;
    private int HEART_RATE_COLOR;

    private TextView mDateView;
    private LinearLayout mStatsContainer;
    private GbChartView mChart;
    private ChartLegendView mLegend;
    private int TOTAL_DAYS;
    private List<? extends BloodPressureSample> allSamples;

    @Override
    protected boolean isSingleDay() {
        return false;
    }

    public static BloodPressurePeriodFragment newInstance(int totalDays) {
        BloodPressurePeriodFragment fragment = new BloodPressurePeriodFragment();
        Bundle args = new Bundle();
        args.putInt("totalDays", totalDays);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TOTAL_DAYS = getArguments() != null ? getArguments().getInt("totalDays") : 7;
    }

    @Override
    protected void init() {
        SYSTOLIC_COLOR = ContextCompat.getColor(requireContext(), R.color.blood_pressure_systolic_color);
        DIASTOLIC_COLOR = ContextCompat.getColor(requireContext(), R.color.blood_pressure_diastolic_color);
        HEART_RATE_COLOR = ContextCompat.getColor(requireContext(), GBApplication.getPrefs().getChartsHeartRateColor().getColorRes());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_blood_pressure_period, container, false);

        final View scrollView = rootView.findViewById(R.id.bp_scroll_view);
        scrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) ->
            getChartsHost().enableSwipeRefresh(scrollY == 0)
        );

        mDateView = rootView.findViewById(R.id.date_view);
        mStatsContainer = rootView.findViewById(R.id.bp_stats_container);
        mChart = rootView.findViewById(R.id.blood_pressure_chart);
        mLegend = rootView.findViewById(R.id.blood_pressure_period_legend);
        mChart.setZoomable(TOTAL_DAYS > 7);
        mChart.dismissSelectionOnTapOutside(scrollView);

        FloatingActionButton exportFab = rootView.findViewById(R.id.bp_export_fab);
        exportFab.setOnClickListener(v -> showExportDialog());

        refresh();

        return rootView;
    }

    @Override
    public String getTitle() {
        return getString(R.string.blood_pressure);
    }

    private int getStartTs() {
        final Calendar day = Calendar.getInstance();
        day.setTime(getEndDate());
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.add(Calendar.DATE, -(TOTAL_DAYS - 1));
        return (int) (day.getTimeInMillis() / 1000);
    }

    private BloodPressureDayData fetchDataForDay(DBHandler db, GBDevice device, int startTs) {
        int endTs = startTs + SEC_PER_DAY - 1;
        List<? extends BloodPressureSample> samples = getSamples(db, device, startTs, endTs);

        final Accumulator systolicAcc = new Accumulator();
        final Accumulator diastolicAcc = new Accumulator();
        final Accumulator heartRateAcc = new Accumulator();

        for (final BloodPressureSample sample : samples) {
            if (sample.getBpSystolic() > 0) {
                systolicAcc.add(sample.getBpSystolic());
            }
            if (sample.getBpDiastolic() > 0) {
                diastolicAcc.add(sample.getBpDiastolic());
            }
            final int pulseRate = BloodPressureChartFragment.getPulseRate(sample);
            if (pulseRate > 0) {
                heartRateAcc.add(pulseRate);
            }
        }

        final int systolicAvg = systolicAcc.getCount() > 0 ? (int) Math.round(systolicAcc.getAverage()) : DATA_INVALID;
        final int systolicMin = systolicAcc.getCount() > 0 ? (int) Math.round(systolicAcc.getMin()) : DATA_INVALID;
        final int systolicMax = systolicAcc.getCount() > 0 ? (int) Math.round(systolicAcc.getMax()) : DATA_INVALID;
        final int diastolicAvg = diastolicAcc.getCount() > 0 ? (int) Math.round(diastolicAcc.getAverage()) : DATA_INVALID;
        final int diastolicMin = diastolicAcc.getCount() > 0 ? (int) Math.round(diastolicAcc.getMin()) : DATA_INVALID;
        final int diastolicMax = diastolicAcc.getCount() > 0 ? (int) Math.round(diastolicAcc.getMax()) : DATA_INVALID;
        final int heartRateAvg = heartRateAcc.getCount() > 0 ? (int) Math.round(heartRateAcc.getAverage()) : DATA_INVALID;

        return new BloodPressureDayData(systolicAvg, systolicMin, systolicMax, diastolicAvg, diastolicMin, diastolicMax, heartRateAvg, samples.size());
    }

    @Override
    protected BloodPressurePeriodData refreshInBackground(ChartsHost chartsHost, DBHandler db, GBDevice device) {
        final int startTs = getStartTs();
        final int endTs = startTs + SEC_PER_DAY * TOTAL_DAYS - 1;

        List<BloodPressureDayData> result = new ArrayList<>();
        for (int i = 0; i < TOTAL_DAYS; i++) {
            BloodPressureDayData dayData = fetchDataForDay(db, device, startTs + i * SEC_PER_DAY);
            result.add(dayData);
        }

        final List<? extends BloodPressureSample> samples = getSamples(db, device, startTs, endTs);
        return new BloodPressurePeriodData(result, samples);
    }

    private List<? extends BloodPressureSample> getSamples(DBHandler db, GBDevice device, int startTs, int endTs) {
        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        final TimeSampleProvider<? extends BloodPressureSample> sampleProvider = coordinator.getBloodPressureSampleProvider(device, db.getDaoSession());
        return sampleProvider.getAllSamples(startTs * 1000L, endTs * 1000L);
    }

    @Override
    protected void updateChartsnUIThread(BloodPressurePeriodData data) {
        final int startTs = getStartTs();
        allSamples = data.samples;
        mDateView.setText(DateTimeUtils.formatDaysUntil(TOTAL_DAYS, getTSEnd()));

        final Accumulator systolicAvgAcc = new Accumulator();
        final Accumulator diastolicAvgAcc = new Accumulator();

        final int n = data.days.size();
        final long firstDay = Instant.ofEpochSecond(startTs).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay();
        final long[] epochDays = new long[n];
        final int[] systolicMin = new int[n];
        final int[] systolicMax = new int[n];
        final int[] diastolicMin = new int[n];
        final int[] diastolicMax = new int[n];
        final int[] heartRateAvg = new int[n];
        for (int i = 0; i < n; i++) {
            final BloodPressureDayData dayData = data.days.get(i);
            epochDays[i] = firstDay + i;
            if (dayData.systolicMin > 0 && dayData.systolicMax > 0) {
                systolicAvgAcc.add(dayData.systolicAvg);
                systolicMin[i] = dayData.systolicMin;
                systolicMax[i] = dayData.systolicMax;
            }
            if (dayData.diastolicMin > 0 && dayData.diastolicMax > 0) {
                diastolicAvgAcc.add(dayData.diastolicAvg);
                diastolicMin[i] = dayData.diastolicMin;
                diastolicMax[i] = dayData.diastolicMax;
            }
            heartRateAvg[i] = Math.max(dayData.heartRateAvg, 0);
        }

        final String emptyValue = requireContext().getString(R.string.stats_empty_value);
        final int systolicAvg = systolicAvgAcc.getCount() > 0 ? (int) Math.round(systolicAvgAcc.getAverage()) : DATA_INVALID;
        final int diastolicAvg = diastolicAvgAcc.getCount() > 0 ? (int) Math.round(diastolicAvgAcc.getAverage()) : DATA_INVALID;

        // Last day with valid data
        int systolicLast = DATA_INVALID;
        int diastolicLast = DATA_INVALID;
        int totalMeasurements = 0;
        for (int i = data.days.size() - 1; i >= 0; i--) {
            final BloodPressureDayData dayData = data.days.get(i);
            if (dayData.systolicAvg > 0 && systolicLast == DATA_INVALID) {
                systolicLast = dayData.systolicAvg;
            }
            if (dayData.diastolicAvg > 0 && diastolicLast == DATA_INVALID) {
                diastolicLast = dayData.diastolicAvg;
            }
            totalMeasurements += dayData.measurementCount;
        }

        final String average = (systolicAvg > 0 && diastolicAvg > 0)
            ? getString(R.string.blood_pressure_avg_format, systolicAvg, diastolicAvg)
            : emptyValue;

        final List<StatTileData> stats = new ArrayList<>();
        stats.add(new StatTileData(systolicLast > 0 ? String.valueOf(systolicLast) : emptyValue, getString(R.string.blood_pressure_systolic)));
        stats.add(new StatTileData(diastolicLast > 0 ? String.valueOf(diastolicLast) : emptyValue, getString(R.string.blood_pressure_diastolic)));
        stats.add(new StatTileData(average, getString(R.string.hr_average)));
        stats.add(new StatTileData(String.valueOf(totalMeasurements), getString(R.string.blood_pressure_measurement_count)));
        mStatsContainer.removeAllViews();
        StatTileGridUtilKt.addStatTileGrid(mStatsContainer, requireContext(), stats, 0);

        final String pressureUnit = getString(R.string.unit_millimetre_of_mercury);
        final String pulseUnit = getString(R.string.bpm);
        final List<String> labels = Arrays.asList(
            getString(R.string.blood_pressure_systolic),
            getString(R.string.blood_pressure_diastolic),
            getString(R.string.heart_rate)
        );
        final List<Integer> colors = Arrays.asList(SYSTOLIC_COLOR, DIASTOLIC_COLOR, HEART_RATE_COLOR);
        final ChartSpec spec = BloodPressureChartData.periodSpec(
            epochDays, systolicMin, systolicMax, diastolicMin, diastolicMax, heartRateAvg,
            labels.toArray(new String[0]), new int[]{SYSTOLIC_COLOR, DIASTOLIC_COLOR, HEART_RATE_COLOR},
            pressureUnit, pulseUnit,
            HeartRateUtils.getInstance().getMinHeartRate(), HeartRateUtils.getInstance().getMaxHeartRate()
        );
        final List<Function1<Integer, String>> rowTexts = new ArrayList<>();
        rowTexts.add(i -> systolicMin[i] > 0 ? systolicMin[i] + " \u2013 " + systolicMax[i] + " " + pressureUnit : emptyValue);
        rowTexts.add(i -> diastolicMin[i] > 0 ? diastolicMin[i] + " \u2013 " + diastolicMax[i] + " " + pressureUnit : emptyValue);
        rowTexts.add(i -> heartRateAvg[i] > 0 ? heartRateAvg[i] + " " + pulseUnit : emptyValue);
        mChart.setSelectionContent(x -> DaySelections.of(epochDays, x, labels, colors, rowTexts, emptyValue));
        mChart.setSpec(spec);
        mLegend.setSeries(spec.getSeries().size() > 1 ? spec.getSeries() : Collections.emptyList());
    }

    private void showExportDialog() {
        if (allSamples == null || allSamples.isEmpty()) {
            return;
        }
        String dateLabel = mDateView.getText().toString();
        String[] options = {"PDF", "CSV"};
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.appmanager_app_share)
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    BloodPressureExportHelper.exportPdf(requireContext(), allSamples, dateLabel, mChart.toLightBitmap());
                } else {
                    BloodPressureExportHelper.exportCsv(requireContext(), allSamples);
                }
            })
            .show();
    }

    @Override
    protected void setupLegend(Chart<?> chart) {
    }

    @Override
    protected void renderCharts() {
        mChart.invalidate();
    }

    protected static class BloodPressurePeriodData extends ChartsData {
        public List<BloodPressureDayData> days;
        public List<? extends BloodPressureSample> samples;

        protected BloodPressurePeriodData(List<BloodPressureDayData> days,
                                          List<? extends BloodPressureSample> samples) {
            this.days = days;
            this.samples = samples;
        }
    }

    protected static class BloodPressureDayData extends ChartsData {
        public int systolicAvg;
        public int systolicMin;
        public int systolicMax;
        public int diastolicAvg;
        public int diastolicMin;
        public int diastolicMax;
        public int heartRateAvg;
        public int measurementCount;

        protected BloodPressureDayData(int systolicAvg, int systolicMin, int systolicMax,
                                       int diastolicAvg, int diastolicMin, int diastolicMax,
                                       int heartRateAvg, int measurementCount) {
            this.systolicAvg = systolicAvg;
            this.systolicMin = systolicMin;
            this.systolicMax = systolicMax;
            this.diastolicAvg = diastolicAvg;
            this.diastolicMin = diastolicMin;
            this.diastolicMax = diastolicMax;
            this.heartRateAvg = heartRateAvg;
            this.measurementCount = measurementCount;
        }
    }

    @Nullable
    @Override
    public ChartDataRange getAvailableDataRange(final GBDevice device, final DBHandler db) {
        return ChartDataRange.ofSamples(device.getDeviceCoordinator().getBloodPressureSampleProvider(device, db.getDaoSession()));
    }
}
