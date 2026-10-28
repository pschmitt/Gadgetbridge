package nodomain.freeyourgadget.gadgetbridge.activities.charts.sleep

import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.SleepSession
import nodomain.freeyourgadget.gadgetbridge.model.SleepStage
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepSessionChartDataTest {
    private fun index(kind: ActivityKind) = when (kind) {
        ActivityKind.DEEP_SLEEP -> 0
        ActivityKind.LIGHT_SLEEP -> 1
        else -> 5
    }

    @Test
    fun nativeStagesSupplyHypnogramWithoutActivitySamples() {
        val session = SleepSession(listOf(
            SleepStage(ActivityKind.DEEP_SLEEP, 1_000_000, 1_600_000),
            SleepStage(ActivityKind.LIGHT_SLEEP, 1_600_000, 2_200_000),
        ))
        val data = SleepStagesChartData.sessionSamples(listOf(session), ::index, 5)
        assertArrayEquals(longArrayOf(1000, 1600, 2200), data.seconds)
        assertArrayEquals(intArrayOf(0, 1, 5), data.stages)
        assertTrue(!SleepStagesChartData.hypnogramSpec(data.seconds, data.stages, intArrayOf(1, 2)).isEmpty)
    }

    @Test
    fun gapBetweenNightAndNapIsNotRenderedAsSleep() {
        val night = SleepSession(listOf(SleepStage(ActivityKind.DEEP_SLEEP, 1_000_000, 2_000_000)))
        val nap = SleepSession(listOf(SleepStage(ActivityKind.LIGHT_SLEEP, 4_000_000, 5_000_000)))
        val data = SleepStagesChartData.sessionSamples(listOf(nap, night), ::index, 5)
        assertArrayEquals(longArrayOf(1000, 2000, 4000, 5000), data.seconds)
        assertArrayEquals(intArrayOf(0, 5, 1, 5), data.stages)
    }
}
