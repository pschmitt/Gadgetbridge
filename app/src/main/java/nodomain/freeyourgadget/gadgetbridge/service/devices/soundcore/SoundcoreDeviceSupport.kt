/*  Copyright (C) 2026 David Giron

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.activities.multipoint.MultipointPairingActivity
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getParcelableCompat
import android.content.Context
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreEarbudsCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreEarbudsCoordinator.Capability
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdateDeviceState
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.btbr.TransactionBuilder
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.protocol.impl.v1.SoundcoreProtocolImplV1
import nodomain.freeyourgadget.gadgetbridge.service.serial.AbstractHeadphoneSerialDeviceSupportV2
import java.util.UUID

open class SoundcoreDeviceSupport : AbstractHeadphoneSerialDeviceSupportV2<SoundcoreProtocolImplV1>() {
    private var multipointReceiverRegistered = false
    private var firmwareReceivedForConnection = false

    private val multipointReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val intentDevice = intent.getParcelableCompat<GBDevice>(GBDevice.EXTRA_DEVICE)
            if (intentDevice?.address != getDevice().address) return
            val action = intent.action ?: return
            val coordinator = getDevice().deviceCoordinator as SoundcoreEarbudsCoordinator
            if (!coordinator.supports(getDevice(), Capability.Multipoint)) return
            val actions = coordinator.getSupportedMultipointActions()
            if (action !in actions) return

            when (action) {
                MultipointPairingActivity.ACTION_MULTIPOINT_GET_STATUS -> {
                    val enabled = GBApplication.getDeviceSpecificSharedPrefs(getDevice().address)
                        .getBoolean(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_DUAL_CONNECTION, false)
                    mDeviceProtocol.broadcastMultipointStatus(enabled)
                }
                MultipointPairingActivity.ACTION_MULTIPOINT_GET_DEVICES -> refreshMultipointDevices()
                MultipointPairingActivity.ACTION_MULTIPOINT_ENABLE -> setDualConnection(true)
                MultipointPairingActivity.ACTION_MULTIPOINT_DISABLE -> setDualConnection(false)
                MultipointPairingActivity.ACTION_MULTIPOINT_START_PAIRING -> {
                    val enabled = intent.getBooleanExtra(MultipointPairingActivity.EXTRA_PAIRING_ENABLED, false)
                    if (enabled) sendMultipointCommand(mDeviceProtocol.encodeStartPairing())
                    mDeviceProtocol.broadcastMultipointPairing(enabled)
                }
                MultipointPairingActivity.ACTION_MULTIPOINT_CONNECT_DEVICE,
                MultipointPairingActivity.ACTION_MULTIPOINT_DISCONNECT_DEVICE -> {
                    val address = intent.getStringExtra(MultipointPairingActivity.EXTRA_DEVICE_ADDRESS) ?: return
                    sendMultipointCommand(if (action == MultipointPairingActivity.ACTION_MULTIPOINT_CONNECT_DEVICE) {
                        mDeviceProtocol.encodeConnectDevice(address)
                    } else {
                        mDeviceProtocol.encodeDisconnectDevice(address)
                    })
                    refreshMultipointDevices()
                }
                MultipointPairingActivity.ACTION_MULTIPOINT_FORGET_DEVICE -> {
                    val address = intent.getStringExtra(MultipointPairingActivity.EXTRA_DEVICE_ADDRESS) ?: return
                    sendMultipointCommand(mDeviceProtocol.encodeForgetDevice(address))
                    sendMultipointCommand(mDeviceProtocol.encodePairedDevicesRequest())
                }
            }
        }
    }

    override fun disconnect() {
        firmwareReceivedForConnection = false
        super.disconnect()
    }

    override fun dropConnection() {
        firmwareReceivedForConnection = false
        super.dropConnection()
    }

    override fun dispose() {
        synchronized(ConnectionMonitor) {
            firmwareReceivedForConnection = false
            if (multipointReceiverRegistered) {
                LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(multipointReceiver)
                multipointReceiverRegistered = false
            }
            super.dispose()
        }
    }

    override fun evaluateGBDeviceEvent(deviceEvent: GBDeviceEvent) {
        if (deviceEvent is GBDeviceEventUpdateDeviceState) {
            if (deviceEvent.state == GBDevice.State.INITIALIZED &&
                (!firmwareReceivedForConnection || !isConnected || getDevice().state == GBDevice.State.INITIALIZED)
            ) {
                return
            }
            if (deviceEvent.state == GBDevice.State.NOT_CONNECTED ||
                deviceEvent.state == GBDevice.State.WAITING_FOR_RECONNECT
            ) {
                firmwareReceivedForConnection = false
            }
        }

        super.evaluateGBDeviceEvent(deviceEvent)

        if (deviceEvent is GBDeviceEventVersionInfo &&
            !deviceEvent.fwVersion?.trim { it.isWhitespace() || it == '\u0000' }.isNullOrEmpty() &&
            !firmwareReceivedForConnection && isConnected &&
            (getDevice().state == GBDevice.State.CONNECTED || getDevice().state == GBDevice.State.INITIALIZING)
        ) {
            firmwareReceivedForConnection = true
            super.evaluateGBDeviceEvent(GBDeviceEventUpdateDeviceState(GBDevice.State.INITIALIZED))
        }
    }

    private fun setDualConnection(enabled: Boolean) {
        GBApplication.getDeviceSpecificSharedPrefs(getDevice().address).edit()
            .putBoolean(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_DUAL_CONNECTION, enabled)
            .apply()
        sendMultipointCommand(mDeviceProtocol.encodeDualConnection(enabled))
        mDeviceProtocol.broadcastMultipointStatus(enabled)
    }

    private fun refreshMultipointDevices() {
        sendMultipointCommand(mDeviceProtocol.encodeConnectionStatusRequest())
        sendMultipointCommand(mDeviceProtocol.encodePairedDevicesRequest())
    }

    private fun sendMultipointCommand(bytes: ByteArray?) {
        if (bytes == null) return
        val builder = createTransactionBuilder("multipoint")
        builder.write(*bytes)
        builder.queue()
    }

    override fun setContext(gbDevice: GBDevice, btAdapter: BluetoothAdapter, context: Context) {
        super.setContext(gbDevice, btAdapter, context)

        val coordinator = gbDevice.deviceCoordinator as? SoundcoreEarbudsCoordinator
            ?: throw IllegalArgumentException("Expected a SoundcoreEarbudsCoordinator for Soundcore earbuds support")

        addSupportedService(coordinator.getServiceUUID() ?: getServiceUUID(coordinator.getModelName()))
        val actions = coordinator.getSupportedMultipointActions()
        if (coordinator.supports(gbDevice, Capability.Multipoint) &&
            actions.isNotEmpty() && !multipointReceiverRegistered
        ) {
            val filter = IntentFilter().apply { actions.forEach { addAction(it) } }
            LocalBroadcastManager.getInstance(context).registerReceiver(multipointReceiver, filter)
            multipointReceiverRegistered = true
        }
    }

    protected override fun createDeviceProtocol(): SoundcoreProtocolImplV1 =
        (getDevice().deviceCoordinator as SoundcoreEarbudsCoordinator).createProtocol(getDevice())

    fun getServiceUUID(modelId: String): UUID {
        require(MODEL_ID_PATTERN.matches(modelId)) {
            "Invalid Soundcore model ID for control service UUID: $modelId"
        }
        val serviceSuffix = modelId.toInt(radix = 16) or 0x10000
        return UUID.fromString("$UUID_DEVICE_CTRL_PREFIX${serviceSuffix.toString(16)}")
    }

    override fun initializeDevice(builder: TransactionBuilder): TransactionBuilder {
        firmwareReceivedForConnection = false
        val coordinator = getDevice().deviceCoordinator as SoundcoreEarbudsCoordinator
        val delayMillis = coordinator.getConnectDelayMillis()
        if (delayMillis > 0) {
            builder.sleep(delayMillis)
        }
        // 1. Request device info (battery, firmware, serial, embedded control functions)
        builder.write(*mDeviceProtocol.encodeDeviceInfoRequest())
        // 2. Request extended info (firmware details, serial, additional settings)
        if (coordinator.supportsExtendedInfoRequest()) {
            builder.sleep(300)
            builder.write(*mDeviceProtocol.encodeExtendedInfoRequest())
        }
        // 3. Session-init handshake - the device ACKs with an empty response
        if (coordinator.supportsSessionInitRequest()) {
            builder.sleep(300)
            builder.write(*mDeviceProtocol.encodeSessionInitRequest())
        }
        return builder
    }

    private companion object {
        const val UUID_DEVICE_CTRL_PREFIX = "0cf12d31-fac3-4553-bd80-d6832e7"
        val MODEL_ID_PATTERN = Regex("[0-9A-Fa-f]{5}")
    }
}
