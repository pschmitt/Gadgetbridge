/*  Copyright (C) 2026

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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.Chart;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.GaugeDrawer;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.HrvSummarySample;

public class TrainingReadinessFragment extends AbstractChartFragment<TrainingReadinessFragment.TrainingReadinessData> {
    private static final int[] ZONE_COLOR_RES = TrainingReadinessReport.ZONE_COLOR_RES;
    private static final int[] ZONE_NAME_RES = TrainingReadinessReport.ZONE_NAME_RES;
    private static final int[] ZONE_DESC_RES = TrainingReadinessReport.ZONE_DESC_RES;
    private static final float[] ZONE_SEGMENTS = TrainingReadinessReport.ZONE_SEGMENTS;

    private final GaugeDrawer gaugeDrawer = new GaugeDrawer();

    private TextView dateView;
    private ImageView gaugeBar;
    private TextView gaugeValue;
    private TextView gaugeZone;
    private TextView zoneDescription;
    private TextView lastUpdated;
    private LinearLayout statsContainer;

    @Override
    public String getTitle() {
        return getString(R.string.metric_garmin_training_readiness);
    }

    @Override
    protected void init() {
        // Nothing to precompute - colors are resolved lazily via zoneColors().
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_training_readiness, container, false);

        rootView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            getChartsHost().enableSwipeRefresh(scrollY == 0);
        });

        dateView = rootView.findViewById(R.id.training_readiness_date_view);
        gaugeBar = rootView.findViewById(R.id.training_readiness_gauge_bar);
        gaugeValue = rootView.findViewById(R.id.training_readiness_gauge_value);
        gaugeZone = rootView.findViewById(R.id.training_readiness_gauge_zone);
        zoneDescription = rootView.findViewById(R.id.training_readiness_zone_description);
        lastUpdated = rootView.findViewById(R.id.training_readiness_last_updated);
        statsContainer = rootView.findViewById(R.id.training_readiness_stats_container);

        refresh();

        return rootView;
    }

    @Override
    protected TrainingReadinessData refreshInBackground(final ChartsHost chartsHost, final DBHandler db, final GBDevice device) {
        final TrainingReadinessReport report = TrainingReadinessReport.load(db, device, getTSEnd() * 1000L);
        return new TrainingReadinessData(report.getScore(), report.getTimestamp(), report.getHrvStatus(), report.getHrvWeeklyAverage(),
                report.getAcuteLoad(), report.getChronicLoad(), report.getSleepScore(), report.getRecoveryMinutes(),
                report.getSleepHistoryAvg(), report.getStressHistoryAvg(), report.getStressRanges());
    }

    @Override
    protected void updateChartsnUIThread(final TrainingReadinessData data) {
        dateView.setText(new SimpleDateFormat("E, MMM dd", Locale.getDefault()).format(getEndDate()));

        final int[] zoneColors = zoneColors();
        if (data.score != null) {
            final int zoneIndex = zoneIndexForScore(data.score);

            gaugeDrawer.drawSegmentedGauge(gaugeBar, zoneColors, ZONE_SEGMENTS, data.score / 100f, false, true);
            gaugeValue.setText(String.valueOf(data.score));
            gaugeZone.setText(getString(ZONE_NAME_RES[zoneIndex]));
            gaugeZone.setTextColor(zoneColors[zoneIndex]);
            zoneDescription.setText(getString(ZONE_DESC_RES[zoneIndex]));
            lastUpdated.setText(data.timestamp != null
                    ? getString(R.string.training_readiness_last_updated, new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(data.timestamp)))
                    : "");
        } else {
            gaugeDrawer.drawSegmentedGauge(gaugeBar, zoneColors, ZONE_SEGMENTS, -1f, false, true);
            gaugeValue.setText(getString(R.string.stats_empty_value));
            gaugeZone.setText("");
            zoneDescription.setText("");
            lastUpdated.setText("");
        }

        final List<StatTileData> stats = new ArrayList<>();
        stats.add(buildHrvTile(data.hrvStatus, data.hrvWeeklyAverage));
        stats.add(buildAcuteLoadTile(data.acuteLoad, data.chronicLoad));
        stats.add(buildSleepScoreTile(data.sleepScore, getString(R.string.menuitem_sleep)));
        stats.add(buildRecoveryTile(data.recoveryMinutes));
        stats.add(buildSleepScoreTile(data.sleepHistoryAvg, getString(R.string.training_readiness_factor_sleep_3_nights)));
        stats.add(buildStressTile(data.stressHistoryAvg, data.stressRanges));

        statsContainer.removeAllViews();
        StatTileGridUtilKt.addStatTileGrid(statsContainer, requireContext(), stats, 0);
    }

    private StatTileData buildHrvTile(final HrvSummarySample.Status status, @Nullable final Integer weeklyAverageMs) {
        final String value = weeklyAverageMs != null
                ? getString(R.string.hrv_status_unit, weeklyAverageMs)
                : getString(R.string.stats_empty_value);
        final String label = getString(R.string.pref_header_hrv_status);

        switch (status) {
            case BALANCED:
                return indicatorTile(value, label, getString(R.string.hrv_status_balanced), R.color.hrv_status_balanced);
            case UNBALANCED:
                return indicatorTile(value, label, getString(R.string.hrv_status_unbalanced), R.color.hrv_status_unbalanced);
            case LOW:
                return indicatorTile(value, label, getString(R.string.hrv_status_low), R.color.hrv_status_low);
            case POOR:
                return indicatorTile(value, label, getString(R.string.hrv_status_poor), R.color.hrv_status_poor);
            case NONE:
            default:
                return new StatTileData(value, label);
        }
    }

    private StatTileData buildAcuteLoadTile(@Nullable final Integer acuteLoad, @Nullable final Integer chronicLoad) {
        final String label = getString(R.string.training_acute_load);
        if (acuteLoad == null) {
            return new StatTileData(getString(R.string.stats_empty_value), label);
        }

        final String value = String.valueOf(acuteLoad);

        if (chronicLoad == null || chronicLoad <= 0) {
            return indicatorTile(value, label, getString(R.string.none), null);
        }

        final float ratio = (float) acuteLoad / chronicLoad;
        if (ratio < LoadFragment.OPTIMAL_LOAD_RATIO_LOWER) {
            return indicatorTile(value, label, getString(R.string.low), R.color.training_load_low);
        } else if (ratio < LoadFragment.OPTIMAL_LOAD_RATIO_UPPER) {
            return indicatorTile(value, label, getString(R.string.optimal), R.color.training_load_optimal);
        } else if (ratio < 2f) {
            return indicatorTile(value, label, getString(R.string.high), R.color.training_load_high);
        } else {
            return indicatorTile(value, label, getString(R.string.very_high), R.color.training_load_high);
        }
    }

    private StatTileData buildSleepScoreTile(@Nullable final Integer score, final String label) {
        if (score == null) {
            return new StatTileData(getString(R.string.stats_empty_value), label);
        }

        final String value = String.valueOf(score);
        if (score >= 90) {
            return indicatorTile(value, label, getString(R.string.sleep_score_excellent), R.color.training_readiness_high);
        } else if (score >= 80) {
            return indicatorTile(value, label, getString(R.string.sleep_score_good), R.color.training_readiness_moderate);
        } else if (score >= 60) {
            return indicatorTile(value, label, getString(R.string.sleep_score_fair), R.color.training_readiness_low);
        } else {
            return indicatorTile(value, label, getString(R.string.sleep_score_poor), R.color.training_readiness_poor);
        }
    }

    private StatTileData buildRecoveryTile(@Nullable final Integer minutesOrNull) {
        // No sample yet is treated the same as "0 minutes" - a device that hasn't recorded a
        // recovery time is fully recovered by default, not in an unknown state.
        final int minutes = (minutesOrNull != null) ? minutesOrNull : 0;
        final String value = (minutes / 60) + getString(R.string.unit_hours);
        final String label = getString(R.string.recoveryTime);

        if (minutes == 0) {
            return indicatorTile(value, label, getString(R.string.training_readiness_recovery_fully_recovered), R.color.training_load_optimal);
        }
        return indicatorTile(value, label, getString(R.string.training_readiness_recovery_recovering), null);
    }

    private StatTileData buildStressTile(@Nullable final Integer avgStress, final int[] stressRanges) {
        final String label = getString(R.string.training_readiness_factor_stress_3_days);
        if (avgStress == null) {
            return new StatTileData(getString(R.string.stats_empty_value), label);
        }

        final StressFragment.StressType stressType = StressFragment.StressType.fromStress(avgStress, stressRanges);
        return new StatTileData(
                String.valueOf(avgStress),
                label,
                null,
                stressType.getLabel(requireContext()),
                null,
                stressType.getColor(requireContext())
        );
    }

    private StatTileData indicatorTile(final String value, final String label, final String indicatorText, @Nullable final Integer colorRes) {
        final int color = colorRes != null
                ? ContextCompat.getColor(requireContext(), colorRes)
                : GBApplication.getSecondaryTextColor(requireContext());
        return new StatTileData(value, label, null, indicatorText, null, color);
    }

    private int[] zoneColors() {
        final int[] colors = new int[ZONE_COLOR_RES.length];
        for (int i = 0; i < ZONE_COLOR_RES.length; i++) {
            colors[i] = ContextCompat.getColor(requireContext(), ZONE_COLOR_RES[i]);
        }
        return colors;
    }

    private static int zoneIndexForScore(final int score) {
        return TrainingReadinessReport.zoneIndexForScore(score);
    }

    @Override
    protected void renderCharts() {
        // No MPAndroidChart view on this fragment - the gauge is drawn directly in updateChartsnUIThread.
    }

    @Override
    protected void setupLegend(final Chart<?> chart) {
    }

    protected static class TrainingReadinessData extends ChartsData {
        @Nullable
        final Integer score;
        @Nullable
        final Long timestamp;
        final HrvSummarySample.Status hrvStatus;
        @Nullable
        final Integer hrvWeeklyAverage;
        @Nullable
        final Integer acuteLoad;
        @Nullable
        final Integer chronicLoad;
        @Nullable
        final Integer sleepScore;
        @Nullable
        final Integer recoveryMinutes;
        @Nullable
        final Integer sleepHistoryAvg;
        @Nullable
        final Integer stressHistoryAvg;
        final int[] stressRanges;

        TrainingReadinessData(@Nullable final Integer score, @Nullable final Long timestamp,
                              final HrvSummarySample.Status hrvStatus, @Nullable final Integer hrvWeeklyAverage,
                              @Nullable final Integer acuteLoad, @Nullable final Integer chronicLoad,
                              @Nullable final Integer sleepScore, @Nullable final Integer recoveryMinutes,
                              @Nullable final Integer sleepHistoryAvg, @Nullable final Integer stressHistoryAvg,
                              final int[] stressRanges) {
            this.score = score;
            this.timestamp = timestamp;
            this.hrvStatus = hrvStatus;
            this.hrvWeeklyAverage = hrvWeeklyAverage;
            this.acuteLoad = acuteLoad;
            this.chronicLoad = chronicLoad;
            this.sleepScore = sleepScore;
            this.recoveryMinutes = recoveryMinutes;
            this.sleepHistoryAvg = sleepHistoryAvg;
            this.stressHistoryAvg = stressHistoryAvg;
            this.stressRanges = stressRanges;
        }
    }

    @Nullable
    @Override
    public ChartDataRange getAvailableDataRange(final GBDevice device, final DBHandler db) {
        return ChartDataRange.union(
                ChartDataRange.ofSamples(device.getDeviceCoordinator().getHrvSummarySampleProvider(device, db.getDaoSession())),
                ChartDataRange.ofSamples(device.getDeviceCoordinator().getStressSampleProvider(device, db.getDaoSession()))
        );
    }
}
