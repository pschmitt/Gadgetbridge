package nodomain.freeyourgadget.gadgetbridge.impl

import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class GBDeviceServiceTest : TestBase() {
    @Test
    fun isBuiltFromTheGivenContext() {
        val service = GBDeviceService(app)
        assertNull(service.device)

        val device = createDummyGDevice("AA:BB:CC:DD:EE:FF")
        assertSame(device, service.forDevice(device).device)
    }
}
