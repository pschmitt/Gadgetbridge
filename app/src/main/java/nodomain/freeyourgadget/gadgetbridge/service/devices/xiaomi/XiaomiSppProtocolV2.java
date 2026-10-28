/*  Copyright (C) 2024 Yoran Vulker

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.VisibleForTesting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import nodomain.freeyourgadget.gadgetbridge.BuildConfig;
import nodomain.freeyourgadget.gadgetbridge.service.btbr.BtBRAction;
import nodomain.freeyourgadget.gadgetbridge.service.btbr.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.btbr.actions.FunctionAction;
import nodomain.freeyourgadget.gadgetbridge.service.btbr.actions.WriteAction;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

import static nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.XiaomiSppPacketV2.PACKET_PREAMBLE;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.XiaomiSppPacketV2.PACKET_TYPE_ACK;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.XiaomiSppPacketV2.PACKET_TYPE_DATA;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.XiaomiSppPacketV2.PACKET_TYPE_NACK;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.XiaomiSppPacketV2.PACKET_TYPE_SESSION_CONFIG;

/**
 * Data packets are delivered go-back-N: the watch accepts them strictly in sequence and acks
 * cumulatively, so a packet that reaches it out of order is dropped together with everything
 * sent after it until the missing one is resent.
 */
public class XiaomiSppProtocolV2 extends AbstractXiaomiSppProtocol {
    private static final Logger LOG = LoggerFactory.getLogger(XiaomiSppProtocolV2.class);

    /// Same value as the send timeout announced in the session config.
    static final long RESEND_TIMEOUT_MILLIS = 10_000L;
    /// Resend timeouts in a row without any ack before the link is considered dead.
    static final int MAX_TIMEOUTS_WITHOUT_ACK = 3;

    private final XiaomiSppSupport support;
    private final XiaomiSppV2SendWindow sendWindow = new XiaomiSppV2SendWindow();
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private final Runnable timeoutRunnable = this::onResendTimeout;
    private boolean timeoutPending = false;
    private int timeoutsWithoutAck = 0;

    public XiaomiSppProtocolV2(final XiaomiSppSupport support) {
        this.support = support;
    }

    @VisibleForTesting
    protected void queue(final String taskName, final BtBRAction action) {
        support.commsSupport.createTransactionBuilder(taskName)
                .add(action)
                .queue();
    }

    /// Ends the connection so the regular reconnect logic takes over.
    @VisibleForTesting
    protected void dropConnection() {
        support.commsSupport.dropConnection();
    }

    /// Whether to pretend the next frame in the given direction got lost (debug builds only).
    @VisibleForTesting
    protected boolean simulatePacketLoss(final boolean outbound) {
        if (!BuildConfig.DEBUG) {
            return false;
        }
        final int percentage = support.commsSupport.getDevicePrefs().getInt(
                outbound ? "pref_debug_drop_packet_percentage_out" : "pref_debug_drop_packet_percentage_in",
                0
        );
        return percentage > 0 && ThreadLocalRandom.current().nextInt(100) < percentage;
    }

    private void sendAck(final int sequenceNumber) {
        final byte[] frame = new XiaomiSppPacketV2.AckPacket.Builder()
                .setSequenceNumber(sequenceNumber)
                .build()
                .encode(null);
        queue(String.format(Locale.ROOT, "send ack for %d", sequenceNumber),
                new FunctionAction(socket -> writeFrame(socket, frame)));
    }

    private boolean writeFrame(final BluetoothSocket socket, final byte[] frame) {
        if (simulatePacketLoss(true)) {
            LOG.warn("Simulating dropped outbound packet type={} seq={}", frame[2] & 0x0f, frame[3] & 0xff);
            return true;
        }
        return new WriteAction(frame).run(socket);
    }

    @Override
    public void reset() {
        dispose();
    }

    @Override
    public void dispose() {
        synchronized (timeoutRunnable) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            timeoutPending = false;
            timeoutsWithoutAck = 0;
        }
        sendWindow.clear();
    }

    @Override
    public int findNextPacketOffset(byte[] buffer) {
        for (int i = 1; i < buffer.length; i++) {
            if (buffer[i] == PACKET_PREAMBLE[0])
                return i;
        }

        return -1;
    }

    @Override
    public ParseResult processPacket(byte[] rxBuf) {
        if (rxBuf.length < 8) {
            LOG.debug("processPacket(): not enough bytes in buffer to process packet (got {} of required {} bytes)",
                    rxBuf.length,
                    8);
            return new ParseResult(ParseResult.Status.Incomplete);
        }

        final ByteBuffer buffer = ByteBuffer.wrap(rxBuf).order(ByteOrder.LITTLE_ENDIAN);
        final byte[] headerMagic = new byte[PACKET_PREAMBLE.length];
        buffer.get(headerMagic);

        if (!Arrays.equals(PACKET_PREAMBLE, headerMagic)) {
            LOG.warn("processPacket(): invalid header magic (expected {}, got {})",
                    GB.hexdump(PACKET_PREAMBLE),
                    GB.hexdump(headerMagic));
            return new ParseResult(ParseResult.Status.Invalid);
        }

        buffer.get(); // flags and packet type
        buffer.get(); // packet sequence number
        final int packetSize = 8 + (buffer.getShort() & 0xffff);
        buffer.getShort(); // checksum

        if (rxBuf.length < packetSize) {
            LOG.debug("processPacket(): missing {} bytes (got {}/{} bytes)",
                    packetSize - rxBuf.length,
                    rxBuf.length,
                    packetSize);
            return new ParseResult(ParseResult.Status.Incomplete);
        }

        final XiaomiSppPacketV2 decodedPacket = XiaomiSppPacketV2.decode(rxBuf);
        if (decodedPacket != null && simulatePacketLoss(false)) {
            LOG.warn("Simulating dropped inbound packet type={} seq={}",
                    decodedPacket.getPacketType(),
                    decodedPacket.getSequenceNumber());
        } else if (decodedPacket != null) {
            switch (decodedPacket.getPacketType()) {
                case PACKET_TYPE_SESSION_CONFIG:
                    // TODO handle device's session config
                    LOG.info("Received session config, opcode={}", ((XiaomiSppPacketV2.SessionConfigPacket)decodedPacket).getOpCode());
                    support.getAuthService().startEncryptedHandshake();
                    break;
                case PACKET_TYPE_DATA:
                    XiaomiSppPacketV2.DataPacket dataPacket = (XiaomiSppPacketV2.DataPacket) decodedPacket;
                    try {
                        support.onPacketReceived(dataPacket.getChannel(), dataPacket.getPayloadBytes(support.getAuthService()));
                    } catch (final Exception ex) {
                        LOG.error("Exception while handling received packet", ex);
                    }
                    // TODO: only directly ack protobuf packets, bulk ack others
                    sendAck(decodedPacket.getSequenceNumber());
                    break;
                case PACKET_TYPE_ACK:
                    LOG.debug("receive ack for packet {}", decodedPacket.getSequenceNumber());
                    onAck(decodedPacket.getSequenceNumber());
                    break;
                case PACKET_TYPE_NACK:
                    onNack(decodedPacket.getSequenceNumber());
                    break;
                default:
                    LOG.warn("Unhandled packet with type {} (decoded type {})", decodedPacket.getPacketType(), decodedPacket.getClass().getSimpleName());
                    break;
            }
        }

        return new ParseResult(ParseResult.Status.Complete, packetSize);
    }

    private void onAck(final int sequenceNumber) {
        if (sendWindow.onAck(sequenceNumber) > 0) {
            onAckProgress();
        }
    }

    private void onNack(final int expectedSequenceNumber) {
        onAck((expectedSequenceNumber - 1) & 0xff);

        if (sendWindow.getOldestUnacked() != expectedSequenceNumber) {
            LOG.warn("receive nack, watch expects packet {}, which is not pending", expectedSequenceNumber);
            return;
        }

        LOG.warn("receive nack, watch expects packet {}, resending", expectedSequenceNumber);
        queueResend(String.format(Locale.ROOT, "resend from %d", expectedSequenceNumber));
    }

    private void queueResend(final String taskName) {
        queue(taskName, new FunctionAction(this::resendUnacked));
    }

    private boolean resendUnacked(final BluetoothSocket socket) {
        final List<byte[]> frames = sendWindow.getUnackedFrames();
        LOG.debug("resending {} unacked packets", frames.size());
        for (final byte[] frame : frames) {
            if (!writeFrame(socket, frame)) {
                return false;
            }
        }
        armResendTimeout();
        return true;
    }

    private void onAckProgress() {
        synchronized (timeoutRunnable) {
            timeoutsWithoutAck = 0;
            timeoutHandler.removeCallbacks(timeoutRunnable);
            timeoutPending = false;
        }
        if (!sendWindow.isEmpty()) {
            armResendTimeout();
        }
    }

    private void armResendTimeout() {
        synchronized (timeoutRunnable) {
            if (!timeoutPending) {
                timeoutPending = true;
                timeoutHandler.postDelayed(timeoutRunnable, RESEND_TIMEOUT_MILLIS);
            }
        }
    }

    private void onResendTimeout() {
        final int timeouts;
        synchronized (timeoutRunnable) {
            timeoutPending = false;
            if (sendWindow.isEmpty()) {
                timeoutsWithoutAck = 0;
                return;
            }
            timeouts = ++timeoutsWithoutAck;
        }

        if (timeouts >= MAX_TIMEOUTS_WITHOUT_ACK) {
            LOG.warn("no ack for packet {} after {} timeouts, dropping the connection", sendWindow.getOldestUnacked(), timeouts);
            dispose();
            dropConnection();
            return;
        }

        LOG.warn("no ack for packet {} within {} ms, resending", sendWindow.getOldestUnacked(), RESEND_TIMEOUT_MILLIS);
        queueResend(String.format(Locale.ROOT, "resend from %d after timeout", sendWindow.getOldestUnacked()));
    }

    @Override
    public boolean initializeSession() {
        dispose();
        queue("send session config", new WriteAction(XiaomiSppPacketV2.newSessionConfigPacketBuilder()
                .setOpCode(XiaomiSppPacketV2.SessionConfigPacket.OPCODE_START_SESSION_REQUEST)
                .setSequenceNumber(0)
                .build()
                .encode(null)));
        return false;
    }

    @Override
    public void writePacket(final TransactionBuilder builder, final XiaomiChannelHandler.Channel channel, final byte[] payloadBytes) {
        builder.add(createDataPacketAction(channel, payloadBytes));
    }

    /**
     * The payload is encrypted here, but the sequence number is only taken when the action runs
     * on the queue's write thread, so packets reach the socket in sequence whichever thread
     * queued them.
     */
    BtBRAction createDataPacketAction(final XiaomiChannelHandler.Channel channel, final byte[] payloadBytes) {
        final byte[] packetPayload = XiaomiSppPacketV2.newDataPacketBuilder()
                .setChannel(channel)
                .setOpCode(XiaomiSppPacketV2.DataPacket.getOpCodeForChannel(channel))
                .setPayload(payloadBytes)
                .build()
                .getPacketPayloadBytes(support.getAuthService());

        return new FunctionAction(socket -> {
            final int sequenceNumber = sendWindow.nextSequenceNumber();
            final byte[] frame = XiaomiSppPacketV2.encodeFrame(PACKET_TYPE_DATA, sequenceNumber, packetPayload);
            sendWindow.onSent(sequenceNumber, frame);
            final boolean written = writeFrame(socket, frame);
            armResendTimeout();
            return written;
        });
    }
}
