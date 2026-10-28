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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.bluetooth.BluetoothSocket;
import android.os.Looper;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.OutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.service.btbr.BtBRAction;
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.XiaomiChannelHandler.Channel;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

/**
 * Drives {@link XiaomiSppProtocolV2} with overrides that run every queued action at once on a socket
 * recording each write, standing in for the queue's single write thread.
 */
public class XiaomiSppProtocolV2Test extends TestBase {
    private final List<byte[]> written = new ArrayList<>();
    private boolean connectionDropped;
    private boolean dropOutbound;
    private boolean dropInbound;
    private BluetoothSocket socket;
    private XiaomiSppProtocolV2 protocol;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        socket = mock(BluetoothSocket.class);
        when(socket.getOutputStream()).thenReturn(new OutputStream() {
            @Override
            public void write(final int b) {
                written.add(new byte[]{(byte) b});
            }

            @Override
            public void write(final byte[] b, final int off, final int len) {
                written.add(Arrays.copyOfRange(b, off, off + len));
            }
        });

        protocol = new XiaomiSppProtocolV2(mock(XiaomiSppSupport.class)) {
            @Override
            protected void queue(final String taskName, final BtBRAction action) {
                action.run(socket);
            }

            @Override
            protected void dropConnection() {
                connectionDropped = true;
            }

            @Override
            protected boolean simulatePacketLoss(final boolean outbound) {
                return outbound ? dropOutbound : dropInbound;
            }
        };
    }

    /** Sends data packets with sequence numbers 0 to count - 1 and returns their frames. */
    private List<byte[]> sendPackets(final int count) {
        for (int i = 0; i < count; i++) {
            Assert.assertTrue(protocol.createDataPacketAction(Channel.Data, new byte[]{(byte) i}).run(socket));
        }
        final List<byte[]> frames = new ArrayList<>(written);
        written.clear();
        return frames;
    }

    /** Feeds bytes read from the socket the way the receive buffer does, one packet at a time. */
    private void receive(final String hex) {
        byte[] buffer = GB.hexStringToByteArray(hex);
        while (buffer.length > 0) {
            final AbstractXiaomiSppProtocol.ParseResult result = protocol.processPacket(buffer);
            Assert.assertEquals(AbstractXiaomiSppProtocol.ParseResult.Status.Complete, result.status);
            buffer = Arrays.copyOfRange(buffer, result.packetSize, buffer.length);
        }
    }

    private static int sequenceNumberOf(final byte[] frame) {
        return frame[3] & 0xff;
    }

    private static void assertFrames(final List<byte[]> expected, final List<byte[]> actual) {
        Assert.assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            Assert.assertArrayEquals("frame " + i, expected.get(i), actual.get(i));
        }
    }

    @Test
    public void resendsFromThePacketTheWatchExpectsAfterANack() {
        final List<byte[]> sent = sendPackets(21);

        // Redmi Watch 6 log from #6888: acks 8 and 9, then type 0 for 17.
        receive("A5A5010800000000A5A5010900000000A5A5001100000000");
        assertFrames(sent.subList(17, 21), written);
        written.clear();

        // Then ack 17 and type 0 for 18: everything from 18 on is resent, 17 is not.
        receive("A5A5011100000000A5A5001200000000");
        assertFrames(sent.subList(18, 21), written);
    }

    @Test
    public void ignoresANackForAPacketNotPending() {
        sendPackets(3);

        receive("A5A5000300000000");

        Assert.assertTrue(written.isEmpty());
    }

    @Test
    public void takesTheSequenceNumberWhenThePacketIsWritten() {
        final BtBRAction first = protocol.createDataPacketAction(Channel.Data, new byte[]{0x01});
        final BtBRAction second = protocol.createDataPacketAction(Channel.Data, new byte[]{0x02});

        Assert.assertTrue(second.run(socket));
        Assert.assertTrue(first.run(socket));

        Assert.assertEquals(2, written.size());
        Assert.assertEquals(0, sequenceNumberOf(written.get(0)));
        Assert.assertEquals(0x02, written.get(0)[written.get(0).length - 1]);
        Assert.assertEquals(1, sequenceNumberOf(written.get(1)));
        Assert.assertEquals(0x01, written.get(1)[written.get(1).length - 1]);
    }

    @Test
    public void resendsOnTimeoutAndDropsTheConnectionWhenNothingIsAcked() {
        final List<byte[]> sent = sendPackets(2);

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(XiaomiSppProtocolV2.RESEND_TIMEOUT_MILLIS));
        assertFrames(sent, written);
        written.clear();

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(XiaomiSppProtocolV2.RESEND_TIMEOUT_MILLIS));
        assertFrames(sent, written);
        Assert.assertFalse(connectionDropped);

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(XiaomiSppProtocolV2.RESEND_TIMEOUT_MILLIS));
        Assert.assertTrue(connectionDropped);
    }

    @Test
    public void anAckRestartsTheTimeoutCount() {
        sendPackets(3);

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(XiaomiSppProtocolV2.RESEND_TIMEOUT_MILLIS));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(XiaomiSppProtocolV2.RESEND_TIMEOUT_MILLIS));
        receive("A5A5010000000000");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(XiaomiSppProtocolV2.RESEND_TIMEOUT_MILLIS));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(XiaomiSppProtocolV2.RESEND_TIMEOUT_MILLIS));

        Assert.assertFalse(connectionDropped);
    }

    @Test
    public void staysQuietOnceEverythingIsAcked() {
        sendPackets(3);

        receive("A5A5010200000000");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(5 * XiaomiSppProtocolV2.RESEND_TIMEOUT_MILLIS));

        Assert.assertTrue(written.isEmpty());
        Assert.assertFalse(connectionDropped);
    }

    @Test
    public void resendsDataPacketsLostOnTheWayOut() {
        dropOutbound = true;
        Assert.assertTrue(sendPackets(3).isEmpty());
        dropOutbound = false;

        receive("A5A5000000000000");

        Assert.assertEquals(3, written.size());
        for (int i = 0; i < 3; i++) {
            Assert.assertEquals(i, sequenceNumberOf(written.get(i)));
        }
    }

    @Test
    public void ignoresAnAckLostOnTheWayIn() {
        final List<byte[]> sent = sendPackets(3);

        dropInbound = true;
        receive("A5A5010200000000");
        dropInbound = false;
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(XiaomiSppProtocolV2.RESEND_TIMEOUT_MILLIS));

        assertFrames(sent, written);
    }
}
