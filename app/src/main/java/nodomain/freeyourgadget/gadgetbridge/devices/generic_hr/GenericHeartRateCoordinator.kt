package nodomain.freeyourgadget.gadgetbridge.devices.generic_hr

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLEDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider
import nodomain.freeyourgadget.gadgetbridge.entities.AbstractActivitySample
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceCandidate
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattService
import nodomain.freeyourgadget.gadgetbridge.service.devices.generic_hr.GenericHeartRateSupport

class GenericHeartRateCoordinator : AbstractBLEDeviceCoordinator() {
    override fun isExperimental(): Boolean {
        // Not heavily tested
        return true
    }

    override fun getOrderPriority(): Int {
        return Int.MAX_VALUE
    }

    override fun getBondingStyle(): Int {
        return BONDING_STYLE_NONE
    }

    override fun suggestUnbindBeforePair(): Boolean {
        // Not needed
        return false
    }

    override fun supports(candidate: GBDeviceCandidate): Boolean {
        return candidate.supportsService(GattService.SERVICE_HEART_RATE.uuid)
    }

    override fun getManufacturer(): String {
        return "Generic"
    }

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> {
        return GenericHeartRateSupport::class.java
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_generic_heart_rate_sensor
    }

    override fun getDefaultIconResource(): Int {
        return R.drawable.ic_device_lovetoy
    }

    override fun supportsHeartRateMeasurement(device: GBDevice): Boolean {
        return true
    }

    override fun supportsCharts(device: GBDevice): Boolean {
        return true
    }

    override fun getSampleProvider(device: GBDevice, session: DaoSession): SampleProvider<out AbstractActivitySample>? {
        return GenericHeartRateActivitySampleProvider(device, session)
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.CHEST_STRAP
    }
}
