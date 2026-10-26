/*  Copyright (C) 2022 José Rebelo

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.huami;

import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.net.Uri;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.devices.huami.HuamiFWHelper;
import nodomain.freeyourgadget.gadgetbridge.service.SleepAsAndroidSender;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.btle.actions.WriteAction;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class HuamiSupportTest {
    @Test
    public void testSetCurrentTime() {
        final HuamiSupport huamiSupport = createSupport();
        final TransactionBuilder testTransactionBuilder = huamiSupport.createTransactionBuilder("test");

        huamiSupport.setCurrentTime(testTransactionBuilder);
        final WriteAction action = (WriteAction) testTransactionBuilder.getTransaction().getActions().get(0);

        Assert.assertArrayEquals(new byte[]{-26, 7, 12, 15, 20, 38, 53, 4, 0, 0, 4}, action.getValue());
    }

    @Test
    public void testRawSensorAccelType1() {
        final HuamiSupport huamiSupport = createSupport();
        final SleepAsAndroidSender sender = mock(SleepAsAndroidSender.class);
        huamiSupport.sleepAsAndroidSender = sender;

        // Mi Band 4 (fw V1.0.9.70): type 0x01, index, then int16 x/y/z samples, 10 per packet
        huamiSupport.handleRawSensorData(GB.hexStringToByteArray(
                "0100BEFF7D018AFFC5FF1001C0FFC4FFE1001100DAFFFF00500089FF9E0076005DFF3E00BF0079FFEAFF990096FFBBFF8E007CFFF6FF980041FF0500B900"));
        // 11 samples
        huamiSupport.handleRawSensorData(GB.hexStringToByteArray(
                "0103BCFEF6FFDBFF14FF74FF030009FF5AFF3C000EFFF0FF350009FF9A00200006FFE0FF0A00F9FE1000250007FF0200340003FFDEFF3A0004FFEFFF370002FFFAFF3400"));

        final ArgumentCaptor<Float> x = ArgumentCaptor.forClass(Float.class);
        final ArgumentCaptor<Float> y = ArgumentCaptor.forClass(Float.class);
        final ArgumentCaptor<Float> z = ArgumentCaptor.forClass(Float.class);
        verify(sender, times(21)).onAccelChanged(x.capture(), y.capture(), z.capture());

        // first sample: x=-66 y=381 z=-118, 1 g ~ 256
        Assert.assertEquals(2.529f, x.getAllValues().get(0), 0.01f);
        Assert.assertEquals(-14.600f, y.getAllValues().get(0), 0.01f);
        Assert.assertEquals(4.522f, z.getAllValues().get(0), 0.01f);

        // wrist mostly at rest: average magnitude close to 1 g
        double sum = 0;
        for (int i = 0; i < 21; i++) {
            final float gx = x.getAllValues().get(i), gy = y.getAllValues().get(i), gz = z.getAllValues().get(i);
            sum += Math.sqrt(gx * gx + gy * gy + gz * gz);
        }
        Assert.assertEquals(9.81, sum / 21, 2.5);
    }

    private HuamiSupport createSupport() {
        return new HuamiSupport() {
            @Override
            public BluetoothGattCharacteristic getCharacteristic(final UUID uuid) {
                return new BluetoothGattCharacteristic(null, 0, 0);
            }

            @Override
            public HuamiFWHelper createFWHelper(final Uri uri, final Context context) throws IOException {
                return null;
            }

            @Override
            public Calendar createCalendar() {
                // 2022-12-15 20:38:53 GMT+1
                final Instant instant = Instant.ofEpochMilli(1671133133000L);
                final ZonedDateTime zdt = ZonedDateTime.ofInstant(instant, ZoneId.of("Europe/Paris"));
                return GregorianCalendar.from(zdt);
            }
        };
    }
}
