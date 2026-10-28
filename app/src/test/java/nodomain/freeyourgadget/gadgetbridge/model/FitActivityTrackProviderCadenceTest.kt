package nodomain.freeyourgadget.gadgetbridge.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FitActivityTrackProviderCadenceTest {

    private fun normalized(cadence: Int, kind: ActivityKind): Int {
        val point = ActivityPoint()
        point.cadence = cadence
        FitActivityTrackProvider.normalizeStepCadence(point, kind)
        return point.cadence
    }

    @Test
    fun running_isDoubled() {
        assertEquals(170, normalized(85, ActivityKind.RUNNING))
    }

    @Test
    fun walking_isDoubled() {
        assertEquals(110, normalized(55, ActivityKind.WALKING))
    }

    @Test
    fun cycling_isKept() {
        assertEquals(85, normalized(85, ActivityKind.CYCLING))
    }

    @Test
    fun rowing_isKept() {
        assertEquals(24, normalized(24, ActivityKind.ROWING_MACHINE))
    }

    @Test
    fun unsetAndZero_areKept() {
        assertEquals(-1, normalized(-1, ActivityKind.RUNNING))
        assertEquals(0, normalized(0, ActivityKind.RUNNING))
    }
}
