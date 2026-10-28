package nodomain.freeyourgadget.gadgetbridge.devices.innovo

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLEDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.GenericRespiratoryRateSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.GenericSpo2SampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.TimeSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.generic_hr.GenericHeartRateActivitySampleProvider
import nodomain.freeyourgadget.gadgetbridge.entities.AbstractActivitySample
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.RespiratoryRateSample
import nodomain.freeyourgadget.gadgetbridge.model.Spo2Sample
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.innovo.InnovoPulseOximeterSupport
import java.util.regex.Pattern

class InnovoPulseOximeterCoordinator : AbstractBLEDeviceCoordinator() {
    override fun getSupportedDeviceName(): Pattern {
        return Pattern.compile("^iP900BPB$")
    }

    override fun getManufacturer(): String {
        return "Innovo"
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_innovo_ip900bp_b
    }

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> {
        return InnovoPulseOximeterSupport::class.java
    }

    override fun getBondingStyle(): Int {
        return BONDING_STYLE_NONE
    }

    override fun suggestUnbindBeforePair(): Boolean {
        return false
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.PULSE_OXIMETER
    }

    override fun supportsHeartRateMeasurement(device: GBDevice): Boolean {
        return true
    }

    override fun supportsManualHeartRateMeasurement(device: GBDevice): Boolean {
        return false
    }

    override fun getSampleProvider(
        device: GBDevice,
        session: DaoSession
    ): SampleProvider<out AbstractActivitySample> {
        return GenericHeartRateActivitySampleProvider(device, session)
    }

    override fun supportsSpo2(device: GBDevice): Boolean {
        return true
    }

    override fun getSpo2SampleProvider(
        device: GBDevice,
        session: DaoSession
    ): TimeSampleProvider<out Spo2Sample> {
        return GenericSpo2SampleProvider(device, session)
    }

    override fun supportsRespiratoryRate(device: GBDevice): Boolean {
        return true
    }

    override fun supportsDayRespiratoryRate(device: GBDevice): Boolean {
        return true
    }

    override fun supportsSleepRespiratoryRate(device: GBDevice): Boolean {
        return false
    }

    override fun getRespiratoryRateSampleProvider(
        device: GBDevice,
        session: DaoSession
    ): TimeSampleProvider<out RespiratoryRateSample> {
        return GenericRespiratoryRateSampleProvider(device, session)
    }
}
