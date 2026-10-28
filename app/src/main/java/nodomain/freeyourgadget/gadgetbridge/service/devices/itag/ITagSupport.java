/*  Copyright (C) 2020-2024 Arjan Schrijver, Taavi Eomäe

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.itag;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Intent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventCameraRemote;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventFindPhone;
import nodomain.freeyourgadget.gadgetbridge.devices.itag.ITagConstants;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLESingleDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattService;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.IntentListener;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.battery.BatteryInfoProfile;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.deviceinfo.DeviceInfoProfile;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

import static nodomain.freeyourgadget.gadgetbridge.devices.itag.ITagConstants.PREF_BEEP_ON_DISCONNECT;
import static nodomain.freeyourgadget.gadgetbridge.devices.itag.ITagConstants.PREF_BUTTON_EVENT;

public class ITagSupport extends AbstractBTLESingleDeviceSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ITagSupport.class);
    private final GBDeviceEventBatteryInfo batteryCmd = new GBDeviceEventBatteryInfo();
    private final DeviceInfoProfile<ITagSupport> deviceInfoProfile;
    private final BatteryInfoProfile<ITagSupport> batteryInfoProfile;

    private final IntentListener mListener = new IntentListener() {
        @Override
        public void notify(Intent intent) {
            String s = intent.getAction();
            if (s.equals(DeviceInfoProfile.ACTION_DEVICE_INFO)) {
                handleDeviceInfo((nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.deviceinfo.DeviceInfo) intent.getParcelableExtra(DeviceInfoProfile.EXTRA_DEVICE_INFO));
            } else if (s.equals(BatteryInfoProfile.ACTION_BATTERY_INFO)) {
                handleBatteryInfo((nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.battery.BatteryInfo) intent.getParcelableExtra(BatteryInfoProfile.EXTRA_BATTERY_INFO));
            }
        }
    };

    public ITagSupport() {
        super(LOG);
        addSupportedService(GattService.SERVICE_GAP.uuid);
        addSupportedService(GattService.SERVICE_GATT.uuid);
        addSupportedService(GattService.SERVICE_BATTERY.uuid);

        addSupportedService(GattService.SERVICE_IMMEDIATE_ALERT.uuid);
        addSupportedService(ITagConstants.UUID_SERVICE_BUTTON);
        addSupportedService(ITagConstants.UUID_SERVICE_SERIAL_NRF);


        deviceInfoProfile = new DeviceInfoProfile<>(this);
        deviceInfoProfile.addListener(mListener);
        batteryInfoProfile = new BatteryInfoProfile<>(this);
        batteryInfoProfile.addListener(mListener);

        addSupportedProfile(deviceInfoProfile);
        addSupportedProfile(batteryInfoProfile);
    }

    private void handleBatteryInfo(nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.battery.BatteryInfo info) {
        batteryCmd.level = (short) info.getPercentCharged();
        handleGBDeviceEvent(batteryCmd);
    }

    @Override
    protected TransactionBuilder initializeDevice(TransactionBuilder builder) {
        builder.setDeviceState(GBDevice.State.INITIALIZING);
        requestDeviceInfo(builder);
        builder.notify(ITagConstants.UUID_SERVICE_BUTTON, true);
        applyBeepOnDisconnect(builder);
        setInitialized(builder);
        batteryInfoProfile.requestBatteryInfo(builder);
        return builder;
    }

    private void requestDeviceInfo(TransactionBuilder builder) {
        LOG.debug("Requesting device info!");
        deviceInfoProfile.requestDeviceInfo(builder);
    }

    private void setInitialized(TransactionBuilder builder) {
        builder.setDeviceState(GBDevice.State.INITIALIZED);
    }


    @Override
    public boolean useAutoConnect() {
        return true;
    }

    private void handleDeviceInfo(nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.deviceinfo.DeviceInfo info) {

    }

    @Override
    public void onFindDevice(boolean start) {
        onSetConstantVibration(start ? resolveAlertLevel() : 0x00);
    }

    private int resolveAlertLevel() {
        final String pref = getDevicePrefs().getString(ITagConstants.PREF_ALERT_LEVEL, "high");
        return "mild".equals(pref) ? ITagConstants.ALERT_LEVEL_MILD : ITagConstants.ALERT_LEVEL_HIGH;
    }

    @Override
    public void onSetConstantVibration(int intensity) {
        getQueue().clear();

        TransactionBuilder builder = createTransactionBuilder("beeping");
        builder.write(ITagConstants.UUID_LINK_LOSS_ALERT_LEVEL, new byte[]{(byte) intensity});
        builder.queue();
    }

    @Override
    public boolean onCharacteristicChanged(BluetoothGatt gatt,
                                           BluetoothGattCharacteristic characteristic,
                                           byte[] value) {
        if (super.onCharacteristicChanged(gatt, characteristic, value)) {
            return true;
        }
        UUID characteristicUUID = characteristic.getUuid();
        if (ITagConstants.UUID_SERVICE_BUTTON.equals(characteristicUUID)) {
            LOG.info("Button: {}", GB.hexdump(value));
            if (value.length > 0 && value[0] == 1) {
                onButtonPressed();
            }
            return true;
        }
        LOG.info("Unhandled characteristic changed: " + characteristicUUID);
        return false;
    }


    private void onButtonPressed() {
        String button_event = getDevicePrefs().getString(PREF_BUTTON_EVENT, "findphone");
        LOG.debug("Button pressed! Perform {}", button_event);

        final GBDeviceEvent evt = switch (button_event) {
            case "takephoto" -> {
                final GBDeviceEventCameraRemote cameraRemoteEvent = new GBDeviceEventCameraRemote();
                cameraRemoteEvent.event = GBDeviceEventCameraRemote.Event.TAKE_PICTURE;
                yield cameraRemoteEvent;
            }
            default -> {
                final GBDeviceEventFindPhone findPhoneEvent = new GBDeviceEventFindPhone();
                findPhoneEvent.event = GBDeviceEventFindPhone.Event.START;
                yield findPhoneEvent;
            }
        };
        evaluateGBDeviceEvent(evt);

    }

    @Override
    public boolean onCharacteristicRead(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic, byte[] value,
                                        int status) {
        if (super.onCharacteristicRead(gatt, characteristic, value, status)) {
            return true;
        }
        UUID characteristicUUID = characteristic.getUuid();

        LOG.info("Unhandled characteristic read: " + characteristicUUID);
        return false;
    }

    @Override
    public void onSendConfiguration(String config) {
        switch (config) {
            case PREF_BEEP_ON_DISCONNECT -> {
                TransactionBuilder builder = createTransactionBuilder("apply config");
                applyBeepOnDisconnect(builder);
                builder.queue();
            }
        }
    }

    private void applyBeepOnDisconnect(TransactionBuilder builder) {
        if (getCharacteristic(ITagConstants.UUID_CHAR_LINKLOSS_FFE2) == null) {
            return;
        }
        final boolean beep = getDevicePrefs().getBoolean(ITagConstants.PREF_BEEP_ON_DISCONNECT, false);
        builder.write(ITagConstants.UUID_CHAR_LINKLOSS_FFE2, new byte[]{(byte) (beep ? 0x01 : 0x00)});
    }

    @Override
    public boolean getImplicitCallbackModify() {
        return true;
    }

    @Override
    public boolean getSendWriteRequestResponse() {
        return false;
    }
}
