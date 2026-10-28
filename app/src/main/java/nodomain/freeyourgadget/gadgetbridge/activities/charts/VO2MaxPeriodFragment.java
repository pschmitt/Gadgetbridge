/*  Copyright (C) 2024-2026 a0z, José Rebelo, Thomas Kuehne

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
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.github.mikephil.charting.charts.Chart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import kotlin.jvm.functions.Function1;

import androidx.annotation.Nullable;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartLegendView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.DaySelections;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.GbChartView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.vo2max.VO2MaxChartData;
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.GaugeDrawer;
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.Vo2MaxWidget;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.Vo2MaxSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser;
import nodomain.freeyourgadget.gadgetbridge.model.Vo2MaxSample;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;

public class VO2MaxPeriodFragment extends AbstractChartFragment<VO2MaxPeriodFragment.VO2MaxData> {
    protected static final Logger LOG = LoggerFactory.getLogger(VO2MaxPeriodFragment.class);

    private static final String ARG_TOTAL_DAYS = "totalDays";
    private static final String ARG_SHOW_GAUGES = "showGauges";
    private static final int DEFAULT_TOTAL_DAYS = 30;

    private TextView mDateView;
    private GbChartView vo2MaxChart;
    private ChartLegendView vo2MaxLegend;
    private int totalDays;
    private boolean showGauges;
    GBDevice device;

    private TextView vo2MaxRunningValue;
    private TextView vo2MaxCyclingValue;
    private TextView vo2MaxValue;
    private ImageView vo2MaxRunningGauge;
    private ImageView vo2MaxCyclingGauge;
    private ImageView vo2MaxGauge;
    protected GaugeDrawer gaugeDrawer = new GaugeDrawer();
    private RelativeLayout vo2maxCyclingWrapper;
    private RelativeLayout vo2maxRunningWrapper;
    private RelativeLayout vo2maxWrapper;
    private GridLayout tilesGridWrapper;

    public static VO2MaxPeriodFragment newInstance(final int totalDays, final boolean showGauges) {
        final VO2MaxPeriodFragment fragment = new VO2MaxPeriodFragment();
        final Bundle args = new Bundle();
        args.putInt(ARG_TOTAL_DAYS, totalDays);
        args.putBoolean(ARG_SHOW_GAUGES, showGauges);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final int layoutRes = showGauges ? R.layout.fragment_vo2max_month : R.layout.fragment_vo2max_period;
        View rootView = inflater.inflate(layoutRes, container, false);

        mDateView = rootView.findViewById(R.id.vo2max_date_view);
        vo2MaxChart = rootView.findViewById(R.id.vo2max_chart);
        vo2MaxChart.setZoomable(true);
        vo2MaxChart.dismissSelectionOnTapOutside(rootView);
        vo2MaxLegend = rootView.findViewById(R.id.vo2max_chart_legend);
        device = getChartsHost().getDevice();

        if (showGauges) {
            vo2MaxRunningValue = rootView.findViewById(R.id.vo2max_running_gauge_value);
            vo2MaxCyclingValue = rootView.findViewById(R.id.vo2max_cycling_gauge_value);
            vo2MaxValue = rootView.findViewById(R.id.vo2max_gauge_value);
            vo2MaxRunningGauge = rootView.findViewById(R.id.vo2max_running_gauge);
            vo2MaxCyclingGauge = rootView.findViewById(R.id.vo2max_cycling_gauge);
            vo2MaxGauge = rootView.findViewById(R.id.vo2max_gauge);
            vo2maxCyclingWrapper = rootView.findViewById(R.id.vo2max_cycling_card_layout);
            vo2maxRunningWrapper = rootView.findViewById(R.id.vo2max_running_card_layout);
            vo2maxWrapper = rootView.findViewById(R.id.vo2max_card_layout);
            tilesGridWrapper = rootView.findViewById(R.id.tiles_grid_wrapper);
            if (!supportsVO2MultiSport(device)) {
                tilesGridWrapper.removeView(vo2maxCyclingWrapper);
                tilesGridWrapper.removeView(vo2maxRunningWrapper);
            } else {
                tilesGridWrapper.removeView(vo2maxWrapper);
            }
        }

        refresh();

        return rootView;
    }

    public boolean supportsVO2MultiSport(GBDevice device) {
        DeviceCoordinator coordinator = device.getDeviceCoordinator();
        return coordinator.supportsVO2MultiSport(device);
    }

    @Override
    public String getTitle() {
        return getString(R.string.menuitem_vo2_max);
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
        showGauges = getArguments() != null && getArguments().getBoolean(ARG_SHOW_GAUGES, false);
    }

    @Override
    protected VO2MaxData refreshInBackground(ChartsHost chartsHost, DBHandler db, GBDevice device) {
        final Date rangeStart = DateTimeUtils.dayStart(new Date(getTSStart() * 1000L));
        final Date rangeEnd = DateTimeUtils.dayEnd(new Date(getTSEnd() * 1000L));
        final int tsFrom = (int) (rangeStart.getTime() / 1000L);
        final int tsTo = (int) (rangeEnd.getTime() / 1000L);

        List<VO2MaxRecord> records = new ArrayList<>();
        List<? extends Vo2MaxSample> samples = getAllSamples(db, device, tsFrom, tsTo);
        for (Vo2MaxSample sample : samples) {
            records.add(new VO2MaxRecord(sample.getTimestamp() / 1000, sample.getValue(), sample.getType()));
        }

        Map<Vo2MaxSample.Type, VO2MaxRecord> latestValues = null;
        if (showGauges) {
            latestValues = new HashMap<>();
            for (Vo2MaxSample.Type type : Vo2MaxSample.Type.values()) {
                Vo2MaxSample sample = getLatestVo2MaxSample(db, device, type);
                if (sample != null) {
                    latestValues.put(type, new VO2MaxRecord(sample.getTimestamp() / 1000, sample.getValue(), type));
                }
            }
        }

        return new VO2MaxData(records, tsFrom, tsTo, latestValues);
    }

    @Override
    protected void updateChartsnUIThread(VO2MaxData vo2MaxData) {
        mDateView.setText(DateTimeUtils.formatDaysUntil(totalDays, getTSEnd()));

        final ZoneId zone = ZoneId.systemDefault();
        final long firstDay = Instant.ofEpochSecond(vo2MaxData.tsFrom).atZone(zone).toLocalDate().toEpochDay();
        final long lastDay = Instant.ofEpochSecond(vo2MaxData.tsTo).atZone(zone).toLocalDate().toEpochDay();
        final int n = (int) (lastDay - firstDay + 1);
        final long[] epochDays = new long[n];
        for (int i = 0; i < n; i++) {
            epochDays[i] = firstDay + i;
        }

        final boolean multiSport = supportsVO2MultiSport(device);
        final String[] labels = multiSport
                ? new String[]{getString(R.string.vo2max_running), getString(R.string.vo2max_cycling)}
                : new String[]{getString(R.string.menuitem_vo2_max)};
        final int[] colors = multiSport
                ? new int[]{getResources().getColor(R.color.vo2max_running_char_line_color), getResources().getColor(R.color.vo2max_cycling_char_line_color)}
                : new int[]{getResources().getColor(R.color.vo2max_running_char_line_color)};
        final double[][] values = new double[labels.length][n];
        final long[][] times = new long[labels.length][n];
        for (final VO2MaxRecord record : vo2MaxData.records) {
            final int series;
            if (!multiSport || record.type == Vo2MaxSample.Type.RUNNING) {
                series = 0;
            } else if (record.type == Vo2MaxSample.Type.CYCLING) {
                series = 1;
            } else {
                continue;
            }
            final int i = (int) (Instant.ofEpochSecond(record.timestamp).atZone(zone).toLocalDate().toEpochDay() - firstDay);
            if (i < 0 || i >= n || record.timestamp < times[series][i]) {
                continue;
            }
            times[series][i] = record.timestamp;
            values[series][i] = record.value;
        }

        final ChartSpec spec = VO2MaxChartData.spec(epochDays, values, labels, colors);
        final List<Function1<Integer, String>> rowTexts = new ArrayList<>();
        for (int s = 0; s < labels.length; s++) {
            final double[] seriesValues = values[s];
            rowTexts.add(i -> seriesValues[i] > 0 ? formatVO2MaxValue((float) seriesValues[i]) : getString(R.string.stats_empty_value));
        }
        final List<Integer> rowColors = new ArrayList<>();
        for (final int color : colors) {
            rowColors.add(color);
        }
        vo2MaxChart.setSelectionContent(x -> DaySelections.of(
                epochDays, x, Arrays.asList(labels), rowColors, rowTexts, getString(R.string.stats_empty_value)
        ));
        vo2MaxChart.setSpec(spec);

        final List<ChartSeries> legendSeries = new ArrayList<>();
        if (multiSport) {
            for (final ChartSeries series : spec.getSeries()) {
                if (!series.getPoints().isEmpty()) {
                    legendSeries.add(series);
                }
            }
        }
        vo2MaxLegend.setSeries(legendSeries);

        if (showGauges) {
            updateGaugeTiles(vo2MaxData);
        }
    }

    private void updateGaugeTiles(final VO2MaxData vo2MaxData) {
        final int[] colors = Vo2MaxWidget.colors(requireContext());
        final float[] segments = Vo2MaxWidget.SEGMENTS;
        final ActivityUser activityUser = new ActivityUser();
        final int age = activityUser.getAgeAt(LocalDate.ofInstant(getEndDate().toInstant(), ZoneId.systemDefault()));
        if (supportsVO2MultiSport(device)) {
            // Running
            VO2MaxRecord latestRunningRecord = vo2MaxData.getLatestValue(Vo2MaxSample.Type.RUNNING);
            float runningVO2MaxValue = VO2MaxRanges.INSTANCE.calculateVO2MaxPercentile(latestRunningRecord != null ? latestRunningRecord.value : 0, age, activityUser.getGender());
            vo2MaxRunningValue.setText(latestRunningRecord != null ? formatVO2MaxValue(latestRunningRecord.value) : "-");
            gaugeDrawer.drawSegmentedGauge(vo2MaxRunningGauge, colors, segments, runningVO2MaxValue, false, true);

            // Cycling
            VO2MaxRecord latestCyclingRecord = vo2MaxData.getLatestValue(Vo2MaxSample.Type.CYCLING);
            float cyclingVO2MaxValue = VO2MaxRanges.INSTANCE.calculateVO2MaxPercentile(latestCyclingRecord != null ? latestCyclingRecord.value : 0, age, activityUser.getGender());
            gaugeDrawer.drawSegmentedGauge(vo2MaxCyclingGauge, colors, segments, cyclingVO2MaxValue, false, true);
            vo2MaxCyclingValue.setText(latestCyclingRecord != null ? formatVO2MaxValue(latestCyclingRecord.value) : "-");
        } else {
            VO2MaxRecord latestRecord = vo2MaxData.getLatestValue(Vo2MaxSample.Type.ANY);
            float vO2MaxValue = VO2MaxRanges.INSTANCE.calculateVO2MaxPercentile(latestRecord != null ? latestRecord.value : 0, age, activityUser.getGender());
            gaugeDrawer.drawSegmentedGauge(vo2MaxGauge, colors, segments, vO2MaxValue, false, true);
            vo2MaxValue.setText(latestRecord != null ? formatVO2MaxValue(latestRecord.value) : "-");
        }
    }

    private static String formatVO2MaxValue(final float value) {
        return String.format(Locale.getDefault(), "%.1f", value);
    }

    @Override
    protected void renderCharts() {
        vo2MaxChart.invalidate();
    }

    public List<? extends Vo2MaxSample> getAllSamples(final DBHandler db, final GBDevice device, int tsFrom, int tsTo) {
        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        final TimeSampleProvider<? extends Vo2MaxSample> sampleProvider = coordinator.getVo2MaxSampleProvider(device, db.getDaoSession());
        return sampleProvider.getAllSamples(tsFrom * 1000L, tsTo * 1000L);
    }

    public Vo2MaxSample getLatestVo2MaxSample(final DBHandler db, final GBDevice device, Vo2MaxSample.Type type) {
        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        final Vo2MaxSampleProvider sampleProvider = (Vo2MaxSampleProvider) coordinator.getVo2MaxSampleProvider(device, db.getDaoSession());
        return sampleProvider.getLatestSample(type, getTSEnd() * 1000L);
    }

    @Override
    protected void setupLegend(Chart<?> chart) {
    }

    protected static class VO2MaxRecord {
        float value;
        long timestamp;
        Vo2MaxSample.Type type;

        protected VO2MaxRecord(long timestamp, float value, Vo2MaxSample.Type type) {
            this.timestamp = timestamp;
            this.value = value;
            this.type = type;
        }
    }

    protected static class VO2MaxData extends ChartsData {
        private final List<? extends VO2MaxRecord> records;
        private final int tsFrom;
        private final int tsTo;
        @Nullable
        private final Map<Vo2MaxSample.Type, VO2MaxRecord> latestValues;

        protected VO2MaxData(List<? extends VO2MaxRecord> records, int tsFrom, int tsTo, @Nullable Map<Vo2MaxSample.Type, VO2MaxRecord> latestValues) {
            this.records = records;
            this.tsFrom = tsFrom;
            this.tsTo = tsTo;
            this.latestValues = latestValues;
        }

        @Nullable
        public VO2MaxRecord getLatestValue(Vo2MaxSample.Type type) {
            return latestValues != null ? latestValues.get(type) : null;
        }
    }

    @Nullable
    @Override
    public ChartDataRange getAvailableDataRange(final GBDevice device, final DBHandler db) {
        return ChartDataRange.ofSamples(device.getDeviceCoordinator().getVo2MaxSampleProvider(device, db.getDaoSession()));
    }
}
