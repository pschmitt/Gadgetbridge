package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.communicator.v1;

import static nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLEDeviceSupport.calcMaxWriteChunk;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.GarminSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.communicator.CobsCoDec;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.communicator.ICommunicator;

public class CommunicatorV1 implements ICommunicator {
    private static final Logger LOG = LoggerFactory.getLogger(CommunicatorV1.class);

    // Seen in Vivofit / Forerunner 620
    public static final UUID UUID_SERVICE_GARMIN_GFDI_V0 = UUID.fromString("9b012401-bc30-ce9a-e111-0f67e491abde");
    public static final UUID UUID_CHARACTERISTIC_GARMIN_GFDI_V0_SEND = UUID.fromString("df334c80-e6a7-d082-274d-78fc66f85e16");
    public static final UUID UUID_CHARACTERISTIC_GARMIN_GFDI_V0_RECEIVE = UUID.fromString("4acbcd28-7425-868e-f447-915c8f00d0cb");

    // Seen in Vivomove HR
    public static final UUID UUID_SERVICE_GARMIN_GFDI_V1 = UUID.fromString("6a4e2401-667b-11e3-949a-0800200c9a66");
    public static final UUID UUID_CHARACTERISTIC_GARMIN_GFDI_V1_SEND = UUID.fromString("6a4e4c80-667b-11e3-949a-0800200c9a66");
    public static final UUID UUID_CHARACTERISTIC_GARMIN_GFDI_V1_RECEIVE = UUID.fromString("6a4ecd28-667b-11e3-949a-0800200c9a66");

    private BluetoothGattCharacteristic characteristicSend;
    private BluetoothGattCharacteristic characteristicReceive;

    private final GarminSupport mSupport;

    public final CobsCoDec cobsCoDec;
    public int maxWriteSize = 20;

    public CommunicatorV1(final GarminSupport garminSupport) {
        this.mSupport = garminSupport;
        this.cobsCoDec = new CobsCoDec();
    }

    @Override
    public void onMtuChanged(final int mtu) {
        maxWriteSize = calcMaxWriteChunk(mtu);
    }

    @Override
    public boolean initializeDevice(final TransactionBuilder builder) {
        characteristicSend = mSupport.getCharacteristic(UUID_CHARACTERISTIC_GARMIN_GFDI_V1_SEND);
        characteristicReceive = mSupport.getCharacteristic(UUID_CHARACTERISTIC_GARMIN_GFDI_V1_RECEIVE);

        if (characteristicSend == null || characteristicReceive == null) {
            characteristicSend = mSupport.getCharacteristic(UUID_CHARACTERISTIC_GARMIN_GFDI_V0_SEND);
            characteristicReceive = mSupport.getCharacteristic(UUID_CHARACTERISTIC_GARMIN_GFDI_V0_RECEIVE);
        }

        if (characteristicSend == null || characteristicReceive == null) {
            LOG.warn("Failed to find V0/V1 GFDI characteristics");
            return false;
        }

        builder.notify(characteristicReceive, true);

        LOG.debug("Initializing as Garmin V1");

        return true;
    }

    @Override
    public void dispose() {

    }

    @Override
    public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {

    }

    @Override
    public void sendMessage(final String taskName, final byte[] message) {
        if (null == message)
            return;

        final byte[] payload = CobsCoDec.encode(message);

        final TransactionBuilder builder = mSupport.createTransactionBuilder(taskName);
        int remainingBytes = payload.length;
        if (remainingBytes > maxWriteSize - 1) {
            int position = 0;
            while (remainingBytes > 0) {
                final byte[] fragment = Arrays.copyOfRange(payload, position, position + Math.min(remainingBytes, maxWriteSize - 1));
                builder.write(characteristicSend, fragment);
                position += fragment.length;
                remainingBytes -= fragment.length;
            }
        } else {
            builder.write(characteristicSend, payload);
        }
        builder.queue();
    }

    @Override
    public boolean onCharacteristicChanged(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final byte[] value) {
        if (characteristic.getUuid().equals(characteristicReceive.getUuid())) {
            this.cobsCoDec.receivedBytes(value);
            this.mSupport.onMessage(this.cobsCoDec.retrieveMessage());

            return true;
        }

        return false;
    }

    @Override
    public void onHeartRateTest() {
        LOG.error("onHeartRateTest is not implemented for V1");
    }

    @Override
    public void onEnableRealtimeHeartRateMeasurement(final boolean enable) {
        LOG.error("onEnableRealtimeHeartRateMeasurement is not implemented for V1");
    }

    @Override
    public void onEnableRealtimeSteps(final boolean enable) {
        LOG.error("onEnableRealtimeSteps is not implemented for V1");
    }

    @Override
    public void onEnableRealtimeAccelerometer(final boolean enable) {
        LOG.error("onEnableRealtimeAccelerometer is not implemented for V1");
    }

    @Override
    public void onEnableRealtimeSpo2(final boolean enable) {
        LOG.error("onEnableRealtimeSpo2 is not implemented for V1");
    }

    @Override
    public void onEnableRealtimeRrIntervals(final boolean enable) {
        LOG.error("onEnableRealtimeRrIntervals is not implemented for V1");
    }
}
