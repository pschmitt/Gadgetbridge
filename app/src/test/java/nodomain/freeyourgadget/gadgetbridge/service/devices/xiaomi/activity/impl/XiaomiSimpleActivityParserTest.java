/*  Copyright (C) 2026 Dany Mestas

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
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.activity.impl;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries;

public class XiaomiSimpleActivityParserTest {
    @Test
    public void shortsAreUnsigned() {
        final XiaomiSimpleActivityParser parser = new XiaomiSimpleActivityParser.Builder()
                .setHeaderSize(1)
                .addShort(ActivitySummaryEntries.STEPS, ActivitySummaryEntries.UNIT_STEPS)
                .addShort(ActivitySummaryEntries.PROJECTED_TIME_MARATHON, ActivitySummaryEntries.UNIT_SECONDS, 0.5)
                .build();
        final ByteBuffer buf = ByteBuffer.wrap(new byte[]{
                (byte) 0xC0,
                (byte) 0x50, (byte) 0xC3, // 50000
                (byte) 0xFE, (byte) 0xFF, // 65534
        }).order(ByteOrder.LITTLE_ENDIAN);
        final BaseActivitySummary summary = new BaseActivitySummary();

        parser.parse(summary, buf);

        final ActivitySummaryData data = ActivitySummaryData.fromJson(summary.getSummaryData());
        assertEquals(50000, data.getNumber(ActivitySummaryEntries.STEPS, null).intValue());
        assertEquals(32767, data.getNumber(ActivitySummaryEntries.PROJECTED_TIME_MARATHON, null).intValue());
    }
}
