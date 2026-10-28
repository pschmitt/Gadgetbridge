/*  Copyright (C) 2023-2024 Martin.JM, a0z

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

import android.graphics.Color;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.github.mikephil.charting.charts.Chart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartLegendView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartSelection;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spo2.Spo2ChartData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.databinding.FragmentSpo2Binding;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.Spo2ManualMeasurement;
import nodomain.freeyourgadget.gadgetbridge.model.Spo2Sample;
import nodomain.freeyourgadget.gadgetbridge.util.Accumulator;

// Based on StressDailyFragment

public class Spo2ChartFragment extends AbstractChartFragment<Spo2ChartFragment.Spo2ChartsData> {
    protected static final Logger LOG = LoggerFactory.getLogger(Spo2ChartFragment.class);

    static int DATA_INVALID = -1;

    private FragmentSpo2Binding binding;

    @Override
    protected void init() {
    }

    @Override
    public View onCreateView(final LayoutInflater inflater,
                             final ViewGroup container,
                             final Bundle savedInstanceState) {
        binding = FragmentSpo2Binding.inflate(inflater, container, false);
        binding.manualMeasurements.setVisibility(View.GONE);
        binding.spo2LineChart.setZoomable(true);
        binding.spo2LineChart.dismissSelectionOnTapOutside(binding.getRoot());
        refresh();
        return binding.getRoot();
    }

    @Override
    protected Spo2ChartsData refreshInBackground(final ChartsHost chartsHost, final DBHandler db, final GBDevice device) {
        Calendar day = Calendar.getInstance();
        day.setTime(getEndDate());
        day.add(Calendar.DATE, 0);
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.add(Calendar.HOUR, 0);
        int startTs = (int) (day.getTimeInMillis() / 1000);
        int endTs = startTs + 24 * 60 * 60 - 1;
        final String formattedDate = new SimpleDateFormat("E, MMM dd").format(chartsHost.getEndDate());
        return fetchSpo2Data(db, device, startTs, endTs, formattedDate);
    }

    @Override
    protected void updateChartsnUIThread(Spo2ChartsData data) {
        binding.manualMeasurementsList.removeAllViews();
        binding.manualMeasurements.setVisibility(View.GONE);
        final String emptyValue = requireContext().getString(R.string.stats_empty_value);
        binding.dateView.setText(data.formattedDate);

        final List<StatTileData> stats = new ArrayList<>();
        stats.add(new StatTileData(
                data.minimum > 0 ? getString(R.string.battery_percentage_str, String.valueOf(data.minimum)) : emptyValue,
                getString(R.string.hr_minimum)
        ));
        stats.add(new StatTileData(
                data.maximum > 0 ? getString(R.string.battery_percentage_str, String.valueOf(data.maximum)) : emptyValue,
                getString(R.string.hr_maximum)
        ));
        stats.add(new StatTileData(
                data.average > 0 ? getString(R.string.battery_percentage_str, String.valueOf(data.average)) : emptyValue,
                getString(R.string.hr_average)
        ));
        binding.spo2DailyStatsContainer.removeAllViews();
        StatTileGridUtilKt.addStatTileGrid(binding.spo2DailyStatsContainer, requireContext(), stats, 0);

        final List<Spo2Sample> manualMeasurementSamples = new ArrayList<>();
        final List<Spo2Sample> autoSamples = new ArrayList<>();
        for (final Spo2Sample sample : data.samples) {
            if (sample.getType() == Spo2Sample.Type.MANUAL) {
                manualMeasurementSamples.add(sample);
            } else {
                autoSamples.add(sample);
            }
        }
        final long[] autoSeconds = new long[autoSamples.size()];
        final int[] autoValues = new int[autoSamples.size()];
        for (int i = 0; i < autoSamples.size(); i++) {
            autoSeconds[i] = autoSamples.get(i).getTimestamp() / 1000L;
            autoValues[i] = autoSamples.get(i).getSpo2();
        }
        final long[] manualSeconds = new long[manualMeasurementSamples.size()];
        final int[] manualValues = new int[manualMeasurementSamples.size()];
        for (int i = 0; i < manualMeasurementSamples.size(); i++) {
            manualSeconds[i] = manualMeasurementSamples.get(i).getTimestamp() / 1000L;
            manualValues[i] = manualMeasurementSamples.get(i).getSpo2();
        }

        final int spo2Color = getResources().getColor(R.color.spo2_color);
        final String label = getString(R.string.pref_header_spo2);
        final ChartSpec spec = Spo2ChartData.daySpec(
                data.startTs, autoSeconds, autoValues, manualSeconds, manualValues, data.average,
                GBApplication.getPrefs().getBoolean("charts_show_average", true), label, spo2Color, Color.GRAY
        );
        binding.spo2LineChart.setSelectionContent(x -> {
            final long time = Math.round(x);
            final String title = DateFormat.getTimeFormat(requireContext()).format(new Date(time * 1000L));
            int value = 0;
            for (final Spo2Sample sample : data.samples) {
                if (sample.getTimestamp() / 1000L == time && sample.getSpo2() > 0) {
                    value = sample.getSpo2();
                }
            }
            if (value == 0) {
                return new ChartSelection(title, Collections.emptyList(), title + ".");
            }
            final String text = getString(R.string.battery_percentage_str, String.valueOf(value));
            return new ChartSelection(
                    title,
                    Collections.singletonList(new ChartSelection.Row(spo2Color, text)),
                    title + ". " + label + " " + text + "."
            );
        });
        binding.spo2LineChart.setSpec(spec);

        final List<ChartSeries> legendSeries = new ArrayList<>();
        if (!spec.getSeries().isEmpty()) {
            legendSeries.add(spec.getSeries().get(0));
            if (!spec.getLimitLines().isEmpty()) {
                legendSeries.add(ChartLegendView.lineItem(getString(R.string.hr_average), Color.GRAY));
            }
        }
        binding.spo2ChartLegend.setSeries(legendSeries);

        if (!manualMeasurementSamples.isEmpty()) {
            for (Spo2Sample sample : manualMeasurementSamples) {
                View itemView = LayoutInflater.from(getContext()).inflate(R.layout.item_spo2_manual_measurment, binding.manualMeasurementsList, false);
                TextView timeText = itemView.findViewById(R.id.timeText);
                TextView valueText = itemView.findViewById(R.id.valueText);
                Spo2ManualMeasurement measurement = new Spo2ManualMeasurement(sample.getTimestamp(), sample.getSpo2());
                timeText.setText(measurement.getTime());
                valueText.setText(measurement.getValue());
                binding.manualMeasurementsList.addView(itemView);
            }
            binding.manualMeasurementsList.getChildAt(binding.manualMeasurementsList.getChildCount() - 1)
                    .findViewById(R.id.separator)
                    .setVisibility(View.GONE);
            binding.manualMeasurements.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public String getTitle() {
        return requireContext().getString(R.string.pref_header_spo2);
    }

    @Override
    protected void setupLegend(final Chart<?> chart) {}

    @Override
    protected void renderCharts() {
        binding.spo2LineChart.invalidate();
    }

    private List<? extends Spo2Sample> getSamples(final DBHandler db, final GBDevice device, int startTs, int endTs) {
        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        final TimeSampleProvider<? extends Spo2Sample> sampleProvider = coordinator.getSpo2SampleProvider(device, db.getDaoSession());
        return sampleProvider.getAllSamples(startTs * 1000L, endTs * 1000L);
    }


    private Spo2ChartsData fetchSpo2Data(final DBHandler db,
                                         final GBDevice device,
                                         final int startTs,
                                         final int endTs,
                                         final String formattedDate) {
        List<? extends Spo2Sample> samples = getSamples(db, device, startTs, endTs);

        final Accumulator accumulator = new Accumulator();
        for (int i = 0; i < samples.size(); i++) {
            final Spo2Sample sample = samples.get(i);
            if (sample.getSpo2() > 0) {
                accumulator.add(sample.getSpo2());
            }
        }

        final int average = accumulator.getCount() > 0 ? (int) Math.round(accumulator.getAverage()) : DATA_INVALID;
        final int minimum = accumulator.getCount() > 0 ? (int) Math.round(accumulator.getMin()) : DATA_INVALID;
        final int maximum = accumulator.getCount() > 0 ? (int) Math.round(accumulator.getMax()) : DATA_INVALID;

        return new Spo2ChartsData(samples, startTs, average, minimum, maximum, formattedDate);
    }

    protected static class Spo2ChartsData extends ChartsData {
        public List<? extends Spo2Sample> samples;
        public final int startTs;
        public final int average;
        public final int minimum;
        public final int maximum;
        public final String formattedDate;

        public Spo2ChartsData(final List<? extends Spo2Sample> samples,
                              final int startTs,
                              final int average,
                              final int minimum,
                              final int maximum,
                              final String formattedDate) {
            this.samples = samples;
            this.startTs = startTs;
            this.average = average;
            this.minimum = minimum;
            this.maximum = maximum;
            this.formattedDate = formattedDate;
        }
    }

    @Nullable
    @Override
    public ChartDataRange getAvailableDataRange(final GBDevice device, final DBHandler db) {
        return ChartDataRange.ofSamples(device.getDeviceCoordinator().getSpo2SampleProvider(device, db.getDaoSession()));
    }
}
