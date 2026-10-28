/*  Copyright (C) 2015-2024 Andreas Shimokawa, Carsten Pfeiffer, Daniele
    Gobbetti, Dikay900, José Rebelo, Pavel Elagin

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
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.github.mikephil.charting.charts.Chart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.HeartRateUtils;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartLegendView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.GbChartView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;

public class ActivitySleepChartFragment extends AbstractActivityChartFragment<AbstractActivityChartFragment.StageSamples> {
    protected static final Logger LOG = LoggerFactory.getLogger(ActivitySleepChartFragment.class);

    private GbChartView mChart;
    private ChartLegendView mLegend;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_charts, container, false);

        mChart = rootView.findViewById(R.id.activitysleepchart);
        mLegend = rootView.findViewById(R.id.activitysleepchart_legend);
        mChart.setZoomable(true);
        mChart.dismissSelectionOnTapOutside(rootView);

        // refresh immediately instead of use refreshIfVisible(), for perceived performance
        refresh();

        return rootView;
    }

    @Override
    public String getTitle() {
        return getString(R.string.activity_sleepchart_activity_and_sleep);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ChartsHost.REFRESH.equals(action)) {
            refresh();
        } else {
            super.onReceive(context, intent);
        }
    }

    @Override
    protected StageSamples refreshInBackground(ChartsHost chartsHost, DBHandler db, GBDevice device) {
        List<? extends ActivitySample> samples = getSamples(db, device);
        List<? extends ActivitySample> highResSamples = getSamplesHighRes(db, device);
        return stageSamples(device, samples, highResSamples != null ? highResSamples : samples);
    }

    @Override
    protected void updateChartsnUIThread(StageSamples samples) {
        final HeartRateUtils heartRateUtils = HeartRateUtils.getInstance();
        final ChartSpec spec = stagesSpec(samples, 0, false, heartRateUtils.getMinHeartRate(), heartRateUtils.getMaxHeartRate());
        mChart.setSelectionContent(stagesSelection(samples));
        mChart.setSpec(spec);

        final List<ChartSeries> legend = new ArrayList<>();
        if (!spec.isEmpty()) {
            legend.add(ChartLegendView.squareItem(akActivity.label, akActivity.color));
            legend.add(ChartLegendView.squareItem(akLightSleep.label, akLightSleep.color));
            legend.add(ChartLegendView.squareItem(akDeepSleep.label, akDeepSleep.color));
            if (supportsRemSleep(getChartsHost().getDevice())) {
                legend.add(ChartLegendView.squareItem(akRemSleep.label, akRemSleep.color));
            }
            legend.add(ChartLegendView.squareItem(akNotWorn.label, akNotWorn.color));
            if (spec.getEndYAxis() != null) {
                legend.add(ChartLegendView.lineItem(HEARTRATE_LABEL, HEARTRATE_COLOR));
            }
        }
        mLegend.setSeries(legend);
    }

    @Override
    protected void renderCharts() {
        mChart.invalidate();
    }

    @Override
    protected void setupLegend(Chart<?> chart) {
    }

    @Override
    protected List<? extends ActivitySample> getSamples(DBHandler db, GBDevice device, int tsFrom, int tsTo) {
        return getAllSamples(db, device, tsFrom, tsTo);
    }

    @Override
    protected List<? extends ActivitySample> getSamplesHighRes(DBHandler db, GBDevice device, int tsFrom, int tsTo) {
        return getAllSamplesHighRes(db, device, tsFrom, tsTo);
    }
}
