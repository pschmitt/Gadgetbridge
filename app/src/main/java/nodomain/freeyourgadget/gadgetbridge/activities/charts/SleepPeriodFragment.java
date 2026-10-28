/*  Copyright (C) 2017-2024 Andreas Shimokawa, Carsten Pfeiffer, José Rebelo,
    Pavel Elagin, Petr Vaněk, a0z

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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.utils.ViewPortHandler;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import kotlin.jvm.functions.Function1;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartLegendView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.DaySelections;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.sleep.SleepPeriodChartData;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.sleep.SleepScoreChartData;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.databinding.FragmentWeeksleepChartBinding;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser;
import nodomain.freeyourgadget.gadgetbridge.model.SleepScoreSample;
import nodomain.freeyourgadget.gadgetbridge.model.SleepSession;
import nodomain.freeyourgadget.gadgetbridge.util.Accumulator;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;
import nodomain.freeyourgadget.gadgetbridge.util.SleepRangeUtils;

public class SleepPeriodFragment extends SleepFragment<SleepPeriodFragment.MyChartsData> {
    protected static final Logger LOG = LoggerFactory.getLogger(SleepPeriodFragment.class);

    protected int TOTAL_DAYS = getRangeDays();

    private FragmentWeeksleepChartBinding binding;
    protected int mTargetValue = 0;

    protected boolean SHOW_BALANCE;

    public static SleepPeriodFragment newInstance(int totalDays) {
        SleepPeriodFragment fragmentFirst = new SleepPeriodFragment();
        Bundle args = new Bundle();
        args.putInt("totalDays", totalDays);
        fragmentFirst.setArguments(args);
        return fragmentFirst;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TOTAL_DAYS = getArguments() != null ? getArguments().getInt("totalDays") : 0;
    }

    private MySleepWeeklyData getMySleepWeeklyData(final GBDevice device, final List<List<SleepSession>> sessionsByDay, final boolean showRemSleep, final boolean showAwakeSleep) {
        int totalDaysForAverage = 0;
        long awakeWeeklyTotal = 0;
        long remWeeklyTotal = 0;
        long deepWeeklyTotal = 0;
        long lightWeeklyTotal = 0;

        for (List<SleepSession> daySessions : sessionsByDay) {
            if (calculateBalance(daySessions) > 0) {
                totalDaysForAverage++;
            }

            final List<Float> totalAmounts = getTotalsForSessions(daySessions, showRemSleep, showAwakeSleep);
            int i = 0;
            deepWeeklyTotal += totalAmounts.get(i++).longValue();
            lightWeeklyTotal += totalAmounts.get(i++).longValue();
            if (showRemSleep) {
                remWeeklyTotal += totalAmounts.get(i++).longValue();
            }
            if (showAwakeSleep) {
                awakeWeeklyTotal += totalAmounts.get(i++).longValue();
            }
        }

        return new MySleepWeeklyData(
                awakeWeeklyTotal,
                remWeeklyTotal,
                deepWeeklyTotal,
                lightWeeklyTotal,
                totalDaysForAverage);
    }

    private List<List<SleepSession>> getSleepSessionsByDay(final DBHandler db,
                                                           final Calendar day,
                                                           final GBDevice device) {
        final Calendar firstDay = (Calendar) day.clone();
        firstDay.add(Calendar.DATE, -TOTAL_DAYS + 1);

        final int[] firstRange = SleepRangeUtils.getSleepRange(firstDay);
        final int[] lastRange = SleepRangeUtils.getSleepRange(day);

        final List<SleepSession> allSessions = device.getDeviceCoordinator()
                .getSleepSessionProvider(device, db.getDaoSession())
                .getSleepSessionsEndingIn(firstRange[0], lastRange[1]);

        final List<List<SleepSession>> result = new ArrayList<>(TOTAL_DAYS);
        final Calendar cursor = (Calendar) firstDay.clone();
        for (int counter = 0; counter < TOTAL_DAYS; counter++) {
            final int[] range = SleepRangeUtils.getSleepRange(cursor);
            final List<SleepSession> daySessions = new ArrayList<>();
            for (SleepSession session : allSessions) {
                final long endSeconds = session.getEndTime() / 1000L;
                if (endSeconds > range[0] && endSeconds <= range[1]) {
                    daySessions.add(session);
                }
            }
            result.add(daySessions);
            cursor.add(Calendar.DATE, 1);
        }
        return result;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentWeeksleepChartBinding.inflate(inflater, container, false);

        View rootView = binding.getRoot();
        rootView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            getChartsHost().enableSwipeRefresh(scrollY == 0);
        });

        final int goal = getGoal();
        if (goal >= 0) {
            mTargetValue = goal;
        }

        SHOW_BALANCE = GBApplication.getPrefs().getBoolean("charts_show_balance_sleep", true);
        if (SHOW_BALANCE) {
            binding.balance.setVisibility(View.VISIBLE);
        } else {
            binding.balance.setVisibility(View.GONE);
        }

        if (!supportsSleepScore()) {
            binding.sleepScoreWrapper.setVisibility(View.GONE);
        } else {
            binding.sleepScoreChart.setZoomable(TOTAL_DAYS > 7);
            binding.sleepScoreChart.dismissSelectionOnTapOutside(rootView);
        }

        binding.weekSleepChart.setZoomable(TOTAL_DAYS > 7);
        binding.weekSleepChart.dismissSelectionOnTapOutside(rootView);
        // refresh immediately instead of use refreshIfVisible(), for perceived performance
        refresh();

        return rootView;
    }

    @Override
    protected void updateChartsnUIThread(MyChartsData mcd) {
        final WeekChartsData weekBeforeData = mcd.getWeekBeforeData();
        final boolean showRemSleep = mcd.isShowRemSleep();
        final boolean showAwakeSleep = mcd.isShowAwakeSleep();
        updateWeekChart(weekBeforeData, showRemSleep, showAwakeSleep);

        if (supportsSleepScore()) {
            updateSleepScoreChart(weekBeforeData);
            final String emptyValue = getString(R.string.stats_empty_value);
            final List<StatTileData> scoreStats = new ArrayList<>();
            scoreStats.add(new StatTileData(weekBeforeData.getHighestSleepScore() > 0 ? String.valueOf(weekBeforeData.getHighestSleepScore()) : emptyValue, getString(R.string.highest)));
            scoreStats.add(new StatTileData(weekBeforeData.getLowestSleepScore() > 0 ? String.valueOf(weekBeforeData.getLowestSleepScore()) : emptyValue, getString(R.string.lowest)));
            scoreStats.add(new StatTileData(weekBeforeData.getAvgSleepScore() > 0 ? String.valueOf(weekBeforeData.getAvgSleepScore()) : emptyValue, getString(R.string.hr_average)));
            binding.sleepScoreStatsContainer.removeAllViews();
            StatTileGridUtilKt.addStatTileGrid(binding.sleepScoreStatsContainer, requireContext(), scoreStats, 0);
        }

        final MySleepWeeklyData sleepWeeklyData = mcd.getSleepWeeklyData();
        final int totalDaysForAverage = sleepWeeklyData.getTotalDaysForAverage();
        final List<StatTileData> legendStats = new ArrayList<>();
        if (totalDaysForAverage > 0) {
            float avgDeep = Math.abs(sleepWeeklyData.getTotalDeep() / totalDaysForAverage);
            legendStats.add(buildSleepStageTile(R.color.chart_deep_sleep_dark, R.string.sleep_colored_stats_deep_avg,
                    DateTimeUtils.formatDurationHoursMinutes((int) avgDeep, TimeUnit.MINUTES)));
            float avgLight = Math.abs(sleepWeeklyData.getTotalLight() / totalDaysForAverage);
            legendStats.add(buildSleepStageTile(R.color.chart_light_sleep_dark, R.string.sleep_colored_stats_light_avg,
                    DateTimeUtils.formatDurationHoursMinutes((int) avgLight, TimeUnit.MINUTES)));
            if (showRemSleep) {
                float avgRem = Math.abs(sleepWeeklyData.getTotalRem() / totalDaysForAverage);
                legendStats.add(buildSleepStageTile(R.color.chart_rem_sleep_dark, R.string.sleep_colored_stats_rem_avg,
                        DateTimeUtils.formatDurationHoursMinutes((int) avgRem, TimeUnit.MINUTES)));
            }
            if (showAwakeSleep) {
                float avgAwake = Math.abs(sleepWeeklyData.getTotalAwake() / totalDaysForAverage);
                legendStats.add(buildSleepStageTile(R.color.chart_awake_sleep_dark, R.string.sleep_colored_stats_awake_avg,
                        DateTimeUtils.formatDurationHoursMinutes((int) avgAwake, TimeUnit.MINUTES)));
            }
        } else {
            legendStats.add(buildSleepStageTile(R.color.chart_deep_sleep_dark, R.string.sleep_colored_stats_deep_avg, "-"));
            legendStats.add(buildSleepStageTile(R.color.chart_light_sleep_dark, R.string.sleep_colored_stats_light_avg, "-"));
            if (showRemSleep) {
                legendStats.add(buildSleepStageTile(R.color.chart_rem_sleep_dark, R.string.sleep_colored_stats_rem_avg, "-"));
            }
            if (showAwakeSleep) {
                legendStats.add(buildSleepStageTile(R.color.chart_awake_sleep_dark, R.string.sleep_colored_stats_awake_avg, "-"));
            }
        }
        binding.sleepLegendStatsContainer.removeAllViews();
        StatTileGridUtilKt.addStatTileGrid(binding.sleepLegendStatsContainer, requireContext(), legendStats, 0);

        binding.sleepDates.setText(DateTimeUtils.formatDaysUntil(TOTAL_DAYS, getTSEnd()));

        binding.balance.setText(mcd.getWeekBeforeData().getBalanceMessage());
    }

    @Override
    protected MyChartsData refreshInBackground(ChartsHost chartsHost, DBHandler db, GBDevice device) {
        Calendar day = Calendar.getInstance();
        day.setTime(chartsHost.getEndDate());
        //NB: we could have omitted the day, but this way we can move things to the past easily
        List<List<SleepSession>> sessionsByDay = getSleepSessionsByDay(db, day, device);
        final boolean showRemSleep = supportsRemSleep(device) || hasRemSleep(sessionsByDay);
        final boolean showAwakeSleep = supportsAwakeSleep(device) || hasAwakeSleep(sessionsByDay);
        WeekChartsData weekBeforeData = refreshWeekBeforeData(db, day, device, sessionsByDay, showRemSleep, showAwakeSleep);
        MySleepWeeklyData sleepWeeklyData = getMySleepWeeklyData(device, sessionsByDay, showRemSleep, showAwakeSleep);

        return new MyChartsData(weekBeforeData, sleepWeeklyData, showRemSleep, showAwakeSleep);
    }

    private static boolean hasRemSleep(final List<List<SleepSession>> sessionsByDay) {
        for (final List<SleepSession> daySessions : sessionsByDay) {
            for (final SleepSession session : daySessions) {
                if (session.getRemSleepDuration() > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasAwakeSleep(final List<List<SleepSession>> sessionsByDay) {
        for (final List<SleepSession> daySessions : sessionsByDay) {
            for (final SleepSession session : daySessions) {
                if (session.getAwakeSleepDuration() > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    protected WeekChartsData refreshWeekBeforeData(DBHandler db, Calendar day, GBDevice device, List<List<SleepSession>> sessionsByDay, final boolean showRemSleep, final boolean showAwakeSleep) {
        day = (Calendar) day.clone(); // do not modify the caller's argument
        day.add(Calendar.DATE, -TOTAL_DAYS + 1);

        long balance = 0;
        long daily_balance = 0;
        int totalDaysForAverage = 0;
        final long[] epochDays = new long[TOTAL_DAYS];
        final List<List<Float>> stageMinutes = new ArrayList<>(TOTAL_DAYS);
        final int[] sleepScores = new int[TOTAL_DAYS];
        final Accumulator sleepScoreAccumulator = new Accumulator();
        for (int counter = 0; counter < TOTAL_DAYS; counter++) {
            // Sleep stages
            List<SleepSession> daySessions = sessionsByDay.get(counter);
            daily_balance = calculateBalance(daySessions);
            if (daily_balance > 0) {
                totalDaysForAverage++;
            }
            balance += daily_balance;
            stageMinutes.add(getTotalsForSessions(daySessions, showRemSleep, showAwakeSleep));
            epochDays[counter] = LocalDate.of(day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH)).toEpochDay();
            // Sleep score
            if (supportsSleepScore()) {
                List<? extends SleepScoreSample> sleepScoreSamples = getSleepScoreSamples(db, device, day);
                if (!sleepScoreSamples.isEmpty() && sleepScoreSamples.get(sleepScoreSamples.size() - 1).getSleepScore() > 0) {
                    int sleepScore = sleepScoreSamples.get(sleepScoreSamples.size() - 1).getSleepScore();
                    sleepScoreAccumulator.add(sleepScore);
                    sleepScores[counter] = sleepScore;
                }
            }
            day.add(Calendar.DATE, 1);
        }

        final double[][] minutes = new double[stageMinutes.isEmpty() ? 0 : stageMinutes.get(0).size()][TOTAL_DAYS];
        for (int dayIndex = 0; dayIndex < TOTAL_DAYS; dayIndex++) {
            for (int stage = 0; stage < minutes.length; stage++) {
                minutes[stage][dayIndex] = stageMinutes.get(dayIndex).get(stage);
            }
        }

        float average = 0;
        if (totalDaysForAverage > 0) {
            average = Math.abs(balance / totalDaysForAverage);
        }
        return new WeekChartsData(
                epochDays,
                minutes,
                getBalanceMessage(balance, mTargetValue, totalDaysForAverage),
                average,
                sleepScores,
                (int) Math.round(sleepScoreAccumulator.getAverage()),
                (int) Math.round(sleepScoreAccumulator.getMax()),
                (int) Math.round(sleepScoreAccumulator.getMin())
        );
    }

    private void updateWeekChart(final WeekChartsData data, final boolean showRemSleep, final boolean showAwakeSleep) {
        final String[] labels = getStageLabels(showRemSleep, showAwakeSleep);
        final int[] colors = getColors(showRemSleep, showAwakeSleep);
        final int averageColor = data.getAverage() > mTargetValue ? Color.GREEN : Color.RED;
        final boolean showAverage = GBApplication.getPrefs().getBoolean("charts_show_average", true);
        final int targetColor = getResources().getColor(R.color.chart_deep_sleep_dark);
        final ChartSpec spec = SleepPeriodChartData.periodSpec(
                data.epochDays, data.stageMinutes, labels, colors,
                mTargetValue, targetColor, data.getAverage(), showAverage, averageColor
        );

        // The last stage is awake time, which does not count towards the total sleep time
        final int sleepStages = showAwakeSleep ? labels.length - 1 : labels.length;
        final String emptyValue = getString(R.string.stats_empty_value);
        final List<String> rowLabels = new ArrayList<>();
        final List<Integer> rowColors = new ArrayList<>();
        final List<Function1<Integer, String>> rowTexts = new ArrayList<>();
        rowLabels.add("");
        rowColors.add(null);
        rowTexts.add(i -> {
            double total = 0;
            for (int stage = 0; stage < sleepStages; stage++) {
                total += data.stageMinutes[stage][i];
            }
            return total > 0 ? getString(R.string.sleep_total_value, getHM((long) total)) : emptyValue;
        });
        for (int stage = 0; stage < labels.length; stage++) {
            final int index = stage;
            rowLabels.add(labels[stage]);
            rowColors.add(colors[stage]);
            rowTexts.add(i -> data.stageMinutes[index][i] > 0 ? getHM((long) data.stageMinutes[index][i]) : emptyValue);
        }
        binding.weekSleepChart.setSelectionContent(x -> DaySelections.of(
                data.epochDays, x, rowLabels, rowColors, rowTexts, emptyValue
        ));
        binding.weekSleepChart.setSpec(spec);

        final List<ChartSeries> legend = new ArrayList<>();
        if (!spec.isEmpty()) {
            for (int stage = 0; stage < labels.length; stage++) {
                legend.add(ChartLegendView.squareItem(labels[stage], colors[stage]));
            }
            if (mTargetValue > 0) {
                legend.add(ChartLegendView.lineItem(getString(R.string.target), targetColor));
            }
            if (showAverage && data.getAverage() > 0) {
                legend.add(ChartLegendView.lineItem(getString(R.string.average, getAverage(data.getAverage())), averageColor));
            }
        }
        binding.weekSleepChartLegend.setSeries(legend);
    }

    private String[] getStageLabels(final boolean showRemSleep, final boolean showAwakeSleep) {
        String[] labels = {
                getString(R.string.sleep_colored_stats_deep),
                getString(R.string.sleep_colored_stats_light)
        };
        if (showRemSleep) {
            labels = ArrayUtils.add(labels, getString(R.string.sleep_colored_stats_rem));
        }
        if (showAwakeSleep) {
            labels = ArrayUtils.add(labels, getString(R.string.abstract_chart_fragment_kind_awake_sleep));
        }
        return labels;
    }

    private void updateSleepScoreChart(final WeekChartsData data) {
        final int color = getResources().getColor(R.color.chart_light_sleep_light);
        final String label = getString(R.string.sleep_score);
        final String emptyValue = getString(R.string.stats_empty_value);
        final Function1<Integer, String> rowText = i -> data.sleepScores[i] > 0 ? String.valueOf(data.sleepScores[i]) : emptyValue;
        binding.sleepScoreChart.setSelectionContent(x -> DaySelections.of(
                data.epochDays, x, Collections.singletonList(label), Collections.singletonList(color),
                Collections.singletonList(rowText), emptyValue
        ));
        binding.sleepScoreChart.setSpec(SleepScoreChartData.periodSpec(data.epochDays, data.sleepScores, label, color));
    }

    @Override
    protected void renderCharts() {
        binding.weekSleepChart.invalidate();
        binding.sleepScoreChart.invalidate();
    }

    @Override
    public String getTitle() {
        if (GBApplication.getPrefs().getChartsRange() == ChartsRange.MONTH) {
            return getString(R.string.weeksleepchart_sleep_a_month);
        } else {
            return getString(R.string.weeksleepchart_sleep_a_week);
        }
    }

    String getPieDescription(int targetValue) {
        return getString(R.string.weeksleepchart_today_sleep_description, DateTimeUtils.formatDurationHoursMinutes(targetValue, TimeUnit.MINUTES));
    }

    int getGoal() {
        return GBApplication.getPrefs().getInt(ActivityUser.PREF_USER_SLEEP_DURATION_MINUTES, ActivityUser.defaultUserSleepDurationGoal);
    }

    protected long calculateBalance(List<SleepSession> sessions) {
        long balance = 0;

        for (SleepSession session : sessions) {
            balance += session.getDeepSleepDuration() + session.getLightSleepDuration() + session.getRemSleepDuration();
        }
        return (int) (balance / 60);
    }

    protected String getBalanceMessage(long balance, int targetValue, int totalDaysForAverage) {
        if (balance > 0) {
            final long totalBalance = balance - ((long) targetValue * totalDaysForAverage);
            if (totalBalance > 0)
                return getString(R.string.overslept, getHM(totalBalance));
            else
                return getString(R.string.lack_of_sleep, getHM(Math.abs(totalBalance)));
        } else
            return getString(R.string.no_data);
    }

    List<Float> getTotalsForSessions(List<SleepSession> sessions, final boolean showRemSleep, final boolean showAwakeSleep) {
        long totalSecondsDeepSleep = 0;
        long totalSecondsLightSleep = 0;
        long totalSecondsRemSleep = 0;
        long totalSecondsAwakeSleep = 0;
        for (SleepSession session : sessions) {
            totalSecondsDeepSleep += session.getDeepSleepDuration();
            totalSecondsLightSleep += session.getLightSleepDuration();
            totalSecondsRemSleep += session.getRemSleepDuration();
            totalSecondsAwakeSleep += session.getAwakeSleepDuration();
        }
        int totalMinutesDeepSleep = (int) (totalSecondsDeepSleep / 60);
        int totalMinutesLightSleep = (int) (totalSecondsLightSleep / 60);
        int totalMinutesRemSleep = (int) (totalSecondsRemSleep / 60);
        int totalMinutesAwakeSleep = (int) (totalSecondsAwakeSleep / 60);

        final List<Float> activityAmountsTotals = new ArrayList<>(4);
        activityAmountsTotals.add((float) totalMinutesDeepSleep);
        activityAmountsTotals.add((float) totalMinutesLightSleep);
        if (showRemSleep) {
            activityAmountsTotals.add((float) totalMinutesRemSleep);
        }
        if (showAwakeSleep) {
            activityAmountsTotals.add((float) totalMinutesAwakeSleep);
        }

        return activityAmountsTotals;
    }

    protected String formatPieValue(long value) {
        return DateTimeUtils.formatDurationHoursMinutes(value, TimeUnit.MINUTES);
    }

    String[] getPieLabels() {
        String[] labels = {
                getString(R.string.abstract_chart_fragment_kind_deep_sleep),
                getString(R.string.abstract_chart_fragment_kind_light_sleep)
        };
        if (supportsRemSleep(getChartsHost().getDevice())) {
            labels = ArrayUtils.add(labels, getString(R.string.abstract_chart_fragment_kind_rem_sleep));
        }
        if (supportsAwakeSleep(getChartsHost().getDevice())) {
            labels = ArrayUtils.add(labels, getString(R.string.abstract_chart_fragment_kind_awake_sleep));
        }
        return labels;
    }

    DataSetValueFormatter getPieValueFormatter() {
        return new DataSetValueFormatter() {
            @Override
            public String getFormattedValue(final float value, final Entry<?> entry, final int dataSetIndex, final ViewPortHandler viewPortHandler) {
                return formatPieValue((long) value);
            }
        };
    }

    int[] getColors(final boolean showRemSleep, final boolean showAwakeSleep) {
        int[] colors = {akDeepSleep.color, akLightSleep.color};
        if (showRemSleep) {
            colors = ArrayUtils.add(colors, akRemSleep.color);
        }
        if (showAwakeSleep) {
            colors = ArrayUtils.add(colors, akAwakeSleep.color);
        }
        return colors;
    }

    @Override
    protected void setupLegend(Chart<?> chart) {
    }

    private String getHM(long value) {
        return DateTimeUtils.formatDurationHoursMinutes(value, TimeUnit.MINUTES);
    }

    private StatTileData buildSleepStageTile(final int colorRes, final int labelRes, final String value) {
        return new StatTileData(value, getString(labelRes), null, null, null, null, ContextCompat.getColor(requireContext(), colorRes));
    }

    String getAverage(float value) {
        return getHM((long) value);
    }

    protected static class MyChartsData extends ChartsData {
        private final WeekChartsData weekBeforeData;
        private final MySleepWeeklyData sleepWeeklyData;
        private final boolean showRemSleep;
        private final boolean showAwakeSleep;

        MyChartsData(WeekChartsData weekBeforeData, MySleepWeeklyData sleepWeeklyData, final boolean showRemSleep, final boolean showAwakeSleep) {
            this.weekBeforeData = weekBeforeData;
            this.sleepWeeklyData = sleepWeeklyData;
            this.showRemSleep = showRemSleep;
            this.showAwakeSleep = showAwakeSleep;
        }

        WeekChartsData getWeekBeforeData() {
            return weekBeforeData;
        }

        MySleepWeeklyData getSleepWeeklyData() {
            return sleepWeeklyData;
        }

        boolean isShowRemSleep() {
            return showRemSleep;
        }

        boolean isShowAwakeSleep() {
            return showAwakeSleep;
        }
    }

    private int getRangeDays() {
        return GBApplication.getPrefs().getChartsRange().getDays();
    }

    protected static class WeekChartsData {
        private final long[] epochDays;
        private final double[][] stageMinutes;
        private final String balanceMessage;
        private final float average;
        private final int[] sleepScores;
        private final int avgSleepScore;
        private final int highestSleepScore;
        private final int lowestSleepScore;

        public WeekChartsData(long[] epochDays, double[][] stageMinutes, String balanceMessage, float average, int[] sleepScores, int avgSleepScore, int highestSleepScore, int lowestSleepScore) {
            this.epochDays = epochDays;
            this.stageMinutes = stageMinutes;
            this.balanceMessage = balanceMessage;
            this.average = average;
            this.sleepScores = sleepScores;
            this.avgSleepScore = avgSleepScore;
            this.highestSleepScore = highestSleepScore;
            this.lowestSleepScore = lowestSleepScore;
        }

        public int getHighestSleepScore() {
            return highestSleepScore;
        }

        public int getLowestSleepScore() {
            return lowestSleepScore;
        }

        public int getAvgSleepScore() {
            return avgSleepScore;
        }

        public String getBalanceMessage() {
            return balanceMessage;
        }

        public float getAverage() {
            return average;
        }
    }

    private static class MySleepWeeklyData {
        private final long totalAwake;
        private final long totalRem;
        private final long totalDeep;
        private final long totalLight;
        private final int totalDaysForAverage;

        public MySleepWeeklyData(long totalAwake,
                                 long totalRem,
                                 long totalDeep,
                                 long totalLight,
                                 int totalDaysForAverage) {
            this.totalDeep = totalDeep;
            this.totalRem = totalRem;
            this.totalAwake = totalAwake;
            this.totalLight = totalLight;
            this.totalDaysForAverage = totalDaysForAverage;
        }

        public long getTotalAwake() {
            return this.totalAwake;
        }

        public long getTotalRem() {
            return this.totalRem;
        }

        public long getTotalDeep() {
            return this.totalDeep;
        }

        public long getTotalLight() {
            return this.totalLight;
        }

        public int getTotalDaysForAverage() {
            return this.totalDaysForAverage;
        }
    }
}
