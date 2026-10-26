package nodomain.freeyourgadget.gadgetbridge.devices.beurer

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLEDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.GenericBloodPressureSampleProvider
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.beurer.BeurerBm59Support
import java.util.regex.Pattern

class BeurerBm59Coordinator : AbstractBLEDeviceCoordinator() {
    protected override fun getSupportedDeviceName(): Pattern {
        return Pattern.compile("^BM59")
    }

    override fun getManufacturer(): String {
        return "Beurer"
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_beurer_bm_59
    }

    override fun getBondingStyle(): Int {
        return BONDING_STYLE_LAZY
    }

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> {
        return BeurerBm59Support::class.java
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.BLOOD_PRESSURE_METER
    }

    override fun getBatteryCount(device: GBDevice): Int {
        return 0 // unconfirmed
    }

    override fun suggestUnbindBeforePair(): Boolean {
        return false
    }

    override fun supportsBloodPressureMeasurement(device: GBDevice): Boolean {
        return true
    }

    override fun getBloodPressureSampleProvider(
        device: GBDevice,
        session: DaoSession
    ): GenericBloodPressureSampleProvider {
        return GenericBloodPressureSampleProvider(device, session)
    }
}
