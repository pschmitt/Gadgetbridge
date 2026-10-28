package nodomain.freeyourgadget.gadgetbridge.util.elevation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.random.Random

/** Summing raw per-point rises turns a 750 m climb into ~15 km: vertical noise gets banked thousands of times. */
class ElevationGainTest {

    private fun gain(altitudes: List<Double?>) = ElevationGain.fromAltitudes(altitudes).gain

    private fun loss(altitudes: List<Double?>) = ElevationGain.fromAltitudes(altitudes).loss

    /** A climb of [trueGain] metres over [samples], with Gaussian vertical error of [sigma]. Sigma 3 m is realistic. */
    private fun noisyClimb(
        trueGain: Double,
        samples: Int,
        sigma: Double = 3.0,
        seed: Int = 11,
    ): List<Double?> {
        val random = Random(seed)
        return List(samples) { i ->
            trueGain * (i.toDouble() / max(samples - 1, 1)) + gaussian(random) * sigma
        }
    }

    private fun gaussian(random: Random): Double {
        // Box-Muller: kotlin.random has no normal distribution.
        val u1 = 1.0 - random.nextDouble()
        val u2 = 1.0 - random.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2 * Math.PI * u2)
    }

    @Test
    fun aFlatRoute_reportsEssentiallyNoClimb() {
        val flat = noisyClimb(trueGain = 0.0, samples = 3600)
        // A naive sum over this same data yields thousands of meters.
        assertTrue(gain(flat) < 60.0)
    }

    @Test
    fun aRealClimb_isReportedAccuratelyNotInflated() {
        val oneHour = noisyClimb(trueGain = 300.0, samples = 3600)
        val twoHours = noisyClimb(trueGain = 750.0, samples = 7200)

        assertEquals(300.0, gain(oneHour), 60.0)
        assertEquals(750.0, gain(twoHours), 100.0)
    }

    @Test
    fun accuracy_doesNotDecayWithRouteLength() {
        // Same climb, 4x samples.
        val short = gain(noisyClimb(trueGain = 200.0, samples = 900))
        val long = gain(noisyClimb(trueGain = 200.0, samples = 3600))
        assertTrue(abs(short - long) < 80.0)
    }

    @Test
    fun aCleanStaircase_isMeasuredAndDescentIsNotCountedAsGain() {
        // 30 m up, down, up; each level held so the smoothing can follow.
        val altitudes = listOf(0.0, 30.0, 0.0, 30.0).flatMap { level -> List<Double?>(40) { level } }
        assertEquals(60.0, gain(altitudes), 6.0)
        assertEquals(30.0, loss(altitudes), 6.0)
    }

    @Test
    fun aSparseRoute_isNotUnderReported() {
        // One point every ~100 m: a heavily lagging filter would swallow much of the climb.
        val sparse = List<Double?>(50) { i -> 750.0 * (i / 49.0) }
        assertTrue(gain(sparse) > 680.0)
    }

    @Test
    fun aTwoPointClimb_isNotSwallowedByTheSmoothingLag() {
        // With two points the lag is the whole route; the residual settle recovers it.
        assertEquals(80.0, gain(listOf(10.0, 90.0)), 2.0)
        assertEquals(80.0, loss(listOf(90.0, 10.0)), 2.0)
    }

    @Test
    fun movementBelowTheStepThreshold_neverAccumulates() {
        // Jitter of plus-minus 2 m, under the 5 m step, so nothing is banked.
        val jitter = List<Double?>(500) { i -> if (i % 2 == 0) 2.0 else -2.0 }
        assertEquals(0.0, gain(jitter), 0.0)
    }

    @Test
    fun nullAndNonFiniteAltitudes_areSkippedNotTreatedAsZero() {
        // A null as 0 m would invent a fall to sea level.
        val withHoles = listOf(100.0, null, 103.0, Double.NaN, 106.0, null, 109.0)
        assertTrue(gain(withHoles) < 14.0)
        assertEquals(0.0, gain(emptyList()), 0.0)
        assertEquals(0.0, gain(listOf(null, null)), 0.0)
        assertEquals(0.0, gain(listOf(42.0)), 0.0)
    }
}
