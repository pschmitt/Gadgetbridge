package nodomain.freeyourgadget.gadgetbridge.service.devices.cycling_sensor.support

import nodomain.freeyourgadget.gadgetbridge.service.devices.cycling_sensor.support.CyclingSensorSupport.CyclingSpeedCadenceMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Payloads follow the CSC Measurement characteristic (0x2A5B): a flags byte, then optional wheel
 * data (uint32 revolutions, uint16 event time) and crank data (uint16 revolutions, uint16 event
 * time), all little endian, event times in 1/1024 s.
 */
class CyclingSpeedCadenceMeasurementTest {

    private fun hex(s: String): ByteArray {
        val clean = s.replace(" ", "")
        return ByteArray(clean.length / 2) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }

    private fun wheel(revolutions: Int, ticks: Int) = CyclingSpeedCadenceMeasurement().apply {
        revolutionDataPresent = true
        revolutionCount = revolutions
        lastRevolutionTimeTicks = ticks
    }

    private fun crank(revolutions: Int, ticks: Int) = CyclingSpeedCadenceMeasurement().apply {
        cadenceDataPresent = true
        crankRevolutionCount = revolutions
        lastCrankRevolutionTimeTicks = ticks
    }

    @Test
    fun wheelOnly() {
        val m = CyclingSpeedCadenceMeasurement.fromPayload(hex("01 e8030000 0004"))

        assertTrue(m.revolutionDataPresent)
        assertFalse(m.cadenceDataPresent)
        assertEquals(1000, m.revolutionCount)
        assertEquals(1024, m.lastRevolutionTimeTicks)
    }

    @Test
    fun combinedSpeedAndCadence_keepsWheelData() {
        val m = CyclingSpeedCadenceMeasurement.fromPayload(hex("03 e8030000 0004 2a00 0008"))

        assertTrue(m.revolutionDataPresent)
        assertTrue(m.cadenceDataPresent)
        assertEquals(1000, m.revolutionCount)
        assertEquals(1024, m.lastRevolutionTimeTicks)
        assertEquals(42, m.crankRevolutionCount)
        assertEquals(2048, m.lastCrankRevolutionTimeTicks)
    }

    @Test
    fun crankOnly_fiveBytes() {
        val m = CyclingSpeedCadenceMeasurement.fromPayload(hex("02 2a00 0008"))

        assertFalse(m.revolutionDataPresent)
        assertTrue(m.cadenceDataPresent)
        assertEquals(42, m.crankRevolutionCount)
        assertEquals(2048, m.lastCrankRevolutionTimeTicks)
    }

    @Test
    fun reservedFlagBits_areIgnored() {
        val m = CyclingSpeedCadenceMeasurement.fromPayload(hex("81 e8030000 0004"))

        assertTrue(m.revolutionDataPresent)
        assertFalse(m.cadenceDataPresent)
        assertEquals(1000, m.revolutionCount)
    }

    @Test
    fun crankFields_areUnsigned() {
        val m = CyclingSpeedCadenceMeasurement.fromPayload(hex("02 ffff ffff"))

        assertEquals(65535, m.crankRevolutionCount)
        assertEquals(65535, m.lastCrankRevolutionTimeTicks)
    }

    @Test
    fun payloadShorterThanFlagsDeclare_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            CyclingSpeedCadenceMeasurement.fromPayload(hex("03 e8030000 0004"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CyclingSpeedCadenceMeasurement.fromPayload(ByteArray(0))
        }
    }

    @Test
    fun speedSince_oneSecondTwoRevolutions() {
        val speed = wheel(1002, 2048).speedSince(wheel(1000, 1024), 2.0f)

        assertEquals(4.0f, speed!!, 0.001f)
    }

    @Test
    fun speedSince_acrossEventTimeRollover() {
        // 0xFE00 -> 0x0200 is 1024 ticks after the 16-bit timer wraps
        val speed = wheel(1002, 0x0200).speedSince(wheel(1000, 0xFE00), 2.0f)

        assertEquals(4.0f, speed!!, 0.001f)
    }

    @Test
    fun speedSince_noTimePassed_isNull() {
        assertNull(wheel(1000, 1024).speedSince(wheel(1000, 1024), 2.0f))
    }

    @Test
    fun cadenceSince_twoRevolutionsInOneAndAHalfSeconds() {
        assertEquals(80, crank(102, 1024 + 1536).cadenceSince(crank(100, 1024)))
    }

    @Test
    fun cadenceSince_acrossRevolutionAndEventTimeRollover() {
        // 0xFFFF -> 0x0001 is 2 revolutions, 0xFC00 -> 0x0200 is 1536 ticks
        assertEquals(80, crank(0x0001, 0x0200).cadenceSince(crank(0xFFFF, 0xFC00)))
    }

    @Test
    fun cadenceSince_noNewCrankEvent_isNull() {
        assertNull(crank(100, 1024).cadenceSince(crank(100, 1024)))
    }
}
