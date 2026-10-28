package nodomain.freeyourgadget.gadgetbridge.activities.charts;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.Chart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import kotlin.jvm.functions.Function1;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.bodyenergy.BodyEnergyChartData;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.DaySelections;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.GbChartView;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.BodyEnergySample;
import nodomain.freeyourgadget.gadgetbridge.util.Accumulator;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;

public class BodyEnergyPeriodFragment extends AbstractChartFragment<BodyEnergyPeriodFragment.BodyEnergyPeriodData> {
    protected static final Logger LOG = LoggerFactory.getLogger(BodyEnergyPeriodFragment.class);

    static int SEC_PER_DAY = 24 * 60 * 60;
    static int DATA_INVALID = -1;

    private int BODY_ENERGY_COLOR;

    private TextView mDateView;
    private LinearLayout bodyEnergyStatsContainer;
    private GbChartView bodyEnergyChart;
    private int TOTAL_DAYS;

    @Override
    protected boolean isSingleDay() {
        return false;
    }

    public static BodyEnergyPeriodFragment newInstance(int totalDays) {
        BodyEnergyPeriodFragment fragment = new BodyEnergyPeriodFragment();
        Bundle args = new Bundle();
        args.putInt("totalDays", totalDays);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TOTAL_DAYS = getArguments() != null ? getArguments().getInt("totalDays") : 7;
    }

    @Override
    protected void init() {
        BODY_ENERGY_COLOR = ContextCompat.getColor(requireContext(), R.color.body_energy_level_color);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_body_energy_period, container, false);

        rootView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            getChartsHost().enableSwipeRefresh(scrollY == 0);
        });

        mDateView = rootView.findViewById(R.id.date_view);
        bodyEnergyStatsContainer = rootView.findViewById(R.id.body_energy_period_stats_container);
        bodyEnergyChart = rootView.findViewById(R.id.body_energy_chart);
        bodyEnergyChart.setZoomable(TOTAL_DAYS > 7);
        bodyEnergyChart.dismissSelectionOnTapOutside(rootView);

        refresh();

        return rootView;
    }

    @Override
    public String getTitle() {
        return getString(R.string.body_energy);
    }

    private int getStartTs() {
        final Calendar day = Calendar.getInstance();
        day.setTime(getEndDate());
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.add(Calendar.DATE, -(TOTAL_DAYS - 1));
        return (int) (day.getTimeInMillis() / 1000);
    }

    private BodyEnergyDayData fetchBodyEnergyDataForDay(DBHandler db, GBDevice device, int startTs) {
        int endTs = startTs + SEC_PER_DAY - 1;
        List<? extends BodyEnergySample> samples = getSamples(db, device, startTs, endTs);

        final Accumulator accumulator = new Accumulator();
        for (final BodyEnergySample sample : samples) {
            if (sample.getEnergy() > 0) {
                accumulator.add(sample.getEnergy());
            }
        }

        final int minimum = accumulator.getCount() > 0 ? (int) Math.round(accumulator.getMin()) : DATA_INVALID;
        final int maximum = accumulator.getCount() > 0 ? (int) Math.round(accumulator.getMax()) : DATA_INVALID;

        return new BodyEnergyDayData(samples, minimum, maximum);
    }

    @Override
    protected BodyEnergyPeriodData refreshInBackground(ChartsHost chartsHost, DBHandler db, GBDevice device) {
        final int startTs = getStartTs();

        List<BodyEnergyDayData> result = new ArrayList<>();
        for (int i = 0; i < TOTAL_DAYS; i++) {
            BodyEnergyDayData dayData = fetchBodyEnergyDataForDay(db, device, startTs + i * SEC_PER_DAY);
            result.add(dayData);
        }
        return new BodyEnergyPeriodData(result);
    }

    private List<? extends BodyEnergySample> getSamples(DBHandler db, GBDevice device, int startTs, int endTs) {
        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        final TimeSampleProvider<? extends BodyEnergySample> sampleProvider = coordinator.getBodyEnergySampleProvider(device, db.getDaoSession());
        return sampleProvider.getAllSamples(startTs * 1000L, endTs * 1000L);
    }

    @Override
    protected void updateChartsnUIThread(BodyEnergyPeriodData data) {
        final int startTs = getStartTs();
        mDateView.setText(DateTimeUtils.formatDaysUntil(TOTAL_DAYS, getTSEnd()));

        final int n = data.days.size();
        final long firstDay = Instant.ofEpochSecond(startTs).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay();
        final long[] epochDays = new long[n];
        final int[] dayMinimum = new int[n];
        final int[] dayMaximum = new int[n];
        final Accumulator minAccumulator = new Accumulator();
        final Accumulator maxAccumulator = new Accumulator();
        for (int i = 0; i < n; i++) {
            final BodyEnergyDayData dayData = data.days.get(i);
            epochDays[i] = firstDay + i;
            if (dayData.minimum > 0 && dayData.maximum > 0) {
                dayMinimum[i] = dayData.minimum;
                dayMaximum[i] = dayData.maximum;
                minAccumulator.add(dayData.minimum);
                maxAccumulator.add(dayData.maximum);
            }
        }

        final String emptyValue = requireContext().getString(R.string.stats_empty_value);
        final int minimum = minAccumulator.getCount() > 0 ? (int) Math.round(minAccumulator.getMin()) : DATA_INVALID;
        final int maximum = maxAccumulator.getCount() > 0 ? (int) Math.round(maxAccumulator.getMax()) : DATA_INVALID;

        bodyEnergyStatsContainer.removeAllViews();
        final List<StatTileData> stats = new ArrayList<>();
        stats.add(new StatTileData(minimum > 0 ? String.valueOf(minimum) : emptyValue, getString(R.string.hr_minimum)));
        stats.add(new StatTileData(maximum > 0 ? String.valueOf(maximum) : emptyValue, getString(R.string.hr_maximum)));
        StatTileGridUtilKt.addStatTileGrid(bodyEnergyStatsContainer, requireContext(), stats, 0);

        final String label = getString(R.string.body_energy);
        final Function1<Integer, String> rangeText = i -> dayMinimum[i] > 0
                ? dayMinimum[i] + " \u2013 " + dayMaximum[i]
                : getString(R.string.stats_empty_value);
        bodyEnergyChart.setSelectionContent(x -> DaySelections.of(
                epochDays, x, Collections.singletonList(label), Collections.singletonList(BODY_ENERGY_COLOR),
                Collections.singletonList(rangeText), getString(R.string.stats_empty_value)
        ));
        bodyEnergyChart.setSpec(BodyEnergyChartData.periodSpec(epochDays, dayMinimum, dayMaximum, BODY_ENERGY_COLOR, label));
    }

    @Override
    protected void setupLegend(Chart<?> chart) {
    }

    @Override
    protected void renderCharts() {
        bodyEnergyChart.invalidate();
    }

    protected static class BodyEnergyPeriodData extends ChartsData {
        public List<BodyEnergyDayData> days;

        protected BodyEnergyPeriodData(List<BodyEnergyDayData> days) {
            this.days = days;
        }
    }

    protected static class BodyEnergyDayData extends ChartsData {
        public List<? extends BodyEnergySample> samples;
        public int minimum;
        public int maximum;

        protected BodyEnergyDayData(List<? extends BodyEnergySample> samples, int minimum, int maximum) {
            this.samples = samples;
            this.minimum = minimum;
            this.maximum = maximum;
        }
    }

    @Nullable
    @Override
    public ChartDataRange getAvailableDataRange(final GBDevice device, final DBHandler db) {
        return ChartDataRange.ofSamples(device.getDeviceCoordinator().getBodyEnergySampleProvider(device, db.getDaoSession()));
    }
}
