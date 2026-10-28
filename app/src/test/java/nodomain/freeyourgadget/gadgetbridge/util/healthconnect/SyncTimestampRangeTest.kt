package nodomain.freeyourgadget.gadgetbridge.util.healthconnect

import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.devices.XiaomiDailySummarySampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.MiBand8Coordinator
import nodomain.freeyourgadget.gadgetbridge.entities.HealthConnectSyncState
import nodomain.freeyourgadget.gadgetbridge.entities.XiaomiDailySummarySample
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * The orchestrator skips a data type whose range does not satisfy start < end. These tests pin the
 * range [HealthConnectUtils.getSyncTimestampRange] resolves for resting heart rate.
 */
class SyncTimestampRangeTest : TestBase() {
    private val coordinator = MiBand8Coordinator()
    private val dataType = HealthConnectPermissionManager.HealthConnectDataType.RESTING_HEART_RATE
    private val sampleMillis = 1_780_000_000_000L

    private lateinit var device: GBDevice

    @Before
    fun setUpDevice() {
        device = createDummyGDevice("00:00:00:00:00:41")
        DBHelper.getDevice(device, daoSession)
        XiaomiDailySummarySampleProvider(device, daoSession).persistSamples(
            listOf(XiaomiDailySummarySample().apply {
                timestamp = sampleMillis
                steps = 1000
                hrResting = 58
            }),
            context
        )
    }

    private fun range(): Pair<Instant, Instant> {
        val range = HealthConnectUtils.getSyncTimestampRange(context, device, coordinator, dataType)
        assertNotNull(range)
        return range!!
    }

    @Test
    fun singleSampleWithoutSyncState_givesANonEmptyRange() {
        val (start, end) = range()
        assertEquals(Instant.ofEpochMilli(sampleMillis), end)
        assertTrue("start $start must precede end $end", start.isBefore(end))
    }

    @Test
    fun singleSampleAlreadySynced_givesAnEmptyRange() {
        val deviceId = DBHelper.getDevice(device, daoSession).id!!
        daoSession.healthConnectSyncStateDao.insertOrReplace(
            HealthConnectSyncState(deviceId, dataType.name, sampleMillis / 1000)
        )

        val (start, end) = range()
        assertEquals(start, end)
    }
}
