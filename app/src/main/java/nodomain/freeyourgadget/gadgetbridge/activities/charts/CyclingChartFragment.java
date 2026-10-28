package nodomain.freeyourgadget.gadgetbridge.activities.charts;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.LegendEntry;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.utils.ViewPortHandler;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartDataBuilder;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.CyclingSample;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;

public class CyclingChartFragment extends AbstractChartFragment<CyclingChartFragment.CyclingChartsData>{
    private LineChart cyclingHistoryChart;

    private int BACKGROUND_COLOR;
    private int DESCRIPTION_COLOR;
    private int CHART_TEXT_COLOR;
    private int LEGEND_TEXT_COLOR;
    private int CHART_LINE_COLOR_DISTANCE;
    private int CHART_LINE_COLOR_SPEED;

    protected static class CyclingChartsData extends DefaultChartsData<LineData> {
        public CyclingChartsData(LineData lineData) {
            super(lineData, new TimeFormatter());
        }
    }

    @Override
    public String getTitle() {
        return "Cycling data";
    }

    @Override
    protected void init() {
        BACKGROUND_COLOR = GBApplication.getBackgroundColor(requireContext());
        LEGEND_TEXT_COLOR = DESCRIPTION_COLOR = GBApplication.getTextColor(requireContext());
        CHART_TEXT_COLOR = GBApplication.getSecondaryTextColor(requireContext());

        if (GBApplication.getPrefs().getChartsHeartRateColor() == ChartsHeartRateColor.RED) {
            CHART_LINE_COLOR_DISTANCE = ContextCompat.getColor(getContext(), R.color.chart_activity_dark);
            CHART_LINE_COLOR_SPEED  = ContextCompat.getColor(getContext(), R.color.chart_heartrate);
        } else {
            CHART_LINE_COLOR_DISTANCE = ContextCompat.getColor(getContext(), R.color.chart_activity_light);
            CHART_LINE_COLOR_SPEED = ContextCompat.getColor(getContext(), R.color.chart_heartrate_alternative);
        }
    }

    @Override
    protected CyclingChartsData refreshInBackground(ChartsHost chartsHost, DBHandler db, GBDevice device) {
        List<CyclingSample> samples = getSamples(db, device);

        return new CyclingChartsDataBuilder(samples).build();
    }

    protected class CyclingChartsDataBuilder {
        private final List<CyclingSample> samples;

        private final List<Entry> lineEntries = new ArrayList<>();

        long averageSum;
        long averageNumSamples;

        public CyclingChartsDataBuilder(final List<CyclingSample> samples) {
            // Samples from a cadence-only sensor carry no distance to chart
            this.samples = new ArrayList<>();
            for (CyclingSample sample : samples) {
                if (sample.getDistance() != null) {
                    this.samples.add(sample);
                }
            }
        }

        private void reset() {
            lineEntries.clear();

            averageSum = 0;
            averageNumSamples = 0;
        }

        public CyclingChartsData build() {
            List<Entry> distanceEntries = new ArrayList<>();
            List<Entry> speedEntries = new ArrayList<>();

            Float dayStart = 0f;

            if(!samples.isEmpty()){
                dayStart = samples.get(0).getDistance() / 1000f;
            }

            int nextIndex = 0;
            CyclingSample oldSample = null;
            for (CyclingSample sample : samples) {
                // add distance in Km
                distanceEntries.add(new Entry<>(sample.getTimestamp(), (sample.getDistance() / 1000f) - dayStart, null, null));

                if(oldSample != null) {
                    float deltaMeters = sample.getDistance() - oldSample.getDistance();
                    float deltaMillis = sample.getTimestamp() - oldSample.getTimestamp();

                    float metersPerMillisecond = deltaMeters / deltaMillis;
                    float kmh = metersPerMillisecond * 3600;

                    // Float speed = sample.getSpeed();
                    if(kmh < 6.0) {
                        // speed to slow, cutting down to 0
                        speedEntries.add(new Entry<>(oldSample.getTimestamp() + 30_000, 0, null, null));
                    }
                    speedEntries.add(new Entry<>(sample.getTimestamp(), kmh, null, null));
                }

                nextIndex++;
                oldSample = sample;
            }

            LineDataSet distanceSet = new LineDataSet(distanceEntries, "Cycling");
            distanceSet.setLineWidth(ChartDataBuilder.LINE_WIDTH_DP);
            distanceSet.setColor(CHART_LINE_COLOR_DISTANCE);
            distanceSet.setDrawCirclesEnabled(false);
            distanceSet.setDrawCircleHoleEnabled(false);
            distanceSet.setDrawValuesEnabled(true);
            distanceSet.setValueTextSize(10f);
            distanceSet.setValueTextColor(CHART_TEXT_COLOR);
            distanceSet.setHighlightEnabled(false);
            distanceSet.setValueFormatter(new CyclingDistanceFormatter(CyclingChartFragment.this, dayStart));
            distanceSet.setAxisDependency(cyclingHistoryChart.getAxisLeft().getAxisDependency());
            LineData lineData = new LineData(distanceSet);

            LineDataSet speedSet = new LineDataSet(speedEntries, "Speed");
            speedSet.setLineWidth(ChartDataBuilder.LINE_WIDTH_DP);
            speedSet.setColor(CHART_LINE_COLOR_SPEED);
            speedSet.setDrawCirclesEnabled(false);
            speedSet.setDrawCircleHoleEnabled(false);
            speedSet.setDrawValuesEnabled(true);
            speedSet.setValueTextSize(10f);
            speedSet.setValueTextColor(CHART_TEXT_COLOR);
            speedSet.setHighlightEnabled(true);
            speedSet.setValueFormatter(new CyclingSpeedFormatter(CyclingChartFragment.this));
            speedSet.setAxisDependency(cyclingHistoryChart.getAxisRight().getAxisDependency());

            lineData.addDataSet(speedSet);

            return new CyclingChartsData(lineData);
        }
    }

    @Override
    protected void renderCharts() {
        cyclingHistoryChart.animateX(ANIM_TIME, Easing.INSTANCE.getEaseInOutQuart());
    }

    @Override
    protected void setupLegend(final Chart<?> chart) {
        final List<LegendEntry> legendEntries = new ArrayList<>(2);

        final LegendEntry distanceEntry = new LegendEntry();
        distanceEntry.setLabel(getString(R.string.activity_list_summary_distance));
        distanceEntry.setFormColor(CHART_LINE_COLOR_DISTANCE);
        legendEntries.add(distanceEntry);

        final LegendEntry speedEntry = new LegendEntry();
        speedEntry.setLabel(getString(R.string.Speed));
        speedEntry.setFormColor(CHART_LINE_COLOR_SPEED);
        legendEntries.add(speedEntry);

        chart.getLegend().setEntries(legendEntries);
        chart.getLegend().setTextColor(LEGEND_TEXT_COLOR);
    }

    @Override
    protected void updateChartsnUIThread(CyclingChartsData cyclingData) {
        cyclingHistoryChart.setData(null); // workaround for https://github.com/PhilJay/MPAndroidChart/issues/2317
        cyclingHistoryChart.getXAxis().setValueFormatter(cyclingData.getXValueFormatter());
        cyclingHistoryChart.getXAxis().setAvoidFirstLastClippingEnabled(true);

        cyclingHistoryChart.setData(cyclingData.getData());
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_cycling, container, false);

        cyclingHistoryChart = rootView.findViewById(R.id.chart_cycling_history);

        cyclingHistoryChart.setBackgroundColor(BACKGROUND_COLOR);
        cyclingHistoryChart.getDescription().setTextColor(DESCRIPTION_COLOR);
        configureBarLineChartDefaults(cyclingHistoryChart);

        final XAxis x = cyclingHistoryChart.getXAxis();
        x.setDrawLabelsEnabled(true);
        x.setDrawGridLinesEnabled(true);
        x.setEnabled(true);
        x.setTextColor(CHART_TEXT_COLOR);
        x.setDrawLimitLinesBehindDataEnabled(true);

        final YAxis yAxisLeft = cyclingHistoryChart.getAxisLeft();
        yAxisLeft.setDrawGridLinesEnabled(true);
        yAxisLeft.setTextColor(CHART_LINE_COLOR_DISTANCE);
        yAxisLeft.setEnabled(true);
        yAxisLeft.setGridColor(CHART_LINE_COLOR_DISTANCE);

        final YAxis yAxisRight = cyclingHistoryChart.getAxisRight();
        yAxisRight.setDrawGridLinesEnabled(true);
        yAxisRight.setTextColor(CHART_LINE_COLOR_SPEED);
        yAxisRight.setEnabled(true);
        yAxisRight.setGridColor(CHART_LINE_COLOR_SPEED);

        refresh();

        return rootView;
    }

    private List<CyclingSample> getSamples(final DBHandler db, final GBDevice device) {
        final int tsStart = getTSStart();
        final int tsEnd = getTSEnd();
        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        final TimeSampleProvider<CyclingSample> sampleProvider = coordinator.getCyclingSampleProvider(device, db.getDaoSession());
        return sampleProvider.getAllSamples(tsStart * 1000L, tsEnd * 1000L);
    }



    protected static class CyclingDistanceFormatter extends DataSetValueFormatter {
        // private final DecimalFormat formatter = new DecimalFormat("0.00 km");
        Float dayStartDistance;
        CyclingChartFragment fragment;

        public CyclingDistanceFormatter(CyclingChartFragment fragment, Float dayStartDistance) {
            this.dayStartDistance = dayStartDistance;
            this.fragment = fragment;
        }

        @Override
        public String getFormattedValue(final float value, final Entry<?> entry, final int dataSetIndex, final ViewPortHandler viewPortHandler) {
            return fragment.getString(R.string.chart_cycling_point_label_distance, entry.getY(), entry.getY() + dayStartDistance);
        }
    }

    protected static class CyclingSpeedFormatter extends DataSetValueFormatter {
        CyclingChartFragment fragment;

        public CyclingSpeedFormatter(CyclingChartFragment fragment) {
            this.fragment = fragment;
        }

        @Override
        public String getFormattedValue(final float value, final Entry<?> entry, final int dataSetIndex, final ViewPortHandler viewPortHandler) {
            return fragment.getString(R.string.chart_cycling_point_label_speed, entry.getY());
        }
    }

    protected static class TimeFormatter implements IAxisValueFormatter {
        DateFormat annotationDateFormat = SimpleDateFormat.getTimeInstance(DateFormat.SHORT);
        Calendar cal = GregorianCalendar.getInstance();

        public TimeFormatter() {
        }

        @Override
        public String getFormattedValue(final float value, final AxisBase axis) {
            cal.clear();
            cal.setTimeInMillis((long)(value));
            Date date = cal.getTime();
            return annotationDateFormat.format(date);
        }
    }

    @Nullable
    @Override
    public ChartDataRange getAvailableDataRange(final GBDevice device, final DBHandler db) {
        return ChartDataRange.ofSamples(device.getDeviceCoordinator().getCyclingSampleProvider(device, db.getDaoSession()));
    }
}
