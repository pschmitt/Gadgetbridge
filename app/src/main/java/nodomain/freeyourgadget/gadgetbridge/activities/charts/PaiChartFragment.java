/*  Copyright (C) 2023-2024 Daniel Dakhno, José Rebelo

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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.Chart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import kotlin.jvm.functions.Function1;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartLegendView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.DaySelections;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.GbChartView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.pai.PaiChartData;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec;
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.GaugeDrawer;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.PaiSample;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;

public class PaiChartFragment extends AbstractChartFragment<PaiChartFragment.PaiChartsData> {
    protected static final Logger LOG = LoggerFactory.getLogger(PaiChartFragment.class);

    protected final int TOTAL_DAYS = getRangeDays();

    protected ImageView mGoalMinutesGauge;
    protected GbChartView mWeekChart;
    protected ChartLegendView mWeekLegend;
    protected TextView mDateView;
    protected LinearLayout mSummaryStatsContainer;
    protected TextView mLineLowInc;
    protected TextView mLineLowTime;
    protected TextView mLineModerateInc;
    protected TextView mLineModerateTime;
    protected TextView mLineHighInc;
    protected TextView mLineHighTime;
    protected View mTileLow;
    protected View mTileModerate;
    protected View mTileHigh;

    protected int PAI_TOTAL_COLOR;
    protected int PAI_DAY_COLOR;

    @Override
    protected void init() {
        PAI_TOTAL_COLOR = ContextCompat.getColor(requireContext(), R.color.chart_pai_weekly);
        PAI_DAY_COLOR = ContextCompat.getColor(requireContext(), R.color.chart_pai_today);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater,
                             final ViewGroup container,
                             final Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_pai_chart, container, false);

        rootView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            getChartsHost().enableSwipeRefresh(scrollY == 0);
        });

        mGoalMinutesGauge = rootView.findViewById(R.id.goal_minutes_gauge);
        mWeekChart = rootView.findViewById(R.id.pai_chart_week);
        mWeekLegend = rootView.findViewById(R.id.pai_chart_legend);
        mDateView = rootView.findViewById(R.id.pai_date_view);
        mSummaryStatsContainer = rootView.findViewById(R.id.pai_summary_stats_container);
        mLineLowInc = rootView.findViewById(R.id.pai_line_low_inc);
        mLineLowTime = rootView.findViewById(R.id.pai_line_low_time);
        mLineModerateInc = rootView.findViewById(R.id.pai_line_moderate_inc);
        mLineModerateTime = rootView.findViewById(R.id.pai_line_moderate_time);
        mLineHighInc = rootView.findViewById(R.id.pai_line_high_inc);
        mLineHighTime = rootView.findViewById(R.id.pai_line_high_time);
        mTileLow = rootView.findViewById(R.id.pai_tile_low);
        mTileModerate = rootView.findViewById(R.id.pai_tile_moderate);
        mTileHigh = rootView.findViewById(R.id.pai_tile_high);

        if (!getChartsHost().getDevice().getDeviceCoordinator().supportsPaiTime(getChartsHost().getDevice())) {
            mLineLowTime.setVisibility(View.GONE);
            mLineModerateTime.setVisibility(View.GONE);
            mLineHighTime.setVisibility(View.GONE);
        }

        if (!getChartsHost().getDevice().getDeviceCoordinator().supportsPaiLow(getChartsHost().getDevice())) {
            mTileLow.setVisibility(View.GONE);
        }

        mWeekChart.dismissSelectionOnTapOutside(rootView);

        // refresh immediately instead of use refreshIfVisible(), for perceived performance
        refresh();

        return rootView;
    }

    private int getRangeDays() {
        return GBApplication.getPrefs().getChartsRange().getDays();
    }

    private int getPaiTarget() {
        return getChartsHost().getDevice().getDeviceCoordinator().getPaiTarget();
    }

    @Override
    public String getTitle() {
        if (GBApplication.getPrefs().getChartsRange() == ChartsRange.MONTH) {
            return getString(R.string.pai_chart_per_month);
        } else {
            return getString(R.string.pai_chart_per_week);
        }
    }

    @Override
    protected PaiChartsData refreshInBackground(final ChartsHost chartsHost, final DBHandler db, final GBDevice device) {
        final Calendar day = Calendar.getInstance();
        day.setTime(chartsHost.getEndDate());
        //NB: we could have omitted the day, but this way we can move things to the past easily
        final DayData dayData = refreshDayData(db, day, device);
        final WeekData weekBeforeData = refreshWeekBeforeData(db, day, device);

        return new PaiChartsData(dayData, weekBeforeData);
    }

    @Override
    protected void updateChartsnUIThread(final PaiChartsData pcd) {
        int[] colors = new int[] {
                ContextCompat.getColor(GBApplication.getContext(), R.color.chart_pai_weekly),
                ContextCompat.getColor(GBApplication.getContext(), R.color.chart_pai_today)
        };
        final int width = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                300,
                GBApplication.getContext().getResources().getDisplayMetrics()
        );
        mGoalMinutesGauge.setImageBitmap(GaugeDrawer.drawCircleGaugeSegmented(
                width,
                width / 15,
                colors,
                pcd.getDayData().getGaugeSegments(),
                false,
                String.valueOf(pcd.getDayData().getTotal()),
                String.valueOf(getPaiTarget()),
                getContext()
        ));

        updateWeekChart(pcd.getWeekBeforeData());

        mDateView.setText(DateTimeUtils.formatDate(pcd.getDayData().day.getTime()));

        final List<StatTileData> summaryStats = new ArrayList<>();
        summaryStats.add(new StatTileData(
                requireContext().getString(R.string.pai_plus_num, pcd.getDayData().today),
                getString(R.string.activity_summary_today),
                null, null, null, null,
                PAI_DAY_COLOR
        ));
        summaryStats.add(new StatTileData(
                String.valueOf(pcd.getDayData().total),
                getString(R.string.weekly_total),
                null, null, null, null,
                PAI_TOTAL_COLOR
        ));
        mSummaryStatsContainer.removeAllViews();
        StatTileGridUtilKt.addStatTileGrid(mSummaryStatsContainer, requireContext(), summaryStats, 0);

        mLineLowInc.setText(String.valueOf(pcd.getDayData().paiLow));
        mLineLowTime.setText(requireContext().getString(R.string.num_min, pcd.getDayData().minutesLow));
        mLineModerateInc.setText(String.valueOf(pcd.getDayData().paiModerate));
        mLineModerateTime.setText(requireContext().getString(R.string.num_min, pcd.getDayData().minutesModerate));
        mLineHighInc.setText(String.valueOf(pcd.getDayData().paiHigh));
        mLineHighTime.setText(requireContext().getString(R.string.num_min, pcd.getDayData().minutesHigh));
    }

    private void updateWeekChart(final WeekData week) {
        final String totalLabel = getString(R.string.pai_total);
        final String dayLabel = getString(R.string.pai_day);
        final ChartSpec spec = PaiChartData.periodSpec(
                week.epochDays, week.totals, week.today, getPaiTarget(), totalLabel, PAI_TOTAL_COLOR, dayLabel, PAI_DAY_COLOR
        );
        final String emptyValue = getString(R.string.stats_empty_value);
        final List<Function1<Integer, String>> rowTexts = new ArrayList<>();
        rowTexts.add(i -> week.totals[i] > 0 ? String.valueOf(week.totals[i]) : emptyValue);
        rowTexts.add(i -> week.totals[i] > 0 ? getString(R.string.pai_plus_num, week.today[i]) : emptyValue);
        mWeekChart.setSelectionContent(x -> DaySelections.of(
                week.epochDays, x, Arrays.asList(totalLabel, dayLabel), Arrays.asList(PAI_TOTAL_COLOR, PAI_DAY_COLOR),
                rowTexts, emptyValue
        ));
        mWeekChart.setSpec(spec);
        mWeekLegend.setSeries(spec.getSeries());
    }

    @Override
    protected void renderCharts() {
        mWeekChart.invalidate();
    }

    protected WeekData refreshWeekBeforeData(final DBHandler db,
                                             Calendar day,
                                             final GBDevice device) {
        day = (Calendar) day.clone(); // do not modify the caller's argument
        day.add(Calendar.DATE, -TOTAL_DAYS + 1);

        final long[] epochDays = new long[TOTAL_DAYS];
        final int[] totals = new int[TOTAL_DAYS];
        final int[] today = new int[TOTAL_DAYS];
        for (int counter = 0; counter < TOTAL_DAYS; counter++) {
            epochDays[counter] = LocalDate.of(day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH)).toEpochDay();
            final Optional<? extends PaiSample> sampleOpt = getSamplePaiForDay(db, device, day);
            if (sampleOpt.isPresent()) {
                totals[counter] = Math.round(sampleOpt.get().getPaiTotal());
                today[counter] = Math.round(sampleOpt.get().getPaiToday());
            }
            day.add(Calendar.DATE, 1);
        }

        return new WeekData(epochDays, totals, today);
    }

    protected DayData refreshDayData(final DBHandler db,
                                     final Calendar day,
                                     final GBDevice device) {
        final Optional<? extends PaiSample> sampleOpt = getSamplePaiForDay(db, device, day);

        final int today;
        final int total;
        final int paiLow;
        final int paiModerate;
        final int paiHigh;
        final int minutesLow;
        final int minutesModerate;
        final int minutesHigh;

        if (sampleOpt.isPresent()) {
            final PaiSample sample = sampleOpt.get();
            today = Math.round(sample.getPaiToday());
            total = Math.round(sample.getPaiTotal());
            paiLow = Math.round(sample.getPaiLow());
            paiModerate = Math.round(sample.getPaiModerate());
            paiHigh = Math.round(sample.getPaiHigh());
            minutesLow = sample.getTimeLow();
            minutesModerate = sample.getTimeModerate();
            minutesHigh = sample.getTimeHigh();
        } else {
            today = 0;
            total = 0;
            paiLow = 0;
            paiModerate = 0;
            paiHigh = 0;
            minutesLow = 0;
            minutesModerate = 0;
            minutesHigh = 0;
        }

        final int maxPai = Math.max(getPaiTarget(), total);

        float[] segments = new float[] {
                (float) (total - today) / maxPai,
                (float) today / maxPai
        };

        return new DayData(day, segments, today, total, paiLow, paiModerate, paiHigh, minutesLow, minutesModerate, minutesHigh);
    }

    @Override
    protected void setupLegend(Chart<?> chart) {
    }

    private Optional<? extends PaiSample> getSamplePaiForDay(final DBHandler db, final GBDevice device, final Calendar day) {
        final Date dayStart = DateTimeUtils.dayStart(day.getTime());
        final Date dayEnd = DateTimeUtils.dayEnd(day.getTime());
        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        final TimeSampleProvider<? extends PaiSample> sampleProvider = coordinator.getPaiSampleProvider(device, db.getDaoSession());
        final List<? extends PaiSample> daySamples = sampleProvider.getAllSamples(dayStart.getTime(), dayEnd.getTime());
        return Optional.ofNullable(daySamples.isEmpty() ? null : daySamples.get(daySamples.size() - 1));
    }

    protected static class DayData {
        private final Calendar day;
        private final float[] gaugeSegments;
        private final int today;
        private final int total;
        private final int paiLow;
        private final int paiModerate;
        private final int paiHigh;
        private final int minutesLow;
        private final int minutesModerate;
        private final int minutesHigh;

        DayData(final Calendar day,
                float[] gaugeSegments,
                final int today,
                final int total,
                final int paiLow,
                final int paiModerate,
                final int paiHigh,
                final int minutesLow,
                final int minutesModerate,
                final int minutesHigh) {
            this.day = day;
            this.gaugeSegments = gaugeSegments;
            this.today = today;
            this.total = total;
            this.paiLow = paiLow;
            this.paiModerate = paiModerate;
            this.paiHigh = paiHigh;
            this.minutesLow = minutesLow;
            this.minutesModerate = minutesModerate;
            this.minutesHigh = minutesHigh;
        }

        public int getTotal() {
            return total;
        }

        public float[] getGaugeSegments() {
            return gaugeSegments;
        }
    }

    protected static class WeekData {
        private final long[] epochDays;
        private final int[] totals;
        private final int[] today;

        WeekData(final long[] epochDays, final int[] totals, final int[] today) {
            this.epochDays = epochDays;
            this.totals = totals;
            this.today = today;
        }
    }

    protected static class PaiChartsData extends ChartsData {
        private final WeekData weekBeforeData;
        private final DayData dayData;

        PaiChartsData(final DayData dayData, final WeekData weekBeforeData) {
            this.dayData = dayData;
            this.weekBeforeData = weekBeforeData;
        }

        public DayData getDayData() {
            return dayData;
        }

        public WeekData getWeekBeforeData() {
            return weekBeforeData;
        }
    }

    @Nullable
    @Override
    public ChartDataRange getAvailableDataRange(final GBDevice device, final DBHandler db) {
        return ChartDataRange.ofSamples(device.getDeviceCoordinator().getPaiSampleProvider(device, db.getDaoSession()));
    }
}
