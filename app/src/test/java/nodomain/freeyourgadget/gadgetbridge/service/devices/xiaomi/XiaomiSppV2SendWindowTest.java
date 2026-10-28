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
package nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi;

import org.junit.Assert;
import org.junit.Test;

public class XiaomiSppV2SendWindowTest {

    private static void send(final XiaomiSppV2SendWindow window, final int count) {
        for (int i = 0; i < count; i++) {
            final int sequenceNumber = window.nextSequenceNumber();
            window.onSent(sequenceNumber, new byte[]{(byte) sequenceNumber});
        }
    }

    @Test
    public void ackIsCumulative() {
        final XiaomiSppV2SendWindow window = new XiaomiSppV2SendWindow();
        send(window, 5);

        Assert.assertEquals(3, window.onAck(2));
        Assert.assertEquals(3, window.getOldestUnacked());
        Assert.assertEquals(2, window.getUnackedFrames().size());
    }

    @Test
    public void ignoresAnAckForNoUnackedFrame() {
        final XiaomiSppV2SendWindow window = new XiaomiSppV2SendWindow();
        send(window, 5);
        window.onAck(2);

        Assert.assertEquals(0, window.onAck(1));
        Assert.assertEquals(0, window.onAck(9));
        Assert.assertEquals(3, window.getOldestUnacked());
    }

    @Test
    public void sequenceNumbersWrap() {
        final XiaomiSppV2SendWindow window = new XiaomiSppV2SendWindow();
        send(window, 254);
        window.onAck(253);
        send(window, 4);

        Assert.assertEquals(254, window.getOldestUnacked());
        Assert.assertEquals(3, window.onAck(0));
        Assert.assertEquals(1, window.getOldestUnacked());
        Assert.assertEquals(2, window.nextSequenceNumber());
    }

    @Test
    public void forgetsTheOldestFramesPastTheLimit() {
        final XiaomiSppV2SendWindow window = new XiaomiSppV2SendWindow();
        send(window, XiaomiSppV2SendWindow.MAX_TRACKED_FRAMES + 2);

        Assert.assertEquals(2, window.getOldestUnacked());
        Assert.assertEquals(XiaomiSppV2SendWindow.MAX_TRACKED_FRAMES, window.getUnackedFrames().size());
    }

    @Test
    public void clearRestartsTheSequence() {
        final XiaomiSppV2SendWindow window = new XiaomiSppV2SendWindow();
        send(window, 3);
        window.clear();

        Assert.assertTrue(window.isEmpty());
        Assert.assertEquals(-1, window.getOldestUnacked());
        Assert.assertEquals(0, window.nextSequenceNumber());
    }
}
