package nodomain.freeyourgadget.gadgetbridge.util.healthconnect.syncers

import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivityPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Date

class RecordedWorkoutSyncerCadenceTest {

    private val start = Instant.ofEpochSecond(1_700_000_000)
    private val end = start.plusSeconds(600)

    private fun pt(offsetSeconds: Long, cadence: Int): ActivityPoint {
        val p = ActivityPoint(Date.from(start.plusSeconds(offsetSeconds)))
        p.cadence = cadence
        return p
    }

    private fun rates(points: List<ActivityPoint>, unit: ActivityKind.CycleUnit): List<Double> =
        RecordedWorkoutSyncer.cadenceSamples(points, unit, start, end).map { it.second }

    @Test
    fun steps_areKept() {
        assertEquals(listOf(170.0, 176.0), rates(listOf(pt(1, 170), pt(2, 176)), ActivityKind.CycleUnit.STEPS))
    }

    @Test
    fun revolutions_areKept() {
        assertEquals(listOf(90.0), rates(listOf(pt(1, 90)), ActivityKind.CycleUnit.REVOLUTIONS))
    }

    @Test
    fun unsetAndZero_areDropped() {
        assertEquals(listOf(80.0), rates(listOf(pt(1, -1), pt(2, 0), pt(3, 80)), ActivityKind.CycleUnit.STEPS))
    }

    @Test
    fun outsideWorkout_isDropped() {
        assertEquals(listOf(80.0), rates(listOf(pt(-5, 70), pt(3, 80), pt(601, 90)), ActivityKind.CycleUnit.STEPS))
    }

    @Test
    fun pointWithoutTime_isDropped() {
        val noTime = ActivityPoint()
        noTime.cadence = 80
        assertTrue(rates(listOf(noTime), ActivityKind.CycleUnit.STEPS).isEmpty())
    }

    @Test
    fun strokes_haveNoRecord() {
        assertTrue(rates(listOf(pt(1, 30)), ActivityKind.CycleUnit.STROKES).isEmpty())
    }

    @Test
    fun samplesKeepPointTime() {
        val samples = RecordedWorkoutSyncer.cadenceSamples(listOf(pt(42, 80)), ActivityKind.CycleUnit.STEPS, start, end)
        assertEquals(start.plusSeconds(42), samples.single().first)
    }
}
