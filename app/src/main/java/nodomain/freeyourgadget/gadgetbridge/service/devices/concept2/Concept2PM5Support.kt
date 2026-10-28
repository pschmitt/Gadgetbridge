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
package nodomain.freeyourgadget.gadgetbridge.service.devices.concept2

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLESingleDeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder
import nodomain.freeyourgadget.gadgetbridge.service.devices.concept2.protocol.PM5RawRecord
import nodomain.freeyourgadget.gadgetbridge.service.devices.concept2.protocol.PM5Uuids
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class Concept2PM5Support : AbstractBTLESingleDeviceSupport(LOG) {
    private val versionInfo = GBDeviceEventVersionInfo()
    private val notifications = PM5NotificationHandler()

    init {
        addSupportedService(PM5Uuids.SERVICE_DEVICE_INFO)
        addSupportedService(PM5Uuids.SERVICE_CONTROL)
        addSupportedService(PM5Uuids.SERVICE_ROWING)
    }

    override fun useAutoConnect(): Boolean = false

    override fun initializeDevice(builder: TransactionBuilder): TransactionBuilder {
        builder.setDeviceState(GBDevice.State.INITIALIZING)
        builder.read(PM5Uuids.SERIAL_NUMBER)
        builder.read(PM5Uuids.HARDWARE_REVISION)
        builder.read(PM5Uuids.FIRMWARE_REVISION)
        // status blocks four times a second
        builder.write(PM5Uuids.SAMPLE_RATE, PM5Uuids.SAMPLE_RATE_250_MS)
        // One subscription carries every rowing block; enabling an individual rowing
        // characteristic as well would take its id off the multiplexed stream.
        builder.notify(PM5Uuids.MULTIPLEXED, true)
        builder.setDeviceState(GBDevice.State.INITIALIZED)
        return builder
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ): Boolean {
        if (status != BluetoothGatt.GATT_SUCCESS) {
            return super.onCharacteristicRead(gatt, characteristic, value, status)
        }
        val text = String(value, Charsets.US_ASCII).trim { it <= ' ' }
        return when (characteristic.uuid) {
            PM5Uuids.SERIAL_NUMBER -> {
                LOG.info("Serial number {}", text)
                true
            }

            PM5Uuids.HARDWARE_REVISION -> {
                versionInfo.hwVersion = text
                true
            }

            PM5Uuids.FIRMWARE_REVISION -> {
                versionInfo.fwVersion = text
                handleGBDeviceEvent(versionInfo)
                true
            }

            else -> super.onCharacteristicRead(gatt, characteristic, value, status)
        }
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): Boolean {
        if (characteristic.uuid != PM5Uuids.MULTIPLEXED) {
            return super.onCharacteristicChanged(gatt, characteristic, value)
        }
        notifications.onNotification(PM5RawRecord(System.currentTimeMillis(), PM5RawRecord.SOURCE_MULTIPLEXED, value))
        return true
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(Concept2PM5Support::class.java)
    }
}
