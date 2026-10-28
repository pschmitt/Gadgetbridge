/*  Copyright (C) 2026 Dany Mestas

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
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.XMarkerSpec;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummaryTableBuilder;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummaryValue;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityPoint;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrack;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrack.SegmentInfo;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrack.SegmentIntensity;

/**
 * Derives per-workout intervals from {@link ActivityTrack} segments. A segment becomes one
 * interval row; its intensity (ACTIVE/REST/UNKNOWN) drives the color and label. Per-segment
 * averages (HR, cadence, speed) are computed from the segment's {@link ActivityPoint}s.
 */
public final class IntervalChartUtils {
    private static final String INTERVALS_HEADER = "intervals_header";

    private IntervalChartUtils() {
    }

    /** One interval row derived from a single {@link ActivityTrack} segment. */
    public static final class IntervalRow {
        public final SegmentIntensity type;
        public final int startSeconds;     // offset from track start
        public final int durationSeconds;
        public final int count;            // strokes (rowing); -1 when not encoded
        @Nullable public final Integer distanceMeters;
        @Nullable public final Integer avgHr;
        @Nullable public final Integer avgCadence;
        @Nullable public final Float avgSpeed;  // m/s

        public IntervalRow(final SegmentIntensity type, final int startSeconds, final int durationSeconds,
                           final int count, @Nullable final Integer distanceMeters, @Nullable final Integer avgHr,
                           @Nullable final Integer avgCadence, @Nullable final Float avgSpeed) {
            this.type = type != null ? type : SegmentIntensity.UNKNOWN;
            this.startSeconds = startSeconds;
            this.durationSeconds = durationSeconds;
            this.count = count;
            this.distanceMeters = distanceMeters;
            this.avgHr = avgHr;
            this.avgCadence = avgCadence;
            this.avgSpeed = avgSpeed;
        }
    }

    /**
     * Builds interval rows from a track's segment point-lists and their {@link SegmentInfo}s.
     * Duration is the span of point timestamps in the segment; strokes/distance come from the
     * segment header. HR/cadence/speed averages are computed over valid points in the segment.
     */
    @NonNull
    public static List<IntervalRow> deriveIntervals(@Nullable final List<List<ActivityPoint>> segments,
                                                    @Nullable final List<SegmentInfo> infos) {
        final List<IntervalRow> rows = new ArrayList<>();
        if (segments == null || infos == null) {
            return rows;
        }
        // Track start = first timestamp of the first non-empty segment; interval start offsets
        // are measured from it (matching the chart x-axis, which is zeroed at the track start).
        Long trackStartMs = null;
        for (final List<ActivityPoint> seg : segments) {
            if (seg != null && !seg.isEmpty()) {
                trackStartMs = seg.get(0).getTime().getTime();
                break;
            }
        }

        final int n = Math.min(segments.size(), infos.size());
        for (int i = 0; i < n; i++) {
            final List<ActivityPoint> pts = segments.get(i);
            final SegmentInfo info = infos.get(i);
            if (pts == null || pts.isEmpty()) {
                continue;
            }

            final long firstMs = pts.get(0).getTime().getTime();
            final long lastMs = pts.get(pts.size() - 1).getTime().getTime();
            final int startSeconds = trackStartMs != null
                    ? (int) Math.max(0, Math.round((firstMs - trackStartMs) / 1000.0)) : 0;
            final int durationSeconds = (int) Math.max(0, Math.round((lastMs - firstMs) / 1000.0));

            long hrSum = 0;
            int hrCount = 0;
            long cadenceSum = 0;
            int cadenceCount = 0;
            double speedSum = 0;
            int speedCount = 0;
            for (final ActivityPoint p : pts) {
                final int hr = p.getHeartRate();
                if (hr > 0) {
                    hrSum += hr;
                    hrCount++;
                }
                final int cad = p.getCadence();
                if (cad > 0) {
                    cadenceSum += cad;
                    cadenceCount++;
                }
                final float spd = p.getSpeed();
                if (spd > 0f) {
                    speedSum += spd;
                    speedCount++;
                }
            }

            final Integer avgHr = hrCount > 0 ? (int) Math.round((double) hrSum / hrCount) : null;
            final Integer avgCadence = cadenceCount > 0 ? (int) Math.round((double) cadenceSum / cadenceCount) : null;
            final Float avgSpeed = speedCount > 0 ? (float) (speedSum / speedCount) : null;
            final Integer strokes = info != null ? info.getStrokes() : null;
            final Integer distance = info != null ? info.getDistanceMeters() : null;

            rows.add(new IntervalRow(
                    info != null ? info.getIntensity() : SegmentIntensity.UNKNOWN,
                    startSeconds,
                    durationSeconds,
                    strokes != null ? strokes : -1,
                    distance,
                    avgHr,
                    avgCadence,
                    avgSpeed));
        }
        return rows;
    }

    public static int colorForType(final Context ctx, final SegmentIntensity type) {
        final int colorRes;
        switch (type) {
            case ACTIVE:
                colorRes = R.color.chart_interval_active;
                break;
            case REST:
                colorRes = R.color.chart_interval_rest;
                break;
            default:
                colorRes = R.color.chart_interval_unknown;
                break;
        }
        return ContextCompat.getColor(ctx, colorRes);
    }

    public static String labelForType(final Context ctx, final SegmentIntensity type) {
        switch (type) {
            case ACTIVE:
                return ctx.getString(R.string.interval_type_active);
            case REST:
                return ctx.getString(R.string.interval_type_rest);
            default:
                return ctx.getString(R.string.interval_type_unknown);
        }
    }

    public static String shortLabelForType(final Context ctx, final SegmentIntensity type) {
        return labelForType(ctx, type).toLowerCase(Locale.getDefault());
    }

    /**
     * Builds the lines marking where each interval starts, for a time axis in seconds. The
     * first interval starts on the y axis, so its line is transparent and only its label shows. A
     * rest label goes at the bottom of the plot, where the signal sits low during
     * rest, and an active label at the top. An interval of unknown type takes the side opposite the
     * label before it, since a short block puts two boundaries within a few pixels of each other
     * and same-side labels then overlap. The last label is drawn left of its line: a final
     * boundary close to the end of the workout leaves no room on its right.
     */
    @NonNull
    public static List<XMarkerSpec> boundaryLines(final Context ctx, @Nullable final List<IntervalRow> intervals) {
        final List<XMarkerSpec> lines = new ArrayList<>();
        if (intervals == null) {
            return lines;
        }
        boolean previousBottom = false;
        for (int i = 0; i < intervals.size(); i++) {
            final IntervalRow interval = intervals.get(i);
            final boolean bottom;
            switch (interval.type) {
                case REST:
                    bottom = true;
                    break;
                case ACTIVE:
                    bottom = false;
                    break;
                default:
                    bottom = !previousBottom;
                    break;
            }
            previousBottom = bottom;
            final boolean last = i > 0 && i == intervals.size() - 1;
            lines.add(new XMarkerSpec(
                    interval.startSeconds,
                    i == 0 ? Color.TRANSPARENT : colorForType(ctx, interval.type),
                    shortLabelForType(ctx, interval.type),
                    bottom,
                    last));
        }
        return lines;
    }

    /**
     * Whether {@code intervals} describe a genuine ACTIVE/REST interval workout. Providers also
     * split tracks into segments for recording pauses or plain laps with UNKNOWN intensity, and
     * those must not surface as intervals.
     */
    public static boolean hasIntervalPhases(@Nullable final List<IntervalRow> intervals) {
        if (intervals == null || intervals.size() < 2) {
            return false;
        }
        for (final IntervalRow interval : intervals) {
            if (interval.type != SegmentIntensity.UNKNOWN) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds {@code intervals} to {@code summaryData} as the {@link ActivitySummaryEntries#GROUP_INTERVALS}
     * table, unless the device already reported one or the intervals carry no ACTIVE/REST phases.
     */
    public static void addToSummaryData(final Context ctx,
                                        final ActivitySummaryData summaryData,
                                        final List<IntervalRow> intervals) {
        if (!hasIntervalPhases(intervals) || summaryData.getKeys().contains(INTERVALS_HEADER)) {
            return;
        }
        final ActivitySummaryTableBuilder tableBuilder = new ActivitySummaryTableBuilder(
                ActivitySummaryEntries.GROUP_INTERVALS, INTERVALS_HEADER, Arrays.asList(
                "#",
                "pref_header_time",
                "Strokes",
                "Distance",
                "heart_rate"
        ));
        for (int i = 0; i < intervals.size(); i++) {
            final IntervalRow interval = intervals.get(i);
            tableBuilder.addRow("interval_" + (i + 1), Arrays.asList(
                    new ActivitySummaryValue(labelForType(ctx, interval.type)),
                    new ActivitySummaryValue(interval.durationSeconds, ActivitySummaryEntries.UNIT_SECONDS),
                    new ActivitySummaryValue(interval.count > 0 ? interval.count : null, ActivitySummaryEntries.UNIT_NONE),
                    new ActivitySummaryValue(interval.distanceMeters, ActivitySummaryEntries.UNIT_METERS),
                    new ActivitySummaryValue(interval.avgHr, ActivitySummaryEntries.UNIT_BPM)
            ));
        }
        tableBuilder.addToSummaryData(summaryData);
    }
}
