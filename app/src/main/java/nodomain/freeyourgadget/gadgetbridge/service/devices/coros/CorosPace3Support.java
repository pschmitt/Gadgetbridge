/*  Copyright (C) 2026 David Giron

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.coros;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdateDeviceState;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLESingleDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattCharacteristic;
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattService;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.deviceinfo.DeviceInfo;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.deviceinfo.DeviceInfoProfile;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

public class CorosPace3Support extends AbstractBTLESingleDeviceSupport {
    private static final Logger LOG = LoggerFactory.getLogger(CorosPace3Support.class);

    private static final UUID SERVICE_CONTROL = UUID.fromString("6e400001-b5a3-f393-e0a9-77656c6f6f70");
    private static final UUID CHARACTERISTIC_CONTROL_NOTIFY = UUID.fromString("6e400003-b5a3-f393-e0a9-77656c6f6f70");
    private static final UUID CHARACTERISTIC_CONTROL_WRITE = UUID.fromString("6e400002-b5a3-f393-e0a9-77656c6f6f70");
    private static final byte[] QUERY_CAPABILITIES = {(byte) 0xb0, 0x00, 0x00, 0x01, 0x02, 0x03};
    private static final byte[] FIND_DEVICE = {(byte) 0xb4, 0x00};
    private static final byte[] FIND_DEVICE_ACK = {(byte) 0xb4, 0x00, 0x01, 0x01};
    private static final byte[] FIND_DEVICE_DISMISSED = {0x04, 0x00, 0x01, 0x01};

    private final DeviceInfoProfile<CorosPace3Support> deviceInfoProfile;
    private boolean awaitingCapabilities;

    public CorosPace3Support() {
        super(LOG);
        addSupportedService(SERVICE_CONTROL);
        addSupportedService(GattService.SERVICE_DEVICE_INFORMATION.uuid);
        addSupportedService(GattService.SERVICE_BATTERY.uuid);
        deviceInfoProfile = new DeviceInfoProfile<>(this);
        deviceInfoProfile.addListener(intent -> {
            final DeviceInfo info = intent.getParcelableExtra(DeviceInfoProfile.EXTRA_DEVICE_INFO);
            if (info != null) {
                for (final GBDeviceEvent event : DeviceInfoProfile.toDeviceEvents(info)) {
                    handleGBDeviceEvent(event);
                }
            }
        });
        addSupportedProfile(deviceInfoProfile);
    }

    @Override
    protected TransactionBuilder initializeDevice(final TransactionBuilder builder) {
        awaitingCapabilities = false;
        builder.setDeviceState(GBDevice.State.INITIALIZING);
        if (getCharacteristic(CHARACTERISTIC_CONTROL_NOTIFY) == null ||
                getCharacteristic(CHARACTERISTIC_CONTROL_WRITE) == null) {
            LOG.warn("COROS control characteristics are missing");
            return builder;
        }

        // The capabilities response fits in one notification at this MTU.
        builder.requestMtu(365);
        deviceInfoProfile.requestDeviceInfo(builder);
        builder.read(GattCharacteristic.CHARACTERISTIC_BATTERY_LEVEL.uuid);
        builder.notify(CHARACTERISTIC_CONTROL_NOTIFY, true);
        builder.notify(GattCharacteristic.CHARACTERISTIC_BATTERY_LEVEL.uuid, true);
        getCharacteristic(CHARACTERISTIC_CONTROL_WRITE).setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);

        // Account binding and profile/settings synchronization are not implemented. Do not
        // report initialization success until the watch answers a read-only protocol query.
        awaitingCapabilities = true;
        builder.write(CHARACTERISTIC_CONTROL_WRITE, QUERY_CAPABILITIES);
        return builder;
    }

    @Override
    public boolean useAutoConnect() {
        return true;
    }

    @Override
    public boolean onCharacteristicRead(final BluetoothGatt gatt,
                                        final BluetoothGattCharacteristic characteristic,
                                        final byte[] value, final int status) {
        if (GattCharacteristic.CHARACTERISTIC_BATTERY_LEVEL.uuid.equals(characteristic.getUuid())) {
            if (status == BluetoothGatt.GATT_SUCCESS && value.length == 1 && (value[0] & 0xff) <= 100) {
                final GBDeviceEventBatteryInfo battery = new GBDeviceEventBatteryInfo();
                battery.level = (short) (value[0] & 0xff);
                handleGBDeviceEvent(battery);
            }
            return true;
        }
        return super.onCharacteristicRead(gatt, characteristic, value, status);
    }

    @Override
    public boolean onCharacteristicChanged(final BluetoothGatt gatt,
                                           final BluetoothGattCharacteristic characteristic,
                                           final byte[] value) {
        final UUID uuid = characteristic.getUuid();
        if (GattCharacteristic.CHARACTERISTIC_BATTERY_LEVEL.uuid.equals(uuid)) {
            return onCharacteristicRead(gatt, characteristic, value, BluetoothGatt.GATT_SUCCESS);
        }
        if (!CHARACTERISTIC_CONTROL_NOTIFY.equals(uuid)) {
            return super.onCharacteristicChanged(gatt, characteristic, value);
        }

        final CorosPacket packet;
        try {
            packet = CorosPacket.parse(value);
        } catch (final IllegalArgumentException e) {
            if (value.length >= 2 && (value[0] & 0xff) == 0xb0 && value[1] != 0) {
                awaitingCapabilities = false;
            }
            LOG.warn("Rejected COROS control packet: opcode={}, reason={}, data={}",
                    value.length == 0 ? "missing" : Integer.toHexString(value[0] & 0xff),
                    e.getMessage(), GB.hexdump(value));
            return true;
        }

        final byte[] payload = packet.getPayload();
        LOG.debug("Received COROS control packet: opcode={}, payload={}",
                Integer.toHexString(packet.getOpcode()), GB.lazyHexdump(payload));
        switch (packet.getOpcode()) {
            case 0xb0:
                if (awaitingCapabilities && payload.length >= 4 && payload[0] == 1 && payload[1] == 0) {
                    awaitingCapabilities = false;
                    handleGBDeviceEvent(new GBDeviceEventUpdateDeviceState(GBDevice.State.INITIALIZED));
                }
                break;
            case 0x04:
                if (Arrays.equals(value, FIND_DEVICE_DISMISSED)) {
                    // Acknowledge the watch-side dismissal; this is not a phone-side stop command.
                    sendCommand("acknowledge find device dismissal", FIND_DEVICE_DISMISSED);
                }
                break;
            case 0xb4:
                if (Arrays.equals(value, FIND_DEVICE_ACK)) {
                    LOG.debug("COROS acknowledged find device");
                }
                break;
        }
        return true;
    }

    @Override
    public void onFindDevice(final boolean start) {
        // The watch dismisses the alert itself. No phone-side stop command is known.
        if (start) {
            sendCommand("find device", FIND_DEVICE);
        }
    }

    private void sendCommand(final String task, final byte[] command) {
        if (!isConnected()) {
            return;
        }
        final TransactionBuilder builder = createTransactionBuilder(task);
        builder.write(CHARACTERISTIC_CONTROL_WRITE, command);
        builder.queue();
    }
}
