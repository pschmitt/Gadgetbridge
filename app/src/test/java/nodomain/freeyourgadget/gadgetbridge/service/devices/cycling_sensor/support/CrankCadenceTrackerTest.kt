package nodomain.freeyourgadget.gadgetbridge.service.devices.cycling_sensor.support

import nodomain.freeyourgadget.gadgetbridge.service.devices.cycling_sensor.support.CyclingSensorSupport.CrankCadenceTracker
import nodomain.freeyourgadget.gadgetbridge.service.devices.cycling_sensor.support.CyclingSensorSupport.CyclingSpeedCadenceMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CrankCadenceTrackerTest {

    private fun crank(revolutions: Int, ticks: Int) = CyclingSpeedCadenceMeasurement().apply {
        cadenceDataPresent = true
        crankRevolutionCount = revolutions
        lastCrankRevolutionTimeTicks = ticks
    }

    @Test
    fun firstMeasurement_hasNothingToCompare() {
        assertNull(CrankCadenceTracker().update(crank(100, 1024), 10_000))
    }

    @Test
    fun newCrankEvent_reportsCadence() {
        val tracker = CrankCadenceTracker()
        tracker.update(crank(100, 1024), 10_000)

        assertEquals(80, tracker.update(crank(102, 2560), 11_500))
    }

    @Test
    fun noNewEventShortlyAfter_staysNull() {
        val tracker = CrankCadenceTracker()
        tracker.update(crank(100, 1024), 10_000)
        tracker.update(crank(102, 2560), 11_500)

        // A slow cadence repeats the last event time on some notifications; that is not a stop
        assertNull(tracker.update(crank(102, 2560), 12_500))
    }

    @Test
    fun noNewEventForLongerThanIdle_reportsZero() {
        val tracker = CrankCadenceTracker()
        tracker.update(crank(100, 1024), 10_000)
        tracker.update(crank(102, 2560), 11_500)
        tracker.update(crank(102, 2560), 12_500)

        assertEquals(0, tracker.update(crank(102, 2560), 11_500 + CrankCadenceTracker.IDLE_MILLIS + 1))
    }

    @Test
    fun gapLongerThanAnEventTimeRollover_isNotCompared() {
        val tracker = CrankCadenceTracker()
        tracker.update(crank(100, 1024), 10_000)

        assertNull(tracker.update(crank(150, 2048), 10_000 + CrankCadenceTracker.MAX_GAP_MILLIS + 1))
    }

    @Test
    fun pedalingResumesAfterAStop() {
        val tracker = CrankCadenceTracker()
        tracker.update(crank(100, 1024), 10_000)
        assertEquals(0, tracker.update(crank(100, 1024), 14_000))

        assertEquals(80, tracker.update(crank(102, 2560), 15_000))
    }
}
