package nodomain.freeyourgadget.gadgetbridge.service.devices.innovo

import nodomain.freeyourgadget.gadgetbridge.util.GB
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InnovoPlxMeasurementTest {
    @Test
    fun testMeasurement() {
        val measurement = InnovoPlxMeasurement.parse(GB.hexStringToByteArray("3e6300500013200000000007f0"))

        assertNotNull(measurement)
        assertEquals(99f, measurement!!.spo2!!, 0.001f)
        assertEquals(80f, measurement.pulseRate!!, 0.001f)
        assertEquals(19, measurement.respiratoryRate)
        assertEquals(0x0020, measurement.measurementStatus)
        assertEquals(0, measurement.deviceStatus)
        assertEquals(0.7f, measurement.perfusionIndex!!, 0.001f)
        assertFalse(measurement.isUnreliable())
    }

    @Test
    fun testFingerOut() {
        val measurement = InnovoPlxMeasurement.parse(GB.hexStringToByteArray("3e000000000000002008000000"))

        assertNotNull(measurement)
        assertEquals(0f, measurement!!.spo2!!, 0.001f)
        assertEquals(0f, measurement.pulseRate!!, 0.001f)
        assertEquals(0x000820, measurement.deviceStatus)
        assertTrue(measurement.isUnreliable())
    }

    @Test
    fun testLowPerfusion() {
        val measurement = InnovoPlxMeasurement.parse(GB.hexStringToByteArray("3e640056000d20002000000000"))

        assertNotNull(measurement)
        assertEquals(100f, measurement!!.spo2!!, 0.001f)
        assertEquals(0x000020, measurement.deviceStatus)
        assertTrue(measurement.isUnreliable())
    }

    @Test
    fun testPleth() {
        assertNull(InnovoPlxMeasurement.parse(GB.hexStringToByteArray("01c9")))
    }
}
