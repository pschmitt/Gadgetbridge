/*  Copyright (C) 2017-2024 a0z, José Rebelo

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

import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.Chart;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import kotlin.jvm.functions.Function1;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.hrv.HrvChartData;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartLegendView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartSelection;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.DaySelections;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.GbChartView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec;
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.HrvWidget;
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.GaugeDrawer;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.SectionHeaderView;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.HrvSummarySample;
import nodomain.freeyourgadget.gadgetbridge.model.HrvValueSample;
import nodomain.freeyourgadget.gadgetbridge.model.SleepSession;
import nodomain.freeyourgadget.gadgetbridge.util.Accumulator;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;


public class HRVStatusFragment extends AbstractChartFragment<HRVStatusFragment.HRVStatusWeeklyData> {
    protected static final Logger LOG = LoggerFactory.getLogger(HRVStatusFragment.class);
    private static final String ARG_VIEW_MODE = "view_mode";
    private static final String ARG_TOTAL_DAYS = "total_days";
    private static final int DEFAULT_TOTAL_DAYS = 7;

    private enum ViewMode {
        LAST_NIGHT,
        PERIOD
    }

    private enum PeriodDataType {
        DAILY_AVERAGE,
        LAST_NIGHT_AVERAGE,
        WEEKLY_AVERAGE,
        DAILY_RANGE
    }

    private static final PeriodDataType[] PERIOD_DATA_TYPE_ORDER = {
            PeriodDataType.DAILY_AVERAGE,
            PeriodDataType.LAST_NIGHT_AVERAGE,
            PeriodDataType.WEEKLY_AVERAGE,
            PeriodDataType.DAILY_RANGE
    };
    private static final PeriodDataType DEFAULT_PERIOD_DATA_TYPE = PeriodDataType.DAILY_AVERAGE;

    protected GaugeDrawer gaugeDrawer;
    private ImageView mHRVStatusGauge;
    private GbChartView mWeeklyHRVStatusChart;
    private ChartLegendView mHRVChartLegend;
    private ChipGroup mHRVStatusDataTypeGroup;
    private LinearLayout mHRVStatusStatsContainer;
    private TextView mDateView;
    private TextView mHRVGaugeValue;
    private TextView mHRVGaugeStatus;
    private LinearLayout mHRVStatusPeriodStatsContainer;
    protected int TEXT_COLOR;
    protected int HRV_AVERAGE_COLOR;
    protected int HRV_RANGE_COLOR;
    protected int HRV_LAST_NIGHT_COLOR;
    protected int HRV_BASELINE_FILL_COLOR;
    protected int HRV_WEEKLY_AVERAGE_COLOR;

    private ViewMode viewMode = ViewMode.PERIOD;
    private int totalDays = DEFAULT_TOTAL_DAYS;
    private boolean showDailyAverage = true;
    private boolean showLastNightAverage = true;
    private boolean showWeeklyAverage = true;
    private boolean showDailyRange = false;

    public static HRVStatusFragment newLastNightInstance() {
        final HRVStatusFragment fragment = new HRVStatusFragment();
        final Bundle args = new Bundle();
        args.putString(ARG_VIEW_MODE, ViewMode.LAST_NIGHT.name());
        fragment.setArguments(args);
        return fragment;
    }

    public static HRVStatusFragment newPeriodInstance(final int totalDays) {
        final HRVStatusFragment fragment = new HRVStatusFragment();
        final Bundle args = new Bundle();
        args.putString(ARG_VIEW_MODE, ViewMode.PERIOD.name());
        args.putInt(ARG_TOTAL_DAYS, totalDays);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        if (getArguments() != null) {
            final String viewModeArg = getArguments().getString(ARG_VIEW_MODE, ViewMode.PERIOD.name());
            viewMode = ViewMode.valueOf(viewModeArg);
            totalDays = getArguments().getInt(ARG_TOTAL_DAYS, DEFAULT_TOTAL_DAYS);
        }

        super.onCreate(savedInstanceState);
    }

    @Override
    protected boolean isSingleDay() {
        return viewMode == ViewMode.LAST_NIGHT;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_hrv_status, container, false);

        rootView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            getChartsHost().enableSwipeRefresh(scrollY == 0);
        });

        mWeeklyHRVStatusChart = rootView.findViewById(R.id.hrv_weekly_line_chart);
        mWeeklyHRVStatusChart.dismissSelectionOnTapOutside(rootView);
        mHRVChartLegend = rootView.findViewById(R.id.hrv_chart_legend);
        mHRVStatusStatsContainer = rootView.findViewById(R.id.hrv_status_stats_container);
        mDateView = rootView.findViewById(R.id.hrv_status_date_view);
        mHRVStatusGauge = rootView.findViewById(R.id.hrv_status_gauge_bar);
        mHRVGaugeValue = rootView.findViewById(R.id.hrv_gauge_value);
        mHRVGaugeStatus = rootView.findViewById(R.id.hrv_gauge_status);
        mHRVStatusDataTypeGroup = rootView.findViewById(R.id.hrv_status_chart_data_type_group);
        mHRVStatusPeriodStatsContainer = rootView.findViewById(R.id.hrv_status_period_stats_container);

        if (viewMode != ViewMode.LAST_NIGHT) {
            rootView.findViewById(R.id.hrv_gauge).setVisibility(View.GONE);
            mHRVStatusStatsContainer.setVisibility(View.GONE);
            mHRVStatusPeriodStatsContainer.setVisibility(View.VISIBLE);
            final SectionHeaderView chartHeader = rootView.findViewById(R.id.hrv_chart_header);
            chartHeader.setTitle(getString(R.string.hrv_status_trend));
            final ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) chartHeader.getLayoutParams();
            params.topMargin = getResources().getDimensionPixelSize(R.dimen.chart_section_spacing);
            chartHeader.setLayoutParams(params);
        }

        gaugeDrawer = new GaugeDrawer();
        setupPeriodDataTypeChips(inflater);
        refresh();

        return rootView;
    }

    private void setupPeriodDataTypeChips(final LayoutInflater inflater) {
        if (mHRVStatusDataTypeGroup == null) {
            return;
        }

        if (viewMode == ViewMode.LAST_NIGHT) {
            mHRVStatusDataTypeGroup.setVisibility(View.GONE);
            return;
        }

        mHRVStatusDataTypeGroup.setVisibility(View.VISIBLE);
        mHRVStatusDataTypeGroup.removeAllViews();
        mHRVStatusDataTypeGroup.setSingleSelection(false);
        mHRVStatusDataTypeGroup.setSelectionRequired(true);
        for (final PeriodDataType dataType : PERIOD_DATA_TYPE_ORDER) {
            addPeriodDataTypeChip(inflater, dataType);
        }
        mHRVStatusDataTypeGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                Toast.makeText(requireContext(), R.string.charts_at_least_one_item, Toast.LENGTH_SHORT).show();
                final Chip defaultChip = findPeriodDataTypeChip(DEFAULT_PERIOD_DATA_TYPE);
                if (defaultChip != null) {
                    defaultChip.setChecked(true);
                }
                return;
            }

            final boolean selectedDailyAverage = isPeriodDataTypeSelected(PeriodDataType.DAILY_AVERAGE);
            final boolean selectedLastNightAverage = isPeriodDataTypeSelected(PeriodDataType.LAST_NIGHT_AVERAGE);
            final boolean selectedWeeklyAverage = isPeriodDataTypeSelected(PeriodDataType.WEEKLY_AVERAGE);
            final boolean selectedDailyRange = isPeriodDataTypeSelected(PeriodDataType.DAILY_RANGE);
            if (showDailyAverage == selectedDailyAverage
                    && showLastNightAverage == selectedLastNightAverage
                    && showWeeklyAverage == selectedWeeklyAverage
                    && showDailyRange == selectedDailyRange) {
                return;
            }

            showDailyAverage = selectedDailyAverage;
            showLastNightAverage = selectedLastNightAverage;
            showWeeklyAverage = selectedWeeklyAverage;
            showDailyRange = selectedDailyRange;
            refresh();
        });
    }

    private void addPeriodDataTypeChip(final LayoutInflater inflater, final PeriodDataType dataType) {
        final Chip chip = (Chip) inflater.inflate(R.layout.layout_chart_chip, mHRVStatusDataTypeGroup, false);
        chip.setId(View.generateViewId());
        chip.setText(getString(getPeriodDataTypeLabel(dataType)));
        chip.setTag(dataType);
        chip.setVisibility(dataType == PeriodDataType.WEEKLY_AVERAGE ? View.GONE : View.VISIBLE);
        mHRVStatusDataTypeGroup.addView(chip);
        chip.setChecked(isPeriodDataTypeVisible(dataType));
    }

    private boolean isPeriodDataTypeSelected(final PeriodDataType dataType) {
        final Chip chip = findPeriodDataTypeChip(dataType);
        return chip != null && chip.isChecked();
    }

    private boolean isPeriodDataTypeVisible(final PeriodDataType dataType) {
        switch (dataType) {
            case DAILY_AVERAGE:
                return showDailyAverage;
            case LAST_NIGHT_AVERAGE:
                return showLastNightAverage;
            case WEEKLY_AVERAGE:
                return showWeeklyAverage;
            case DAILY_RANGE:
                return showDailyRange;
            default:
                throw new IllegalArgumentException("Unknown period data type: " + dataType);
        }
    }

    private Chip findPeriodDataTypeChip(final PeriodDataType dataType) {
        for (int i = 0; i < mHRVStatusDataTypeGroup.getChildCount(); i++) {
            final View child = mHRVStatusDataTypeGroup.getChildAt(i);
            if (child instanceof Chip && child.getTag() == dataType) {
                return (Chip) child;
            }
        }
        return null;
    }

    private int getPeriodDataTypeLabel(final PeriodDataType dataType) {
        switch (dataType) {
            case DAILY_AVERAGE:
                return R.string.hrv_status_day_avg_legend;
            case LAST_NIGHT_AVERAGE:
                return R.string.hrv_status_nightly_avg_legend;
            case WEEKLY_AVERAGE:
                return R.string.hrv_status_seven_days_avg_legend;
            case DAILY_RANGE:
                return R.string.hrv_status_daily_range_legend;
            default:
                throw new IllegalArgumentException("Unknown period data type: " + dataType);
        }
    }

    @Override
    public String getTitle() {
        return getString(R.string.pref_header_hrv_status);
    }

    @Override
    protected void init() {
        TEXT_COLOR = GBApplication.getTextColor(requireContext());
        HRV_AVERAGE_COLOR = getResources().getColor(R.color.hrv_status_char_line_color);
        HRV_RANGE_COLOR = getResources().getColor(R.color.hrv_status_range_color);
        HRV_LAST_NIGHT_COLOR = getResources().getColor(R.color.hrv_status_last_night_color);
        HRV_BASELINE_FILL_COLOR = getResources().getColor(R.color.hrv_status_baseline_fill_color);
        HRV_WEEKLY_AVERAGE_COLOR = getResources().getColor(R.color.hrv_status_weekly_avg_color);
    }

    @Override
    protected HRVStatusWeeklyData refreshInBackground(ChartsHost chartsHost, DBHandler db, GBDevice device) {
        Calendar day = Calendar.getInstance();
        day.setTime(getEndDate());

        final List<HRVStatusDayData> periodData = getPeriodData(db, day, device);
        final LastNightData lastNightData = viewMode == ViewMode.LAST_NIGHT ?
                getLastNightData(db, device, day) :
                new LastNightData(new ArrayList<>(), getLastNightSearchStart(day), getLastNightSearchEnd(day));
        return new HRVStatusWeeklyData(periodData, lastNightData.samples, lastNightData.start, lastNightData.end);
    }

    @Override
    protected void renderCharts() {
        mWeeklyHRVStatusChart.invalidate();
    }

    @Override
    protected void updateChartsnUIThread(HRVStatusWeeklyData weeklyData) {
        if (viewMode == ViewMode.LAST_NIGHT) {
            updateLastNightChart(weeklyData);
        } else {
            updatePeriodChart(weeklyData);
        }

        HRVStatusDayData today = weeklyData.getCurrentDay();

        final String statusLabel;
        final int statusColor;
        switch (today.status) {
            case POOR:
                statusLabel = getString(R.string.hrv_status_poor);
                statusColor = getResources().getColor(R.color.hrv_status_poor);
                break;
            case LOW:
                statusLabel = getString(R.string.hrv_status_low);
                statusColor = getResources().getColor(R.color.hrv_status_low);
                break;
            case UNBALANCED:
                statusLabel = getString(R.string.hrv_status_unbalanced);
                statusColor = getResources().getColor(R.color.hrv_status_unbalanced);
                break;
            case BALANCED:
                statusLabel = getString(R.string.hrv_status_balanced);
                statusColor = getResources().getColor(R.color.hrv_status_balanced);
                break;
            case NONE:
            default:
                statusLabel = "";
                statusColor = TEXT_COLOR;
                break;
        }
        mHRVGaugeStatus.setText(statusLabel);
        mHRVGaugeStatus.setTextColor(statusColor);

        final boolean lastNight = viewMode == ViewMode.LAST_NIGHT;
        if (!lastNight) {
            mHRVStatusPeriodStatsContainer.removeAllViews();
            final List<StatTileData> periodStats = new ArrayList<>();
            periodStats.add(new StatTileData(formatHrvStatusValue(weeklyData.getPeriodNightlyAverage()), getString(R.string.hrv_status_nightly_avg)));
            periodStats.add(new StatTileData(formatHrvStatusValue(weeklyData.getHighestNightlyAverage()), getString(R.string.hrv_status_highest_nightly_avg)));
            StatTileGridUtilKt.addStatTileGrid(mHRVStatusPeriodStatsContainer, requireContext(), periodStats, 0);
            return;
        }

        mHRVStatusStatsContainer.removeAllViews();
        final List<StatTileData> stats = new ArrayList<>();
        // Show weekly average even if we don't have full 7 days - it's computed from available data
        stats.add(new StatTileData(
                today.weeklyAvg > 0 ? getString(R.string.hrv_status_unit, today.weeklyAvg) :
                        (today.dayAvg > 0 ? getString(R.string.hrv_status_unit, today.dayAvg) : getString(R.string.stats_empty_value)),
                getString(R.string.hrv_status_seven_days_avg)
        ));
        final boolean hasStatus = today.status != HrvSummarySample.Status.NONE;
        stats.add(new StatTileData(
                hasStatus ? statusLabel : "-",
                getString(R.string.hrv_status_seven_days_avg_status),
                hasStatus ? statusColor : null
        ));
        stats.add(new StatTileData(formatHrvStatusValue(today.dayAvg), getString(R.string.hrv_status_day_avg)));
        stats.add(new StatTileData(
                today.baseLineBalancedLower > 0 && today.baseLineBalancedUpper > 0
                        ? getString(R.string.hrv_status_baseline, today.baseLineBalancedLower, today.baseLineBalancedUpper) : "-",
                getString(R.string.hrv_status_baseline_label)
        ));
        stats.add(new StatTileData(formatHrvStatusValue(today.lastNight), getString(R.string.hrv_status_last_night)));
        stats.add(new StatTileData(formatHrvStatusValue(today.lastNight5MinHigh), getString(R.string.hrv_status_last_night_highest_5)));
        StatTileGridUtilKt.addStatTileGrid(mHRVStatusStatsContainer, requireContext(), stats, 0);

        final float value = HrvWidget.calculateGaugeValue(today.weeklyAvg, today.baseLineLowUpper, today.baseLineBalancedLower, today.baseLineBalancedUpper);
        final String valueText = value > 0 ? getString(R.string.hrv_status_unit, today.weeklyAvg) : getString(R.string.stats_empty_value);
        mHRVGaugeValue.setText(valueText);
        gaugeDrawer.drawSegmentedGauge(mHRVStatusGauge, HrvWidget.colors(requireContext()), HrvWidget.SEGMENTS, value, false, true);
    }

    private String formatHrvStatusValue(final int value) {
        return value > 0 ? getString(R.string.hrv_status_unit, value) : getString(R.string.stats_empty_value);
    }

    private void updatePeriodChart(final HRVStatusWeeklyData weeklyData) {
        mDateView.setText(DateTimeUtils.formatDaysUntil(totalDays, getTSEnd()));
        final List<HRVStatusDayData> days = weeklyData.getDaysData();
        final int n = days.size();
        final long[] epochDays = new long[n];
        final int[] dayAvg = new int[n];
        final int[] lastNight = new int[n];
        final int[] weeklyAvg = new int[n];
        final int[] dayMin = new int[n];
        final int[] dayMax = new int[n];
        final int[] baselineLow = new int[n];
        final int[] baselineHigh = new int[n];
        for (int i = 0; i < n; i++) {
            final HRVStatusDayData day = days.get(i);
            epochDays[i] = LocalDate.of(day.day.get(Calendar.YEAR), day.day.get(Calendar.MONTH) + 1, day.day.get(Calendar.DAY_OF_MONTH)).toEpochDay();
            dayAvg[i] = day.dayAvg;
            lastNight[i] = day.lastNight;
            weeklyAvg[i] = day.weeklyAvg;
            dayMin[i] = day.dayMin;
            dayMax[i] = day.dayMax;
            baselineLow[i] = day.baseLineBalancedLower;
            baselineHigh[i] = day.baseLineBalancedUpper;
        }

        final String[] labels = {
                getString(R.string.hrv_status_day_avg_legend),
                getString(R.string.hrv_status_nightly_avg_legend),
                getString(R.string.hrv_status_daily_range_legend),
                getString(R.string.hrv_status_baseline_label),
                getString(R.string.hrv_status_seven_days_avg_legend),
        };
        final int[] colors = {HRV_AVERAGE_COLOR, HRV_LAST_NIGHT_COLOR, HRV_RANGE_COLOR, HRV_BASELINE_FILL_COLOR, HRV_WEEKLY_AVERAGE_COLOR};
        final boolean hasWeeklyAverage = Arrays.stream(weeklyAvg).anyMatch(v -> v > 0);
        final Chip weeklyChip = findPeriodDataTypeChip(PeriodDataType.WEEKLY_AVERAGE);
        if (weeklyChip != null) {
            weeklyChip.setVisibility(hasWeeklyAverage ? View.VISIBLE : View.GONE);
        }
        final ChartSpec spec = HrvChartData.periodSpec(
                epochDays, dayAvg, lastNight, weeklyAvg, dayMin, dayMax, baselineLow, baselineHigh,
                showDailyAverage, showLastNightAverage, showWeeklyAverage && hasWeeklyAverage, showDailyRange, labels, colors
        );

        final List<String> rowLabels = new ArrayList<>();
        final List<Integer> rowColors = new ArrayList<>();
        final List<Function1<Integer, String>> rowTexts = new ArrayList<>();
        for (final ChartSeries series : spec.getSeries()) {
            rowLabels.add(series.getLabel());
            switch (series.getKey()) {
                case "day_avg":
                    rowColors.add(colors[0]);
                    rowTexts.add(i -> formatHrvStatusValue(dayAvg[i]));
                    break;
                case "last_night":
                    rowColors.add(colors[1]);
                    rowTexts.add(i -> formatHrvStatusValue(lastNight[i]));
                    break;
                case "weekly_avg":
                    rowColors.add(colors[4]);
                    rowTexts.add(i -> formatHrvStatusValue(weeklyAvg[i]));
                    break;
                case "day_range":
                    rowColors.add(colors[2]);
                    rowTexts.add(i -> formatHrvRange(dayMin[i], dayMax[i]));
                    break;
                default:
                    rowColors.add(colors[3]);
                    rowTexts.add(i -> formatHrvRange(baselineLow[i], baselineHigh[i]));
                    break;
            }
        }
        mWeeklyHRVStatusChart.setSelectionContent(x -> DaySelections.of(
                epochDays, x, rowLabels, rowColors, rowTexts, getString(R.string.stats_empty_value)
        ));
        showChart(spec);
    }

    private void updateLastNightChart(final HRVStatusWeeklyData weeklyData) {
        final String formattedDate = new SimpleDateFormat("E, MMM dd", Locale.getDefault()).format(getEndDate());
        mDateView.setText(formattedDate);

        final List<? extends HrvValueSample> samples = weeklyData.lastNightSamples;
        final long[] epochSeconds = new long[samples.size()];
        final int[] values = new int[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            epochSeconds[i] = samples.get(i).getTimestamp() / 1000L;
            values[i] = samples.get(i).getValue();
        }
        final String label = getString(R.string.hrv_status_last_night_legend);
        final ChartSpec spec = HrvChartData.lastNightSpec(epochSeconds, values, label, HRV_AVERAGE_COLOR);
        mWeeklyHRVStatusChart.setSelectionContent(x -> {
            final String title = DateFormat.getTimeFormat(requireContext()).format(new Date(Math.round(x) * 1000L));
            int value = 0;
            for (int i = 0; i < epochSeconds.length; i++) {
                if (epochSeconds[i] == Math.round(x)) {
                    value = values[i];
                }
            }
            final String text = formatHrvStatusValue(value);
            return new ChartSelection(
                    title,
                    Collections.singletonList(new ChartSelection.Row(HRV_AVERAGE_COLOR, text)),
                    title + ". " + label + " " + text + "."
            );
        });
        showChart(spec);
    }

    private void showChart(final ChartSpec spec) {
        mWeeklyHRVStatusChart.setSpec(spec);
        final List<ChartSeries> legendSeries = new ArrayList<>();
        for (final ChartSeries series : spec.getSeries()) {
            if (!series.getPoints().isEmpty()) {
                legendSeries.add(series);
            }
        }
        mHRVChartLegend.setSeries(legendSeries);
    }

    private String formatHrvRange(final int low, final int high) {
        return low > 0 && high > low ? low + " \u2013 " + getString(R.string.hrv_status_unit, high) : getString(R.string.stats_empty_value);
    }

    private List<HRVStatusDayData> getPeriodData(DBHandler db, Calendar day, GBDevice device) {
        day = DateTimeUtils.dayStart(day);
        day.add(Calendar.DATE, -totalDays + 1);

        List<HRVStatusDayData> weeklyData = new ArrayList<>();
        for (int counter = 0; counter < totalDays; counter++) {
            int startTs = (int) (day.getTimeInMillis() / 1000);
            int endTs = startTs + 24 * 60 * 60 - 1;
            Optional<? extends HrvSummarySample> latestSummarySample = getSamples(db, device, startTs, endTs)
                    .stream()
                    .max(Comparator.comparingLong(HrvSummarySample::getTimestamp));
            List<? extends HrvValueSample> valueSamples = getHrvValueSamples(db, device, startTs, endTs);

            final Accumulator dayAccumulator = new Accumulator();
            for (HrvValueSample valueSample : valueSamples) {
                if (valueSample.getValue() > 0) {
                    dayAccumulator.add(valueSample.getValue());
                }
            }

            int avgHRV = dayAccumulator.getCount() > 0 ? (int) Math.round(dayAccumulator.getAverage()) : 0;
            int minHRV = dayAccumulator.getCount() > 0 ? (int) Math.round(dayAccumulator.getMin()) : 0;
            int maxHRV = dayAccumulator.getCount() > 0 ? (int) Math.round(dayAccumulator.getMax()) : 0;
            if (latestSummarySample.isPresent()) {
                final HrvSummarySample sample = latestSummarySample.get();
                Calendar finalDay = (Calendar) day.clone();
                weeklyData.add(new HRVStatusDayData(
                        finalDay,
                        counter,
                        sample.getTimestamp(),
                        avgHRV,
                        minHRV,
                        maxHRV,
                        sample.getWeeklyAverage() != null ? sample.getWeeklyAverage() : 0,
                        sample.getLastNightAverage() != null ? sample.getLastNightAverage() : 0,
                        sample.getLastNight5MinHigh() != null ? sample.getLastNight5MinHigh() : 0,
                        sample.getBaselineLowUpper() != null ? sample.getBaselineLowUpper() : 0,
                        sample.getBaselineBalancedLower() != null ? sample.getBaselineBalancedLower() : 0,
                        sample.getBaselineBalancedUpper() != null ? sample.getBaselineBalancedUpper() : 0,
                        sample.getStatus() != null ? sample.getStatus() : HrvSummarySample.Status.NONE
                ));
            } else {
                HRVStatusDayData d = new HRVStatusDayData(
                        (Calendar) day.clone(),
                        counter,
                        0,
                        avgHRV,
                        minHRV,
                        maxHRV,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        HrvSummarySample.Status.NONE
                );
                weeklyData.add(d);
            }

            day.add(Calendar.DATE, 1);
        }
        return weeklyData;
    }

    private List<? extends HrvSummarySample> getSamples(final DBHandler db, final GBDevice device, int tsFrom, int tsTo) {
        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        final TimeSampleProvider<? extends HrvSummarySample> sampleProvider = coordinator.getHrvSummarySampleProvider(device, db.getDaoSession());
        if (sampleProvider == null) {
            LOG.warn("Device {} does not implement HrvSummarySampleProvider", device);
            return new ArrayList<>();
        }
        return sampleProvider.getAllSamples(tsFrom * 1000L, tsTo * 1000L);
    }

    public List<? extends HrvValueSample> getHrvValueSamples(final DBHandler db, final GBDevice device, int tsFrom, int tsTo) {
        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        final TimeSampleProvider<? extends HrvValueSample> sampleProvider = coordinator.getHrvValueSampleProvider(device, db.getDaoSession());
        if (sampleProvider == null) {
            LOG.warn("Device {} does not implement HrvValueSampleProvider", device);
            return new ArrayList<>();
        }
        return sampleProvider.getAllSamples(tsFrom * 1000L, tsTo * 1000L);
    }

    private LastNightData getLastNightData(final DBHandler db, final GBDevice device, final Calendar day) {
        final Calendar searchStart = getLastNightSearchStart(day);
        final Calendar searchEnd = getLastNightSearchEnd(day);
        final int startTs = (int) (searchStart.getTimeInMillis() / 1000);
        final int endTs = (int) (searchEnd.getTimeInMillis() / 1000);

        final List<SleepSession> sleepSessions = device.getDeviceCoordinator()
                .getSleepSessionProvider(device, db.getDaoSession())
                .getSleepSessions(startTs, endTs);
        if (sleepSessions.isEmpty()) {
            return new LastNightData(new ArrayList<>(), searchStart, searchEnd);
        }

        final SleepSession lastSession = sleepSessions.get(sleepSessions.size() - 1);
        final Calendar sleepStart = Calendar.getInstance();
        sleepStart.setTimeInMillis(lastSession.getStartTime());
        final Calendar sleepEnd = Calendar.getInstance();
        sleepEnd.setTimeInMillis(lastSession.getEndTime());

        final List<? extends HrvValueSample> samples = getHrvValueSamples(
                db,
                device,
                (int) (sleepStart.getTimeInMillis() / 1000),
                (int) (sleepEnd.getTimeInMillis() / 1000));
        return new LastNightData(samples, sleepStart, sleepEnd);
    }

    private Calendar getLastNightSearchStart(final Calendar day) {
        final Calendar lastNightStart = DateTimeUtils.dayStart((Calendar) day.clone());
        lastNightStart.add(Calendar.DATE, -1);
        lastNightStart.add(Calendar.HOUR_OF_DAY, 12);
        return lastNightStart;
    }

    private Calendar getLastNightSearchEnd(final Calendar day) {
        final Calendar lastNightEnd = DateTimeUtils.dayStart((Calendar) day.clone());
        lastNightEnd.add(Calendar.HOUR_OF_DAY, 12);
        return lastNightEnd;
    }

    @Override
    protected void setupLegend(Chart<?> chart) {}

    protected static class HRVStatusWeeklyData extends ChartsData {
        private final List<HRVStatusDayData> data;
        private final List<? extends HrvValueSample> lastNightSamples;
        private final Calendar lastNightStart;
        private final Calendar lastNightEnd;

        public HRVStatusWeeklyData(final List<HRVStatusDayData> chartsData,
                                   final List<? extends HrvValueSample> lastNightSamples,
                                   final Calendar lastNightStart,
                                   final Calendar lastNightEnd) {
            this.data = chartsData;
            this.lastNightSamples = lastNightSamples;
            this.lastNightStart = lastNightStart;
            this.lastNightEnd = lastNightEnd;
        }

        public HRVStatusDayData getDay(int i) {
            return this.data.get(i);
        }

        public HRVStatusDayData getCurrentDay() {
            return this.data.get(this.data.size() - 1);
        }

        public List<HRVStatusDayData> getDaysData() {
            return data;
        }

        public int getPeriodNightlyAverage() {
            final Accumulator accumulator = new Accumulator();
            for (final HRVStatusDayData day : data) {
                if (day.lastNight > 0) {
                    accumulator.add(day.lastNight);
                }
            }
            return accumulator.getCount() > 0 ? (int) Math.round(accumulator.getAverage()) : 0;
        }

        public int getHighestNightlyAverage() {
            int highestNightlyAverage = 0;
            for (final HRVStatusDayData day : data) {
                if (day.lastNight > highestNightlyAverage) {
                    highestNightlyAverage = day.lastNight;
                }
            }
            return highestNightlyAverage;
        }
    }

    protected static class LastNightData {
        private final List<? extends HrvValueSample> samples;
        private final Calendar start;
        private final Calendar end;

        public LastNightData(final List<? extends HrvValueSample> samples,
                             final Calendar start,
                             final Calendar end) {
            this.samples = samples;
            this.start = start;
            this.end = end;
        }
    }

    protected static class HRVStatusDayData {
        public Integer i;
        public long timestamp;
        public Integer weeklyAvg;
        public Integer lastNight;
        public Integer lastNight5MinHigh;
        public Integer dayAvg;
        public Integer dayMin;
        public Integer dayMax;
        public Integer baseLineBalancedLower;
        public Integer baseLineBalancedUpper;
        public Integer baseLineLowUpper;
        public HrvSummarySample.Status status;
        public Calendar day;

        public HRVStatusDayData(Calendar day,
                                int i, long timestamp,
                                Integer dayAvg,
                                Integer dayMin,
                                Integer dayMax,
                                Integer weeklyAvg,
                                Integer lastNight,
                                Integer lastNight5MinHigh,
                                Integer baseLineLowUpper,
                                Integer baseLineBalancedLower,
                                Integer baseLineBalancedUpper,
                                HrvSummarySample.Status status) {
            this.lastNight = lastNight;
            this.weeklyAvg = weeklyAvg;
            this.lastNight5MinHigh = lastNight5MinHigh;
            this.i = i;
            this.timestamp = timestamp;
            this.status = status;
            this.day = day;
            this.dayAvg = dayAvg;
            this.dayMin = dayMin;
            this.dayMax = dayMax;
            this.baseLineLowUpper = baseLineLowUpper;
            this.baseLineBalancedLower = baseLineBalancedLower;
            this.baseLineBalancedUpper = baseLineBalancedUpper;
        }
    }

    @Nullable
    @Override
    public ChartDataRange getAvailableDataRange(final GBDevice device, final DBHandler db) {
        return ChartDataRange.union(
                ChartDataRange.ofSamples(device.getDeviceCoordinator().getHrvSummarySampleProvider(device, db.getDaoSession())),
                ChartDataRange.ofSamples(device.getDeviceCoordinator().getHrvValueSampleProvider(device, db.getDaoSession()))
        );
    }
}
