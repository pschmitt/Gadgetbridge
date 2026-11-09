package nodomain.freeyourgadget.gadgetbridge.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TimeWeightedAverageAccumulatorTest {
    private static final double DELTA = 0.0001;

    @Test
    public void nothingAddedCountsNothing() {
        final TimeWeightedAverageAccumulator accumulator = new TimeWeightedAverageAccumulator(600, 60);

        assertEquals(0, accumulator.getCount());
    }

    @Test
    public void aSingleValueIsCounted() {
        final TimeWeightedAverageAccumulator accumulator = new TimeWeightedAverageAccumulator(600, 60);
        accumulator.add(1_700_000_000L, 70);

        assertEquals(1, accumulator.getCount());
        assertEquals(70, accumulator.getMin(), DELTA);
        assertEquals(70, accumulator.getMax(), DELTA);
        assertEquals(70, accumulator.getAverage(), DELTA);
    }

    @Test
    public void everyValueIsCounted() {
        final TimeWeightedAverageAccumulator accumulator = new TimeWeightedAverageAccumulator(600, 60);
        accumulator.add(1_700_000_000L, 60);
        accumulator.add(1_700_000_060L, 80);

        assertEquals(2, accumulator.getCount());
        assertEquals(60, accumulator.getMin(), DELTA);
        assertEquals(80, accumulator.getMax(), DELTA);
        assertEquals(70, accumulator.getAverage(), DELTA);
    }
}
