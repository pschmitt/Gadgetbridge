/*  Copyright (C) 2020-2024 José Rebelo, Petr Vaněk

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
package nodomain.freeyourgadget.gadgetbridge.activities;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.github.mikephil.charting.charts.Chart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.AbstractActivityChartFragment;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.ChartsData;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.ChartsHost;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartLegendView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.GbChartView;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec;
import nodomain.freeyourgadget.gadgetbridge.database.DBAccess;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityPoint;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrack;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrackProvider;

public class ActivitySummariesChartFragment extends AbstractActivityChartFragment<ChartsData> {
    private static final Logger LOG = LoggerFactory.getLogger(ActivitySummariesChartFragment.class);

    private GbChartView mChart;
    private ChartLegendView mLegend;
    private View view;

    // If a track file is being used (takes precedence over activity data)
    private BaseActivitySummary summary;

    // If activity data is being used
    private GBDevice gbDevice;
    private int startTime;
    private int endTime;

    private RefreshTask refreshTask;

    @Override
    protected void onReceive(final Context context, final Intent intent) {
        // FIXME: We need to override this, or we crash
        //  This class should be refactored not to extend AbstractActivityChartFragment
        //    java.lang.ClassCastException: nodomain.freeyourgadget.gadgetbridge.activities.ActivitySummaryDetail cannot be cast to nodomain.freeyourgadget.gadgetbridge.activities.charts.ChartsHost
        //      at nodomain.freeyourgadget.gadgetbridge.activities.charts.AbstractChartFragment.getChartsHost(AbstractChartFragment.java:164)
        //      at nodomain.freeyourgadget.gadgetbridge.activities.charts.AbstractChartFragment.getStartDate(AbstractChartFragment.java:176)
        //      at nodomain.freeyourgadget.gadgetbridge.activities.charts.AbstractChartFragment.onReceive(AbstractChartFragment.java:215)
        //      at nodomain.freeyourgadget.gadgetbridge.activities.charts.AbstractChartFragment$1.onReceive(AbstractChartFragment.java:82)
        //      at androidx.localbroadcastmanager.content.LocalBroadcastManager.executePendingBroadcasts(LocalBroadcastManager.java:319)
    }

    public void setDateAndGetData(@Nullable BaseActivitySummary summary, GBDevice gbDevice, long startTime, long endTime) {
        this.summary = summary;
        this.startTime = (int) startTime;
        this.endTime = (int) endTime;
        this.gbDevice = gbDevice;
        if (this.view != null) {
            startRefreshTask();
        }
    }

    protected RefreshTask createLocalRefreshTask(String task, Context context) {
        return new RefreshTask(task, context);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_activity_summaries_chart, container, false);
        mChart = rootView.findViewById(R.id.activity_summaries_chart);
        mChart.setZoomable(true);
        mLegend = rootView.findViewById(R.id.activity_summaries_chart_legend);
        return rootView;
    }

    @Override
    public void onViewCreated(final View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        init();
        this.view = view;
        if (this.summary != null || this.gbDevice != null) {
            startRefreshTask();
        }
    }

    @Override
    public void onDestroyView() {
        if (refreshTask != null) {
            refreshTask.cancel(true);
            refreshTask = null;
        }
        mChart = null;
        mLegend = null;
        view = null;
        super.onDestroyView();
    }

    private void startRefreshTask() {
        if (refreshTask != null) {
            refreshTask.cancel(true);
        }
        refreshTask = createLocalRefreshTask("getting hr and activity", getActivity());
        refreshTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public String getTitle() {
        return "";
    }

    @Override
    protected List<? extends ActivitySample> getSamples(DBHandler db, GBDevice device, int tsFrom, int tsTo) {
        return getAllSamples(db, device, tsFrom, tsTo);
    }

    @Override
    protected List<? extends ActivitySample> getSamplesHighRes(DBHandler db, GBDevice device, int tsFrom, int tsTo) {
        return getAllSamplesHighRes(db, device, tsFrom, tsTo);
    }

    @Override
    protected void setupLegend(Chart<?> chart) {
    }

    @Override
    protected ChartsData refreshInBackground(ChartsHost chartsHost, DBHandler db, GBDevice device) {
        return null;
    }

    @Override
    protected void renderCharts() {
    }

    @Override
    protected void updateChartsnUIThread(ChartsData chartsData) {
    }

    private void showSamples(final StageSamples samples) {
        final HeartRateUtils heartRateUtils = HeartRateUtils.getInstance();
        final ChartSpec spec = stagesSpec(samples, 0, false, heartRateUtils.getMinHeartRate(), heartRateUtils.getMaxHeartRate());
        mChart.setSelectionContent(stagesSelection(samples));
        mChart.setSpec(spec);

        final List<ChartSeries> legend = new ArrayList<>();
        if (!spec.isEmpty()) {
            legend.add(ChartLegendView.squareItem(akActivity.label, akActivity.color));
            if (spec.getEndYAxis() != null) {
                legend.add(ChartLegendView.lineItem(HEARTRATE_LABEL, HEARTRATE_COLOR));
            }
        }
        mLegend.setSeries(legend.size() > 1 ? legend : Collections.emptyList());
    }

    public class RefreshTask extends DBAccess {
        private StageSamples samples;

        public RefreshTask(String task, Context context) {
            super(task, context, false);
        }

        @Override
        protected void doInBackground(DBHandler handler) {
            final List<? extends ActivitySample> activitySamples = getAllSamples(handler, gbDevice, startTime, endTime);
            final List<? extends ActivitySample> highResSamples = getAllSamplesHighRes(handler, gbDevice, startTime, endTime);
            samples = stageSamples(gbDevice, activitySamples, highResSamples != null ? highResSamples : activitySamples);

            if (summary != null) {
                final ActivityTrackProvider activityTrackProvider = gbDevice.getDeviceCoordinator().getActivityTrackProvider(gbDevice, getContext());
                if (activityTrackProvider != null) {
                    final ActivityTrack activityTrack = activityTrackProvider.getActivityTrack(summary);
                    if (activityTrack != null) {
                        final List<ActivityPoint> activityPoints = activityTrack.getAllPoints();
                        if (activityPoints != null && !activityPoints.isEmpty()) {
                            samples = withTrackHeartRate(samples, activityPoints);
                        }
                    }
                }
            }
        }

        @Override
        protected void onPostExecute(Object o) {
            super.onPostExecute(o);
            if (getTaskError() != null || mChart == null || samples == null) {
                return;
            }
            showSamples(samples);
        }

        private StageSamples withTrackHeartRate(final StageSamples samples, final List<ActivityPoint> activityPoints) {
            final long[] hrSeconds = new long[activityPoints.size()];
            final int[] heartRates = new int[activityPoints.size()];
            final HeartRateUtils heartRateUtils = HeartRateUtils.getInstance();
            for (int i = 0; i < activityPoints.size(); i++) {
                final int heartRate = activityPoints.get(i).getHeartRate();
                hrSeconds[i] = activityPoints.get(i).getTime().getTime() / 1000L;
                if (heartRateUtils.isValidHeartRateValue(heartRate)) {
                    heartRates[i] = heartRate;
                }
            }
            return samples.withHeartRate(hrSeconds, heartRates);
        }
    }
}
