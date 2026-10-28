package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.communicator;

import static nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLEDeviceSupport.calcMaxWriteChunk;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.google.gson.JsonElement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.ChecksumCalculator;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.GarminSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http.GarminJson;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http.GarminJsonException;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

public class GarminWatchAppCommunicator {
    private static final Logger LOG = LoggerFactory.getLogger(GarminWatchAppCommunicator.class);

    // The characteristics are the same as Communicator V1, but different service?
    public static final UUID UUID_SERVICE_GARMIN_WATCH_APPS = UUID.fromString( "6a4e8022-667b-11e3-949a-0800200c9a66");
    public static final UUID UUID_CHARACTERISTIC_WATCH_APP_TX = UUID.fromString("6a4e4c80-667b-11e3-949a-0800200c9a66");
    public static final UUID UUID_CHARACTERISTIC_WATCH_APP_RX = UUID.fromString("6a4ecd28-667b-11e3-949a-0800200c9a66");

    private static final byte TYPE_COMMAND = 0x00;
    private static final byte TYPE_DATA = (byte) 0xf1;

    private static final byte COMMAND_OPEN = 0x01;
    private static final byte COMMAND_CLOSE = 0x02;
    private static final byte COMMAND_INBOUND_OPEN = 0x03;
    private static final byte COMMAND_INBOUND_CLOSE = 0x04;

    private static final byte DATA_START = 0x00;
    private static final byte DATA_LAST = 0x01;
    private static final byte DATA_ACK = 0x02;
    private static final byte DATA_KEY = 0x05;
    private static final byte DATA_KEY_REQUEST = 0x06;

    private static final byte ACK_START = 0x00;
    private static final byte ACK_LAST = 0x02;
    private static final byte ACK_KEY = 0x05;

    private static final byte KEY_FLAG_OUTBOUND = 0x00;
    private static final byte KEY_FLAG_INBOUND = 0x02;

    private static final int FRAGMENT_DATA_SIZE = 19;
    /// The watch rejects a message of 15 fragments with ACK error 0x01
    private static final int MAX_FRAGMENTS = 14;
    /**
     * The maximum size of an encrypted message, including the padding.
     */
    public static final int MAX_MESSAGE_SIZE = MAX_FRAGMENTS * FRAGMENT_DATA_SIZE;
    private static final long TIMEOUT_MS = 5000;

    private enum State {
        IDLE,
        OPEN,
        KEY,
        START,
        DATA,
        CLOSE,
    }

    private final GarminSupport mSupport;
    private final SecureRandom random = new SecureRandom();
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private final Runnable timeoutRunnable = this::onTimeout;

    private BluetoothGattCharacteristic characteristicSend;
    private BluetoothGattCharacteristic characteristicReceive;

    private State state = State.IDLE;
    private UUID currentApp;
    @Nullable
    private Consumer<Boolean> currentResultCallback;
    private byte[] currentKey;
    private byte[] currentCipher;
    private int fragmentDataSize = FRAGMENT_DATA_SIZE;

    private final Deque<PendingMessage> pendingMessages = new ArrayDeque<>();

    private UUID inboundApp;
    private byte[] inboundKey;
    private int inboundLength;
    private final ByteArrayOutputStream inboundData = new ByteArrayOutputStream();

    public GarminWatchAppCommunicator(final GarminSupport garminSupport) {
        this.mSupport = garminSupport;
    }

    public boolean initializeDevice(final TransactionBuilder builder, @Nullable final BluetoothGattService service) {
        if (service == null) {
            LOG.debug("Watch App service not found");
            return false;
        }

        for (final BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
            final int properties = characteristic.getProperties();
            if (UUID_CHARACTERISTIC_WATCH_APP_RX.equals(characteristic.getUuid())) {
                characteristicReceive = characteristic;
            } else if (UUID_CHARACTERISTIC_WATCH_APP_TX.equals(characteristic.getUuid())) {
                characteristicSend = characteristic;
            } else {
                LOG.warn("Unknown watch app characteristic {} properties 0x{}", characteristic.getUuid(), Integer.toHexString(properties));
            }
        }

        if (characteristicSend == null || characteristicReceive == null) {
            LOG.warn("Watch app characteristics not found");
            return false;
        }

        builder.notify(characteristicReceive, true);
        return true;
    }

    public synchronized void onMtuChanged(final int mtu) {
        // The first byte of each fragment is the fragment header
        fragmentDataSize = calcMaxWriteChunk(mtu) - 1;
        LOG.debug("Fragment data size is {} for mtu {}", fragmentDataSize, mtu);
    }

    public synchronized void dispose() {
        timeoutHandler.removeCallbacks(timeoutRunnable);
        state = State.IDLE;
        reportResult(false);
        failPending(pending -> true);
    }

    /**
     * Queues a message to a watch app. If replace is set, any messages for the same app that
     * are waiting for transfer are replaced. The onResult callback receives true when the watch
     * acknowledges the message, and false when the transfer fails.
     */
    public synchronized void sendMessage(final UUID app, final JsonElement message, final boolean replace, @Nullable final Consumer<Boolean> onResult) {
        if (replace) {
            pendingMessages.removeIf(pending -> pending.app().equals(app));
        }
        pendingMessages.add(new PendingMessage(app, message, onResult));
        if (state == State.IDLE) {
            startNext();
        }
    }

    public boolean onCharacteristicChanged(final BluetoothGattCharacteristic characteristic, final byte[] value) {
        if (characteristic != characteristicReceive) {
            return false;
        }

        if (value.length < 2) {
            LOG.warn("Watch app packet too small");
            return true;
        }

        synchronized (this) {
            if (value[0] == TYPE_COMMAND) {
                onCommand(value);
            } else if (value[0] == TYPE_DATA) {
                onData(value);
            } else {
                onFragment(value);
            }
        }
        return true;
    }

    private void startNext() {
        if (!pollNext(null)) {
            return;
        }
        state = State.OPEN;
        write("open connection", ByteBuffer.allocate(19)
            .put(TYPE_COMMAND)
            .put(COMMAND_OPEN)
            .put(uuidToBytes(currentApp))
            .put((byte) 0x00)
            .array());
    }

    /**
     * Takes the next message from the queue and encrypts it. If [app] is set, only a message for that app is taken.
     *
     * @return false if no message is available
     */
    private boolean pollNext(@Nullable final UUID app) {
        PendingMessage pending;
        while ((pending = pendingMessages.peek()) != null && (app == null || pending.app().equals(app))) {
            pendingMessages.poll();
            final byte[] blob;
            try {
                blob = GarminJson.encode(pending.message());
            } catch (final GarminJsonException e) {
                LOG.error("Failed to encode message {}", pending.message(), e);
                if (pending.onResult() != null) {
                    pending.onResult().accept(false);
                }
                continue;
            }

            final byte[] key = new byte[16];
            random.nextBytes(key);
            final byte[] cipher = Xxtea.encrypt(pad(blob), key);
            if (cipher.length > MAX_MESSAGE_SIZE) {
                LOG.error("Message is too long: {} bytes", cipher.length);
                if (pending.onResult() != null) {
                    pending.onResult().accept(false);
                }
                continue;
            }

            currentApp = pending.app();
            currentResultCallback = pending.onResult();
            currentKey = key;
            currentCipher = cipher;
            return true;
        }
        return false;
    }

    private void exchangeKey() {
        state = State.KEY;
        write("exchange key", ByteBuffer.allocate(19)
            .put(TYPE_DATA)
            .put(DATA_KEY)
            .put(currentKey)
            .put(KEY_FLAG_OUTBOUND)
            .array());
    }

    private void onCommand(final byte[] value) {
        switch (value[1]) {
            case COMMAND_OPEN:
                if (state != State.OPEN) {
                    LOG.warn("Unexpected open connection response in state {}", state);
                    return;
                }
                if (value.length < 3 || value[2] != 0x00) {
                    LOG.warn("Open connection failed: {}", GB.hexdump(value));
                    // The next messages would also fail anyway
                    failPending(pending -> pending.app().equals(currentApp));
                    finish();
                    return;
                }
                exchangeKey();
                break;
            case COMMAND_CLOSE:
                if (state == State.CLOSE) {
                    finish();
                }
                break;
            case COMMAND_INBOUND_OPEN:
                if (value.length < 18) {
                    LOG.warn("Inbound open connection is too short");
                    return;
                }
                inboundApp = uuidFromBytes(value, 2);
                inboundKey = null;
                inboundData.reset();
                write("inbound open connection", TYPE_COMMAND, COMMAND_INBOUND_OPEN, (byte) 0x00, (byte) 0x01);
                break;
            case COMMAND_INBOUND_CLOSE:
                write("inbound close connection", TYPE_COMMAND, COMMAND_INBOUND_CLOSE, (byte) 0x00, (byte) 0x01);
                break;
            default:
                LOG.debug("Unhandled watch app command 0x{}", Integer.toHexString(value[1] & 0xff));
        }
    }

    private void onData(final byte[] value) {
        switch (value[1]) {
            case DATA_ACK:
                onAck(value);
                break;
            case DATA_KEY_REQUEST:
                inboundKey = new byte[16];
                random.nextBytes(inboundKey);
                write("inbound exchange key", ByteBuffer.allocate(19)
                    .put(TYPE_DATA)
                    .put(DATA_KEY)
                    .put(inboundKey)
                    .put(KEY_FLAG_INBOUND)
                    .array());
                break;
            case DATA_START:
                if (value.length < 7) {
                    LOG.warn("Inbound start is too short");
                    return;
                }
                inboundLength = ByteBuffer.wrap(value, 3, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                inboundData.reset();
                write("inbound start ack", TYPE_DATA, DATA_ACK, ACK_START, (byte) 0x00, (byte) 0xff);
                break;
            case DATA_LAST:
                if (value.length < 5) {
                    LOG.warn("Inbound last packet is too short");
                    return;
                }
                write("inbound last ack", TYPE_DATA, DATA_ACK, ACK_LAST, (byte) 0x00, value[2]);
                onInboundComplete(ByteBuffer.wrap(value, 3, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xffff);
                break;
            default:
                LOG.debug("Unhandled watch app data packet 0x{}", Integer.toHexString(value[1] & 0xff));
        }
    }

    private void onAck(final byte[] value) {
        if (value.length < 4) {
            LOG.warn("ACK is too short");
            return;
        }
        final byte acked = value[2];
        final byte status = value[3];

        final byte expected = switch (state) {
            case KEY -> ACK_KEY;
            case START -> ACK_START;
            case DATA -> ACK_LAST;
            // The watch also acknowledges the key of an inbound transfer
            default -> -1;
        };
        if (expected == -1) {
            return;
        }
        if (acked != expected) {
            LOG.warn("Unexpected ACK for 0x{} in state {}", Integer.toHexString(acked & 0xff), state);
            return;
        }
        if (status != 0x00) {
            LOG.warn("ACK error 0x{} in state {}", Integer.toHexString(status & 0xff), state);
            close();
            return;
        }

        if (state == State.KEY) {
            state = State.START;
            write("start message", ByteBuffer.allocate(7).order(ByteOrder.LITTLE_ENDIAN)
                .put(TYPE_DATA)
                .put(DATA_START)
                .put((byte) 0x02)
                .putInt(currentCipher.length)
                .array());
        } else if (state == State.START) {
            state = State.DATA;
            sendFragments();
        } else {
            LOG.debug("Message sent to {}", currentApp);
            reportResult(true);
            // Reuse the connection if the next message is for the same app
            if (pollNext(currentApp)) {
                exchangeKey();
            } else {
                close();
            }
        }
    }

    private void sendFragments() {
        final int fragmentCount = (currentCipher.length + fragmentDataSize - 1) / fragmentDataSize;
        LOG.debug("Sending {} bytes in {} fragments", currentCipher.length, fragmentCount);
        final TransactionBuilder builder = mSupport.createTransactionBuilder("send message fragments");
        for (int i = 0; i < fragmentCount; i++) {
            final int from = i * fragmentDataSize;
            final int to = Math.min(from + fragmentDataSize, currentCipher.length);
            final byte[] fragment = new byte[1 + to - from];
            fragment[0] = (byte) ((i << 4) | 0x01);
            System.arraycopy(currentCipher, from, fragment, 1, to - from);
            builder.write(characteristicSend, fragment);
        }
        final int crc = ChecksumCalculator.computeCrc(currentCipher, 0, currentCipher.length);
        builder.write(characteristicSend, TYPE_DATA, DATA_LAST, (byte) fragmentCount, (byte) crc, (byte) (crc >> 8));
        builder.queue();
        restartTimeout();
    }

    private void onFragment(final byte[] value) {
        if (inboundKey == null) {
            LOG.warn("Fragment without an inbound transfer");
            return;
        }
        inboundData.write(value, 1, value.length - 1);
    }

    private void onInboundComplete(final int crc) {
        final byte[] cipher = inboundData.toByteArray();
        inboundData.reset();
        final byte[] key = inboundKey;
        inboundKey = null;

        if (key == null || cipher.length != inboundLength) {
            LOG.warn("Unexpected inbound message has length {}, expected {}", cipher.length, inboundLength);
            return;
        }
        if (ChecksumCalculator.computeCrc(cipher, 0, cipher.length) != crc) {
            LOG.warn("Inbound message has unexpected CRC");
            return;
        }

        final byte[] plain = Xxtea.decrypt(cipher, key);
        final int padding = plain[0] & 0xff;
        // The watch uses no padding when the data is already aligned
        if (padding > 4 || 1 + padding > plain.length) {
            LOG.warn("Inbound message has an invalid padding {}", padding);
            return;
        }
        final JsonElement message;
        try {
            message = GarminJson.decode(Arrays.copyOfRange(plain, 1, plain.length - padding));
        } catch (final GarminJsonException e) {
            LOG.error("Failed to decode inbound message {}", GB.hexdump(plain), e);
            return;
        }

        LOG.debug("Received message from {}: {}", inboundApp, message);
        mSupport.onWatchAppMessage(inboundApp, message);
    }

    private void close() {
        state = State.CLOSE;
        write("close connection", TYPE_COMMAND, COMMAND_CLOSE, (byte) 0x01);
    }

    private void finish() {
        timeoutHandler.removeCallbacks(timeoutRunnable);
        state = State.IDLE;
        currentCipher = null;
        currentKey = null;
        reportResult(false);
        startNext();
    }

    /**
     * Runs the callback of the current message, if it did not run yet.
     */
    private void reportResult(final boolean success) {
        final Consumer<Boolean> onResult = currentResultCallback;
        currentResultCallback = null;
        if (onResult != null) {
            onResult.accept(success);
        }
    }

    private void failPending(final Predicate<PendingMessage> filter) {
        final Iterator<PendingMessage> iterator = pendingMessages.iterator();
        while (iterator.hasNext()) {
            final PendingMessage pending = iterator.next();
            if (filter.test(pending)) {
                iterator.remove();
                if (pending.onResult() != null) {
                    pending.onResult().accept(false);
                }
            }
        }
    }

    private synchronized void onTimeout() {
        LOG.warn("Timeout in state {}", state);
        if (state == State.CLOSE) {
            finish();
        } else if (state != State.IDLE) {
            close();
        }
    }

    private void write(final String taskName, final byte... value) {
        final TransactionBuilder builder = mSupport.createTransactionBuilder(taskName);
        builder.write(characteristicSend, value);
        builder.queue();
        if (state != State.IDLE) {
            restartTimeout();
        }
    }

    private void restartTimeout() {
        timeoutHandler.removeCallbacks(timeoutRunnable);
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);
    }

    /**
     * Pads a byte array to a multiple of 4, including the first byte as the padding length.
     */
    public static byte[] pad(final byte[] blob) {
        final int padding = (4 - (1 + blob.length) % 4) % 4;
        final byte[] plain = new byte[1 + blob.length + padding];
        plain[0] = (byte) padding;
        System.arraycopy(blob, 0, plain, 1, blob.length);
        return plain;
    }

    private static byte[] uuidToBytes(final UUID uuid) {
        return ByteBuffer.allocate(16)
            .putLong(uuid.getMostSignificantBits())
            .putLong(uuid.getLeastSignificantBits())
            .array();
    }

    @SuppressWarnings("SameParameterValue")
    private static UUID uuidFromBytes(final byte[] bytes, final int offset) {
        final ByteBuffer buf = ByteBuffer.wrap(bytes, offset, 16);
        return new UUID(buf.getLong(), buf.getLong());
    }

    private record PendingMessage(UUID app, JsonElement message, @Nullable Consumer<Boolean> onResult) {
    }
}
