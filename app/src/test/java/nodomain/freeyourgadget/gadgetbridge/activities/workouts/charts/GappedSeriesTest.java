package nodomain.freeyourgadget.gadgetbridge.activities.workouts.charts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.github.mikephil.charting.data.Entry;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartDataBuilder;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec;
import nodomain.freeyourgadget.gadgetbridge.model.workout.WorkoutChart;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

public class GappedSeriesTest extends TestBase {
    private static final long MS_PER_SECOND = 1000L;

    private static List<Entry> runs(final int count) {
        final List<Entry> entries = new ArrayList<>();
        for (int run = 0; run < count; run++) {
            for (int i = 0; i < 10; i++) {
                entries.add(new Entry<>((run * 100L + i) * MS_PER_SECOND, 5, null, null));
            }
        }
        return entries;
    }

    private int segmentCount(final WorkoutChart.Series series) {
        final WorkoutChart chart = new WorkoutChart("power", "Power", "", series);
        final ChartSpec spec = WorkoutChartSpecs.spec(getContext(), Collections.singletonList(chart), false);
        return ChartDataBuilder.INSTANCE.segments(spec.getSeries().get(0)).size();
    }

    @Test
    public void pausesBreakTheSeries() {
        final WorkoutChart.Series series = DefaultWorkoutCharts.gappedSeries(runs(3), 0);

        assertNotNull(series.getMaxGap());
        assertEquals(3, segmentCount(series));
    }

    @Test
    public void tooManyPausesKeepTheSeriesWhole() {
        assertNotNull(DefaultWorkoutCharts.maxGapSeconds(runs(50)));
        assertNull(DefaultWorkoutCharts.maxGapSeconds(runs(51)));
    }
}
