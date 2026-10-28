package nodomain.freeyourgadget.gadgetbridge.service.devices.innovo

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Intent
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdateDeviceInfo
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo
import nodomain.freeyourgadget.gadgetbridge.devices.GenericHeartRateSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.GenericRespiratoryRateSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.GenericSpo2SampleProvider
import nodomain.freeyourgadget.gadgetbridge.entities.GenericHeartRateSample
import nodomain.freeyourgadget.gadgetbridge.entities.GenericRespiratoryRateSample
import nodomain.freeyourgadget.gadgetbridge.entities.GenericSpo2Sample
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLESingleDeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattService
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.IntentListener
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.battery.BatteryInfo
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.battery.BatteryInfoProfile
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.deviceinfo.DeviceInfo
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.deviceinfo.DeviceInfoProfile
import nodomain.freeyourgadget.gadgetbridge.util.GB
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getParcelableCompat
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.math.roundToInt

class InnovoPulseOximeterSupport : AbstractBTLESingleDeviceSupport(LOG) {
    private val deviceInfoProfile = DeviceInfoProfile(this)
    private val batteryInfoProfile = BatteryInfoProfile(this)

    private var newSamples = false

    init {
        val listener = IntentListener { intent: Intent? ->
            when (intent?.action) {
                DeviceInfoProfile.ACTION_DEVICE_INFO -> {
                    intent.getParcelableCompat<DeviceInfo>(DeviceInfoProfile.EXTRA_DEVICE_INFO)
                        ?.let { handleDeviceInfo(it) }
                }

                BatteryInfoProfile.ACTION_BATTERY_INFO -> {
                    intent.getParcelableCompat<BatteryInfo>(BatteryInfoProfile.EXTRA_BATTERY_INFO)
                        ?.let { handleBatteryInfo(it) }
                }
            }
        }

        addSupportedService(GattService.SERVICE_DEVICE_INFORMATION.uuid)
        deviceInfoProfile.addListener(listener)
        addSupportedProfile(deviceInfoProfile)

        addSupportedService(GattService.SERVICE_BATTERY.uuid)
        batteryInfoProfile.addListener(listener)
        addSupportedProfile(batteryInfoProfile)

        addSupportedService(UUID_SERVICE)
    }

    override fun useAutoConnect(): Boolean {
        return false
    }

    override fun initializeDevice(builder: TransactionBuilder): TransactionBuilder {
        builder.setDeviceState(GBDevice.State.INITIALIZING)

        deviceInfoProfile.requestDeviceInfo(builder)
        batteryInfoProfile.requestBatteryInfo(builder)

        builder.notify(UUID_CHARACTERISTIC_CONTROL, true)
        builder.notify(UUID_CHARACTERISTIC_MEASUREMENT, true)

        builder.setDeviceState(GBDevice.State.INITIALIZED)

        return builder
    }

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        super.onConnectionStateChange(gatt, status, newState)
        if (newState != BluetoothProfile.STATE_CONNECTED) {
            signalNewSamples()
        }
    }

    override fun dispose() {
        synchronized(ConnectionMonitor) {
            signalNewSamples()
            super.dispose()
        }
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ): Boolean {
        when (characteristic.uuid) {
            UUID_CHARACTERISTIC_CONTROL -> {
                LOG.debug("Ignoring control: {}", GB.hexdump(value))
                return true
            }

            UUID_CHARACTERISTIC_MEASUREMENT -> {
                handleMeasurement(value)
                return true
            }
        }

        return super.onCharacteristicChanged(gatt, characteristic, value)
    }

    private fun handleDeviceInfo(deviceInfo: DeviceInfo) {
        LOG.debug("Device info: {}", deviceInfo)

        val versionCmd = GBDeviceEventVersionInfo()
        versionCmd.fwVersion = deviceInfo.softwareRevision
        handleGBDeviceEvent(versionCmd)

        deviceInfo.manufacturerName?.let { handleGBDeviceEvent(GBDeviceEventUpdateDeviceInfo("MANUFACTURER: ", it)) }
        deviceInfo.modelNumber?.let { handleGBDeviceEvent(GBDeviceEventUpdateDeviceInfo("MODEL: ", it)) }
        deviceInfo.serialNumber?.let { handleGBDeviceEvent(GBDeviceEventUpdateDeviceInfo("SERIAL: ", it)) }
    }

    private fun handleBatteryInfo(batteryInfo: BatteryInfo) {
        LOG.debug("Battery info: {}", batteryInfo)

        val batteryCmd = GBDeviceEventBatteryInfo()
        batteryCmd.level = batteryInfo.percentCharged
        handleGBDeviceEvent(batteryCmd)
    }

    private fun handleMeasurement(value: ByteArray) {
        val measurement = try {
            InnovoPlxMeasurement.parse(value)
        } catch (e: Exception) {
            LOG.warn("Failed to parse measurement", e)
            return
        } ?: return

        LOG.debug("Got measurement: {}", measurement)

        val spo2 = measurement.spo2?.roundToInt() ?: return
        val pulseRate = measurement.pulseRate?.roundToInt() ?: return
        if (measurement.isUnreliable() || spo2 !in 1..100 || pulseRate <= 0) {
            return
        }

        val timestamp = System.currentTimeMillis()

        try {
            GBApplication.acquireDB().use { db ->
                val session = db.daoSession
                val userId = DBHelper.getUser(session).id!!
                val deviceId = DBHelper.getDevice(device, session).id!!

                LOG.debug(
                    "Persisting SpO2 {}, pulse rate {}, respiratory rate {}",
                    spo2,
                    pulseRate,
                    measurement.respiratoryRate
                )

                GenericSpo2SampleProvider(device, session)
                    .addSample(GenericSpo2Sample(timestamp, deviceId, userId, spo2))

                GenericHeartRateSampleProvider(device, session)
                    .addSample(GenericHeartRateSample(timestamp, deviceId, userId, pulseRate))

                measurement.respiratoryRate?.takeIf { it > 0 }?.let { respiratoryRate ->
                    GenericRespiratoryRateSampleProvider(device, session)
                        .addSample(GenericRespiratoryRateSample(timestamp, deviceId, userId, respiratoryRate.toFloat()))
                }
            }

            newSamples = true
        } catch (e: Exception) {
            LOG.error("Failed to save samples", e)
        }
    }

    private fun signalNewSamples() {
        if (newSamples) {
            newSamples = false
            GB.signalActivityDataFinish(device)
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(InnovoPulseOximeterSupport::class.java)

        private val UUID_SERVICE: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        private val UUID_CHARACTERISTIC_CONTROL: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        private val UUID_CHARACTERISTIC_MEASUREMENT: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
    }
}
