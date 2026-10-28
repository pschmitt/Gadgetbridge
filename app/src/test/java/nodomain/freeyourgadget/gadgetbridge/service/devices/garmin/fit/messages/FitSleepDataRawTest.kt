package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FitSleepDataRawTest {

    /** A decoded field as the byte base type returns it: each byte a number, or null for 0xFF. */
    private fun field(vararg halves: Int): Array<Any?> =
        halves.flatMap { half -> listOf(half and 0xFF, (half shr 8) and 0xFF) }
            .map { byte -> if (byte == 0xFF) null else byte }
            .toTypedArray()

    private fun row(movement: Int, heartRate: Int): Array<Any?> =
        field(0x3C00, 0, 0, 0, 0, 0, 0, 0, movement, heartRate)

    @Test
    fun halfToFloat_knownValues() {
        assertEquals(0f, FitSleepDataRaw.halfToFloat(0x0000), 0f)
        assertEquals(1f, FitSleepDataRaw.halfToFloat(0x3C00), 0f)
        assertEquals(0.5f, FitSleepDataRaw.halfToFloat(0x3800), 0f)
        assertEquals(-2f, FitSleepDataRaw.halfToFloat(0xC000), 0f)
        assertEquals(65504f, FitSleepDataRaw.halfToFloat(0x7BFF), 0f)
        assertEquals(5.9604645E-8f, FitSleepDataRaw.halfToFloat(0x0001), 0f)
        assertEquals(Float.POSITIVE_INFINITY, FitSleepDataRaw.halfToFloat(0x7C00), 0f)
        assertTrue(FitSleepDataRaw.halfToFloat(0x7E00).isNaN())
    }

    @Test
    fun decodeFeatures_isLittleEndianInFileOrder() {
        // 52 bpm is 0x5280, 1.5 is 0x3E00
        val features = FitSleepDataRaw.decodeFeatures(row(movement = 0x3E00, heartRate = 0x5280))!!

        assertEquals(10, features.size)
        assertEquals(1f, features[0], 0f)
        assertEquals(1.5f, features[FitSleepDataRaw.MOVEMENT_INDEX], 0f)
        assertEquals(52f, features[FitSleepDataRaw.HEART_RATE_INDEX], 0f)
    }

    @Test
    fun decodeFeatures_keepsBytesTheDecoderCalledInvalid() {
        // 0x57FF holds a 0xFF byte, which the byte base type decodes as null
        val features = FitSleepDataRaw.decodeFeatures(row(movement = 0, heartRate = 0x57FF))!!

        assertEquals(FitSleepDataRaw.halfToFloat(0x57FF), features[FitSleepDataRaw.HEART_RATE_INDEX], 0f)
    }

    @Test
    fun decodeFeatures_rejectsRowsOfAnotherSize() {
        assertNull(FitSleepDataRaw.decodeFeatures(null))
        assertNull(FitSleepDataRaw.decodeFeatures(42))
        assertNull(FitSleepDataRaw.decodeFeatures(field(0x3C00, 0x3C00)))
    }

    @Test
    fun heartRate_onlyWhenPlausible() {
        assertEquals(52f, FitSleepDataRaw.heartRateOf(FitSleepDataRaw.decodeFeatures(row(0, 0x5280)))!!, 0f)
        // 0 and 300 bpm are not a heart rate
        assertNull(FitSleepDataRaw.heartRateOf(FitSleepDataRaw.decodeFeatures(row(0, 0x0000))))
        assertNull(FitSleepDataRaw.heartRateOf(FitSleepDataRaw.decodeFeatures(row(0, 0x5CB0))))
        assertNull(FitSleepDataRaw.heartRateOf(null))
    }

    @Test
    fun movement_zeroWhenStill_nullWhenUnusable() {
        assertEquals(0f, FitSleepDataRaw.movementOf(FitSleepDataRaw.decodeFeatures(row(0x0000, 0x5280)))!!, 0f)
        assertNull(FitSleepDataRaw.movementOf(FitSleepDataRaw.decodeFeatures(row(0xBC00, 0x5280))))
        assertNull(FitSleepDataRaw.movementOf(FitSleepDataRaw.decodeFeatures(row(0x7E00, 0x5280))))
    }
}
