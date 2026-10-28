package nodomain.freeyourgadget.gadgetbridge.activities.charts;

import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_BPM;

import android.graphics.Color;
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

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import kotlin.jvm.functions.Function1;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.HeartRateUtils;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.heartrate.HeartRateChartData;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartLegendView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartSelection;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.DaySelections;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.GbChartView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.SectionHeaderView;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutValueFormatter;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.AbstractActivitySample;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.HeartRateSample;
import nodomain.freeyourgadget.gadgetbridge.util.Accumulator;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;
import nodomain.freeyourgadget.gadgetbridge.util.TimeWeightedAverageAccumulator;

public class HeartRatePeriodFragment extends AbstractChartFragment<HeartRatePeriodFragment.HeartRatePeriodData> {

    protected static final Logger LOG = LoggerFactory.getLogger(HeartRatePeriodFragment.class);

    static int DATA_INVALID = -1;

    protected int HEARTRATE_COLOR;
    protected int HEARTRATE_MIN_COLOR;
    protected int HEARTRATE_AVERAGE_COLOR;
    protected int HEARTRATE_RESTING_COLOR;
    protected int HEARTRATE_MAX_COLOR;

    private TextView mDateView;
    private LinearLayout hrStatsContainer;
    private GbChartView hrLineChart;
    private ChartLegendView hrLegend;
    private int TOTAL_DAYS;

    @Override
    protected boolean isSingleDay() {
        return false;
    }

    public static HeartRatePeriodFragment newInstance(int totalDays) {
        HeartRatePeriodFragment fragmentFirst = new HeartRatePeriodFragment();
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
        View rootView = inflater.inflate(R.layout.fragment_heart_rate, container, false);

        rootView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> getChartsHost().enableSwipeRefresh(scrollY == 0));

        mDateView = rootView.findViewById(R.id.hr_date_view);
        hrLineChart = rootView.findViewById(R.id.heart_rate_line_chart);
        hrLineChart.setZoomable(true);
        hrLineChart.dismissSelectionOnTapOutside(rootView);
        hrLegend = rootView.findViewById(R.id.heart_rate_chart_legend);
        hrStatsContainer = rootView.findViewById(R.id.hr_stats_container);
        final SectionHeaderView chartHeader = rootView.findViewById(R.id.hr_chart_header);
        chartHeader.setTitle(getString(TOTAL_DAYS == 1 ? R.string.day_timeline : R.string.heart_rate_daily));

        refresh();

        return rootView;
    }

    public boolean supportsHeartRateRestingMeasurement() {
        final GBDevice device = getChartsHost().getDevice();
        return device.getDeviceCoordinator().supportsHeartRateRestingMeasurement(device);
    }

    protected List<? extends AbstractActivitySample> getActivitySamples(DBHandler db, GBDevice device, int tsFrom, int tsTo) {
        SampleProvider<? extends ActivitySample> provider = device.getDeviceCoordinator().getSampleProvider(device, db.getDaoSession());
        return provider.getAllActivitySamplesHighRes(tsFrom, tsTo);
    }

    @Override
    public String getTitle() {
        return getString(R.string.heart_rate);
    }

    @Override
    protected void init() {
        HEARTRATE_COLOR = ContextCompat.getColor(requireContext(), GBApplication.getPrefs().getChartsHeartRateColor().getColorRes());
        HEARTRATE_MIN_COLOR = ContextCompat.getColor(requireContext(), R.color.chart_heartrate_minimum);
        HEARTRATE_AVERAGE_COLOR = ContextCompat.getColor(requireContext(), R.color.chart_heartrate);
        HEARTRATE_MAX_COLOR = ContextCompat.getColor(requireContext(), R.color.chart_heartrate_maximum);
        HEARTRATE_RESTING_COLOR = ContextCompat.getColor(requireContext(), R.color.chart_heartrate_resting);
    }

    private HeartRateData fetchHeartRateDataForDay(DBHandler db, GBDevice device, int startTs) {
        int endTs = toTimestamp(DateTimeUtils.shiftByDays(new Date(startTs * 1000L), 1)) - 1;
        List<? extends ActivitySample> samples = getActivitySamples(db, device, startTs, endTs);
        final HeartRateUtils heartRateUtilsInstance = HeartRateUtils.getInstance();

        int restingHeartRate = DATA_INVALID;
        if (supportsHeartRateRestingMeasurement()) {
            restingHeartRate = device.getDeviceCoordinator()
                    .getHeartRateRestingSampleProvider(device, db.getDaoSession())
                    .getAllSamples(startTs * 1000L, endTs * 1000L)
                    .stream()
                    .max(Comparator.comparingLong(HeartRateSample::getTimestamp))
                    .map(HeartRateSample::getHeartRate)
                    .orElse(DATA_INVALID);
        }

        final int maxHRGapMinutes = device.getDeviceCoordinator().getMaxHeartRateMeasurementsGapMinutes(device);
        final TimeWeightedAverageAccumulator accumulator = new TimeWeightedAverageAccumulator(60 * maxHRGapMinutes, 60);
        for (int i = 0; i < samples.size(); i++) {
            final ActivitySample sample = samples.get(i);
            if (heartRateUtilsInstance.isValidHeartRateValue(sample.getHeartRate())) {
                accumulator.add(sample.getTimestamp(), sample.getHeartRate());
            }
        }

        final int average = accumulator.getCount() > 0 ? (int) Math.round(accumulator.getAverage()) : DATA_INVALID;
        final int minimum = accumulator.getCount() > 0 ? (int) Math.round(accumulator.getMin()) : DATA_INVALID;
        final int maximum = accumulator.getCount() > 0 ? (int) Math.round(accumulator.getMax()) : DATA_INVALID;

        return new HeartRateData(samples, restingHeartRate, average, minimum, maximum);
    }

    @Override
    protected HeartRatePeriodData refreshInBackground(ChartsHost chartsHost, DBHandler db, GBDevice device) {
        Pair<Integer, Integer> startAndEndTs = getStartAndEndTS();
        final int startTs = startAndEndTs.getKey();

        List<HeartRateData> result = new ArrayList<>();
        for (int i = 0; i < TOTAL_DAYS; i++) {
            Calendar day = Calendar.getInstance();
            day.setTimeInMillis(startTs * 1000L);
            day.add(Calendar.DATE, i);
            HeartRateData dayData = fetchHeartRateDataForDay(db, device, toTimestamp(day.getTime()));
            result.add(dayData);
        }
        return new HeartRatePeriodData(result);
    }

    @Override
    protected void renderCharts() {
        hrLineChart.invalidate();
    }

    @Override
    protected void setupLegend(Chart<?> chart) {
    }

    private Pair<Integer, Integer> getStartAndEndTS() {
        Date lastDay = DateTimeUtils.dayStart(getEndDate());
        int startTs = toTimestamp(DateTimeUtils.shiftByDays(lastDay, -(TOTAL_DAYS - 1)));
        int endTs = toTimestamp(DateTimeUtils.shiftByDays(lastDay, 1)) - 1;
        return Pair.of(startTs, endTs);
    }

    private void setStatistics(int average, int minimum, int maximum, int resting) {
        hrStatsContainer.removeAllViews();

        final WorkoutValueFormatter workoutValueFormatter = new WorkoutValueFormatter();
        final List<StatTileData> stats = new ArrayList<>();

        stats.add(new StatTileData(
                minimum > 0 ? workoutValueFormatter.formatValue(minimum, UNIT_BPM) : getString(R.string.stats_empty_value),
                getString(R.string.hr_minimum)
        ));

        stats.add(new StatTileData(
                maximum > 0 ? workoutValueFormatter.formatValue(maximum, UNIT_BPM) : getString(R.string.stats_empty_value),
                getString(R.string.hr_maximum)
        ));

        stats.add(new StatTileData(
                average > 0 ? workoutValueFormatter.formatValue(average, UNIT_BPM) : getString(R.string.stats_empty_value),
                getString(R.string.hr_average)
        ));

        if (supportsHeartRateRestingMeasurement()) {
            stats.add(new StatTileData(
                    resting > 0 ? workoutValueFormatter.formatValue(resting, UNIT_BPM) : getString(R.string.stats_empty_value),
                    getString(R.string.hr_resting)
            ));
        }

        StatTileGridUtilKt.addStatTileGrid(hrStatsContainer, requireContext(), stats, 0);
    }

    @Override
    protected void updateChartsnUIThread(HeartRatePeriodData data) {
        Pair<Integer, Integer> startAndEndTs = getStartAndEndTS();
        final int startTs = startAndEndTs.getKey();
        final int endTs = startAndEndTs.getValue();

        //Date date = new Date((long) endTs * 1000);
        mDateView.setText(DateTimeUtils.formatDaysUntil(TOTAL_DAYS, getTSEnd()));
        if (TOTAL_DAYS == 1) {
            setOneDayData(data.samples.get(0), startTs, endTs);
        } else {
            setMultipleDaysData(data, startTs);
        }
    }

    private void setOneDayData(HeartRateData data, int startTs, int endTs) {
        Date date = new Date((long) endTs * 1000);
        String formattedDate = new SimpleDateFormat("E, MMM dd").format(date);
        mDateView.setText(formattedDate);

        final HeartRateUtils heartRateUtilsInstance = HeartRateUtils.getInstance();
        final GBDevice device = getChartsHost().getDevice();
        final int maxHRGapMinutes = device.getDeviceCoordinator().getMaxHeartRateMeasurementsGapMinutes(device);
        final List<? extends ActivitySample> samples = data.samples;
        final long[] seconds = new long[samples.size()];
        final int[] bpm = new int[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            final ActivitySample sample = samples.get(i);
            seconds[i] = sample.getTimestamp();
            bpm[i] = heartRateUtilsInstance.isValidHeartRateValue(sample.getHeartRate()) ? sample.getHeartRate() : 0;
        }

        setStatistics(data.average, data.minimum, data.maximum, data.restingHeartRate);

        final boolean showAverage = GBApplication.getPrefs().getBoolean("charts_show_average", true);
        final ChartSpec spec = HeartRateChartData.daySpec(
                startTs, seconds, bpm, 60 * maxHRGapMinutes, data.average, showAverage, getTitle(), HEARTRATE_COLOR, Color.RED
        );
        final WorkoutValueFormatter formatter = new WorkoutValueFormatter();
        hrLineChart.setSelectionContent(x -> {
            final long time = Math.round(x);
            final String title = DateFormat.getTimeFormat(requireContext()).format(new Date(time * 1000L));
            final List<ChartSelection.Row> rows = new ArrayList<>();
            String description = title + ".";
            for (int i = 0; i < seconds.length; i++) {
                if (seconds[i] == time && bpm[i] > 0) {
                    final String value = formatter.formatValue(bpm[i], UNIT_BPM);
                    rows.add(new ChartSelection.Row(HEARTRATE_COLOR, value));
                    description = title + ". " + getTitle() + " " + value + ".";
                    break;
                }
            }
            return new ChartSelection(title, rows, description);
        });
        hrLineChart.setSpec(spec);

        final List<ChartSeries> legendSeries = new ArrayList<>(spec.getSeries());
        if (!spec.getSeries().isEmpty() && !spec.getLimitLines().isEmpty()) {
            legendSeries.add(ChartLegendView.lineItem(getString(R.string.hr_average), Color.RED));
        }
        hrLegend.setSeries(legendSeries);
    }

    private void setMultipleDaysData(HeartRatePeriodData data, int startTs) {
        final List<HeartRateData> days = data.samples;
        final int n = days.size();
        final long firstDay = Instant.ofEpochSecond(startTs).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay();
        final long[] epochDays = new long[n];
        final int[] minimum = new int[n];
        final int[] resting = new int[n];
        final int[] average = new int[n];
        final int[] maximum = new int[n];
        final Accumulator avgAccumulator = new Accumulator();
        final Accumulator minAccumulator = new Accumulator();
        final Accumulator maxAccumulator = new Accumulator();
        final Accumulator restingAccumulator = new Accumulator();
        for (int i = 0; i < n; i++) {
            final HeartRateData hrData = days.get(i);
            epochDays[i] = firstDay + i;
            if (hrData.average > 0) {
                avgAccumulator.add(hrData.average);
                average[i] = hrData.average;
            }
            if (hrData.minimum > 0) {
                minAccumulator.add(hrData.minimum);
                minimum[i] = hrData.minimum;
            }
            if (hrData.maximum > 0) {
                maxAccumulator.add(hrData.maximum);
                maximum[i] = hrData.maximum;
            }
            if (hrData.restingHeartRate > 0) {
                restingAccumulator.add(hrData.restingHeartRate);
                resting[i] = hrData.restingHeartRate;
            }
        }

        final int averageTotal = avgAccumulator.getCount() > 0 ? (int) Math.round(avgAccumulator.getAverage()) : DATA_INVALID;
        final int minimumTotal = minAccumulator.getCount() > 0 ? (int) Math.round(minAccumulator.getMin()) : DATA_INVALID;
        final int maximumTotal = maxAccumulator.getCount() > 0 ? (int) Math.round(maxAccumulator.getMax()) : DATA_INVALID;
        final int restingAvg = restingAccumulator.getCount() > 0 ? (int) Math.round(restingAccumulator.getAverage()) : DATA_INVALID;
        setStatistics(averageTotal, minimumTotal, maximumTotal, restingAvg);

        final String[] labels = {
                getString(R.string.hr_minimum),
                getString(R.string.hr_resting),
                getString(R.string.hr_average),
                getString(R.string.hr_maximum),
        };
        final int[] colors = {HEARTRATE_MIN_COLOR, HEARTRATE_RESTING_COLOR, HEARTRATE_AVERAGE_COLOR, HEARTRATE_MAX_COLOR};
        final ChartSpec spec = HeartRateChartData.periodSpec(
                epochDays, minimum, resting, average, maximum,
                supportsHeartRateRestingMeasurement(), GBApplication.getPrefs().getBoolean("charts_show_average", true),
                labels, colors
        );

        final WorkoutValueFormatter formatter = new WorkoutValueFormatter();
        final List<String> rowLabels = new ArrayList<>();
        final List<Integer> rowColors = new ArrayList<>();
        final List<Function1<Integer, String>> rowTexts = new ArrayList<>();
        for (final ChartSeries series : spec.getSeries()) {
            final int[] values;
            final int color;
            switch (series.getKey()) {
                case "min":
                    values = minimum;
                    color = colors[0];
                    break;
                case "resting":
                    values = resting;
                    color = colors[1];
                    break;
                case "avg":
                    values = average;
                    color = colors[2];
                    break;
                default:
                    values = maximum;
                    color = colors[3];
                    break;
            }
            rowLabels.add(series.getLabel());
            rowColors.add(color);
            rowTexts.add(i -> values[i] > 0 ? formatter.formatValue(values[i], UNIT_BPM) : getString(R.string.stats_empty_value));
        }
        hrLineChart.setSelectionContent(x -> DaySelections.of(
                epochDays, x, rowLabels, rowColors, rowTexts, getString(R.string.stats_empty_value)
        ));
        hrLineChart.setSpec(spec);
        hrLegend.setSeries(spec.getSeries());
    }

    protected static class HeartRatePeriodData extends ChartsData {
        public List<HeartRateData> samples;

        protected HeartRatePeriodData(List<HeartRateData> samples) {
            this.samples = samples;
        }
    }

    protected static class HeartRateData extends ChartsData {
        public List<? extends ActivitySample> samples;
        public int restingHeartRate;
        public int average;
        public int minimum;
        public int maximum;

        protected HeartRateData(List<? extends ActivitySample> samples, int restingHeartRate, int average, int minimum, int maximum) {
            this.samples = samples;
            this.restingHeartRate = restingHeartRate;
            this.average = average;
            this.minimum = minimum;
            this.maximum = maximum;
        }
    }

    @Nullable
    @Override
    public ChartDataRange getAvailableDataRange(final GBDevice device, final DBHandler db) {
        return ChartDataRange.union(
                ChartDataRange.ofActivitySamples(device.getDeviceCoordinator().getSampleProvider(device, db.getDaoSession())),
                ChartDataRange.ofSamples(device.getDeviceCoordinator().getHeartRateRestingSampleProvider(device, db.getDaoSession()))
        );
    }
}
