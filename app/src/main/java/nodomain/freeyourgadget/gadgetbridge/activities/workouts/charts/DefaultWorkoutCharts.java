/*  Copyright (C) 2025-2026 José Rebelo, a0z, Me7c7, punchdeerflyscorpion, Thomas Kuehne

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
package nodomain.freeyourgadget.gadgetbridge.activities.workouts.charts;

import static nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutValueFormatter.getUnitString;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_BPM;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_BREATHS_PER_MIN;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_MILLISECONDS;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_MM;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_PERCENTAGE;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_SECONDS_PER_100_METERS;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_SECONDS_PER_500_METERS;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_SECONDS_PER_KM;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_SPM;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_WATT;

import android.content.Context;

import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.data.Entry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import kotlin.jvm.functions.Function1;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.DecimalValueFormatter;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.HeartRateZoneChartUtils;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.SpeedYLabelFormatter;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.TimestampTranslation;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityPoint;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser;
import nodomain.freeyourgadget.gadgetbridge.model.GPSCoordinate;
import nodomain.freeyourgadget.gadgetbridge.model.heartratezones.HeartRateZones;
import nodomain.freeyourgadget.gadgetbridge.model.heartratezones.HeartRateZonesResolver;
import nodomain.freeyourgadget.gadgetbridge.model.workout.WorkoutChart;
import nodomain.freeyourgadget.gadgetbridge.util.Accumulator;

public class DefaultWorkoutCharts {
    public static List<WorkoutChart> buildDefaultCharts(final Context context,
                                                        final List<? extends ActivityPoint> activityPoints,
                                                        final ActivityKind activityKind) {
        return buildDefaultCharts(context, activityPoints, activityKind, null, null);
    }

    public static List<WorkoutChart> buildDefaultCharts(final Context context,
                                                        final List<? extends ActivityPoint> activityPoints,
                                                        final ActivityKind activityKind,
                                                        final ActivitySummaryData summary) {
        return buildDefaultCharts(context, activityPoints, activityKind, summary, null);
    }

    /**
     * @param device the workout's device when the caller has one, so heart-rate zones configured on
     *               it can be used. Parsers that build charts while importing a file have no device
     *               in hand and pass null, which falls back to the zones in the summary or to the
     *               age-based default.
     */
    public static List<WorkoutChart> buildDefaultCharts(final Context context,
                                                        final List<? extends ActivityPoint> activityPoints,
                                                        final ActivityKind activityKind,
                                                        final ActivitySummaryData summary,
                                                        final GBDevice device) {
        final ActivityKind.CycleUnit cycleUnit = ActivityKind.getCycleUnit(activityKind);
        final List<WorkoutChart> charts = new LinkedList<>();
        final TimestampTranslation tsTranslation = new TimestampTranslation();
        final int initialCapacity = activityPoints.size();
        final List<Entry> heartRateDataPoints = new ArrayList<>(initialCapacity);
        final List<Entry> speedDataPoints = new ArrayList<>(initialCapacity);
        final List<Entry> cadenceDataPoints = new ArrayList<>(initialCapacity);
        final List<Entry> elevationDataPoints = new ArrayList<>(initialCapacity);
        final List<Entry> powerDataPoints = new ArrayList<>(initialCapacity);
        final List<Entry> respiratoryRatePoints = new ArrayList<>(initialCapacity);
        final List<Entry> temperatureDataPoints = new ArrayList<>(initialCapacity);
        final List<Entry> depthDataPoints = new ArrayList<>(initialCapacity);
        final List<Entry> distancePoints = new ArrayList<>(initialCapacity);
        final List<Entry> staminaPoints = new ArrayList<>(initialCapacity);
        final List<Entry> bodyEnergyPoints = new ArrayList<>(initialCapacity);
        final List<Entry> stepLengthPoints = new ArrayList<>(initialCapacity);
        final List<Entry> n2LoadPoints = new ArrayList<>(initialCapacity);
        final List<Entry> cnsToxicityPoints = new ArrayList<>(initialCapacity);
        final List<Entry> verticalOscillationPoints = new ArrayList<>(initialCapacity);
        final List<Entry> stanceTimePercentPoints = new ArrayList<>(initialCapacity);
        final List<Entry> stanceTimePoints = new ArrayList<>(initialCapacity);
        final List<Entry> verticalRatioPoints = new ArrayList<>(initialCapacity);
        final List<Entry> stanceTimeBalancePoints = new ArrayList<>(initialCapacity);
        final List<Entry> performanceConditionPoints = new ArrayList<>(initialCapacity);

        // some activities / devices provide all points with zero values
        boolean hasSpeedValues = false;
        boolean hasCadenceValues = false;
        boolean hasElevationValues = false;
        boolean hasPowerValues = false;
        boolean hasRespiratoryRateValues = false;
        boolean hasTemperatureValues = false;
        boolean hasDepthValues = false;
        boolean hasDistanceValues = false;
        boolean hasBodyEnergyValues = false;
        boolean hasStaminaValues = false;
        boolean hasStepLengthValues = false;
        boolean hasN2LoadValues = false;
        boolean hasCnsToxicityValues = false;
        boolean hasVerticalOscillationValues = false;
        boolean hasStanceTimePercentValues = false;
        boolean hasStanceTimeValues = false;
        boolean hasVerticalRatioValues = false;
        boolean hasStanceTimeBalanceValues = false;
        boolean hasPerformanceConditionValues = false;

        final Accumulator cadenceAccumulator = new Accumulator();
        final Accumulator temperatureAccumulator = new Accumulator();

        // Distance is not carried by every source (Xiaomi/Bangle GPS tracks expose position but no
        // distance field). When no point provides one, derive a running distance from the GPS fixes
        // so the distance chart still renders. Null for sources that already carry native distance.
        final double[] derivedDistances = deriveCumulativeDistances(activityPoints);

        for (int i = 0; i <= activityPoints.size() - 1; i++) {
            final ActivityPoint point = activityPoints.get(i);
            final long tsShorten = tsTranslation.shorten((int) point.getTime().getTime());

            // HR
            final int heartRate = point.getHeartRate();
            if (heartRate > 0) {
                heartRateDataPoints.add(new Entry<>(tsShorten, heartRate, null, null));
            }

            // Elevation
            final double elevation = point.getAltitude();
            if (elevation > GPSCoordinate.UNKNOWN_ALTITUDE) {
                elevationDataPoints.add(new Entry<>(tsShorten, (float) elevation, null, null));
                hasElevationValues = hasElevationValues || (elevation != 0.0);
            }

            // Speed, cadence and respiratory rate are instantaneous rates whose 0 means "paused /
            // not reported" rather than a measurement, so those zeros are left out of the series and
            // it gaps out instead of cliffing to the axis, same as HR above. Power keeps its zeros:
            // a power meter reads a genuine 0 W whenever the rider coasts.

            // Speed
            final float speed = point.getSpeed();
            if (speed > 0.0f) {
                speedDataPoints.add(new Entry<>(tsShorten, speed, null, null));
                hasSpeedValues = true;
            }

            // Cadence
            final float cadence = point.getCadence();
            // The average feeds the cadence chart's y-axis maximum, so it counts the zeros too.
            if (cadence >= 0.0f) {
                cadenceAccumulator.add(cadence);
            }
            if (cadence > 0.0f) {
                cadenceDataPoints.add(new Entry<>(tsShorten, cadence, null, null));
                hasCadenceValues = true;
            }

            final float power = point.getPower();
            if (power >= 0.0f) {
                powerDataPoints.add(new Entry<>(tsShorten, power, null, null));
                hasPowerValues = hasPowerValues || (power > 0.0f);
            }

            final float respiratoryRate = point.getRespiratoryRate();
            if (respiratoryRate > 0.0f) {
                respiratoryRatePoints.add(new Entry<>(tsShorten, respiratoryRate, null, null));
                hasRespiratoryRateValues = true;
            }

            // Depth (diving activity)
            final double depth = point.getDepth();
            if (depth >= 0.0) {
                depthDataPoints.add(new Entry<>(tsShorten, (float) -depth, null, null));
                hasDepthValues = hasDepthValues || (depth > 0.0);
            }

            // Temperature
            final double temperature = point.getTemperature();
            if (temperature > -273) {
                temperatureDataPoints.add(new Entry<>(tsShorten, (float) temperature, null, null));
                temperatureAccumulator.add(temperature);
                hasTemperatureValues = hasTemperatureValues || (temperature != 0.0);
            }

            // Distance
            double distance = point.getDistance();
            if (distance < 0.0 && derivedDistances != null) {
                distance = derivedDistances[i];
            }
            if (distance >= 0.0) {
                distancePoints.add(new Entry<>(tsShorten, (float) distance, null, null));
                hasDistanceValues = hasDistanceValues || (distance > 0);
            }

            // Body Energy
            final float bodyEnergy = point.getBodyEnergy();
            if (bodyEnergy >= 0.0f) {
                bodyEnergyPoints.add(new Entry<>(tsShorten, bodyEnergy, null, null));
                hasBodyEnergyValues = hasBodyEnergyValues || (bodyEnergy > 0.0f);
            }

            // Stamina
            final float stamina = point.getStamina();
            if (stamina >= 0.0f) {
                staminaPoints.add(new Entry<>(tsShorten, stamina, null, null));
                hasStaminaValues = hasStaminaValues || (stamina > 0.0f);
            }

            // Step Length
            final int stepLength = point.getStepLength();
            if (stepLength >= 0) {
                stepLengthPoints.add(new Entry<>(tsShorten, stepLength, null, null));
                hasStepLengthValues = hasStepLengthValues || (stepLength > 0);
            }

            // CNS Toxicity
            final float cnsToxicity = point.getCnsToxicity();
            if (cnsToxicity >= 0.0f) {
                cnsToxicityPoints.add(new Entry<>(tsShorten, cnsToxicity, null, null));
                hasCnsToxicityValues = hasCnsToxicityValues || (cnsToxicity > 0.0f);
            }

            // N2 Load
            final float n2Load = point.getN2Load();
            if (n2Load >= 0.0f) {
                n2LoadPoints.add(new Entry<>(tsShorten, n2Load, null, null));
                hasN2LoadValues = hasN2LoadValues || (n2Load > 0.0f);
            }

            // Running dynamics
            final float verticalOscillation = point.getVerticalOscillation();
            if (!Float.isNaN(verticalOscillation)) {
                verticalOscillationPoints.add(new Entry<>(tsShorten, verticalOscillation, null, null));
                hasVerticalOscillationValues = hasVerticalOscillationValues || (verticalOscillation > 0.0f);
            }

            final float stanceTimePercent = point.getStanceTimePercent();
            if (!Float.isNaN(stanceTimePercent)) {
                stanceTimePercentPoints.add(new Entry<>(tsShorten, stanceTimePercent, null, null));
                hasStanceTimePercentValues = hasStanceTimePercentValues || (stanceTimePercent > 0.0f);
            }

            final float stanceTime = point.getStanceTime();
            if (!Float.isNaN(stanceTime)) {
                stanceTimePoints.add(new Entry<>(tsShorten, stanceTime, null, null));
                hasStanceTimeValues = hasStanceTimeValues || (stanceTime > 0.0f);
            }

            final float verticalRatio = point.getVerticalRatio();
            if (!Float.isNaN(verticalRatio)) {
                verticalRatioPoints.add(new Entry<>(tsShorten, verticalRatio, null, null));
                hasVerticalRatioValues = hasVerticalRatioValues || (verticalRatio > 0.0f);
            }

            final float stanceTimeBalance = point.getStanceTimeBalance();
            if (!Float.isNaN(stanceTimeBalance)) {
                stanceTimeBalancePoints.add(new Entry<>(tsShorten, stanceTimeBalance, null, null));
                hasStanceTimeBalanceValues = hasStanceTimeBalanceValues || (stanceTimeBalance > 0.0f);
            }

            final int performanceCondition = point.getPerformanceCondition();
            if (performanceCondition > Integer.MIN_VALUE) {
                // Integer.MIN_VALUE is the no-data sentinel; any other value is a valid
                // reading, including negative ones (performance condition can be < 0).
                performanceConditionPoints.add(new Entry<>(tsShorten, performanceCondition, null, null));
                hasPerformanceConditionValues = true;
            }
        }

        if (!heartRateDataPoints.isEmpty()) {
            charts.add(createHeartRateChart(context, heartRateDataPoints, summary, device));
        }

        if (hasSpeedValues && !speedDataPoints.isEmpty()) {
            charts.add(createSpeedChart(context, activityKind, speedDataPoints));
        }

        if (hasCadenceValues && !cadenceDataPoints.isEmpty()) {
            charts.add(createCadenceChart(context, cycleUnit, cadenceDataPoints, cadenceAccumulator));
        }

        if (hasElevationValues && !elevationDataPoints.isEmpty()) {
            charts.add(createElevationChart(context, elevationDataPoints));
        }

        if (hasPowerValues && !powerDataPoints.isEmpty()) {
            charts.add(createPowerChart(context, powerDataPoints));
        }

        if (hasRespiratoryRateValues && !respiratoryRatePoints.isEmpty()) {
            charts.add(createRespiratoryRateChart(context, respiratoryRatePoints));
        }

        if (hasDepthValues && !depthDataPoints.isEmpty()) {
            charts.add(createDepthChart(context, depthDataPoints));
        }

        if (hasTemperatureValues && !temperatureDataPoints.isEmpty()) {
            charts.add(createTemperatureChart(context, temperatureDataPoints, temperatureAccumulator));
        }

        if (hasDistanceValues && !distancePoints.isEmpty()) {
            charts.add(createDistanceChart(context, distancePoints));
        }

        if (hasBodyEnergyValues && !bodyEnergyPoints.isEmpty()) {
            charts.add(createBodyEnergyChart(context, bodyEnergyPoints));
        }

        if (hasStaminaValues && !staminaPoints.isEmpty()) {
            charts.add(createStaminaChart(context, staminaPoints));
        }

        if (hasStepLengthValues && !stepLengthPoints.isEmpty()) {
            charts.add(createStepLengthChart(context, stepLengthPoints));
        }

        if (hasCnsToxicityValues && !cnsToxicityPoints.isEmpty()) {
            charts.add(createCnsToxicityChart(context, cnsToxicityPoints));
        }

        if (hasN2LoadValues && !n2LoadPoints.isEmpty()) {
            charts.add(createN2LoadChart(context, n2LoadPoints));
        }

        if (hasVerticalOscillationValues && !verticalOscillationPoints.isEmpty()) {
            charts.add(createVerticalOscillationChart(context, verticalOscillationPoints));
        }

        if (hasStanceTimePercentValues && !stanceTimePercentPoints.isEmpty()) {
            charts.add(createStanceTimePercentChart(context, stanceTimePercentPoints));
        }

        if (hasStanceTimeValues && !stanceTimePoints.isEmpty()) {
            charts.add(createStanceTimeChart(context, stanceTimePoints));
        }

        if (hasVerticalRatioValues && !verticalRatioPoints.isEmpty()) {
            charts.add(createVerticalRatioChart(context, verticalRatioPoints));
        }

        if (hasStanceTimeBalanceValues && !stanceTimeBalancePoints.isEmpty()) {
            charts.add(createStanceTimeBalanceChart(context, stanceTimeBalancePoints));
        }

        if (hasPerformanceConditionValues && !performanceConditionPoints.isEmpty()) {
            charts.add(createPerformanceConditionChart(context, performanceConditionPoints));
        }

        return charts;
    }

    /**
     * Derive a per-point cumulative distance (metres) when the source carries no distance of its own,
     * so the distance chart still renders. Returns {@code null} when any point already provides a
     * distance (honour the device value) or when there is nothing to derive from, so the caller falls
     * back to {@link ActivityPoint#getDistance()}.
     * <p>
     * Two sources, in order of trust: GPS fixes (outdoor tracks that expose position but no distance,
     * e.g. Xiaomi/Bangle), else the speed stream integrated over time (indoor treadmill/bike workouts
     * that carry machine-reported speed but neither location nor distance). Entries with no usable
     * value are {@link Double#NaN} so they contribute no distance and inject no spurious hop.
     */
    static double[] deriveCumulativeDistances(final List<? extends ActivityPoint> points) {
        for (final ActivityPoint p : points) {
            if (p.getDistance() >= 0.0) {
                return null; // source already carries distance — do not override it
            }
        }
        final double[] fromGps = deriveFromGps(points);
        if (fromGps != null) {
            return fromGps;
        }
        return deriveFromSpeed(points);
    }

    /**
     * Cumulative distance from consecutive GPS fixes via the pure-Java
     * {@link GPSCoordinate#distanceHaversine} (off {@code android.location.Location}, unit-testable).
     * Null Island (0,0) no-fix placeholders and locationless points are skipped ({@code NaN}), so a
     * momentary dropout never injects a jump. {@code null} if there is no usable fix.
     */
    private static double[] deriveFromGps(final List<? extends ActivityPoint> points) {
        final double[] distances = new double[points.size()];
        double cumulativeMeters = 0.0;
        GPSCoordinate previous = null;
        boolean anyFix = false;
        for (int i = 0; i < points.size(); i++) {
            final GPSCoordinate location = points.get(i).getLocation();
            if (location == null || isNullIsland(location)) {
                distances[i] = Double.NaN;
                continue;
            }
            if (previous != null) {
                cumulativeMeters += GPSCoordinate.distanceHaversine(previous, location);
            }
            distances[i] = cumulativeMeters;
            previous = location;
            anyFix = true;
        }
        return anyFix ? distances : null;
    }

    /**
     * Cumulative distance from the speed stream, integrated over time (trapezoid rule between
     * consecutive samples). For indoor workouts whose gadget was connected to the machine and thus
     * report speed but no position. Integrating a measured speed is well-behaved (a smoothing, unlike
     * differentiating position into speed); it never fabricates from nothing — no speed, no distance.
     * Points without a speed value are {@code NaN}. {@code null} if no point carries a speed.
     */
    private static double[] deriveFromSpeed(final List<? extends ActivityPoint> points) {
        final double[] distances = new double[points.size()];
        double cumulativeMeters = 0.0;
        float previousSpeed = -1.0f; // m/s; <0 = no previous sample to bridge from
        long previousTsMillis = -1L;
        boolean anySpeed = false;
        for (int i = 0; i < points.size(); i++) {
            final ActivityPoint point = points.get(i);
            final float speed = point.getSpeed(); // m/s, <0 when absent
            if (speed < 0.0f) {
                distances[i] = Double.NaN;
                continue;
            }
            final long tsMillis = point.getTime().getTime();
            if (previousSpeed >= 0.0f && previousTsMillis >= 0L) {
                final double dtSeconds = (tsMillis - previousTsMillis) / 1000.0;
                if (dtSeconds > 0.0) {
                    cumulativeMeters += 0.5 * (previousSpeed + speed) * dtSeconds;
                }
            }
            distances[i] = cumulativeMeters;
            previousSpeed = speed;
            previousTsMillis = tsMillis;
            anySpeed = anySpeed || speed > 0.0f;
        }
        return anySpeed ? distances : null;
    }

    private static boolean isNullIsland(final GPSCoordinate location) {
        return location.getLatitude() == 0.0 && location.getLongitude() == 0.0;
    }

    private static WorkoutChart createElevationChart(final Context context,
                                                     final List<Entry> elevationDataPoints) {
        final WorkoutChartUnits units = chartUnits();
        final WorkoutChartUnits.Quantity quantity = WorkoutChartUnits.Quantity.ELEVATION;
        final String unitString = getUnitString(context, units.token(quantity));
        // Elevation is safe (and often desirable) to interpolate across a gap: altitude
        // typically doesn't jump discontinuously, even across a paused or dropped-out stretch.
        final WorkoutChart.Series lineData = series(convertPoints(elevationDataPoints, units, quantity), ContextCompat.getColor(context, R.color.chart_line_elevation));
        return new WorkoutChart(
                "elevation",
                context.getString(R.string.Elevation),
                ActivitySummaryEntries.GROUP_ELEVATION,
                lineData,
                new DecimalValueFormatter(units.decimals(quantity))::format,
                unitString
        );
    }

    private static WorkoutChart createHeartRateChart(final Context context,
                                                     final List<Entry> heartRateDataPoints,
                                                     final ActivitySummaryData summary,
                                                     final GBDevice device) {
        final Function1<Double, String> integerFormatter = value -> String.valueOf(value.intValue());

        final HeartRateZones zones = HeartRateZonesResolver.resolve(summary, device, new ActivityUser());
        // Workout entries use x in milliseconds (tsTranslation.shorten on point.getTime().getTime()),
        // so gap and unit conversions are millisecond-based.
        final HeartRateZoneChartUtils.ZoneAnalysis analysis = HeartRateZoneChartUtils.analyze(
                heartRateDataPoints, zones, gapThreshold(heartRateDataPoints), 1000f);
        final WorkoutChart.Series lineData = gappedSeries(heartRateDataPoints, ContextCompat.getColor(context, R.color.chart_line_heart_rate));

        final WorkoutChart chart = new WorkoutChart(
                "heart_rate",
                context.getString(R.string.heart_rate),
                ActivitySummaryEntries.GROUP_HEART_RATE,
                lineData,
                integerFormatter,
                getUnitString(context, UNIT_BPM)
        );
        chart.setSecondsInZone(analysis.secondsInZone);
        chart.setZoneThresholds(zones);
        return chart;
    }

    private static WorkoutChart createSpeedChart(final Context context,
                                                 final ActivityKind activityKind,
                                                 final List<Entry> speedDataPoints) {
        final WorkoutChartUnits units = chartUnits(activityKind);
        if (ActivityKind.isRowingActivity(activityKind)) {
            final String unitString = getUnitString(context, units.token(WorkoutChartUnits.Quantity.PACE_500M));
            final WorkoutChart.Series lineData = gappedSeries(speedDataPoints, ContextCompat.getColor(context, R.color.chart_line_speed));
            return new WorkoutChart(
                    "pace",
                    context.getString(R.string.Pace),
                    ActivitySummaryEntries.GROUP_SPEED,
                    lineData,
                    new SpeedYLabelFormatter(UNIT_SECONDS_PER_500_METERS)::format,
                    unitString
            );
        } else if (ActivityKind.isSwimActivity(activityKind)) {
            final String unitString = getUnitString(context, units.token(WorkoutChartUnits.Quantity.PACE_SWIM));
            final WorkoutChart.Series lineData = gappedSeries(speedDataPoints, ContextCompat.getColor(context, R.color.chart_line_speed));
            return new WorkoutChart(
                    "pace",
                    context.getString(R.string.Pace),
                    ActivitySummaryEntries.GROUP_SPEED,
                    lineData,
                    new SpeedYLabelFormatter(UNIT_SECONDS_PER_100_METERS)::format,
                    unitString
            );
        } else if (ActivityKind.isPaceActivity(activityKind)) {
            final String unitString = getUnitString(context, units.token(WorkoutChartUnits.Quantity.PACE));
            final WorkoutChart.Series lineData = gappedSeries(speedDataPoints, ContextCompat.getColor(context, R.color.chart_line_speed));
            return new WorkoutChart(
                    "pace",
                    context.getString(R.string.Pace),
                    ActivitySummaryEntries.GROUP_SPEED,
                    lineData,
                    new SpeedYLabelFormatter(UNIT_SECONDS_PER_KM)::format,
                    unitString
            );
        } else {
            final WorkoutChartUnits.Quantity quantity = WorkoutChartUnits.Quantity.SPEED;
            final String unitString = getUnitString(context, units.token(quantity));
            final WorkoutChart.Series lineData = gappedSeries(convertPoints(speedDataPoints, units, quantity), ContextCompat.getColor(context, R.color.chart_line_speed));
            return new WorkoutChart(
                    "speed",
                    context.getString(R.string.Speed),
                    ActivitySummaryEntries.GROUP_SPEED,
                    lineData,
                    new DecimalValueFormatter(units.decimals(quantity))::format,
                    unitString
            );
        }
    }

    private static WorkoutChart createCadenceChart(final Context context,
                                                   final ActivityKind.CycleUnit cycleUnit,
                                                   final List<Entry> cadenceDataPoints,
                                                   final Accumulator cadenceAccumulator) {
        final String cadenceUnit = getCadenceUnit(cycleUnit);
        final Function1<Double, String> integerFormatter = value -> String.valueOf(value.intValue());
        float xAxisMaximum = Math.max(
                (float) (cadenceAccumulator.getMax() + 30),
                (float) cadenceAccumulator.getAverage() * 2
        );

        final int color = ContextCompat.getColor(context, R.color.chart_cadence_circle);
        // Cadence sampled every couple of seconds or faster merges into a band as dots, so it is
        // drawn as a line. Sparser series stay as dots.
        if (isDenseSeries(cadenceDataPoints)) {
            final WorkoutChart.Series lineData = gappedSeries(cadenceDataPoints, color);
            return new WorkoutChart(
                    "cadence",
                    context.getString(R.string.workout_cadence),
                    ActivitySummaryEntries.GROUP_CADENCE,
                    lineData,
                    integerFormatter,
                    getUnitString(context, cadenceUnit),
                    0.0,
                    (double) xAxisMaximum
            );
        }

        final WorkoutChart.Series dataset = new WorkoutChart.Series(toPoints(cadenceDataPoints), color, null, true);
        return new WorkoutChart(
                "cadence",
                context.getString(R.string.workout_cadence),
                ActivitySummaryEntries.GROUP_CADENCE,
                dataset,
                integerFormatter,
                getUnitString(context, cadenceUnit),
                0.0,
                (double) xAxisMaximum
        );
    }

    private static WorkoutChart createPowerChart(final Context context,
                                                 final List<Entry> powerDataPoints) {
        final WorkoutChart.Series lineData = gappedSeries(powerDataPoints, ContextCompat.getColor(context, R.color.chart_line_power));
        final Function1<Double, String> integerFormatter = value -> String.valueOf(value.intValue());
        return new WorkoutChart("power", context.getString(R.string.workout_power), ActivitySummaryEntries.GROUP_POWER, lineData, integerFormatter, getUnitString(context, UNIT_WATT));
    }

    private static WorkoutChart createRespiratoryRateChart(final Context context,
                                                           final List<Entry> powerDataPoints) {
        final WorkoutChart.Series lineData = gappedSeries(powerDataPoints, ContextCompat.getColor(context, R.color.respiratory_rate_color));
        final Function1<Double, String> integerFormatter = value -> String.valueOf(value.intValue());
        return new WorkoutChart(
                "respiratory_rate",
                context.getString(R.string.respiratoryrate),
                ActivitySummaryEntries.GROUP_RESPIRATORY_RATE,
                lineData,
                integerFormatter,
                getUnitString(context, UNIT_BREATHS_PER_MIN)
        );
    }

    private static WorkoutChart createDepthChart(final Context context,
                                                 final List<Entry> depthDataPoints) {
        final WorkoutChartUnits units = chartUnits();
        final WorkoutChartUnits.Quantity quantity = WorkoutChartUnits.Quantity.DEPTH;
        final String unitString = getUnitString(context, units.token(quantity));
        // Depth changes continuously during a dive, so it's safe to interpolate across a gap
        // rather than break the segment.
        final WorkoutChart.Series lineData = series(convertPoints(depthDataPoints, units, quantity), ContextCompat.getColor(context, R.color.chart_line_depth));
        return new WorkoutChart(
                "diving_depth",
                context.getString(R.string.diving_depth),
                ActivitySummaryEntries.GROUP_DIVING,
                lineData,
                new DecimalValueFormatter(units.decimals(quantity))::format,
                unitString
        );
    }

    private static WorkoutChart createTemperatureChart(final Context context,
                                                       final List<Entry> temperatureDataPoints,
                                                       final Accumulator temperatureAccumulator) {
        final WorkoutChartUnits units = chartUnits();
        final WorkoutChartUnits.Quantity quantity = WorkoutChartUnits.Quantity.TEMPERATURE;
        final String unitString = getUnitString(context, units.token(quantity));
        // Ambient temperature drifts slowly and gradually, so it's safe to interpolate across
        // a gap rather than break the segment.
        final WorkoutChart.Series lineData = series(convertPoints(temperatureDataPoints, units, quantity), ContextCompat.getColor(context, R.color.chart_line_heart_rate));
        // axis bounds mirror the plotted (already converted) values
        final float axisMin = (float) units.convert(quantity, 0);
        final float axisMax = (float) units.convert(quantity, Math.max(35, temperatureAccumulator.getMax() + 5));
        return new WorkoutChart(
                "temperature",
                context.getString(R.string.menuitem_temperature),
                ActivitySummaryEntries.GROUP_TEMPERATURE,
                lineData,
                new DecimalValueFormatter(units.decimals(quantity))::format,
                unitString,
                (double) axisMin,
                (double) axisMax
        );
    }

    private static WorkoutChart createDistanceChart(final Context context,
                                                    final List<Entry> distancePoints) {
        final WorkoutChartUnits units = chartUnits();
        final WorkoutChartUnits.Quantity quantity = WorkoutChartUnits.Quantity.DISTANCE;
        final String unitString = getUnitString(context, units.token(quantity));
        final WorkoutChart.Series lineData = gappedSeries(convertPoints(distancePoints, units, quantity), ContextCompat.getColor(context, R.color.chart_line_distance));
        return new WorkoutChart(
                "chart_distance",
                context.getString(R.string.distance),
                ActivitySummaryEntries.GROUP_DISTANCE,
                lineData,
                new DecimalValueFormatter(units.decimals(quantity))::format,
                unitString
        );
    }

    private static WorkoutChart createBodyEnergyChart(final Context context,
                                                      final List<Entry> bodyEnergyPoints) {
        // Body energy is a slowly-changing reserve metric, so it's safe to interpolate across
        // a gap rather than break the segment.
        final WorkoutChart.Series lineData = series(bodyEnergyPoints, ContextCompat.getColor(context, R.color.chart_line_body_energy));
        final Function1<Double, String> valueFormatter = value -> String.valueOf(value.intValue());
        return new WorkoutChart(
                "chart_body_energy",
                context.getString(R.string.body_energy),
                ActivitySummaryEntries.GROUP_TRAINING_EFFECT,
                lineData,
                valueFormatter,
                getUnitString(context, UNIT_PERCENTAGE)
        );
    }

    private static WorkoutChart createStaminaChart(final Context context,
                                                   final List<Entry> staminaPoints) {
        // Stamina is a slowly draining/recovering reserve, so it's safe to interpolate across
        // a gap rather than break the segment.
        final WorkoutChart.Series lineData = series(staminaPoints, ContextCompat.getColor(context, R.color.chart_line_stamina));
        final Function1<Double, String> valueFormatter = value -> String.valueOf(value.intValue());
        return new WorkoutChart(
                "chart_stamina",
                context.getString(R.string.stamina),
                ActivitySummaryEntries.GROUP_TRAINING_EFFECT,
                lineData,
                valueFormatter,
                getUnitString(context, UNIT_PERCENTAGE)
        );
    }

    private static WorkoutChart createStepLengthChart(final Context context,
                                                      final List<Entry> stepLengthPoints) {
        final WorkoutChartUnits units = chartUnits();
        final WorkoutChartUnits.Quantity quantity = WorkoutChartUnits.Quantity.STEP_LENGTH;
        final String unitString = getUnitString(context, units.token(quantity));
        final WorkoutChart.Series lineData = gappedSeries(convertPoints(stepLengthPoints, units, quantity), ContextCompat.getColor(context, R.color.chart_line_step_length));
        return new WorkoutChart(
                "chart_step_length",
                context.getString(R.string.step_length),
                ActivitySummaryEntries.GROUP_STEPS,
                lineData,
                new DecimalValueFormatter(units.decimals(quantity))::format,
                unitString
        );
    }

    private static WorkoutChart createCnsToxicityChart(final Context context,
                                                       final List<Entry> cnsToxicityPoints) {
        // CNS toxicity decays continuously over time (even during a pause), so it's safe to
        // interpolate across a gap rather than break the segment.
        final WorkoutChart.Series lineData = series(cnsToxicityPoints, ContextCompat.getColor(context, R.color.chart_cns_toxicity));
        final Function1<Double, String> valueFormatter = value -> String.valueOf(value.intValue());
        return new WorkoutChart(
                "chart_cns_toxicity",
                context.getString(R.string.diving_cns_toxicity),
                ActivitySummaryEntries.GROUP_DIVING,
                lineData,
                valueFormatter,
                getUnitString(context, UNIT_PERCENTAGE)
        );
    }

    private static WorkoutChart createN2LoadChart(final Context context,
                                                  final List<Entry> n2LoadPoints) {
        // N2 load changes continuously over time (even during a pause, as nitrogen off-gasses),
        // so it's safe to interpolate across a gap rather than break the segment.
        final WorkoutChart.Series lineData = series(n2LoadPoints, ContextCompat.getColor(context, R.color.chart_n2_load));
        final Function1<Double, String> valueFormatter = value -> String.valueOf(value.intValue());
        return new WorkoutChart(
                "chart_n2_load",
                context.getString(R.string.diving_nitrogen_load),
                ActivitySummaryEntries.GROUP_DIVING,
                lineData,
                valueFormatter,
                getUnitString(context, UNIT_PERCENTAGE)
        );
    }

    private static WorkoutChart createVerticalOscillationChart(final Context context,
                                                               final List<Entry> verticalOscillationPoints) {
        final WorkoutChart.Series lineData = gappedSeries(verticalOscillationPoints, ContextCompat.getColor(context, R.color.chart_line_stride));
        final Function1<Double, String> valueFormatter = value -> String.valueOf(value.intValue());
        return new WorkoutChart(
                "chart_vertical_oscillation",
                context.getString(R.string.vertical_oscillation),
                ActivitySummaryEntries.GROUP_RUNNING_FORM,
                lineData,
                valueFormatter,
                getUnitString(context, UNIT_MM)
        );
    }

    private static WorkoutChart createStanceTimePercentChart(final Context context,
                                                             final List<Entry> stanceTimePercentPoints) {
        final WorkoutChart.Series lineData = gappedSeries(stanceTimePercentPoints, ContextCompat.getColor(context, R.color.chart_line_swolf));
        final Function1<Double, String> valueFormatter = value -> String.format(java.util.Locale.ROOT, "%.1f", value);
        return new WorkoutChart(
                "chart_stance_time_percent",
                context.getString(R.string.stance_time_percent),
                ActivitySummaryEntries.GROUP_RUNNING_FORM,
                lineData,
                valueFormatter,
                getUnitString(context, UNIT_PERCENTAGE)
        );
    }

    private static WorkoutChart createStanceTimeChart(final Context context,
                                                      final List<Entry> stanceTimePoints) {
        final WorkoutChart.Series lineData = gappedSeries(stanceTimePoints, ContextCompat.getColor(context, R.color.chart_line_step_length));
        final Function1<Double, String> valueFormatter = value -> String.valueOf(value.intValue());
        return new WorkoutChart(
                "chart_stance_time",
                context.getString(R.string.ground_contact_time),
                ActivitySummaryEntries.GROUP_RUNNING_FORM,
                lineData,
                valueFormatter,
                getUnitString(context, UNIT_MILLISECONDS)
        );
    }

    private static WorkoutChart createVerticalRatioChart(final Context context,
                                                         final List<Entry> verticalRatioPoints) {
        final WorkoutChart.Series lineData = gappedSeries(verticalRatioPoints, ContextCompat.getColor(context, R.color.chart_line_stamina));
        final Function1<Double, String> valueFormatter = value -> String.format(java.util.Locale.ROOT, "%.1f", value);
        return new WorkoutChart(
                "chart_vertical_ratio",
                context.getString(R.string.vertical_ratio),
                ActivitySummaryEntries.GROUP_RUNNING_FORM,
                lineData,
                valueFormatter,
                getUnitString(context, UNIT_PERCENTAGE)
        );
    }

    private static WorkoutChart createStanceTimeBalanceChart(final Context context,
                                                             final List<Entry> stanceTimeBalancePoints) {
        final WorkoutChart.Series lineData = gappedSeries(stanceTimeBalancePoints, ContextCompat.getColor(context, R.color.chart_line_body_energy));
        final Function1<Double, String> valueFormatter = value -> String.format(java.util.Locale.ROOT, "%.1f", value);
        return new WorkoutChart(
                "chart_stance_time_balance",
                context.getString(R.string.ground_contact_time_balance),
                ActivitySummaryEntries.GROUP_RUNNING_FORM,
                lineData,
                valueFormatter,
                getUnitString(context, UNIT_PERCENTAGE)
        );
    }

    private static WorkoutChart createPerformanceConditionChart(final Context context,
                                                                final List<Entry> performanceConditionPoints) {
        final WorkoutChart.Series lineData = gappedSeries(performanceConditionPoints, ContextCompat.getColor(context, R.color.chart_line_elevation));
        final Function1<Double, String> valueFormatter = value -> String.valueOf(value.intValue());
        return new WorkoutChart(
                "chart_performance_condition",
                context.getString(R.string.performance_condition),
                ActivitySummaryEntries.GROUP_PERFORMANCE_CONDITION,
                lineData,
                valueFormatter,
                ""
        );
    }

    private static WorkoutChartUnits chartUnits() {
        return new WorkoutChartUnits(
                GBApplication.getPrefs().getDistanceUnit(),
                GBApplication.getPrefs().getTemperatureUnit()
        );
    }

    private static WorkoutChartUnits chartUnits(final ActivityKind activityKind) {
        return new WorkoutChartUnits(
                GBApplication.getPrefs().getDistanceUnit(),
                GBApplication.getPrefs().getTemperatureUnit(),
                activityKind,
                GBApplication.getPrefs().getBoolean("units_nautical", true)
        );
    }

    /**
     * Converts the plotted metric values to the display unit so the chart library picks round ticks
     * in that unit's domain. The label formatter then only has to render decimals, never convert.
     */
    private static List<Entry> convertPoints(final List<Entry> points,
                                             final WorkoutChartUnits units,
                                             final WorkoutChartUnits.Quantity quantity) {
        final List<Entry> converted = new ArrayList<>(points.size());
        for (final Entry entry : points) {
            converted.add(new Entry<>(entry.getX(), (float) units.convert(quantity, entry.getY()), null, null));
        }
        return converted;
    }

    // A gap between consecutive points larger than this multiple of the series' median
    // sample gap starts a new segment, and is not bridged by an interpolated line.
    private static final float GAP_THRESHOLD_FACTOR = 5f;

    // Failsafe for devices with noisy data / too many gaps.
    private static final int MAX_SEGMENTS = 50;

    // Median sample gap, in ms, at or below which a cadence series is drawn as a line.
    private static final float DENSE_SAMPLE_GAP_MS = 2000f;

    private static float[] sampleGaps(final List<Entry> entries) {
        final float[] gaps = new float[entries.size() - 1];
        for (int i = 1; i < entries.size(); i++) {
            gaps[i - 1] = entries.get(i).getX() - entries.get(i - 1).getX();
        }
        return gaps;
    }

    private static float median(final float[] values) {
        final float[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    @VisibleForTesting
    static boolean isDenseSeries(final List<Entry> entries) {
        return entries.size() >= 3 && median(sampleGaps(entries)) <= DENSE_SAMPLE_GAP_MS;
    }

    /**
     * The spacing above which two consecutive samples count as interrupted rather than continuous,
     * in the series' own x units: {@link #GAP_THRESHOLD_FACTOR} times its median sample gap. Returns
     * 0 for a series too short or too irregular to have a meaningful spacing, meaning "never a gap".
     * Time-in-zone uses the same threshold as the chart, so a stretch the chart draws as a gap is
     * also a stretch that accumulates no zone time.
     */
    static float gapThreshold(final List<Entry> entries) {
        if (entries.size() < 3) {
            return 0f;
        }
        final float medianGap = median(sampleGaps(entries));
        if (medianGap <= 0) {
            // Duplicate timestamps, so there is nothing to compare a gap against.
            return 0f;
        }
        return medianGap * GAP_THRESHOLD_FACTOR;
    }

    private static final double MS_PER_SECOND = 1000.0;

    /**
     * A series broken after any gap larger than {@link #gapThreshold} (e.g. a paused workout, or a sensor
     * dropout), so no misleading line is drawn across it.
     */
    @VisibleForTesting
    static WorkoutChart.Series gappedSeries(final List<Entry> entries, final int color) {
        return new WorkoutChart.Series(toPoints(entries), color, maxGapSeconds(entries));
    }

    private static WorkoutChart.Series series(final List<Entry> entries, final int color) {
        return new WorkoutChart.Series(toPoints(entries), color);
    }

    /**
     * {@link #gapThreshold} in seconds, or null for a series that should not break.
     */
    @VisibleForTesting
    static Double maxGapSeconds(final List<Entry> entries) {
        final float gapThreshold = gapThreshold(entries);
        if (gapThreshold <= 0) {
            return null;
        }
        int gaps = 0;
        for (final float gap : sampleGaps(entries)) {
            if (gap > gapThreshold && ++gaps >= MAX_SEGMENTS) {
                return null;
            }
        }
        return gapThreshold / MS_PER_SECOND;
    }

    private static List<ChartPoint> toPoints(final List<Entry> entries) {
        final List<ChartPoint> points = new ArrayList<>(entries.size());
        for (final Entry<?> entry : entries) {
            points.add(new ChartPoint(entry.getX() / MS_PER_SECOND, entry.getY(), null));
        }
        return points;
    }

    public static String getCadenceUnit(final ActivityKind.CycleUnit unit) {
        return switch (unit) {
            case STROKES -> ActivitySummaryEntries.UNIT_STROKES_PER_MINUTE;
            case JUMPS -> ActivitySummaryEntries.UNIT_JUMPS_PER_MINUTE;
            case REPS -> ActivitySummaryEntries.UNIT_REPS_PER_MINUTE;
            case REVOLUTIONS -> ActivitySummaryEntries.UNIT_REVS_PER_MINUTE;
            default -> UNIT_SPM;
        };
    }
}
