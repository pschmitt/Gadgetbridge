package nodomain.freeyourgadget.gadgetbridge.devices.coospo

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import java.util.regex.Pattern

/// #4785
class CoospoH808SCoordinator: CoospoHeartRateCoordinator() {
    override fun getSupportedDeviceName(): Pattern? {
        // Advertised as "808S 0007734", other units use H808S-XXXXXX
        return Pattern.compile("(?i)^(?:COOSPO )?(?:H)?808S(?:[- ].*)?$")
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_coospo_h808s
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.CHEST_STRAP
    }
}
