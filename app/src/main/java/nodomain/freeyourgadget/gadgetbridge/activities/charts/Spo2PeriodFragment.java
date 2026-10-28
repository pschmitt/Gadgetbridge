/*  Copyright (C) 2023-2024 Martin.JM, a0z

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
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.Chart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import kotlin.jvm.functions.Function1;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartLegendView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.DaySelections;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.GbChartView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spo2.Spo2ChartData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.Spo2Sample;
import nodomain.freeyourgadget.gadgetbridge.util.Accumulator;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;

public class Spo2PeriodFragment extends AbstractChartFragment<Spo2PeriodFragment.Spo2PeriodData> {
    protected static final Logger LOG = LoggerFactory.getLogger(Spo2PeriodFragment.class);

    static int SEC_PER_DAY = 24 * 60 * 60;
    static int DATA_INVALID = -1;

    private int SPO2_COLOR;
    private int SPO2_AVG_COLOR;

    private TextView mDateView;
    private LinearLayout spo2StatsContainer;
    private GbChartView spo2Chart;
    private ChartLegendView spo2Legend;
    private int TOTAL_DAYS;

    @Override
    protected boolean isSingleDay() {
        return false;
    }

    public static Spo2PeriodFragment newInstance(int totalDays) {
        Spo2PeriodFragment fragment = new Spo2PeriodFragment();
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
        TypedValue runningColor = new TypedValue();
        SPO2_COLOR = ContextCompat.getColor(requireContext(), R.color.spo2_color);
        requireContext().getTheme().resolveAttribute(R.attr.spo2_avg_color, runningColor, true);
        SPO2_AVG_COLOR = runningColor.data;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_spo2_period, container, false);

        rootView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            getChartsHost().enableSwipeRefresh(scrollY == 0);
        });

        mDateView = rootView.findViewById(R.id.date_view);
        spo2StatsContainer = rootView.findViewById(R.id.spo2_period_stats_container);
        spo2Chart = rootView.findViewById(R.id.spo2_chart);
        spo2Chart.setZoomable(TOTAL_DAYS > 7);
        spo2Chart.dismissSelectionOnTapOutside(rootView);
        spo2Legend = rootView.findViewById(R.id.spo2_chart_legend);

        refresh();

        return rootView;
    }

    @Override
    public String getTitle() {
        return getString(R.string.pref_header_spo2);
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

    private Spo2DayData fetchSpo2DataForDay(DBHandler db, GBDevice device, int startTs) {
        int endTs = startTs + SEC_PER_DAY - 1;
        List<? extends Spo2Sample> samples = getSamples(db, device, startTs, endTs);

        final Accumulator accumulator = new Accumulator();
        for (final Spo2Sample sample : samples) {
            if (sample.getSpo2() > 0) {
                accumulator.add(sample.getSpo2());
            }
        }

        final int average = accumulator.getCount() > 0 ? (int) Math.round(accumulator.getAverage()) : DATA_INVALID;
        final int minimum = accumulator.getCount() > 0 ? (int) Math.round(accumulator.getMin()) : DATA_INVALID;
        final int maximum = accumulator.getCount() > 0 ? (int) Math.round(accumulator.getMax()) : DATA_INVALID;

        return new Spo2DayData(average, minimum, maximum);
    }

    @Override
    protected Spo2PeriodData refreshInBackground(ChartsHost chartsHost, DBHandler db, GBDevice device) {
        final int startTs = getStartTs();

        List<Spo2DayData> result = new ArrayList<>();
        for (int i = 0; i < TOTAL_DAYS; i++) {
            Spo2DayData dayData = fetchSpo2DataForDay(db, device, startTs + i * SEC_PER_DAY);
            result.add(dayData);
        }
        return new Spo2PeriodData(result);
    }

    private List<? extends Spo2Sample> getSamples(DBHandler db, GBDevice device, int startTs, int endTs) {
        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        final TimeSampleProvider<? extends Spo2Sample> sampleProvider = coordinator.getSpo2SampleProvider(device, db.getDaoSession());
        return sampleProvider.getAllSamples(startTs * 1000L, endTs * 1000L);
    }

    @Override
    protected void updateChartsnUIThread(Spo2PeriodData data) {
        final int startTs = getStartTs();
        mDateView.setText(DateTimeUtils.formatDaysUntil(TOTAL_DAYS, getTSEnd()));

        final Accumulator avgAccumulator = new Accumulator();
        final Accumulator minAccumulator = new Accumulator();
        final Accumulator maxAccumulator = new Accumulator();

        final int n = data.days.size();
        final long firstDay = Instant.ofEpochSecond(startTs).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay();
        final long[] epochDays = new long[n];
        final int[] dayMinimum = new int[n];
        final int[] dayMaximum = new int[n];
        final int[] dayAverage = new int[n];
        for (int i = 0; i < n; i++) {
            final Spo2DayData dayData = data.days.get(i);
            epochDays[i] = firstDay + i;
            if (dayData.minimum > 0 && dayData.maximum > 0) {
                avgAccumulator.add(dayData.average);
                minAccumulator.add(dayData.minimum);
                maxAccumulator.add(dayData.maximum);
                dayMinimum[i] = dayData.minimum;
                dayMaximum[i] = dayData.maximum;
                dayAverage[i] = Math.max(dayData.average, 0);
            }
        }

        final String emptyValue = requireContext().getString(R.string.stats_empty_value);
        final int average = avgAccumulator.getCount() > 0 ? (int) Math.round(avgAccumulator.getAverage()) : DATA_INVALID;
        final int minimum = minAccumulator.getCount() > 0 ? (int) Math.round(minAccumulator.getMin()) : DATA_INVALID;
        final int maximum = maxAccumulator.getCount() > 0 ? (int) Math.round(maxAccumulator.getMax()) : DATA_INVALID;

        final List<StatTileData> stats = new ArrayList<>();
        stats.add(new StatTileData(
                minimum > 0 ? getString(R.string.battery_percentage_str, String.valueOf(minimum)) : emptyValue,
                getString(R.string.hr_minimum)
        ));
        stats.add(new StatTileData(
                maximum > 0 ? getString(R.string.battery_percentage_str, String.valueOf(maximum)) : emptyValue,
                getString(R.string.hr_maximum)
        ));
        stats.add(new StatTileData(
                average > 0 ? getString(R.string.battery_percentage_str, String.valueOf(average)) : emptyValue,
                getString(R.string.hr_average)
        ));
        spo2StatsContainer.removeAllViews();
        StatTileGridUtilKt.addStatTileGrid(spo2StatsContainer, requireContext(), stats, 0);

        final String rangeLabel = getString(R.string.pref_header_spo2);
        final String averageLabel = getString(R.string.hr_average);
        final ChartSpec spec = Spo2ChartData.periodSpec(
                epochDays, dayMinimum, dayMaximum, dayAverage, rangeLabel, SPO2_COLOR, averageLabel, SPO2_AVG_COLOR
        );
        final List<Function1<Integer, String>> rowTexts = new ArrayList<>();
        rowTexts.add(i -> dayMinimum[i] > 0
                ? dayMinimum[i] + " \u2013 " + getString(R.string.battery_percentage_str, String.valueOf(dayMaximum[i]))
                : getString(R.string.stats_empty_value));
        rowTexts.add(i -> dayAverage[i] > 0
                ? getString(R.string.battery_percentage_str, String.valueOf(dayAverage[i]))
                : getString(R.string.stats_empty_value));
        spo2Chart.setSelectionContent(x -> DaySelections.of(
                epochDays, x, Arrays.asList(rangeLabel, averageLabel), Arrays.asList(SPO2_COLOR, SPO2_AVG_COLOR),
                rowTexts, getString(R.string.stats_empty_value)
        ));
        spo2Chart.setSpec(spec);
        spo2Legend.setSeries(spec.getSeries());
    }

    @Override
    protected void setupLegend(Chart<?> chart) {
    }

    @Override
    protected void renderCharts() {
        spo2Chart.invalidate();
    }

    protected static class Spo2PeriodData extends ChartsData {
        public List<Spo2DayData> days;

        protected Spo2PeriodData(List<Spo2DayData> days) {
            this.days = days;
        }
    }

    protected static class Spo2DayData extends ChartsData {
        public int average;
        public int minimum;
        public int maximum;

        protected Spo2DayData(int average, int minimum, int maximum) {
            this.average = average;
            this.minimum = minimum;
            this.maximum = maximum;
        }
    }

    @Nullable
    @Override
    public ChartDataRange getAvailableDataRange(final GBDevice device, final DBHandler db) {
        return ChartDataRange.ofSamples(device.getDeviceCoordinator().getSpo2SampleProvider(device, db.getDaoSession()));
    }
}
