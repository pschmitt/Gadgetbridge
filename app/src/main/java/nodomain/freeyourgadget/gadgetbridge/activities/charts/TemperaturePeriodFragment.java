/*  Copyright (C) 2026 The Gadgetbridge Contributors

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

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.Chart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import lineageos.weather.util.TemperatureUtils;
import kotlin.jvm.functions.Function1;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartLegendView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.DaySelections;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.GbChartView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.temperature.TemperatureChartData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.TemperatureSample;
import nodomain.freeyourgadget.gadgetbridge.model.TemperatureUnit;
import nodomain.freeyourgadget.gadgetbridge.util.Accumulator;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;

public class TemperaturePeriodFragment extends AbstractChartFragment<TemperaturePeriodFragment.TemperaturePeriodData> {
    protected static final Logger LOG = LoggerFactory.getLogger(TemperaturePeriodFragment.class);

    static final int SEC_PER_DAY = 24 * 60 * 60;
    static final float DATA_INVALID = Float.NaN;

    private int TEMPERATURE_COLOR;
    private int TEMPERATURE_AVG_COLOR;

    private TextView dateView;
    private LinearLayout statsContainer;
    private GbChartView temperatureChart;
    private ChartLegendView temperatureLegend;
    private int totalDays;

    private final TemperatureUnit temperatureUnit = GBApplication.getPrefs().getTemperatureUnit();

    @Override
    protected boolean isSingleDay() {
        return false;
    }

    public static TemperaturePeriodFragment newInstance(final int totalDays) {
        final TemperaturePeriodFragment fragment = new TemperaturePeriodFragment();
        final Bundle args = new Bundle();
        args.putInt("totalDays", totalDays);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        totalDays = getArguments() != null ? getArguments().getInt("totalDays") : 7;
    }

    @Override
    protected void init() {
        TEMPERATURE_COLOR = ContextCompat.getColor(requireContext(), R.color.chart_temperature);
        TEMPERATURE_AVG_COLOR = ContextCompat.getColor(requireContext(), R.color.chart_temperature_average);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_temperature_period, container, false);

        rootView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) ->
                getChartsHost().enableSwipeRefresh(scrollY == 0));

        dateView = rootView.findViewById(R.id.temperature_period_date_view);
        statsContainer = rootView.findViewById(R.id.temperature_period_stats_container);
        temperatureChart = rootView.findViewById(R.id.temperature_period_chart);
        temperatureChart.setZoomable(totalDays > 7);
        temperatureChart.dismissSelectionOnTapOutside(rootView);
        temperatureLegend = rootView.findViewById(R.id.temperature_period_chart_legend);

        refresh();

        return rootView;
    }

    @Override
    public String getTitle() {
        return getString(R.string.menuitem_temperature);
    }

    private int getStartTs() {
        final Calendar day = Calendar.getInstance();
        day.setTime(getEndDate());
        final Calendar startDay = DateTimeUtils.dayStart(day);
        startDay.add(Calendar.DATE, -(totalDays - 1));
        return (int) (startDay.getTimeInMillis() / 1000);
    }

    private TemperatureDayData fetchTemperatureDataForDay(final DBHandler db, final GBDevice device, final int startTs) {
        final int endTs = startTs + SEC_PER_DAY - 1;
        final List<? extends TemperatureSample> samples = getSamples(db, device, startTs, endTs);

        final Accumulator accumulator = new Accumulator();
        for (final TemperatureSample sample : samples) {
            final float temperature = sample.getTemperature();
            if (!Float.isNaN(temperature)) {
                accumulator.add(toPreferredUnit(temperature));
            }
        }

        final float average = accumulator.getCount() > 0 ? (float) accumulator.getAverage() : DATA_INVALID;
        final float minimum = accumulator.getCount() > 0 ? (float) accumulator.getMin() : DATA_INVALID;
        final float maximum = accumulator.getCount() > 0 ? (float) accumulator.getMax() : DATA_INVALID;

        return new TemperatureDayData(average, minimum, maximum);
    }

    @Override
    protected TemperaturePeriodData refreshInBackground(final ChartsHost chartsHost, final DBHandler db, final GBDevice device) {
        final int startTs = getStartTs();

        final List<TemperatureDayData> result = new ArrayList<>(totalDays);
        for (int i = 0; i < totalDays; i++) {
            final TemperatureDayData dayData = fetchTemperatureDataForDay(db, device, startTs + i * SEC_PER_DAY);
            result.add(dayData);
        }

        return new TemperaturePeriodData(result);
    }

    private List<? extends TemperatureSample> getSamples(final DBHandler db, final GBDevice device, final int startTs, final int endTs) {
        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        final TimeSampleProvider<? extends TemperatureSample> sampleProvider = coordinator.getTemperatureSampleProvider(device, db.getDaoSession());
        return sampleProvider.getAllSamples(startTs * 1000L, endTs * 1000L);
    }

    @Override
    protected void updateChartsnUIThread(final TemperaturePeriodData data) {
        final int startTs = getStartTs();
        dateView.setText(DateTimeUtils.formatDaysUntil(totalDays, getTSEnd()));

        final Accumulator avgAccumulator = new Accumulator();
        final Accumulator minAccumulator = new Accumulator();
        final Accumulator maxAccumulator = new Accumulator();

        final int n = data.days.size();
        final long firstDay = Instant.ofEpochSecond(startTs).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay();
        final long[] epochDays = new long[n];
        final double[] dayMinimum = new double[n];
        final double[] dayMaximum = new double[n];
        final double[] dayAverage = new double[n];
        for (int i = 0; i < n; i++) {
            final TemperatureDayData dayData = data.days.get(i);
            epochDays[i] = firstDay + i;
            dayMinimum[i] = Double.NaN;
            dayMaximum[i] = Double.NaN;
            dayAverage[i] = Double.NaN;
            if (hasData(dayData.minimum) && hasData(dayData.maximum)) {
                minAccumulator.add(dayData.minimum);
                maxAccumulator.add(dayData.maximum);
                dayMinimum[i] = dayData.minimum;
                dayMaximum[i] = dayData.maximum;
            }
            if (hasData(dayData.average)) {
                avgAccumulator.add(dayData.average);
                dayAverage[i] = dayData.average;
            }
        }

        final float average = avgAccumulator.getCount() > 0 ? (float) avgAccumulator.getAverage() : DATA_INVALID;
        final float minimum = minAccumulator.getCount() > 0 ? (float) minAccumulator.getMin() : DATA_INVALID;
        final float maximum = maxAccumulator.getCount() > 0 ? (float) maxAccumulator.getMax() : DATA_INVALID;

        final String emptyValue = getString(R.string.stats_empty_value);
        final List<StatTileData> stats = new ArrayList<>();
        stats.add(new StatTileData(hasData(minimum) ? formatTemperature(minimum) : emptyValue, getString(R.string.hr_minimum)));
        stats.add(new StatTileData(hasData(maximum) ? formatTemperature(maximum) : emptyValue, getString(R.string.hr_maximum)));
        stats.add(new StatTileData(hasData(average) ? formatTemperature(average) : emptyValue, getString(R.string.hr_average)));
        statsContainer.removeAllViews();
        StatTileGridUtilKt.addStatTileGrid(statsContainer, requireContext(), stats, 0);

        final String rangeLabel = getString(R.string.menuitem_temperature);
        final String averageLabel = getString(R.string.hr_average);
        final ChartSpec spec = TemperatureChartData.periodSpec(
                epochDays, dayMinimum, dayMaximum, dayAverage, temperatureUnit == TemperatureUnit.CELSIUS ? 3 : 6,
                rangeLabel, TEMPERATURE_COLOR, averageLabel, TEMPERATURE_AVG_COLOR
        );
        final List<Function1<Integer, String>> rowTexts = new ArrayList<>();
        rowTexts.add(i -> Double.isNaN(dayMinimum[i])
                ? emptyValue
                : new DecimalFormat("0.0").format(dayMinimum[i]) + " \u2013 " + formatTemperature((float) dayMaximum[i]));
        rowTexts.add(i -> Double.isNaN(dayAverage[i]) ? emptyValue : formatTemperature((float) dayAverage[i]));
        temperatureChart.setSelectionContent(x -> DaySelections.of(
                epochDays, x, Arrays.asList(rangeLabel, averageLabel), Arrays.asList(TEMPERATURE_COLOR, TEMPERATURE_AVG_COLOR),
                rowTexts, emptyValue
        ));
        temperatureChart.setSpec(spec);
        temperatureLegend.setSeries(spec.getSeries());
    }

    @Override
    protected void setupLegend(final Chart<?> chart) {
    }

    @Override
    protected void renderCharts() {
        temperatureChart.invalidate();
    }

    private float toPreferredUnit(final float celsius) {
        if (temperatureUnit == TemperatureUnit.CELSIUS) {
            return celsius;
        }

        return (float) TemperatureUtils.celsiusToFahrenheit(celsius);
    }

    private String formatTemperature(final float temperature) {
        return TemperatureUtils.formatTemperature(temperature, temperatureUnit, new DecimalFormat("0.0"));
    }

    private static boolean hasData(final float value) {
        return !Float.isNaN(value);
    }

    protected static class TemperaturePeriodData extends ChartsData {
        public final List<TemperatureDayData> days;

        protected TemperaturePeriodData(final List<TemperatureDayData> days) {
            this.days = days;
        }
    }

    protected static class TemperatureDayData extends ChartsData {
        public final float average;
        public final float minimum;
        public final float maximum;

        protected TemperatureDayData(final float average, final float minimum, final float maximum) {
            this.average = average;
            this.minimum = minimum;
            this.maximum = maximum;
        }
    }

    @Nullable
    @Override
    public ChartDataRange getAvailableDataRange(final GBDevice device, final DBHandler db) {
        return ChartDataRange.ofSamples(device.getDeviceCoordinator().getTemperatureSampleProvider(device, db.getDaoSession()));
    }
}
