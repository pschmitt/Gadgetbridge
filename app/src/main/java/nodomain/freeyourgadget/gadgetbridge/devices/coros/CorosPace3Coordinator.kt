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
package nodomain.freeyourgadget.gadgetbridge.devices.coros

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLEDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.coros.CorosPace3Support
import java.util.regex.Pattern

class CorosPace3Coordinator : AbstractBLEDeviceCoordinator() {
    override fun getSupportedDeviceName(): Pattern = Pattern.compile("^COROS PACE 3 [0-9A-F]{6}$")

    override fun getManufacturer(): String = "COROS"

    override fun getDeviceNameResource(): Int = R.string.devicetype_coros_pace_3

    override fun getDefaultIconResource(): Int = R.drawable.ic_device_miwatch

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind =
        DeviceCoordinator.DeviceKind.WATCH

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> =
        CorosPace3Support::class.java

    override fun getBondingStyle(): Int = BONDING_STYLE_NONE

    override fun suggestUnbindBeforePair(): Boolean = false

    override fun isExperimental(): Boolean = true

    override fun supportsFindDevice(device: GBDevice): Boolean = true
}
