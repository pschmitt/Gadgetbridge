package nodomain.freeyourgadget.gadgetbridge.devices.qnscale

import org.junit.Assert
import org.junit.Test
import java.time.Instant

/**
 * GE Fit Plus frames in the layout of an HCI capture of the Fit Profile app. Address, weight,
 * resistance, timestamp and profile values are synthetic, with checksums recomputed.
 */
class QnScaleProtocolTest {
    private fun hex(s: String): ByteArray = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun bytes(vararg v: Int): ByteArray = v.map { it.toByte() }.toByteArray()

    private val va = QnScaleProtocol.PROTOCOL_TYPE_VA

    @Test
    fun `scale info selects factor 10 and the VA type`() {
        val info = QnScaleProtocol.parse(hex("1212ff112233445566452f0b0009051f6ae7"), 100f, null) as QnScaleProtocol.Frame.ScaleInfo
        Assert.assertEquals(10f, info.scaleFactor)
        Assert.assertEquals(va, info.protocolType)
    }

    @Test
    fun `only the final live frame is stable, with resistance in ohms`() {
        val settling = QnScaleProtocol.parse(hex("100fff01011f4001f4000000000074"), 10f, va) as QnScaleProtocol.Frame.LiveWeight
        Assert.assertFalse(settling.stable)

        val final = QnScaleProtocol.parse(hex("100fff01021f4001f401e000fa0050"), 10f, va) as QnScaleProtocol.Frame.LiveWeight
        Assert.assertTrue(final.stable)
        Assert.assertEquals(80.00f, final.weightKg, 0.001f)
        Assert.assertEquals(500, final.resistance1)
    }

    @Test
    fun `stored record decodes weight, resistance and UTC time`() {
        val stored = QnScaleProtocol.parse(hex("2314ff0909018075e8301f4001f401e000fa0085"), 10f, va) as QnScaleProtocol.Frame.StoredMeasurement
        Assert.assertEquals(80.00f, stored.weightKg, 0.001f)
        Assert.assertEquals(500, stored.resistance1)
        Assert.assertEquals(
            Instant.parse("2026-01-01T00:00:00Z").toEpochMilli(),
            QnScaleProtocol.qnEpochSecondsToMillis(stored.deviceEpochSeconds, va),
        )

        val weightOnly = QnScaleProtocol.parse(hex("2314ff0903018075e8301f68000000000000d7"), 10f, va) as QnScaleProtocol.Frame.StoredMeasurement
        Assert.assertEquals(80.40f, weightOnly.weightKg, 0.001f)
        Assert.assertEquals(0, weightOnly.resistance1)

        Assert.assertEquals(
            QnScaleProtocol.Frame.NoStoredMeasurements,
            QnScaleProtocol.parse(hex("2314ff0000000000000000000000000000000036"), 10f, va),
        )
    }

    @Test
    fun `user exchange matches the app`() {
        Assert.assertEquals(QnScaleProtocol.Frame.VaUserReply(4), QnScaleProtocol.parse(hex("a10604fe01aa"), 10f, va))
        Assert.assertArrayEquals(hex("a00d04fe0000000000000000af"), QnScaleProtocol.buildVaHello())
        Assert.assertArrayEquals(hex("a00d020103e8002806d60402a5"), QnScaleProtocol.buildVaProfile(175, 40, true))
        Assert.assertArrayEquals(hex("2206ff00032a"), QnScaleProtocol.buildVaStoredDataQuery(va))
        Assert.assertArrayEquals(hex("a206011f4008"), QnScaleProtocol.buildVaLastWeight(80.0f))
        Assert.assertArrayEquals(hex("2008ff8075e83034"), QnScaleProtocol.buildTimeSyncFrame(va, 820_540_800L))
        Assert.assertArrayEquals(hex("1f05ff1033"), QnScaleProtocol.buildStableAck(va))
    }

    @Test
    fun `VA config keeps the measurement flags clear`() {
        Assert.assertArrayEquals(hex("1309ff02100000002d"), QnScaleProtocol.buildConfigFrame(va, 0x02, 175, 40, true))
    }

    @Test
    fun `non-VA scales keep the classic live layout`() {
        // 71.68 kg = 0x1C00: byte 4 is 0x00, which in the VA layout would be the state byte.
        val frame = bytes(0x10, 0x0B, 0x05, 0x1C, 0x00, 0x01, 0x01, 0xF4, 0x01, 0xF4, 0x00)
        val live = QnScaleProtocol.parse(frame, 10f, 0x05.toByte()) as QnScaleProtocol.Frame.LiveWeight
        Assert.assertTrue(live.stable)
        Assert.assertEquals(71.68f, live.weightKg, 0.001f)
        Assert.assertEquals(500, live.resistance1)
    }

    @Test
    fun `only VA scales use the UTC 2000 epoch`() {
        Assert.assertEquals(946_684_800_000L, QnScaleProtocol.qnEpochSecondsToMillis(0, va))
        Assert.assertEquals(946_702_800_000L, QnScaleProtocol.qnEpochSecondsToMillis(0, 0x05.toByte()))
    }
}
