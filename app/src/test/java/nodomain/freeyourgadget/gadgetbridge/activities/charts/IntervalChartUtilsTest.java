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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Color;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import kotlin.jvm.functions.Function1;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartSelection;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.XMarkerSpec;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.charts.WorkoutChartSpecs;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummaryGroup;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityPoint;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrack.SegmentInfo;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrack.SegmentIntensity;
import nodomain.freeyourgadget.gadgetbridge.model.workout.WorkoutChart;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

public class IntervalChartUtilsTest extends TestBase {
    private static List<ActivityPoint> segment(final int startSecond, final int seconds, final int hr) {
        final List<ActivityPoint> points = new ArrayList<>();
        for (int s = 0; s < seconds; s++) {
            final ActivityPoint p = new ActivityPoint(new Date(1_700_000_000_000L + (startSecond + s) * 1000L));
            p.setHeartRate(hr);
            points.add(p);
        }
        return points;
    }

    private static List<IntervalChartUtils.IntervalRow> rowingIntervals() {
        return IntervalChartUtils.deriveIntervals(
                Arrays.asList(segment(0, 301, 150), segment(301, 121, 110), segment(422, 301, 155)),
                Arrays.asList(
                        new SegmentInfo(SegmentIntensity.ACTIVE, null, 144),
                        new SegmentInfo(SegmentIntensity.REST, null, 3),
                        new SegmentInfo(SegmentIntensity.ACTIVE, null, 156)));
    }

    @Test
    public void everyIntervalIsLabelledRestLowAndTheLastLeftOfItsLine() {
        final List<XMarkerSpec> lines = IntervalChartUtils.boundaryLines(getContext(), rowingIntervals());

        assertEquals(3, lines.size());
        assertEquals(0.0, lines.get(0).getValue(), 0.0);
        assertFalse(lines.get(0).getLabelAtBottom());
        assertFalse(lines.get(0).getLabelBeforeLine());
        assertEquals(Color.TRANSPARENT, lines.get(0).getColor());
        assertEquals(301.0, lines.get(1).getValue(), 0.0);
        assertTrue(lines.get(1).getLabelAtBottom());
        assertFalse(lines.get(1).getLabelBeforeLine());
        assertEquals(422.0, lines.get(2).getValue(), 0.0);
        assertFalse(lines.get(2).getLabelAtBottom());
        assertTrue(lines.get(2).getLabelBeforeLine());
    }

    @Test
    public void tooltipNamesTheIntervalOfTheSelectedTime() {
        final WorkoutChart chart = new WorkoutChart("heart_rate", "Heart rate", "",
                new WorkoutChart.Series(Arrays.asList(new ChartPoint(0, 150, null), new ChartPoint(700, 155, null)), 0));

        final Function1<Double, ChartSelection> selection =
                WorkoutChartSpecs.selection(getContext(), Collections.singletonList(chart), rowingIntervals());
        assertEquals("5:00 · " + getContext().getString(R.string.interval_type_active), selection.invoke(300.0).getTitle());
        assertEquals("5:01 · " + getContext().getString(R.string.interval_type_rest), selection.invoke(301.0).getTitle());

        final Function1<Double, ChartSelection> plain =
                WorkoutChartSpecs.selection(getContext(), Collections.singletonList(chart), null);
        assertEquals("5:01", plain.invoke(301.0).getTitle());
    }

    @Test
    public void derivesOneRowPerSegment() {
        final List<IntervalChartUtils.IntervalRow> rows = rowingIntervals();

        assertEquals(3, rows.size());
        assertEquals(SegmentIntensity.REST, rows.get(1).type);
        assertEquals(301, rows.get(1).startSeconds);
        assertEquals(120, rows.get(1).durationSeconds);
        assertEquals(3, rows.get(1).count);
        assertEquals(Integer.valueOf(110), rows.get(1).avgHr);
    }

    @Test
    public void phasedIntervalsBecomeTheIntervalsTable() {
        final ActivitySummaryData data = new ActivitySummaryData();
        IntervalChartUtils.addToSummaryData(getContext(), data, rowingIntervals());

        final Map<String, ?> groups = ActivitySummaryGroup.INSTANCE.buildGroupedList(data);
        // header plus one row per interval
        assertEquals(4, ((List<?>) groups.get(ActivitySummaryEntries.GROUP_INTERVALS)).size());
    }

    @Test
    public void deviceReportedIntervalsAreKept() {
        final ActivitySummaryData data = new ActivitySummaryData();
        data.add("intervals_header", "device");
        IntervalChartUtils.addToSummaryData(getContext(), data, rowingIntervals());

        assertFalse(data.has("interval_1"));
    }

    @Test
    public void unphasedSegmentsAreNoIntervals() {
        final List<IntervalChartUtils.IntervalRow> rows = IntervalChartUtils.deriveIntervals(
                Arrays.asList(segment(0, 60, 120), segment(600, 60, 125)),
                Arrays.asList(new SegmentInfo(), new SegmentInfo()));
        assertFalse(IntervalChartUtils.hasIntervalPhases(rows));
        assertTrue(IntervalChartUtils.hasIntervalPhases(rowingIntervals()));

        final ActivitySummaryData data = new ActivitySummaryData();
        IntervalChartUtils.addToSummaryData(getContext(), data, rows);
        assertTrue(data.getKeys().isEmpty());
    }
}
