package nodomain.freeyourgadget.gadgetbridge.activities.charts;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.mikephil.charting.charts.Chart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import kotlin.jvm.functions.Function1;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.calories.CaloriesChartData;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.DaySelections;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.GbChartView;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;

public class CaloriesPeriodFragment extends CaloriesFragment<CaloriesPeriodFragment.CaloriesData> {
    protected static final Logger LOG = LoggerFactory.getLogger(CaloriesPeriodFragment.class);

    private TextView mDateView;
    private LinearLayout caloriesStatsContainer;
    private GbChartView caloriesChart;

    private TextView mBalanceView;

    protected int CALORIES_GOAL;

    @Override
    protected boolean isSingleDay() {
        return false;
    }

    public static CaloriesPeriodFragment newInstance(int totalDays) {
        CaloriesPeriodFragment fragmentFirst = new CaloriesPeriodFragment();
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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_calories_period, container, false);

        rootView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            getChartsHost().enableSwipeRefresh(scrollY == 0);
        });

        mDateView = rootView.findViewById(R.id.calories_date_view);
        caloriesChart = rootView.findViewById(R.id.calories_chart);
        caloriesChart.setZoomable(TOTAL_DAYS > 7);
        caloriesChart.dismissSelectionOnTapOutside(rootView);
        caloriesStatsContainer = rootView.findViewById(R.id.calories_period_stats_container);
        CALORIES_GOAL = GBApplication.getPrefs().getInt(ActivityUser.PREF_USER_CALORIES_BURNT, ActivityUser.defaultUserCaloriesBurntGoal);

        mBalanceView = rootView.findViewById(R.id.balance);

        refresh();

        return rootView;
    }

    @Override
    public String getTitle() {
        return getString(R.string.calories);
    }

    @Override
    protected void init() {
    }

    @Override
    protected CaloriesData refreshInBackground(ChartsHost chartsHost, DBHandler db, GBDevice device) {
        Calendar day = Calendar.getInstance();
        day.setTime(getEndDate());
        List<CaloriesDay> caloriesDaysData = getMyCaloriesDaysData(db, day, device);
        return new CaloriesData(caloriesDaysData);
    }

    @Override
    protected void updateChartsnUIThread(CaloriesData caloriesData) {
        mDateView.setText(DateTimeUtils.formatDaysUntil(TOTAL_DAYS, getTSEnd()));
        final int n = caloriesData.days.size();
        final long[] epochDays = new long[n];
        final long[] active = new long[n];
        for (int i = 0; i < n; i++) {
            final Calendar day = caloriesData.days.get(i).day;
            epochDays[i] = LocalDate.of(day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH)).toEpochDay();
            active[i] = caloriesData.days.get(i).activeCalories;
        }
        final int caloriesColor = getResources().getColor(R.color.calories_color);
        final String label = getString(R.string.active_calories);
        final String kcal = getString(R.string.calories_unit);
        final Function1<Integer, String> caloriesText = i -> active[i] > 0
                ? String.format(Locale.getDefault(), "%d %s", active[i], kcal)
                : getString(R.string.stats_empty_value);
        caloriesChart.setSelectionContent(x -> DaySelections.of(
                epochDays, x, Collections.singletonList(label), Collections.singletonList(caloriesColor),
                Collections.singletonList(caloriesText), getString(R.string.stats_empty_value)
        ));
        caloriesChart.setSpec(CaloriesChartData.periodSpec(epochDays, active, CALORIES_GOAL, caloriesColor, label));

        final List<StatTileData> stats = new ArrayList<>();
        stats.add(new StatTileData(String.format(String.valueOf(caloriesData.activeCaloriesDailyAvg)), getString(R.string.active_calories_avg)));
        stats.add(new StatTileData(String.format(String.valueOf(caloriesData.totalActiveCalories)), getString(R.string.active_calories_total)));
        stats.add(new StatTileData(String.format(String.valueOf(caloriesData.restingCaloriesDailyAvg)), getString(R.string.metabolic_rate)));
        stats.add(new StatTileData(String.format(String.valueOf(caloriesData.totalRestingCalories)), getString(R.string.resting_calories_total)));
        caloriesStatsContainer.removeAllViews();
        StatTileGridUtilKt.addStatTileGrid(caloriesStatsContainer, requireContext(), stats, 0);

        mBalanceView.setText(caloriesData.getBalanceMessage(getContext(), CALORIES_GOAL));
    }

    @Override
    protected void renderCharts() {
        caloriesChart.invalidate();
    }

    @Override
    protected void setupLegend(Chart<?> chart) {
    }

    protected static class CaloriesData extends ChartsData {
        List<CaloriesDay> days;
        long activeCaloriesDailyAvg = 0;
        long totalActiveCalories = 0;
        long restingCaloriesDailyAvg = 0;
        long totalRestingCalories = 0;
        CaloriesDay todayCaloriesDay;

        protected CaloriesData(List<CaloriesDay> days) {
            this.days = days;
            int daysCounter = days.size();
            for (CaloriesDay day : days) {
                this.totalActiveCalories += day.activeCalories;
                this.totalRestingCalories += day.restingCalories;
            }
            if (daysCounter > 0) {
                this.activeCaloriesDailyAvg = this.totalActiveCalories / daysCounter;
                this.restingCaloriesDailyAvg = this.totalRestingCalories / daysCounter;
            }
            this.todayCaloriesDay = days.get(days.size() - 1);
        }

        protected String getBalanceMessage(final Context context, final int targetValue) {
            if (totalActiveCalories == 0) {
                return context.getString(R.string.no_data);
            }

            final long totalBalance = totalActiveCalories - ((long) targetValue * days.size());
            if (totalBalance > 0) {
                return context.getString(R.string.calorie_over_goal, Math.abs(totalBalance));
            } else {
                return context.getString(R.string.calorie_under_goal, Math.abs(totalBalance));
            }
        }
    }
}
