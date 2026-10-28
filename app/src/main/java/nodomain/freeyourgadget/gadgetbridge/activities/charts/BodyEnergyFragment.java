package nodomain.freeyourgadget.gadgetbridge.activities.charts;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;

import com.github.mikephil.charting.charts.Chart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.bodyenergy.BodyEnergyChartData;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartLegendView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartSelection;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.GbChartView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.BodyEnergySample;


public class BodyEnergyFragment extends AbstractChartFragment<BodyEnergyFragment.BodyEnergyData> {
    protected static final Logger LOG = LoggerFactory.getLogger(BodyEnergyFragment.class);

    private TextView mDateView;
    private ImageView bodyEnergyGauge;
    private LinearLayout bodyEnergyStatsContainer;
    private GbChartView bodyEnergyChart;
    private ChartLegendView bodyEnergyLegend;

    protected int TEXT_COLOR;
    protected int SUBTEXT_COLOR;
    protected int AVERAGE_LINE_COLOR;

    // Number of days to include in the average calculation
    private static final int DAYS_FOR_AVERAGE = 30;
    private static final int AVERAGE_BIN_SIZE_MINS = 60;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_body_energy, container, false);

        rootView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            getChartsHost().enableSwipeRefresh(scrollY == 0);
        });

        mDateView = rootView.findViewById(R.id.body_energy_date_view);
        bodyEnergyGauge = rootView.findViewById(R.id.body_energy_gauge);
        bodyEnergyStatsContainer = rootView.findViewById(R.id.body_energy_stats_container);
        bodyEnergyChart = rootView.findViewById(R.id.body_energy_chart);
        bodyEnergyChart.dismissSelectionOnTapOutside(rootView);
        bodyEnergyLegend = rootView.findViewById(R.id.body_energy_chart_legend);
        refresh();


        return rootView;
    }


    @Override
    public String getTitle() {
        return getString(R.string.body_energy);
    }

    @Override
    protected void init() {
        TEXT_COLOR = GBApplication.getTextColor(requireContext());
        SUBTEXT_COLOR = GBApplication.getSecondaryTextColor(requireContext());
        AVERAGE_LINE_COLOR = Color.GRAY;
    }

    @Override
    protected BodyEnergyData refreshInBackground(ChartsHost chartsHost, DBHandler db, GBDevice device) {
        List<? extends BodyEnergySample> todaySamples = getBodyEnergySamples(db, device, getTSStart(), getTSEnd());
        List<List<? extends BodyEnergySample>> historicalData = getHistoricalBodyEnergyData(db, device, DAYS_FOR_AVERAGE);
        return new BodyEnergyData(todaySamples, historicalData);
    }

    @Override
    protected void updateChartsnUIThread(BodyEnergyData bodyEnergyData) {
        String formattedDate = new SimpleDateFormat("E, MMM dd").format(getEndDate());
        mDateView.setText(formattedDate);

        final List<? extends BodyEnergySample> samples = bodyEnergyData.todaySamples;
        final long[] sampleSeconds = new long[samples.size()];
        final int[] levels = new int[samples.size()];
        int gainedValue = 0;
        int drainedValue = 0;
        int lastValue = 0;
        for (int i = 0; i < samples.size(); i++) {
            final int energy = samples.get(i).getEnergy();
            if (energy < lastValue) {
                drainedValue += lastValue - energy;
            } else if (lastValue > 0 && energy > lastValue) {
                gainedValue += energy - lastValue;
            }
            lastValue = energy;
            sampleSeconds[i] = samples.get(i).getTimestamp() / 1000L;
            levels[i] = energy;
        }
        final int newestValue = samples.isEmpty() ? 0 : samples.get(samples.size() - 1).getEnergy();

        final long midnight = dayStartSeconds();
        final double[] averages = bodyEnergyData.historicalData.isEmpty()
                ? new double[0]
                : buildAverages(bodyEnergyData.historicalData, AVERAGE_BIN_SIZE_MINS);
        final int levelColor = getResources().getColor(R.color.body_energy_level_color);
        final String levelLabel = getString(R.string.body_energy_legend_level);
        final String averageLabel = getString(R.string.body_energy_legend_average);
        final ChartSpec spec = BodyEnergyChartData.daySpec(
                midnight, sampleSeconds, levels, averages, levelLabel, levelColor, averageLabel, AVERAGE_LINE_COLOR
        );
        bodyEnergyChart.setSelectionContent(x -> daySelection(Math.round(x), spec, levelColor));
        bodyEnergyChart.setSpec(spec);

        final List<ChartSeries> legendSeries = new ArrayList<>();
        for (int i = spec.getSeries().size() - 1; i >= 0; i--) {
            legendSeries.add(spec.getSeries().get(i));
        }
        bodyEnergyLegend.setSeries(legendSeries);

        final int width = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                300,
                GBApplication.getContext().getResources().getDisplayMetrics()
        );

        bodyEnergyGauge.setImageBitmap(drawGauge(
                width,
                width / 15,
                getResources().getColor(R.color.body_energy_level_color),
                newestValue,
                100
        ));
        final List<StatTileData> stats = new ArrayList<>();
        stats.add(new StatTileData(String.format("+ %s", gainedValue), getString(R.string.body_energy_gained)));
        stats.add(new StatTileData(String.format("- %s", drainedValue), getString(R.string.body_energy_lost)));
        bodyEnergyStatsContainer.removeAllViews();
        StatTileGridUtilKt.addStatTileGrid(bodyEnergyStatsContainer, requireContext(), stats, 0);
    }

    private ChartSelection daySelection(final long time, final ChartSpec spec, final int levelColor) {
        final String title = DateFormat.getTimeFormat(requireContext()).format(new Date(time * 1000L));
        final List<ChartSelection.Row> rows = new ArrayList<>();
        final StringBuilder description = new StringBuilder(title).append('.');
        for (final ChartSeries series : spec.getSeries()) {
            if (!series.getSelectable()) {
                continue;
            }
            for (final ChartPoint point : series.getPoints()) {
                if (point.getX() == time) {
                    final long level = Math.round(point.getY());
                    rows.add(new ChartSelection.Row(levelColor, String.valueOf(level)));
                    description.append(' ').append(series.getLabel()).append(' ').append(level).append('.');
                    break;
                }
            }
        }
        return new ChartSelection(title, rows, description.toString());
    }

    private long dayStartSeconds() {
        final Calendar day = Calendar.getInstance();
        day.setTimeInMillis(getTSEnd() * 1000L);
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        return day.getTimeInMillis() / 1000L;
    }

    @Override
    protected void renderCharts() {
        bodyEnergyChart.invalidate();
    }

    /**
     * Get body energy samples for the specified time range
     */
    public List<? extends BodyEnergySample> getBodyEnergySamples(final DBHandler db, final GBDevice device, int tsFrom, int tsTo) {
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(tsTo * 1000L); //we need today initially, which is the end of the time range
        day.set(Calendar.HOUR_OF_DAY, 0); //and we set time for the start and end of the same day
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        tsFrom = (int) (day.getTimeInMillis() / 1000);
        tsTo = tsFrom + 24 * 60 * 60 - 1;

        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        final TimeSampleProvider<? extends BodyEnergySample> sampleProvider = coordinator.getBodyEnergySampleProvider(device, db.getDaoSession());
        return sampleProvider.getAllSamples(tsFrom * 1000L, tsTo * 1000L);
    }

    /**
     * Get historical body energy data for the specified number of days
     */
    private List<List<? extends BodyEnergySample>> getHistoricalBodyEnergyData(final DBHandler db, final GBDevice device, int daysCount) {
        List<List<? extends BodyEnergySample>> historicalData = new ArrayList<>();

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(getTSEnd() * 1000L);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);

        // Go back one more day to exclude today
        cal.add(Calendar.DAY_OF_YEAR, -1);

        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        final TimeSampleProvider<? extends BodyEnergySample> sampleProvider = coordinator.getBodyEnergySampleProvider(device, db.getDaoSession());

        for (int i = 0; i < daysCount; i++) {
            long dayStart = (int) (cal.getTimeInMillis() / 1000);
            long dayEnd = dayStart + 24 * 60 * 60 - 1;

            List<? extends BodyEnergySample> daySamples = sampleProvider.getAllSamples(dayStart * 1000L, dayEnd * 1000L);
            historicalData.add(daySamples);

            // Move to the previous day
            cal.add(Calendar.DAY_OF_YEAR, -1);
        }

        return historicalData;
    }

    /**
     * Average energy per bin of [binSizeMinutes] from local midnight over [historicDays]; NaN for bins without data.
     */
    private double[] buildAverages(List<List<? extends BodyEnergySample>> historicDays, int binSizeMinutes) {
        if (binSizeMinutes <= 0 || 24 * 60 % binSizeMinutes != 0) {
            throw new IllegalArgumentException("binSizeMinutes must be a positive divisor of 24 hours");
        }

        final int binSizeSeconds = binSizeMinutes * 60;
        final int binsPerDay = 24 * 60 * 60 / binSizeSeconds;

        long[] sum = new long[binsPerDay];
        int[] count = new int[binsPerDay];

        TimeZone tz = TimeZone.getDefault();

        for (List<? extends BodyEnergySample> day : historicDays) {
            for (BodyEnergySample sample : day) {
                long ts = sample.getTimestamp();
                int offsetSec = tz.getOffset(ts) / 1000;
                long localSec = ts / 1000 + offsetSec;
                int bin = (int) ((localSec / binSizeSeconds) % binsPerDay);

                sum[bin] += sample.getEnergy();
                count[bin] += 1;
            }
        }

        final double[] averages = new double[binsPerDay];
        for (int bin = 0; bin < binsPerDay; bin++) {
            averages[bin] = count[bin] != 0 ? (double) sum[bin] / count[bin] : Double.NaN;
        }
        return averages;
    }

    @Override
    protected void setupLegend(Chart<?> chart) {}

    Bitmap drawGauge(int width, int barWidth, @ColorInt int filledColor, int value, int maxValue) {
        int height = width;
        int barMargin = (int) Math.ceil(barWidth / 2f);
        float filledFactor = (float) value / maxValue;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(barWidth);
        paint.setColor(getResources().getColor(R.color.gauge_line_color));
        canvas.drawArc(
                barMargin,
                barMargin,
                width - barMargin,
                width - barMargin,
                120,
                300,
                false,
                paint);
        paint.setStrokeWidth(barWidth);
        paint.setColor(filledColor);
        canvas.drawArc(
                barMargin,
                barMargin,
                width - barMargin,
                height - barMargin,
                120,
                300 * filledFactor,
                false,
                paint
        );

        Paint textPaint = new Paint();
        textPaint.setColor(TEXT_COLOR);
        float textPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, width * 0.06f, requireContext().getResources().getDisplayMetrics());
        textPaint.setTextSize(textPixels);
        textPaint.setTextAlign(Paint.Align.CENTER);
        int yPos = (int) ((float) height / 2 - ((textPaint.descent() + textPaint.ascent()) / 2)) ;
        canvas.drawText(String.valueOf(value), width / 2f, yPos, textPaint);
        Paint textLowerPaint = new Paint();
        textLowerPaint.setColor(SUBTEXT_COLOR);
        textLowerPaint.setTextAlign(Paint.Align.CENTER);
        float textLowerPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, width * 0.025f, requireContext().getResources().getDisplayMetrics());
        textLowerPaint.setTextSize(textLowerPixels);
        int yPosLowerText = (int) ((float) height / 2 - textPaint.ascent()) ;
        canvas.drawText(String.valueOf(maxValue), width / 2f, yPosLowerText, textLowerPaint);

        return bitmap;
    }

    protected static class BodyEnergyData extends ChartsData {
        private final List<? extends BodyEnergySample> todaySamples;
        private final List<List<? extends BodyEnergySample>> historicalData;

        protected BodyEnergyData(List<? extends BodyEnergySample> todaySamples, List<List<? extends BodyEnergySample>> historicalData) {
            this.todaySamples = todaySamples;
            this.historicalData = historicalData;
        }
    }

    @Nullable
    @Override
    public ChartDataRange getAvailableDataRange(final GBDevice device, final DBHandler db) {
        return ChartDataRange.ofSamples(device.getDeviceCoordinator().getBodyEnergySampleProvider(device, db.getDaoSession()));
    }
}
