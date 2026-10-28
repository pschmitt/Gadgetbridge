package nodomain.freeyourgadget.gadgetbridge.activities.charts;

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
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import kotlin.jvm.functions.Function1;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartLegendView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.DaySelections;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.GbChartView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.respiratoryrate.RespiratoryRateChartData;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;

public class RespiratoryRatePeriodFragment extends RespiratoryRateFragment<RespiratoryRatePeriodFragment.RespiratoryRateData> {
    protected static final Logger LOG = LoggerFactory.getLogger(RespiratoryRatePeriodFragment.class);

    private TextView mDateView;
    private LinearLayout statsContainer;
    private GbChartView respiratoryRateChart;
    private ChartLegendView respiratoryRateLegend;

    @Override
    protected boolean isSingleDay() {
        return false;
    }

    public static RespiratoryRatePeriodFragment newInstance (int totalDays) {
        RespiratoryRatePeriodFragment fragmentFirst = new RespiratoryRatePeriodFragment();
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
        View rootView = inflater.inflate(R.layout.fragment_respiratory_rate_period, container, false);

        rootView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            getChartsHost().enableSwipeRefresh(scrollY == 0);
        });

        mDateView = rootView.findViewById(R.id.rr_date_view);
        statsContainer = rootView.findViewById(R.id.respiratory_rate_period_stats_container);
        respiratoryRateChart = rootView.findViewById(R.id.respiratory_rate_line_chart);
        respiratoryRateChart.setZoomable(TOTAL_DAYS > 7);
        respiratoryRateChart.dismissSelectionOnTapOutside(rootView);
        respiratoryRateLegend = rootView.findViewById(R.id.respiratory_rate_chart_legend);
        refresh();

        return rootView;
    }

    @Override
    public String getTitle() {
        return getString(R.string.respiratoryrate);
    }

    @Override
    protected RespiratoryRateData refreshInBackground(ChartsHost chartsHost, DBHandler db, GBDevice device) {
        Calendar day = Calendar.getInstance();
        Date to = new Date((long) this.getTSEnd() * 1000);
        final String formattedDate = DateTimeUtils.formatDaysUntil(TOTAL_DAYS, getTSEnd());

        day.setTime(to);
        List<RespiratoryRateDay> respiratoryRateDaysData = getMyRespiratoryRateDaysData(db, day, device);
        return new RespiratoryRateData(respiratoryRateDaysData, formattedDate);
    }

    @Override
    protected void updateChartsnUIThread(RespiratoryRateData respiratoryRateData) {
        mDateView.setText(respiratoryRateData.formattedDate);
        final String emptyValue = requireContext().getString(R.string.stats_empty_value);
        final List<StatTileData> stats = new ArrayList<>();
        stats.add(new StatTileData(
                respiratoryRateData.awakeRateAvg > 0 ? String.valueOf(respiratoryRateData.awakeRateAvg) : emptyValue,
                getString(R.string.sleep_colored_stats_awake_avg)
        ));
        stats.add(new StatTileData(
                respiratoryRateData.sleepRateAvg > 0 ? String.valueOf(respiratoryRateData.sleepRateAvg) : emptyValue,
                getString(R.string.sleep_avg)
        ));
        statsContainer.removeAllViews();
        StatTileGridUtilKt.addStatTileGrid(statsContainer, requireContext(), stats, 0);

        final int n = respiratoryRateData.days.size();
        final long[] epochDays = new long[n];
        final int[] awake = new int[n];
        final int[] sleep = new int[n];
        for (int i = 0; i < n; i++) {
            final RespiratoryRateDay day = respiratoryRateData.days.get(i);
            epochDays[i] = LocalDate.of(day.day.get(Calendar.YEAR), day.day.get(Calendar.MONTH) + 1, day.day.get(Calendar.DAY_OF_MONTH)).toEpochDay();
            awake[i] = Math.max(day.awakeRateAvg, 0);
            sleep[i] = Math.max(day.sleepRateAvg, 0);
        }
        final String awakeLabel = getString(R.string.sleep_colored_stats_awake_avg);
        final String sleepLabel = getString(R.string.sleep_avg);
        final int awakeColor = getResources().getColor(R.color.chart_awake_sleep_light);
        final int sleepColor = getResources().getColor(R.color.chart_light_sleep_light);
        final ChartSpec spec = RespiratoryRateChartData.periodSpec(epochDays, awake, sleep, awakeLabel, awakeColor, sleepLabel, sleepColor);
        final List<Function1<Integer, String>> rowTexts = new ArrayList<>();
        rowTexts.add(i -> awake[i] > 0 ? String.valueOf(awake[i]) : emptyValue);
        rowTexts.add(i -> sleep[i] > 0 ? String.valueOf(sleep[i]) : emptyValue);
        respiratoryRateChart.setSelectionContent(x -> DaySelections.of(
                epochDays, x, Arrays.asList(awakeLabel, sleepLabel), Arrays.asList(awakeColor, sleepColor), rowTexts, emptyValue
        ));
        respiratoryRateChart.setSpec(spec);
        respiratoryRateLegend.setSeries(spec.getSeries());
    }

    @Override
    protected void renderCharts() {
        respiratoryRateChart.invalidate();
    }

    @Override
    protected void setupLegend(Chart<?> chart) {}

    protected static class RespiratoryRateData extends ChartsData {
        List<RespiratoryRateDay> days;
        int awakeRateAvg;
        int sleepRateAvg;
        final String formattedDate;

        protected RespiratoryRateData(List<RespiratoryRateDay> days, String formattedDate) {
            this.days = days;
            this.formattedDate = formattedDate;
            int awakeTotal = 0;
            int sleepTotal = 0;
            int awakeCounter = 0;
            int sleepCounter = 0;
            for(RespiratoryRateDay day: days) {
                if (day.awakeRateAvg > 0) {
                    awakeTotal += day.awakeRateAvg;
                    awakeCounter++;
                }
                if (day.sleepRateAvg > 0) {
                    sleepTotal += day.sleepRateAvg;
                    sleepCounter++;
                }
            }
            if (awakeTotal > 0) {
                this.awakeRateAvg = Math.round((float) awakeTotal / awakeCounter);
            }
            if (sleepTotal > 0) {
                this.sleepRateAvg = Math.round((float) sleepTotal / sleepCounter);
            }
        }
    }
}
