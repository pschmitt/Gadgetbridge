package nodomain.freeyourgadget.gadgetbridge.activities.charts;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import kotlin.jvm.functions.Function1;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.hydration.HydrationChartData;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartLegendView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.DaySelections;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.GbChartView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.HydrationSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser;
import nodomain.freeyourgadget.gadgetbridge.model.HydrationUnit;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;

public class HydrationPeriodFragment extends HydrationFragment<HydrationPeriodFragment.HydrationData> {
    private int TOTAL_DAYS = 1;

    private TextView mDateView;
    private LinearLayout hydrationStatsContainer;
    private GbChartView hydrationChart;
    private ChartLegendView hydrationLegend;

    @Override
    protected boolean isSingleDay() {
        return false;
    }

    public static HydrationPeriodFragment newInstance(final int totalDays) {
        final HydrationPeriodFragment fragment = new HydrationPeriodFragment();
        final Bundle args = new Bundle();
        args.putInt("totalDays", totalDays);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TOTAL_DAYS = getArguments() != null ? getArguments().getInt("totalDays") : 1;
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_hydration_period, container, false);

        rootView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            getChartsHost().enableSwipeRefresh(scrollY == 0);
        });

        mDateView = rootView.findViewById(R.id.hydration_date_view);
        hydrationChart = rootView.findViewById(R.id.hydration_chart);
        hydrationStatsContainer = rootView.findViewById(R.id.hydration_period_stats_container);
        hydrationLegend = rootView.findViewById(R.id.hydration_chart_legend);
        hydrationChart.setZoomable(TOTAL_DAYS > 7);
        hydrationChart.dismissSelectionOnTapOutside(rootView);

        refresh();

        return rootView;
    }

    @Override
    protected HydrationData refreshInBackground(final ChartsHost chartsHost, final DBHandler db, final GBDevice device) {
        final LocalDate endDate = toLocalDate(chartsHost.getEndDate());
        final LocalDate startDate = endDate.minusDays(TOTAL_DAYS - 1);

        final HydrationSampleProvider provider = device.getDeviceCoordinator().getHydrationSampleProvider(device, db.getDaoSession());
        final Map<Integer, Double> totals = provider != null
                ? provider.getDayTotals(HydrationSampleProvider.toDay(startDate), HydrationSampleProvider.toDay(endDate))
                : Collections.emptyMap();

        final List<HydrationDay> days = new ArrayList<>(TOTAL_DAYS);
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            final Double total = totals.get(HydrationSampleProvider.toDay(date));
            days.add(new HydrationDay(date, total != null ? Math.max(total, 0) : 0));
        }

        return new HydrationData(days, new ActivityUser().getHydrationGoalMl(), HydrationUnit.forDevice(device));
    }

    @Override
    protected void updateChartsnUIThread(HydrationData data) {
        final Context context = requireContext();
        final HydrationUnit unit = data.unit;

        mDateView.setText(DateTimeUtils.formatDaysUntil(TOTAL_DAYS, getTSEnd()));

        final int n = data.days.size();
        final long[] epochDays = new long[n];
        final double[] volumes = new double[n];
        double totalMl = 0;
        for (int i = 0; i < n; i++) {
            final HydrationDay day = data.days.get(i);
            epochDays[i] = day.date.toEpochDay();
            volumes[i] = unit.fromMl(day.totalMl);
            totalMl += day.totalMl;
        }
        final int color = ContextCompat.getColor(context, R.color.hydration_color);
        final String label = getTitle();
        final String emptyValue = getString(R.string.stats_empty_value);
        final ChartSpec spec = HydrationChartData.periodSpec(epochDays, volumes, unit.fromMl(data.goalMl), label, color);
        final Function1<Integer, String> rowText = i -> volumes[i] > 0 ? unit.format(context, data.days.get(i).totalMl) : emptyValue;
        hydrationChart.setSelectionContent(x -> DaySelections.of(
                epochDays, x, Collections.singletonList(label), Collections.singletonList(color),
                Collections.singletonList(rowText), emptyValue
        ));
        hydrationChart.setSpec(spec);

        final List<ChartSeries> legendSeries = new ArrayList<>(spec.getSeries());
        if (!spec.getSeries().isEmpty() && !spec.getLimitLines().isEmpty()) {
            legendSeries.add(ChartLegendView.lineItem(getString(R.string.hydration_goal), color));
        }
        hydrationLegend.setSeries(legendSeries.size() > 1 ? legendSeries : Collections.emptyList());

        final List<StatTileData> stats = Arrays.asList(
                new StatTileData(unit.format(context, data.days.isEmpty() ? 0 : totalMl / data.days.size()), getString(R.string.hydration_daily_average)),
                new StatTileData(unit.format(context, data.goalMl), getString(R.string.hydration_goal))
        );
        hydrationStatsContainer.removeAllViews();
        StatTileGridUtilKt.addStatTileGrid(hydrationStatsContainer, context, stats, 0);
    }

    @Override
    protected void renderCharts() {
        hydrationChart.invalidate();
    }

    protected static class HydrationDay {
        private final LocalDate date;
        private final double totalMl;

        private HydrationDay(final LocalDate date, final double totalMl) {
            this.date = date;
            this.totalMl = totalMl;
        }
    }

    protected static class HydrationData extends ChartsData {
        private final List<HydrationDay> days;
        private final int goalMl;
        private final HydrationUnit unit;

        protected HydrationData(final List<HydrationDay> days, final int goalMl, final HydrationUnit unit) {
            this.days = days;
            this.goalMl = goalMl;
            this.unit = unit;
        }
    }
}
