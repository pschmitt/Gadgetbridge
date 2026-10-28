/*  Copyright (C) 2023-2024

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

import android.graphics.Color;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import kotlin.jvm.functions.Function1;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartLegendView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.DaySelections;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.GbChartView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.stress.StressChartData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.StressSample;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;

public class StressPeriodFragment extends StressFragment<StressPeriodFragment.MyChartsData> {
    protected static final Logger LOG = LoggerFactory.getLogger(StressPeriodFragment.class);

    private static final StressType[] BAR_LEVELS = {
            StressType.HIGH, StressType.MODERATE, StressType.MILD, StressType.RELAXED, StressType.UNKNOWN,
    };

    protected int TOTAL_DAYS = getRangeDays();

    private LinearLayout mStatsContainer;
    private TextView stressDatesText;
    private PieChart mStressLevelsPieChart;
    private GbChartView mWeekChart;
    private ChartLegendView mWeekLegend;

    public static StressPeriodFragment newInstance(int totalDays) {
        StressPeriodFragment fragment = new StressPeriodFragment();
        Bundle args = new Bundle();
        args.putInt("totalDays", totalDays);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TOTAL_DAYS = getArguments() != null ? getArguments().getInt("totalDays") : 0;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_weekstress_chart, container, false);

        rootView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            getChartsHost().enableSwipeRefresh(scrollY == 0);
        });

        mWeekChart = rootView.findViewById(R.id.weekstresschart);
        mWeekChart.setZoomable(TOTAL_DAYS > 7);
        mWeekChart.dismissSelectionOnTapOutside(rootView);
        mWeekLegend = rootView.findViewById(R.id.weekstress_chart_legend);
        mStatsContainer = rootView.findViewById(R.id.stress_stats_container);
        stressDatesText = rootView.findViewById(R.id.stress_dates);
        mStressLevelsPieChart = rootView.findViewById(R.id.stress_pie_chart);

        setupPieChart();
        refresh();

        return rootView;
    }

    @Override
    protected MyChartsData refreshInBackground(ChartsHost chartsHost, DBHandler db, GBDevice device) {
        Calendar day = Calendar.getInstance();
        day.setTime(chartsHost.getEndDate());
        StressPeriodBars weekBeforeData = refreshWeekBeforeStressData(db, day, device);
        MyStressWeeklyData stressWeeklyData = getMyStressWeeklyData(db, day, device);

        return new MyChartsData(weekBeforeData, stressWeeklyData);
    }

    @Override
    protected void updateChartsnUIThread(MyChartsData mcd) {
        updateWeekChart(mcd.getWeekBeforeData());
        updatePieChart(mcd.getStressWeeklyData());
        updateStressTiles(mcd.getStressWeeklyData());

        stressDatesText.setText(DateTimeUtils.formatDaysUntil(TOTAL_DAYS, getTSEnd()));
    }

    private void updateWeekChart(final StressPeriodBars bars) {
        final String[] labels = new String[BAR_LEVELS.length];
        final int[] colors = new int[BAR_LEVELS.length];
        for (int i = 0; i < BAR_LEVELS.length; i++) {
            labels[i] = BAR_LEVELS[i].getLabel(requireContext());
            colors[i] = BAR_LEVELS[i].getColor(requireContext());
        }
        final String averageLabel = getString(R.string.charts_legend_stress_average);
        final ChartSpec spec = StressChartData.periodSpec(bars.epochDays(), bars.minutes(), labels, colors,
                bars.averages(), averageLabel, Color.GRAY);

        final List<String> rowLabels = new ArrayList<>();
        final List<Integer> rowColors = new ArrayList<>();
        final List<Function1<Integer, String>> rowTexts = new ArrayList<>();
        final String emptyValue = getString(R.string.stats_empty_value);
        for (int level = BAR_LEVELS.length - 2; level >= 0; level--) {
            final double[] levelMinutes = bars.minutes()[level];
            rowLabels.add(labels[level]);
            rowColors.add(colors[level]);
            rowTexts.add(i -> levelMinutes[i] > 0
                    ? DateTimeUtils.formatDurationHoursMinutes(Math.round(levelMinutes[i] * 60), TimeUnit.SECONDS)
                    : emptyValue);
        }
        rowLabels.add(averageLabel);
        rowColors.add(Color.GRAY);
        rowTexts.add(i -> bars.averages()[i] > 0 ? String.valueOf(bars.averages()[i]) : emptyValue);
        mWeekChart.setSelectionContent(x -> DaySelections.of(bars.epochDays(), x, rowLabels, rowColors, rowTexts, emptyValue));
        mWeekChart.setSpec(spec);

        final List<ChartSeries> legendSeries = new ArrayList<>();
        if (!spec.getSeries().isEmpty()) {
            for (final StressType type : StressType.values()) {
                legendSeries.add(ChartLegendView.squareItem(type.getLabel(requireContext()), type.getColor(requireContext())));
            }
            legendSeries.add(ChartLegendView.lineItem(averageLabel, Color.GRAY));
        }
        mWeekLegend.setSeries(legendSeries);
    }

    private void updatePieChart(final MyStressWeeklyData stressWeeklyData) {
        List<PieEntry> pieEntries = new ArrayList<>();
        List<Integer> pieColors = new ArrayList<>();

        if (stressWeeklyData.totalDaysForAverage() > 0) {
            long totalTime = stressWeeklyData.totalStressTime();
            if (totalTime > 0) {
                if (stressWeeklyData.totalRelaxed() > 0) {
                    pieEntries.add(new PieEntry<>(stressWeeklyData.totalRelaxed(),
                            StressType.RELAXED.getLabel(getContext()), null, null));
                    pieColors.add(StressType.RELAXED.getColor(getContext()));
                }
                if (stressWeeklyData.totalMild() > 0) {
                    pieEntries.add(new PieEntry<>(stressWeeklyData.totalMild(),
                            StressType.MILD.getLabel(getContext()), null, null));
                    pieColors.add(StressType.MILD.getColor(getContext()));
                }
                if (stressWeeklyData.totalModerate() > 0) {
                    pieEntries.add(new PieEntry<>(stressWeeklyData.totalModerate(),
                            StressType.MODERATE.getLabel(getContext()), null, null));
                    pieColors.add(StressType.MODERATE.getColor(getContext()));
                }
                if (stressWeeklyData.totalHigh() > 0) {
                    pieEntries.add(new PieEntry<>(stressWeeklyData.totalHigh(),
                            StressType.HIGH.getLabel(getContext()), null, null));
                    pieColors.add(StressType.HIGH.getColor(getContext()));
                }
            }
        }

        if (pieEntries.isEmpty()) {
            pieEntries.add(new PieEntry<>(1, null, null, null));
            pieColors.add(getResources().getColor(R.color.gauge_line_color));
        }

        PieDataSet pieDataSet = new PieDataSet(pieEntries, "");
        pieDataSet.setColors(pieColors);
        pieDataSet.setValueTextColor(DESCRIPTION_COLOR);
        pieDataSet.setValueTextSize(13f);
        pieDataSet.setXValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        pieDataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
        pieDataSet.setDrawValuesEnabled(false);
        pieDataSet.setSliceSpace(2f);
        PieData pieData = new PieData(pieDataSet);

        mStressLevelsPieChart.setData(pieData);

        // Set center text with average stress level
        if (stressWeeklyData.averageStress() > 0) {
            int avgStress = stressWeeklyData.averageStress();
            int noc = String.valueOf(avgStress).length();
            SpannableString centerText = new SpannableString(avgStress + "\n" +
                    getContext().getString(R.string.stress_average));
            centerText.setSpan(new RelativeSizeSpan(1.75f), 0, noc, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            centerText.setSpan(new RelativeSizeSpan(0.72f), noc, centerText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            mStressLevelsPieChart.setCenterText(centerText);
        } else {
            SpannableString centerText = new SpannableString("-\n" +
                    getContext().getString(R.string.stress_average));
            centerText.setSpan(new RelativeSizeSpan(1.25f), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            centerText.setSpan(new RelativeSizeSpan(0.72f), 2, centerText.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            mStressLevelsPieChart.setCenterText(centerText);
        }
    }

    private void updateStressTiles(final MyStressWeeklyData stressWeeklyData) {
        final List<StatTileData> stats = new ArrayList<>();
        if (stressWeeklyData.totalDaysForAverage() > 0) {
            int relaxedAvg = (int) (stressWeeklyData.totalRelaxed() / stressWeeklyData.totalDaysForAverage());
            int mildAvg = (int) (stressWeeklyData.totalMild() / stressWeeklyData.totalDaysForAverage());
            int moderateAvg = (int) (stressWeeklyData.totalModerate() / stressWeeklyData.totalDaysForAverage());
            int highAvg = (int) (stressWeeklyData.totalHigh() / stressWeeklyData.totalDaysForAverage());

            if (stressWeeklyData.showStressLevelInPercents()) {
                long totalDailyAvg = relaxedAvg + mildAvg + moderateAvg + highAvg;
                stats.add(buildStressTile(StressType.RELAXED, String.format(Locale.ROOT, "%d%%",
                        totalDailyAvg > 0 ? Math.round(100f * relaxedAvg / totalDailyAvg) : 0)));
                stats.add(buildStressTile(StressType.MILD, String.format(Locale.ROOT, "%d%%",
                        totalDailyAvg > 0 ? Math.round(100f * mildAvg / totalDailyAvg) : 0)));
                stats.add(buildStressTile(StressType.MODERATE, String.format(Locale.ROOT, "%d%%",
                        totalDailyAvg > 0 ? Math.round(100f * moderateAvg / totalDailyAvg) : 0)));
                stats.add(buildStressTile(StressType.HIGH, String.format(Locale.ROOT, "%d%%",
                        totalDailyAvg > 0 ? Math.round(100f * highAvg / totalDailyAvg) : 0)));
            } else {
                stats.add(buildStressTile(StressType.RELAXED, DateTimeUtils.formatDurationHoursMinutes(relaxedAvg, TimeUnit.SECONDS)));
                stats.add(buildStressTile(StressType.MILD, DateTimeUtils.formatDurationHoursMinutes(mildAvg, TimeUnit.SECONDS)));
                stats.add(buildStressTile(StressType.MODERATE, DateTimeUtils.formatDurationHoursMinutes(moderateAvg, TimeUnit.SECONDS)));
                stats.add(buildStressTile(StressType.HIGH, DateTimeUtils.formatDurationHoursMinutes(highAvg, TimeUnit.SECONDS)));
            }
        } else {
            final String emptyValue = getString(R.string.stats_empty_value);
            stats.add(buildStressTile(StressType.RELAXED, emptyValue));
            stats.add(buildStressTile(StressType.MILD, emptyValue));
            stats.add(buildStressTile(StressType.MODERATE, emptyValue));
            stats.add(buildStressTile(StressType.HIGH, emptyValue));
        }

        mStatsContainer.removeAllViews();
        StatTileGridUtilKt.addStatTileGrid(mStatsContainer, requireContext(), stats, 0);
    }

    private StatTileData buildStressTile(final StressType stressType, final String value) {
        final int label = switch (stressType) {
            case RELAXED -> R.string.stress_relaxed_avg;
            case MILD -> R.string.stress_mild_avg;
            case MODERATE -> R.string.stress_moderate_avg;
            case HIGH -> R.string.stress_high_avg;
            default -> R.string.unknown;
        };
        return new StatTileData(value, getString(label), null, null, null, null, stressType.getColor(requireContext()));
    }

    private MyStressWeeklyData getMyStressWeeklyData(DBHandler db, Calendar day, GBDevice device) {
        day = (Calendar) day.clone(); // do not modify the caller's argument
        day.add(Calendar.DATE, -TOTAL_DAYS + 1);
        int totalDaysForAverage = 0;

        long relaxedWeeklyTotal = 0;
        long mildWeeklyTotal = 0;
        long moderateWeeklyTotal = 0;
        long highWeeklyTotal = 0;
        long totalStressTime = 0;
        long avgStressSum = 0;
        long avgStressSamples = 0;

        int[] stressRanges = device.getDeviceCoordinator().getStressRanges();
        boolean showStressLevelInPercents = device.getDeviceCoordinator().showStressLevelInPercents();

        DeviceCoordinator coordinator = device.getDeviceCoordinator();
        int sampleRate = 60; // Default sample rate
        int[] params = coordinator.getStressChartParameters();
        if (params != null && params.length > 0) {
            sampleRate = params[0];
        }

        for (int counter = 0; counter < TOTAL_DAYS; counter++) {
            Calendar dayStart = (Calendar) day.clone();
            Calendar dayEnd = (Calendar) day.clone();
            dayEnd.add(Calendar.DAY_OF_MONTH, 1);

            List<? extends StressSample> samples = getStressSamples(db, device,
                    (int) (dayStart.getTimeInMillis() / 1000),
                    (int) (dayEnd.getTimeInMillis() / 1000));

            if (!samples.isEmpty()) {
                totalDaysForAverage++;

                Map<StressType, Integer> dailyTotals = calculateStressTotals(samples, stressRanges, sampleRate);
                relaxedWeeklyTotal += dailyTotals.getOrDefault(StressType.RELAXED, 0);
                mildWeeklyTotal += dailyTotals.getOrDefault(StressType.MILD, 0);
                moderateWeeklyTotal += dailyTotals.getOrDefault(StressType.MODERATE, 0);
                highWeeklyTotal += dailyTotals.getOrDefault(StressType.HIGH, 0);

                // Calculate average stress for the day
                int dailyAverage = calculateAverageStress(samples);
                if (dailyAverage > 0) {
                    avgStressSum += dailyAverage;
                    avgStressSamples++;
                }
            }

            day.add(Calendar.DATE, 1);
        }

        totalStressTime = relaxedWeeklyTotal + mildWeeklyTotal + moderateWeeklyTotal + highWeeklyTotal;
        int averageStress = avgStressSamples > 0 ? Math.round((float) avgStressSum / avgStressSamples) : 0;

        return new MyStressWeeklyData(relaxedWeeklyTotal, mildWeeklyTotal, moderateWeeklyTotal,
                highWeeklyTotal, totalStressTime, averageStress, totalDaysForAverage,
                showStressLevelInPercents);
    }

    private StressPeriodBars refreshWeekBeforeStressData(DBHandler db, Calendar day, GBDevice device) {
        day = (Calendar) day.clone();
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        day.add(Calendar.DATE, -TOTAL_DAYS + 1);

        final long[] epochDays = new long[TOTAL_DAYS];
        final double[][] minutes = new double[BAR_LEVELS.length][TOTAL_DAYS];
        final int[] averages = new int[TOTAL_DAYS];

        DeviceCoordinator coordinator = device.getDeviceCoordinator();
        int sampleRate = 60;
        int[] params = coordinator.getStressChartParameters();
        if (params != null && params.length > 0) {
            sampleRate = params[0];
        }

        Calendar now = Calendar.getInstance();
        for (int counter = 0; counter < TOTAL_DAYS; counter++) {
            Calendar dayStart = (Calendar) day.clone();
            Calendar dayEnd = (Calendar) day.clone();
            dayEnd.add(Calendar.DAY_OF_MONTH, 1);

            List<? extends StressSample> samples = getStressSamples(db, device,
                    (int) (dayStart.getTimeInMillis() / 1000),
                    (int) (dayEnd.getTimeInMillis() / 1000));

            Map<StressType, Integer> dailyTotals = calculateStressTotals(samples,
                    device.getDeviceCoordinator().getStressRanges(), sampleRate);
            float totalMinutesTracked = dailyTotals.values().stream().reduce(0, Integer::sum) / 60f;

            // Calculate the total possible minutes for this day, excluding future time
            float totalPossibleMinutes;
            if (dayEnd.before(now) || dayEnd.equals(now)) {
                totalPossibleMinutes = 24 * 60;
            } else if (dayStart.after(now)) {
                totalPossibleMinutes = 0;
            } else {
                long minutesFromStartToNow = (now.getTimeInMillis() - dayStart.getTimeInMillis()) / (1000 * 60);
                totalPossibleMinutes = Math.max(0, minutesFromStartToNow);
            }

            epochDays[counter] = LocalDate.of(day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH)).toEpochDay();
            for (int level = 0; level < BAR_LEVELS.length - 1; level++) {
                minutes[level][counter] = dailyTotals.get(BAR_LEVELS[level]) / 60d;
            }
            minutes[BAR_LEVELS.length - 1][counter] = Math.max(totalPossibleMinutes - totalMinutesTracked, 0);
            averages[counter] = calculateAverageStress(samples);

            day.add(Calendar.DATE, 1);
        }
        return new StressPeriodBars(epochDays, minutes, averages);
    }

    private void setupPieChart() {
        mStressLevelsPieChart.setBackgroundColor(BACKGROUND_COLOR);
        mStressLevelsPieChart.getDescription().setTextColor(DESCRIPTION_COLOR);
        mStressLevelsPieChart.setEntryLabelColor(DESCRIPTION_COLOR);
        mStressLevelsPieChart.getDescription().setText("");
        mStressLevelsPieChart.setNoDataText("");
        mStressLevelsPieChart.setNoDataIconEnabled(false);
        mStressLevelsPieChart.setTouchEnabled(false);
        mStressLevelsPieChart.setCenterTextColor(GBApplication.getTextColor(getContext()));
        mStressLevelsPieChart.setCenterTextSize(18f);
        mStressLevelsPieChart.setHoleColor(requireContext().getResources().getColor(R.color.transparent));
        mStressLevelsPieChart.setHoleRadius(87);
        mStressLevelsPieChart.setDrawEntryLabelsEnabled(false);
        mStressLevelsPieChart.getLegend().setEnabled(false);
    }

    @Override
    protected void setupLegend(Chart<?> chart) {
    }

    @Override
    protected void renderCharts() {
        mWeekChart.invalidate();
        mStressLevelsPieChart.invalidate();
    }

    private int getRangeDays() {
        return GBApplication.getPrefs().getChartsRange().getDays();
    }

    @Override
    protected boolean isSingleDay() {
        return false;
    }

    protected static class MyChartsData extends ChartsData {
        private final StressPeriodBars weekBeforeData;
        private final MyStressWeeklyData stressWeeklyData;

        public MyChartsData(StressPeriodBars weekBeforeData,
                            MyStressWeeklyData stressWeeklyData) {
            this.weekBeforeData = weekBeforeData;
            this.stressWeeklyData = stressWeeklyData;
        }

        public StressPeriodBars getWeekBeforeData() {
            return weekBeforeData;
        }

        public MyStressWeeklyData getStressWeeklyData() {
            return stressWeeklyData;
        }
    }

    private record StressPeriodBars(long[] epochDays, double[][] minutes, int[] averages) {
    }

    private record MyStressWeeklyData(long totalRelaxed,
                                      long totalMild,
                                      long totalModerate,
                                       long totalHigh,
                                       long totalStressTime,
                                       int averageStress,
                                       int totalDaysForAverage,
                                       boolean showStressLevelInPercents) {
    }
}
