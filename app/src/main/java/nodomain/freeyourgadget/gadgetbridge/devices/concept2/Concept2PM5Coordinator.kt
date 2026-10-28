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
package nodomain.freeyourgadget.gadgetbridge.devices.concept2

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLEDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.concept2.Concept2PM5Support
import java.util.regex.Pattern

/**
 * Concept2 Performance Monitor 5, the monitor of the RowErg, SkiErg and BikeErg. It advertises
 * while its "Connect Device" screen is open (More Options, Turn Wireless ON), and needs no pairing
 * or key.
 */
class Concept2PM5Coordinator : AbstractBLEDeviceCoordinator() {
    // "PM5 430070439", optionally followed by the erg, as in "PM5 430070439 Row"
    override fun getSupportedDeviceName(): Pattern = Pattern.compile("^PM5 \\d+( .*)?$")

    override fun getManufacturer(): String = "Concept2"

    override fun isExperimental(): Boolean = true

    override fun getBondingStyle(): Int = DeviceCoordinator.BONDING_STYLE_NONE

    override fun suggestUnbindBeforePair(): Boolean = false

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> = Concept2PM5Support::class.java

    override fun getDeviceNameResource(): Int = R.string.devicetype_concept2_pm5

    override fun getDefaultIconResource(): Int = R.drawable.ic_device_rowing_monitor

    // The monitor runs on batteries but does not report their level.
    override fun getBatteryCount(device: GBDevice): Int = 0

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind = DeviceCoordinator.DeviceKind.GYM_EQUIPMENT
}
