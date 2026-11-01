package nodomain.freeyourgadget.gadgetbridge.util.healthconnect.syncers

import nodomain.freeyourgadget.gadgetbridge.model.TemperatureSample
import org.junit.Assert.assertEquals
import org.junit.Test

class TemperatureSyncerSkinDeltaTest {

    private fun skin(timestampMs: Long, celsius: Float): TemperatureSample =
        object : TemperatureSample {
            override fun getTimestamp(): Long = timestampMs
            override fun getTemperature(): Float = celsius
            override fun getTemperatureType(): Int = TemperatureSample.TYPE_SKIN
        }

    @Test
    fun deltasAreRelativeToTheBaseline() {
        val deltas = TemperatureSyncer.skinDeltas(
            listOf(skin(1_000L, 33.0f), skin(2_000L, 33.5f), skin(3_000L, 34.25f)),
            baselineC = 33.5,
            deviceName = "test-device"
        )

        assertEquals(listOf(1_000L, 2_000L, 3_000L), deltas.map { it.time.toEpochMilli() })
        assertEquals(listOf(-0.5, 0.0, 0.75), deltas.map { it.delta.inCelsius })
    }

    @Test
    fun implausibleDeltasAreSkipped() {
        val deltas = TemperatureSyncer.skinDeltas(
            listOf(skin(1_000L, 80f), skin(2_000L, 34f)),
            baselineC = 33.0,
            deviceName = "test-device"
        )

        assertEquals(listOf(2_000L), deltas.map { it.time.toEpochMilli() })
    }
}
