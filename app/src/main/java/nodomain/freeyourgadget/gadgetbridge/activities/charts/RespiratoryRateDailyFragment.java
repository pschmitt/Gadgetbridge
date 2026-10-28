package nodomain.freeyourgadget.gadgetbridge.activities.charts;

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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartSelection;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.GbChartView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.respiratoryrate.RespiratoryRateChartData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.RespiratoryRateSample;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;

public class RespiratoryRateDailyFragment extends RespiratoryRateFragment<RespiratoryRateFragment.RespiratoryRateDay> {
    protected static final Logger LOG = LoggerFactory.getLogger(RespiratoryRateDailyFragment.class);

    private TextView mDateView;
    private LinearLayout statsContainer;
    private GbChartView respiratoryRateChart;

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_respiratory_rate, container, false);

        rootView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            getChartsHost().enableSwipeRefresh(scrollY == 0);
        });

        mDateView = rootView.findViewById(R.id.rr_date_view);
        statsContainer = rootView.findViewById(R.id.respiratory_rate_daily_stats_container);
        respiratoryRateChart = rootView.findViewById(R.id.respiratory_rate_line_chart);
        respiratoryRateChart.setZoomable(true);
        respiratoryRateChart.dismissSelectionOnTapOutside(rootView);
        refresh();

        return rootView;
    }

        @Override
    public String getTitle() {
        return getString(R.string.respiratoryrate);
    }

    @Override
    protected RespiratoryRateDay refreshInBackground(ChartsHost chartsHost, DBHandler db, GBDevice device) {
        Calendar day = Calendar.getInstance();
        day.setTime(chartsHost.getEndDate());
        List<RespiratoryRateDay> stepsDayList = getMyRespiratoryRateDaysData(db, day, device);
        if (stepsDayList.isEmpty()) {
            LOG.error("Failed to get RespiratoryRateDay for {}", day);
            return new RespiratoryRateDay(day, new ArrayList<>(), new ArrayList<>(), true);
        } else {
            return stepsDayList.get(0);
        }
    }

    @Override
    protected void updateChartsnUIThread(RespiratoryRateFragment.RespiratoryRateDay respiratoryRateDay) {
        String formattedDate = new SimpleDateFormat("E, MMM dd").format(respiratoryRateDay.day.getTime());
        mDateView.setText(formattedDate);
        final String emptyValue = requireContext().getString(R.string.stats_empty_value);
        final List<StatTileData> stats = new ArrayList<>();
        stats.add(new StatTileData(
                respiratoryRateDay.awakeRateAvg > 0 ? String.valueOf(respiratoryRateDay.awakeRateAvg) : emptyValue,
                getString(R.string.sleep_colored_stats_awake_avg)
        ));
        stats.add(new StatTileData(
                respiratoryRateDay.sleepRateAvg > 0 ? String.valueOf(respiratoryRateDay.sleepRateAvg) : emptyValue,
                getString(R.string.sleep_avg)
        ));
        stats.add(new StatTileData(
                respiratoryRateDay.rateLowest > 0 ? String.valueOf(respiratoryRateDay.rateLowest) : emptyValue,
                getString(R.string.lowest)
        ));
        stats.add(new StatTileData(
                respiratoryRateDay.rateHighest > 0 ? String.valueOf(respiratoryRateDay.rateHighest) : emptyValue,
                getString(R.string.highest)
        ));
        statsContainer.removeAllViews();
        StatTileGridUtilKt.addStatTileGrid(statsContainer, requireContext(), stats, 0);

        final List<? extends RespiratoryRateSample> samples = respiratoryRateDay.respiratoryRateSamples;
        final long[] seconds = new long[samples.size()];
        final double[] values = new double[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            seconds[i] = samples.get(i).getTimestamp() / 1000L;
            values[i] = samples.get(i).getRespiratoryRate();
        }
        final int rateColor = ContextCompat.getColor(requireContext(), R.color.respiratory_rate_color);
        final long dayStart = DateTimeUtils.dayStart(respiratoryRateDay.day.getTime()).getTime() / 1000L;
        respiratoryRateChart.setSelectionContent(x -> {
            final long time = Math.round(x);
            final String title = DateFormat.getTimeFormat(requireContext()).format(new Date(time * 1000L));
            for (int i = 0; i < seconds.length; i++) {
                if (seconds[i] == time && values[i] > 0) {
                    final String text = String.valueOf(Math.round(values[i]));
                    return new ChartSelection(
                            title,
                            Collections.singletonList(new ChartSelection.Row(rateColor, text)),
                            title + ". " + getTitle() + " " + text + "."
                    );
                }
            }
            return new ChartSelection(title, Collections.emptyList(), title + ".");
        });
        respiratoryRateChart.setSpec(RespiratoryRateChartData.daySpec(dayStart, seconds, values, getTitle(), rateColor));
    }

    @Override
    protected void renderCharts() {
        respiratoryRateChart.invalidate();
    }

    @Override
    protected void setupLegend(Chart<?> chart) {}
}
