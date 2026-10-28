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

import androidx.annotation.Nullable;

import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_LUX_HOURS_KILO;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_MINUTES;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_PERCENTAGE;

import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

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

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartSelection;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.GbChartView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.solar.SolarChargingChartData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutValueFormatter;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.SolarChargeSample;

public class SolarChargingDailyFragment extends AbstractChartFragment<SolarChargingDailyFragment.SolarChargingData> {
    protected static final Logger LOG = LoggerFactory.getLogger(SolarChargingDailyFragment.class);

    private TextView mDateView;
    private LinearLayout solarChargingStatsContainer;
    private GbChartView solarChargingChart;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_solar_charging, container, false);

        rootView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            getChartsHost().enableSwipeRefresh(scrollY == 0);
        });

        mDateView = rootView.findViewById(R.id.solar_charging_date_view);
        solarChargingStatsContainer = rootView.findViewById(R.id.solar_charging_stats_container);
        solarChargingChart = rootView.findViewById(R.id.solar_charging_chart);
        solarChargingChart.dismissSelectionOnTapOutside(rootView);
        refresh();

        return rootView;
    }

    @Override
    public String getTitle() {
        return getString(R.string.menuitem_solar_charging);
    }

    @Override
    protected void init() {
    }

    @Override
    protected SolarChargingData refreshInBackground(ChartsHost chartsHost, DBHandler db, GBDevice device) {
        // getTSStart()/getTSEnd() are a rolling 24h window ending "now" (see
        // ActivityChartsActivity), not calendar midnight-to-midnight - recompute the actual
        // start of the displayed day here so both the query and the chart's x-axis agree on
        // the same true midnight, matching what getSolarChargeSamples() queries against.
        final Calendar day = Calendar.getInstance();
        day.setTimeInMillis(getTSEnd() * 1000L);
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        final long dayStartMillis = day.getTimeInMillis();

        List<? extends SolarChargeSample> todaySamples = getSolarChargeSamples(db, device, getTSStart(), getTSEnd());
        return new SolarChargingData(todaySamples, dayStartMillis);
    }

    @Override
    protected void updateChartsnUIThread(SolarChargingData solarChargingData) {
        String formattedDate = new SimpleDateFormat("E, MMM dd").format(getEndDate());
        mDateView.setText(formattedDate);

        double totalLuxHours = 0;
        long totalGainMillis = 0;
        float peakPercent = 0f;

        final List<? extends SolarChargeSample> samples = solarChargingData.todaySamples;
        final long[] seconds = new long[samples.size()];
        final float[] percent = new float[samples.size()];
        if (!samples.isEmpty()) {
            totalLuxHours = SolarChargingStats.computeLuxHours(samples);
            totalGainMillis = SolarChargingStats.computeGainMillis(samples);
            for (int i = 0; i < samples.size(); i++) {
                final SolarChargeSample sample = samples.get(i);
                peakPercent = Math.max(peakPercent, sample.getPercent());
                seconds[i] = sample.getTimestamp() / 1000L;
                percent[i] = sample.getPercent();
            }
        }

        final int color = getResources().getColor(R.color.chart_solar_charging_color);
        final String label = getString(R.string.solar_charging_intensity_chart_label);
        solarChargingChart.setSelectionContent(x -> {
            final long time = Math.round(x);
            final String title = DateFormat.getTimeFormat(requireContext()).format(new Date(time * 1000L));
            for (int i = 0; i < seconds.length; i++) {
                if (seconds[i] == time && percent[i] > 0) {
                    final String text = String.format(Locale.getDefault(), "%.0f%%", percent[i]);
                    return new ChartSelection(
                            title,
                            Collections.singletonList(new ChartSelection.Row(color, text)),
                            title + ". " + label + " " + text + "."
                    );
                }
            }
            return new ChartSelection(title, Collections.emptyList(), title + ".");
        });
        solarChargingChart.setSpec(SolarChargingChartData.daySpec(
                solarChargingData.dayStartMillis / 1000L, seconds, percent, label, color
        ));

        final WorkoutValueFormatter unitFormatter = new WorkoutValueFormatter();
        solarChargingStatsContainer.removeAllViews();
        final List<StatTileData> stats = new ArrayList<>();
        stats.add(new StatTileData(String.format(Locale.getDefault(), "%.1f%s", totalLuxHours / 1000.0, unitFormatter.getStringResourceByName(UNIT_LUX_HOURS_KILO)), getString(R.string.solar_charging_lux_hours)));
        stats.add(new StatTileData(String.format(Locale.getDefault(), "+ %d %s", totalGainMillis / 60000L, unitFormatter.getStringResourceByName(UNIT_MINUTES)), getString(R.string.solar_charging_battery_gain)));
        stats.add(new StatTileData(String.format(Locale.getDefault(), "%.0f%s", peakPercent, unitFormatter.getStringResourceByName(UNIT_PERCENTAGE)), getString(R.string.solar_charging_peak_intensity)));
        StatTileGridUtilKt.addStatTileGrid(solarChargingStatsContainer, requireContext(), stats, 0);
    }

    @Override
    protected void renderCharts() {
        solarChargingChart.invalidate();
    }

    /**
     * Get solar charging samples for the calendar day containing tsTo (not the raw
     * tsFrom/tsTo range itself, which is a rolling 24h window, not midnight-to-midnight).
     */
    public List<? extends SolarChargeSample> getSolarChargeSamples(final DBHandler db, final GBDevice device, int tsFrom, int tsTo) {
        final Calendar day = Calendar.getInstance();
        day.setTimeInMillis(tsTo * 1000L); // we need today initially, which is the end of the time range
        day.set(Calendar.HOUR_OF_DAY, 0); // and we set time for the start and end of the same day
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        tsFrom = (int) (day.getTimeInMillis() / 1000);
        tsTo = tsFrom + 24 * 60 * 60 - 1;

        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        final TimeSampleProvider<? extends SolarChargeSample> sampleProvider = coordinator.getSolarChargeSampleProvider(device, db.getDaoSession());
        return sampleProvider.getAllSamples(tsFrom * 1000L, tsTo * 1000L);
    }

    @Override
    protected void setupLegend(Chart<?> chart) {}

    protected static class SolarChargingData extends ChartsData {
        private final List<? extends SolarChargeSample> todaySamples;
        private final long dayStartMillis;

        protected SolarChargingData(List<? extends SolarChargeSample> todaySamples, long dayStartMillis) {
            this.todaySamples = todaySamples;
            this.dayStartMillis = dayStartMillis;
        }
    }

    @Nullable
    @Override
    public ChartDataRange getAvailableDataRange(final GBDevice device, final DBHandler db) {
        return ChartDataRange.ofSamples(device.getDeviceCoordinator().getSolarChargeSampleProvider(device, db.getDaoSession()));
    }
}
