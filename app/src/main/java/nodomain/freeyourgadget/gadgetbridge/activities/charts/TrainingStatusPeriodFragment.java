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

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.github.mikephil.charting.charts.Chart;
import com.google.android.material.tabs.TabLayout;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import kotlin.jvm.functions.Function1;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.GbChartView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.trainingstatus.StatusRun;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.trainingstatus.StatusShare;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.trainingstatus.TrainingStatusChartData;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.GenericMetricSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.MetricSample;
import nodomain.freeyourgadget.gadgetbridge.model.TrainingStatus;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;

public class TrainingStatusPeriodFragment extends AbstractChartFragment<TrainingStatusPeriodFragment.TrainingStatusData> {
    private static final String ARG_TOTAL_DAYS = "totalDays";
    private static final int DEFAULT_TOTAL_DAYS = 30;
    private static final int LOOKBACK_DAYS = 30;

    private int totalDays;
    private TextView dateView;
    private GbChartView chart;
    private TabLayout listTabs;
    private LinearLayout sharesContainer;
    private LinearLayout runsContainer;

    public static TrainingStatusPeriodFragment newInstance(final int totalDays) {
        final TrainingStatusPeriodFragment fragment = new TrainingStatusPeriodFragment();
        final Bundle args = new Bundle();
        args.putInt(ARG_TOTAL_DAYS, totalDays);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public String getTitle() {
        return getString(R.string.metric_garmin_training_status);
    }

    @Override
    protected boolean isSingleDay() {
        return false;
    }

    @Override
    protected int getTSStart() {
        return DateTimeUtils.shiftDays(getTSEnd(), -totalDays + 1);
    }

    @Override
    protected void init() {
        totalDays = getArguments() != null ? getArguments().getInt(ARG_TOTAL_DAYS, DEFAULT_TOTAL_DAYS) : DEFAULT_TOTAL_DAYS;
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_training_status, container, false);
        rootView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) ->
                getChartsHost().enableSwipeRefresh(scrollY == 0));

        dateView = rootView.findViewById(R.id.training_status_date_view);
        chart = rootView.findViewById(R.id.training_status_chart);
        chart.dismissSelectionOnTapOutside(rootView);
        sharesContainer = rootView.findViewById(R.id.training_status_shares);
        runsContainer = rootView.findViewById(R.id.training_status_runs);
        listTabs = rootView.findViewById(R.id.training_status_list_tabs);
        listTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(final TabLayout.Tab tab) {
                showList(tab.getPosition());
            }

            @Override
            public void onTabUnselected(final TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(final TabLayout.Tab tab) {
            }
        });

        refresh();

        return rootView;
    }

    @Override
    protected TrainingStatusData refreshInBackground(final ChartsHost chartsHost, final DBHandler db, final GBDevice device) {
        final long lastDay = toEpochDay(getTSEnd() * 1000L);
        final long firstDay = lastDay - totalDays + 1;
        final long today = LocalDate.now().toEpochDay();
        final List<? extends MetricSample> samples = loadSamples(db, device, firstDay, today);
        final List<StatusRun> runs = loadRuns(db, device, sampleDays(samples), sampleStatuses(samples), firstDay, today);
        return new TrainingStatusData(runs, firstDay, lastDay, today);
    }

    /// The samples from epoch day [from] to [to], both included.
    static List<? extends MetricSample> loadSamples(final DBHandler db, final GBDevice device, final long from, final long to) {
        final long fromMillis = LocalDate.ofEpochDay(from).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        final long toMillis = LocalDate.ofEpochDay(to + 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1;
        return GenericMetricSampleProvider.getMetricSamples(db, device, MetricSample.Metric.GARMIN_TRAINING_STATUS, fromMillis, toMillis);
    }

    static long[] sampleDays(final List<? extends MetricSample> samples) {
        final long[] days = new long[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            days[i] = toEpochDay(samples.get(i).getTimestamp());
        }
        return days;
    }

    static int[] sampleStatuses(final List<? extends MetricSample> samples) {
        final int[] statuses = new int[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            statuses[i] = (int) Math.round(samples.get(i).getMetricScore());
        }
        return statuses;
    }

    /// The runs from epoch day [from] to [to], with a run that reaches [from] given its real start.
    static List<StatusRun> loadRuns(final DBHandler db, final GBDevice device, final long[] days, final int[] statuses, final long from, final long to) {
        final List<StatusRun> runs = new ArrayList<>(TrainingStatusChartData.runs(days, statuses, from, to));
        if (runs.isEmpty() || runs.get(0).getStartDay() != from) {
            return runs;
        }
        final StatusRun first = runs.get(0);
        long start = from;
        while (true) {
            final long chunkStart = start - LOOKBACK_DAYS;
            final List<? extends MetricSample> earlier = loadSamples(db, device, chunkStart, start - 1);
            start = TrainingStatusChartData.runStart(sampleDays(earlier), sampleStatuses(earlier), first.getStatus(), start);
            if (start > chunkStart) {
                break;
            }
        }
        runs.set(0, new StatusRun(first.getStatus(), start, first.getEndDay()));
        return runs;
    }

    @Override
    protected void updateChartsnUIThread(final TrainingStatusData data) {
        dateView.setText(DateTimeUtils.formatDaysUntil(totalDays, getTSEnd()));

        final Function1<Integer, String> label = status -> TrainingStatus.label(requireContext(), status);
        final Function1<Integer, Integer> color = status -> TrainingStatus.color(requireContext(), status);
        final Function1<StatusRun, String> range = run -> rangeText(requireContext(), run, data.today);

        chart.setSelectionContent(x -> TrainingStatusChartData.selection(
                data.runs, x, formatDate(Math.round(x), "EEEMMMd"), label, color, range, getString(R.string.no_data)
        ));
        final ChartSpec spec = TrainingStatusChartData.spec(data.runs, data.firstDay, data.lastDay, label, color);
        chart.setSpec(spec);

        final List<StatusShare> shares = TrainingStatusChartData.shares(data.runs, data.firstDay, data.lastDay);
        sharesContainer.removeAllViews();
        for (final StatusShare share : shares) {
            final String days = getResources().getQuantityString(R.plurals.amount_of_days, (int) share.getDays(), (int) share.getDays());
            addStatusRow(sharesContainer, share.getStatus(), getString(R.string.training_status_share, share.getPercent(), days));
        }

        final List<StatusRun> visibleRuns = TrainingStatusChartData.visibleRuns(data.runs, data.firstDay, data.lastDay);
        runsContainer.removeAllViews();
        for (final StatusRun run : visibleRuns) {
            addStatusRow(runsContainer, run.getStatus(), range.invoke(run));
        }

        listTabs.setVisibility(visibleRuns.isEmpty() ? View.GONE : View.VISIBLE);
        showList(listTabs.getSelectedTabPosition());
    }

    private void showList(final int position) {
        final boolean hasRuns = listTabs.getVisibility() == View.VISIBLE;
        runsContainer.setVisibility(hasRuns && position != 1 ? View.VISIBLE : View.GONE);
        sharesContainer.setVisibility(hasRuns && position == 1 ? View.VISIBLE : View.GONE);
    }

    private void addStatusRow(final LinearLayout container, final int status, final String text) {
        final View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_training_status_run, container, false);
        final GradientDrawable dot = new GradientDrawable();
        dot.setColor(TrainingStatus.color(requireContext(), status));
        row.findViewById(R.id.training_status_run_dot).setBackground(dot);
        ((TextView) row.findViewById(R.id.training_status_run_label)).setText(TrainingStatus.label(requireContext(), status));
        ((TextView) row.findViewById(R.id.training_status_run_range)).setText(text);
        container.addView(row);
    }

    static String rangeText(final Context context, final StatusRun run, final long today) {
        return TrainingStatusChartData.rangeText(
                run,
                today,
                day -> formatDate(day, "MMMd"),
                context.getString(R.string.training_status_today),
                (start, end) -> context.getString(R.string.training_status_range, start, end)
        );
    }

    private static String formatDate(final long epochDay, final String skeleton) {
        final Locale locale = Locale.getDefault();
        return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)
                .format(LocalDate.ofEpochDay(epochDay));
    }

    static long toEpochDay(final long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay();
    }

    @Override
    protected void renderCharts() {
        chart.invalidate();
    }

    @Override
    protected void setupLegend(final Chart<?> chart) {
    }

    protected static class TrainingStatusData extends ChartsData {
        final List<StatusRun> runs;
        final long firstDay;
        final long lastDay;
        final long today;

        TrainingStatusData(final List<StatusRun> runs, final long firstDay, final long lastDay, final long today) {
            this.runs = runs;
            this.firstDay = firstDay;
            this.lastDay = lastDay;
            this.today = today;
        }
    }
}
