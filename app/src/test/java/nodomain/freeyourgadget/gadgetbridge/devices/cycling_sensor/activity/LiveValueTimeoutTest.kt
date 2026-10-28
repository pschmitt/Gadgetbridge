package nodomain.freeyourgadget.gadgetbridge.devices.cycling_sensor.activity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveValueTimeoutTest {

    private val stale = LiveValueTimeout.STALE_MILLIS

    @Test
    fun beforeAnyUpdate_neverExpires() {
        assertFalse(LiveValueTimeout().expire(1_000_000))
    }

    @Test
    fun withinTimeout_doesNotExpire() {
        val timeout = LiveValueTimeout().apply { onUpdate(10_000) }

        assertFalse(timeout.expire(10_000 + stale))
    }

    @Test
    fun silentLongerThanTimeout_expiresOnce() {
        val timeout = LiveValueTimeout().apply { onUpdate(10_000) }

        assertTrue(timeout.expire(10_000 + stale + 1))
        assertFalse("the value is reset a single time", timeout.expire(10_000 + stale + 2_000))
    }

    @Test
    fun updateAfterExpiry_armsAgain() {
        val timeout = LiveValueTimeout().apply { onUpdate(10_000) }
        timeout.expire(10_000 + stale + 1)

        timeout.onUpdate(20_000)

        assertFalse(timeout.expire(20_000 + stale))
        assertTrue(timeout.expire(20_000 + stale + 1))
    }
}
