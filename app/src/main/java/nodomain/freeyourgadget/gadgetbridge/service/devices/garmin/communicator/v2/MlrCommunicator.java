package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.communicator.v2;

import android.bluetooth.BluetoothGatt;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.LinkedList;

/**
 * As per <a href="https://gadgetbridge.org/internals/specifics/garmin-protocol/#multi-link-reliable-protocol">the docs.</a>
 */
public class MlrCommunicator {
    private final Logger LOG;

    public static final int MLR_FLAG_MASK = 0x80;
    public static final int HANDLE_MASK = 0x70;
    public static final int HANDLE_SHIFT = 4;
    public static final int REQ_NUM_MASK = 0x0F;
    public static final int SEQ_NUM_MASK = 0x3F;

    private static final int MAX_SEQ_NUM = 0x3F;
    private static final int INITIAL_MAX_UNACKED_SEND = 0x20;
    private static final int MAX_RETRANSMISSION_TIMEOUT = 20000;
    private static final int INITIAL_RETRANSMISSION_TIMEOUT = 1000;
    private static final int ACK_TIMEOUT = 20;
    private static final int ACK_TRIGGER_THRESHOLD = 5;

    private final int handle;

    private final Object lock = new Object();

    @GuardedBy("lock")
    private int maxPacketSize;

    @GuardedBy("lock")
    private int lastSendAck = 0x00;
    @GuardedBy("lock")
    private int nextSendSeq = 0x00;
    @GuardedBy("lock")
    private int nextRcvSeq = 0x00;
    @GuardedBy("lock")
    private int lastRcvAck = 0x00;
    @GuardedBy("lock")
    private int maxNumUnackedSend = INITIAL_MAX_UNACKED_SEND;
    @GuardedBy("lock")
    private int retransmissionTimeout = INITIAL_RETRANSMISSION_TIMEOUT;

    @GuardedBy("lock")
    private final LinkedList<Fragment> fragmentQueue = new LinkedList<>();
    @GuardedBy("lock")
    private final Fragment[] sentFragments = new Fragment[MAX_SEQ_NUM + 1];

    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private final Runnable ackRunnable = this::onAckTimeout;
    private final Runnable retransmissionRunnable = this::onRetransmissionTimeout;

    private volatile boolean closed = false;

    private final MessageSender messageSender;
    private final MessageReceiver messageReceiver;

    @GuardedBy("lock")
    @Nullable
    private AckListener ackListener;

    public MlrCommunicator(final int handle,
                           final int maxPacketSize,
                           final MessageSender messageSender,
                           final MessageReceiver messageReceiver) {
        this.LOG = LoggerFactory.getLogger(MlrCommunicator.class.getName() + "(" + handle + ")");
        this.handle = handle;
        this.maxPacketSize = maxPacketSize;
        this.messageSender = messageSender;
        this.messageReceiver = messageReceiver;
    }

    public void setMaxPacketSize(final int maxPacketSize) {
        synchronized (lock) {
            this.maxPacketSize = maxPacketSize;
        }
    }

    public void setAckListener(@Nullable final AckListener listener) {
        synchronized (lock) {
            this.ackListener = listener;
        }
    }

    public void sendMessage(final String taskName, final byte[] message) {
        if (message == null || message.length == 0) {
            return;
        }

        LOG.debug(
                "Queuing MLR message for '{}' ({} bytes)",
                taskName,
                message.length
        );

        synchronized (lock) {
            int remainingBytes = message.length;
            int i = 0;
            if (remainingBytes > maxPacketSize - 2) {
                int position = 0;
                while (remainingBytes > 0) {
                    final byte[] fragment = Arrays.copyOfRange(message, position, position + Math.min(remainingBytes, maxPacketSize - 2));
                    fragmentQueue.add(new Fragment(taskName, i++, fragment, 0));
                    position += fragment.length;
                    remainingBytes -= fragment.length;
                }
            } else {
                fragmentQueue.add(new Fragment(taskName, 0, message, 0));
            }

            runProtocolLocked();
        }
    }

    public void onPacketReceived(final byte[] packet) {
        if (packet.length < 2) {
            LOG.warn("MLR packet too short: {}", packet.length);
            return;
        }

        // MLR header
        final int byte0 = packet[0] & 0xFF;
        final int byte1 = packet[1] & 0xFF;

        if ((byte0 & MLR_FLAG_MASK) == 0) {
            LOG.error("Received non-MLR packet");
            return;
        }

        final int packetHandle = (byte0 & HANDLE_MASK) >> HANDLE_SHIFT;
        final int reqNum = ((byte0 & REQ_NUM_MASK) << 2) | ((byte1 >> 6) & 0x03);
        final int seqNum = byte1 & SEQ_NUM_MASK;

        if (packetHandle != (handle & 0x07)) {
            LOG.error("MLR packet for wrong handle: expected {}, got {}", handle & 0x07, packetHandle);
            return;
        }

        LOG.debug(
                "MLR packet received: reqNum={}, seqNum={}, dataLen={}",
                reqNum,
                seqNum,
                packet.length - 2
        );

        final byte[] data;
        synchronized (lock) {
            data = processPacketLocked(reqNum, seqNum, packet);
        }

        if (data != null) {
            try {
                messageReceiver.onDataReceived(data);
            } catch (final Exception e) {
                LOG.error("Receiver failed to handle MLR data", e);
            }
        }

        synchronized (lock) {
            if (data != null) {
                scheduleAckIfNeededLocked();
            }
            runProtocolLocked();
        }
    }

    /**
     * Updates the ACK and sequence state for a received packet. Returns the payload when the
     * packet is in sequence, or null.
     */
    @GuardedBy("lock")
    @Nullable
    private byte[] processPacketLocked(final int reqNum, final int seqNum, final byte[] packet) {
        // Process ACK if request number changed
        if (reqNum != lastRcvAck) {
            processAckLocked(reqNum);
        }

        if (packet.length <= 2) {
            return null;
        }

        if (seqNum != nextRcvSeq) {
            LOG.warn("Out-of-sequence packet - expected {}, got {}", nextRcvSeq, seqNum);
            // Correct sequence will be retransmitted by sender
            // Regardless, re-send the expected ack since the sender shouldn't be sending these
            sendAckPacketLocked();
            return null;
        }

        nextRcvSeq = (nextRcvSeq + 1) % (MAX_SEQ_NUM + 1);
        return Arrays.copyOfRange(packet, 2, packet.length);
    }

    @GuardedBy("lock")
    private void processAckLocked(final int reqNum) {
        final int numAcked = (reqNum - lastRcvAck + MAX_SEQ_NUM + 1) % (MAX_SEQ_NUM + 1);
        final int numUnacked = (nextSendSeq - lastRcvAck + MAX_SEQ_NUM + 1) % (MAX_SEQ_NUM + 1);

        LOG.debug(
                "Processing ACK: reqNum={}, numAcked={}, numUnacked={}, will expire fragments [{}, {}]",
                reqNum,
                numAcked,
                numUnacked,
                lastRcvAck,
                reqNum - 1
        );

        // Stop retransmission timer
        timeoutHandler.removeCallbacks(retransmissionRunnable);

        // Remove acked messages from the array
        int ackedBytes = 0;
        for (int i = lastRcvAck; i != reqNum; i = (i + 1) % (MAX_SEQ_NUM + 1)) {
            if (sentFragments[i] == null) {
                LOG.error("Attempting to expire null fragment at index {}", i);
            } else {
                ackedBytes += sentFragments[i].data().length;
            }
            sentFragments[i] = null;
        }

        lastRcvAck = reqNum;

        // Recover from a retransmission backoff
        retransmissionTimeout = INITIAL_RETRANSMISSION_TIMEOUT;
        maxNumUnackedSend = Math.min(maxNumUnackedSend + numAcked, INITIAL_MAX_UNACKED_SEND);

        if (ackListener != null && ackedBytes > 0) {
            ackListener.onAcked(ackedBytes, fragmentQueue.isEmpty() && lastRcvAck == nextSendSeq);
        }

        // Restart retransmission timer if there are still unacked packets
        if (lastRcvAck != nextSendSeq) {
            startRetransmissionTimerLocked();
        }
    }

    @GuardedBy("lock")
    private void scheduleAckIfNeededLocked() {
        timeoutHandler.removeCallbacks(ackRunnable);

        final int numRcvdUnacked = (nextRcvSeq - lastSendAck + MAX_SEQ_NUM + 1) % (MAX_SEQ_NUM + 1);
        if (numRcvdUnacked >= ACK_TRIGGER_THRESHOLD) {
            sendAckPacketLocked();
        } else {
            timeoutHandler.postDelayed(ackRunnable, ACK_TIMEOUT);
            LOG.debug("Started ack timer: {}ms", ACK_TIMEOUT);
        }
    }

    private void onAckTimeout() {
        synchronized (lock) {
            sendAckPacketLocked();
        }
    }

    @GuardedBy("lock")
    private void sendAckPacketLocked() {
        if (closed) {
            LOG.warn("Attempted to send ack packet after closed");
            return;
        }

        timeoutHandler.removeCallbacks(ackRunnable);

        // Send ACK-only packet (no data)
        byte[] packet = createPacket(nextRcvSeq, 0, new byte[0]);
        messageSender.sendPacket("ack reqNum=" + nextRcvSeq, packet);
        lastSendAck = nextRcvSeq;
        LOG.debug("Sent ACK packet: reqNum={}", nextRcvSeq);
    }

    @GuardedBy("lock")
    private void runProtocolLocked() {
        if (closed) {
            LOG.warn("Attempted to run protocol after closed");
            return;
        }

        Fragment fragment;
        while ((fragment = fragmentQueue.peek()) != null) {
            final int numSentUnacked = (nextSendSeq - lastRcvAck + MAX_SEQ_NUM + 1) % (MAX_SEQ_NUM + 1);
            if (numSentUnacked >= maxNumUnackedSend) {
                LOG.debug("Cannot send more packets: {} unacked, max {}", numSentUnacked, maxNumUnackedSend);
                return;
            }
            fragmentQueue.poll();

            // Update the fragment with the current reqNum before storing it
            final Fragment fragmentWithReqNum = new Fragment(fragment.taskName, fragment.num, fragment.data, nextRcvSeq);
            final byte[] packet = createPacket(nextRcvSeq, nextSendSeq, fragment.data);
            messageSender.sendPacket(fragment.taskName + " (" + fragment.num + ")", packet);
            sentFragments[nextSendSeq] = fragmentWithReqNum;

            nextSendSeq = (nextSendSeq + 1) % (MAX_SEQ_NUM + 1);

            // Start retransmission timer if this is the first unacked packet
            if (numSentUnacked == 0) {
                startRetransmissionTimerLocked();
            }

            LOG.debug("Sent MLR packet: seqNum={}, dataLen={}", (nextSendSeq - 1 + MAX_SEQ_NUM + 1) % (MAX_SEQ_NUM + 1), fragment.data.length);
        }
    }

    private byte[] createPacket(final int reqNum, final int seqNum, final byte[] data) {
        byte[] packet = new byte[2 + data.length];

        // First byte: MLR flag (1) + handle (3 bits) + reqNum high bits (4 bits)
        packet[0] = (byte) (MLR_FLAG_MASK | ((handle & 0x07) << HANDLE_SHIFT) | ((reqNum >> 2) & REQ_NUM_MASK));

        // Second byte: reqNum low bits (2 bits) + seqNum (6 bits)
        packet[1] = (byte) (((reqNum & 0x03) << 6) | (seqNum & SEQ_NUM_MASK));

        // Data
        System.arraycopy(data, 0, packet, 2, data.length);

        return packet;
    }

    @GuardedBy("lock")
    private void startRetransmissionTimerLocked() {
        timeoutHandler.removeCallbacks(retransmissionRunnable);
        timeoutHandler.postDelayed(retransmissionRunnable, retransmissionTimeout);

        LOG.debug("Started retransmission timer: {}ms", retransmissionTimeout);
    }

    private void onRetransmissionTimeout() {
        synchronized (lock) {
            if (closed) {
                LOG.warn("Attempted to retransmission timeout after closed");
                return;
            }

            LOG.debug("Retransmission timeout expired");

            // Backoff retransmission timeout and reduce the maximum unacked
            retransmissionTimeout = Math.min(retransmissionTimeout * 2, MAX_RETRANSMISSION_TIMEOUT);
            maxNumUnackedSend = Math.max(1, maxNumUnackedSend / 2);

            LOG.debug(
                    "Retransmission: timeout={}ms, maxUnacked={}, will re-send fragments [{}, {}]",
                    retransmissionTimeout,
                    maxNumUnackedSend,
                    lastRcvAck,
                    nextSendSeq - 1
            );

            for (int i = lastRcvAck; i != nextSendSeq; i = (i + 1) % (MAX_SEQ_NUM + 1)) {
                LOG.debug("Re-sending fragment {}", i);
                final Fragment fragment = sentFragments[i];
                if (fragment == null) {
                    LOG.error("Attempting to re-send null fragment at index {}", i);
                    continue;
                }
                // Use the original reqNum that was stored when the fragment was first sent
                final byte[] packet = createPacket(fragment.reqNum, i, fragment.data);
                messageSender.sendPacket("retransmission " + fragment.taskName + " (" + fragment.num + ")", packet);
            }

            startRetransmissionTimerLocked();
        }
    }

    public void close() {
        LOG.debug("Closing MLR communicator");

        synchronized (lock) {
            closed = true;

            timeoutHandler.removeCallbacksAndMessages(null);

            fragmentQueue.clear();
        }
    }

    public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
        if (newState != BluetoothGatt.STATE_CONNECTED) {
            synchronized (lock) {
                closed = true;
                timeoutHandler.removeCallbacksAndMessages(null);
            }
        }
    }

    public interface MessageSender {
        void sendPacket(String taskName, byte[] packet);
    }

    public interface MessageReceiver {
        void onDataReceived(final byte[] data);
    }

    public interface AckListener {
        /**
         * Called for each acknowledgement, with the number of data bytes that it acknowledges, and whether all
         * queued data is now acknowledged.
         */
        void onAcked(int ackedBytes, boolean allAcked);
    }

    private record Fragment(String taskName, int num, byte[] data, int reqNum) {
    }
}
