package nodomain.freeyourgadget.gadgetbridge.test;

import org.junit.Assert;
import org.junit.Test;

import nodomain.freeyourgadget.gadgetbridge.service.btle.GattCharacteristic;

public class MiscTest extends TestBase {
    @Test
    public void testGattCharacteristic() {
        GattCharacteristic desc = GattCharacteristic.lookup(GattCharacteristic.CHARACTERISTIC_HEART_RATE_CONTROL_POINT.uuid);
        Assert.assertEquals("Heart Rate Control Point", desc.prettyName);
    }
}
