/*  Copyright (C) 2024 Me7c7

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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import lineageos.weather.util.TemperatureUtils;
import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
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
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.TemperatureSample;
import nodomain.freeyourgadget.gadgetbridge.model.TemperatureUnit;
import nodomain.freeyourgadget.gadgetbridge.util.Accumulator;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;

public class TemperatureDailyFragment extends AbstractChartFragment<TemperatureDailyFragment.TemperatureChartData> {

    protected static final Logger LOG = LoggerFactory.getLogger(TemperatureDailyFragment.class);

    protected int TEMPERATURE_COLOR;
    protected int TEMPERATURE_AVG_COLOR;

    private TextView dateView;
    private LinearLayout statsContainer;
    private GbChartView tempLineChart;
    private ChartLegendView tempLegend;

    private final TemperatureUnit temperatureUnit = GBApplication.getPrefs().getTemperatureUnit();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_temperature, container, false);

        rootView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            getChartsHost().enableSwipeRefresh(scrollY == 0);
        });

        dateView = rootView.findViewById(R.id.temp_date_view);
        tempLineChart = rootView.findViewById(R.id.temp_line_chart);
        tempLineChart.setZoomable(true);
        tempLineChart.dismissSelectionOnTapOutside(rootView);
        tempLegend = rootView.findViewById(R.id.temp_chart_legend);
        statsContainer = rootView.findViewById(R.id.temp_stats_container);

        refresh();

        return rootView;
    }

    @Override
    public String getTitle() {
        return getString(R.string.menuitem_temperature);
    }

    @Override
    protected void init() {
        TEMPERATURE_COLOR = ContextCompat.getColor(requireContext(), R.color.chart_temperature);
        TEMPERATURE_AVG_COLOR = ContextCompat.getColor(requireContext(), R.color.chart_temperature_average);
    }

    @Override
    protected TemperatureChartData refreshInBackground(ChartsHost chartsHost, DBHandler db, GBDevice device) {
        final Date day = getEndDate();

        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        final TimeSampleProvider<? extends TemperatureSample> sampleProvider = coordinator.getTemperatureSampleProvider(device, db.getDaoSession());

        final List<? extends TemperatureSample> samples = sampleProvider.getAllSamples(
                DateTimeUtils.dayStart(day).getTime(),
                DateTimeUtils.dayEnd(day).getTime()
        );
        LOG.info("Got {} temperature samples", samples.size());

        return new TemperatureChartData(samples);
    }

    @Override
    protected void renderCharts() {
        tempLineChart.invalidate();
    }

    @Override
    protected void setupLegend(Chart<?> chart) {
    }

    @Override
    protected void updateChartsnUIThread(TemperatureDailyFragment.TemperatureChartData data) {
        Date date = new Date(getTSEnd() * 1000L);
        String formattedDate = new SimpleDateFormat("E, MMM dd").format(date);
        dateView.setText(formattedDate);

        final List<? extends TemperatureSample> samples = data.samples;
        final long[] seconds = new long[samples.size()];
        final double[] values = new double[samples.size()];
        final Accumulator accumulator = new Accumulator();
        for (int i = 0; i < samples.size(); i++) {
            final TemperatureSample sample = samples.get(i);
            seconds[i] = sample.getTimestamp() / 1000L;
            values[i] = temperatureUnit == TemperatureUnit.CELSIUS ?
                    sample.getTemperature() :
                    TemperatureUtils.celsiusToFahrenheit(sample.getTemperature());
            accumulator.add(values[i]);
        }

        final double average = accumulator.getCount() > 0 ? accumulator.getAverage() : -1;
        final double minimum = accumulator.getCount() > 0 ? accumulator.getMin() : -1;
        final double maximum = accumulator.getCount() > 0 ? accumulator.getMax() : -1;
        final String unit = getString(temperatureUnit == TemperatureUnit.CELSIUS ? R.string.unit_celsius : R.string.unit_fahrenheit);

        final List<StatTileData> stats = new ArrayList<>();
        stats.add(new StatTileData(formatTemperature(minimum, unit), getString(R.string.hr_minimum)));
        stats.add(new StatTileData(formatTemperature(maximum, unit), getString(R.string.hr_maximum)));
        stats.add(new StatTileData(formatTemperature(average, unit), getString(R.string.hr_average)));
        statsContainer.removeAllViews();
        StatTileGridUtilKt.addStatTileGrid(statsContainer, requireContext(), stats, 0);

        final long dayStart = DateTimeUtils.dayStart(date).getTime() / 1000L;
        final ChartSpec spec = nodomain.freeyourgadget.gadgetbridge.activities.charts.temperature.TemperatureChartData.daySpec(
                dayStart, seconds, values, average, GBApplication.getPrefs().getBoolean("charts_show_average", true),
                temperatureUnit == TemperatureUnit.CELSIUS ? 3 : 6, getTitle(), TEMPERATURE_COLOR, TEMPERATURE_AVG_COLOR
        );
        tempLineChart.setSelectionContent(x -> {
            final long time = Math.round(x);
            final String title = DateFormat.getTimeFormat(requireContext()).format(new Date(time * 1000L));
            for (int i = 0; i < seconds.length; i++) {
                if (seconds[i] == time) {
                    final String text = formatTemperature(values[i], unit);
                    return new ChartSelection(
                            title,
                            Collections.singletonList(new ChartSelection.Row(TEMPERATURE_COLOR, text)),
                            title + ". " + getTitle() + " " + text + "."
                    );
                }
            }
            return new ChartSelection(title, Collections.emptyList(), title + ".");
        });
        tempLineChart.setSpec(spec);

        final List<ChartSeries> legendSeries = new ArrayList<>(spec.getSeries());
        if (!spec.getSeries().isEmpty() && !spec.getLimitLines().isEmpty()) {
            legendSeries.add(ChartLegendView.lineItem(getString(R.string.hr_average), TEMPERATURE_AVG_COLOR));
        }
        tempLegend.setSeries(legendSeries);
    }

    private String formatTemperature(final double value, final String unit) {
        return value > 0 ? String.format(Locale.ROOT, "%.1f %s", value, unit) : getString(R.string.stats_empty_value);
    }

    protected static class TemperatureChartData extends ChartsData {
        public List<? extends TemperatureSample> samples;

        protected TemperatureChartData(List<? extends TemperatureSample> samples) {
            this.samples = samples;
        }
    }

    @Nullable
    @Override
    public ChartDataRange getAvailableDataRange(final GBDevice device, final DBHandler db) {
        return ChartDataRange.ofSamples(device.getDeviceCoordinator().getTemperatureSampleProvider(device, db.getDaoSession()));
    }
}
