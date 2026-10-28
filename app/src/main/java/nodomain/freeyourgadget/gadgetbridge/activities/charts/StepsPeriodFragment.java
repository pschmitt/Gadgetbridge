package nodomain.freeyourgadget.gadgetbridge.activities.charts;

import android.content.Context;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.Chart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartSelection;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.GbChartView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.steps.StepsPeriodChartData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutValueFormatter;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;

public class StepsPeriodFragment extends StepsFragment<StepsPeriodFragment.StepsData> {
    protected static final Logger LOG = LoggerFactory.getLogger(StepsPeriodFragment.class);

    private TextView mDateView;
    private LinearLayout stepsPeriodStatsContainer;
    private GbChartView stepsChart;

    private TextView mBalanceView;

    protected int CHART_TEXT_COLOR;
    protected int TEXT_COLOR;
    protected int STEPS_GOAL;
    protected boolean SHOW_BALANCE;

    protected int BACKGROUND_COLOR;
    protected int DESCRIPTION_COLOR;

    @Override
    protected boolean isSingleDay() {
        return false;
    }

    public static StepsPeriodFragment newInstance(int totalDays) {
        StepsPeriodFragment fragmentFirst = new StepsPeriodFragment();
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
        View rootView = inflater.inflate(R.layout.fragment_steps_period, container, false);

        rootView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            getChartsHost().enableSwipeRefresh(scrollY == 0);
        });

        mDateView = rootView.findViewById(R.id.steps_date_view);
        stepsChart = rootView.findViewById(R.id.steps_chart);
        stepsChart.setZoomable(TOTAL_DAYS > 7);
        stepsChart.dismissSelectionOnTapOutside(rootView);
        stepsPeriodStatsContainer = rootView.findViewById(R.id.steps_period_stats_container);
        STEPS_GOAL = GBApplication.getPrefs().getInt(ActivityUser.PREF_USER_STEPS_GOAL, ActivityUser.defaultUserStepsGoal);

        mBalanceView = rootView.findViewById(R.id.balance);

        SHOW_BALANCE = GBApplication.getPrefs().getBoolean("charts_show_balance_steps", true);
        if (SHOW_BALANCE) {
            mBalanceView.setVisibility(View.VISIBLE);
        } else {
            mBalanceView.setVisibility(View.GONE);
        }

        refresh();

        return rootView;
    }

    @Override
    public String getTitle() {
        return getString(R.string.steps);
    }

    @Override
    protected void init() {
        TEXT_COLOR = GBApplication.getTextColor(requireContext());
        CHART_TEXT_COLOR = GBApplication.getSecondaryTextColor(requireContext());
        BACKGROUND_COLOR = GBApplication.getBackgroundColor(getContext());
        DESCRIPTION_COLOR = GBApplication.getTextColor(getContext());
        CHART_TEXT_COLOR = GBApplication.getSecondaryTextColor(getContext());
    }

    @Override
    protected StepsData refreshInBackground(ChartsHost chartsHost, DBHandler db, GBDevice device) {
        Calendar day = Calendar.getInstance();
        day.setTime(getEndDate());
        List<StepsDay> stepsDaysData = getMyStepsDaysData(db, day, device);
        return new StepsData(stepsDaysData);
    }

    @Override
    protected void updateChartsnUIThread(StepsData stepsData) {
        mDateView.setText(DateTimeUtils.formatDaysUntil(TOTAL_DAYS, getTSEnd()));

        final long[] epochDays = new long[stepsData.days.size()];
        final long[] steps = new long[stepsData.days.size()];
        for (int i = 0; i < stepsData.days.size(); i++) {
            epochDays[i] = epochDay(stepsData.days.get(i).day);
            steps[i] = stepsData.days.get(i).steps;
        }
        final int stepsColor = ContextCompat.getColor(requireContext(), R.color.steps_color);
        stepsChart.setSelectionContent(x -> selection(epochDays, steps, stepsColor, x));
        stepsChart.setSpec(StepsPeriodChartData.buildChartSpec(epochDays, steps, stepsColor, STEPS_GOAL));

        final WorkoutValueFormatter valueFormatter = new WorkoutValueFormatter();
        stepsPeriodStatsContainer.removeAllViews();
        final List<StatTileData> stats = new ArrayList<>();
        stats.add(new StatTileData(NumberFormat.getInstance().format(stepsData.stepsDailyAvg), getString(R.string.steps_avg)));
        stats.add(new StatTileData(NumberFormat.getInstance().format(stepsData.totalSteps), getString(R.string.steps_total)));
        stats.add(new StatTileData(valueFormatter.formatValue(stepsData.distanceDailyAvg, "km"), getString(R.string.distance_avg)));
        stats.add(new StatTileData(valueFormatter.formatValue(stepsData.totalDistance, "km"), getString(R.string.distance_total)));
        StatTileGridUtilKt.addStatTileGrid(stepsPeriodStatsContainer, requireContext(), stats, 0);

        mBalanceView.setText(stepsData.getBalanceMessage(getContext(), STEPS_GOAL));
    }

    /**
     * The tooltip of a day: its date and steps.
     */
    private ChartSelection selection(final long[] epochDays, final long[] steps, final int stepsColor, final double x) {
        final Locale locale = Locale.getDefault();
        final String title = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEMMMd"), locale)
                .format(LocalDate.ofEpochDay(Math.round(x)));
        long daySteps = 0;
        for (int i = 0; i < epochDays.length; i++) {
            if (epochDays[i] == Math.round(x)) {
                daySteps = steps[i];
            }
        }
        if (daySteps == 0) {
            return new ChartSelection(title, Collections.emptyList(), title + ".");
        }
        final String value = new WorkoutValueFormatter().formatValue(daySteps, ActivitySummaryEntries.UNIT_STEPS);
        return new ChartSelection(
                title,
                Collections.singletonList(new ChartSelection.Row(stepsColor, value)),
                title + ". " + getString(R.string.steps) + " " + value + "."
        );
    }

    private static long epochDay(final Calendar day) {
        return LocalDate.of(day.get(Calendar.YEAR), day.get(Calendar.MONTH) + 1, day.get(Calendar.DAY_OF_MONTH)).toEpochDay();
    }

    @Override
    protected void renderCharts() {
        stepsChart.invalidate();
    }

    @Override
    protected void setupLegend(Chart<?> chart) {
    }

    protected static class StepsData extends ChartsData {
        List<StepsDay> days;
        long stepsDailyAvg = 0;
        double distanceDailyAvg = 0;
        long totalSteps = 0;
        double totalDistance = 0;
        StepsDay todayStepsDay;

        protected StepsData(List<StepsDay> days) {
            this.days = days;
            int daysCounter = 0;
            for (StepsDay day : days) {
                this.totalSteps += day.steps;
                this.totalDistance += day.distance;
                if (day.steps > 0) {
                    daysCounter++;
                }
            }
            if (daysCounter > 0) {
                this.stepsDailyAvg = this.totalSteps / daysCounter;
                this.distanceDailyAvg = this.totalDistance / daysCounter;
            }
            this.todayStepsDay = days.get(days.size() - 1);
        }

        protected String getBalanceMessage(final Context context, final int targetValue) {
            if (totalSteps == 0) {
                return context.getString(R.string.no_data);
            }

            final long totalBalance = totalSteps - ((long) targetValue * days.size());
            if (totalBalance > 0) {
                return context.getString(R.string.overstep, NumberFormat.getInstance().format(Math.abs(totalBalance)));
            } else {
                return context.getString(R.string.lack_of_step, NumberFormat.getInstance().format(Math.abs(totalBalance)));

            }
        }
    }
}
