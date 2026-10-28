/*  Copyright (C) 2026 NachtigallersLehrling, David Giron

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
package nodomain.freeyourgadget.gadgetbridge.devices.soundcore

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.devices.SoundcoreQ30Protocol
import java.util.UUID
import java.util.regex.Pattern

open class SoundcoreQ30Coordinator : SoundcoreEarbudsCoordinator() {
    override fun getModelName(): String = "A3028"

    override fun getSupportedDeviceName(): Pattern = Pattern.compile("Soundcore +Q30", Pattern.CASE_INSENSITIVE)

    override fun getDeviceNameResource(): Int = R.string.devicetype_soundcore_q30

    override fun getDefaultIconResource(): Int = R.drawable.ic_device_headphones

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind =
        DeviceCoordinator.DeviceKind.HEADPHONES

    override fun getBatteryCount(device: GBDevice): Int = 1

    override fun getServiceUUID(): UUID =
        UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    override fun createProtocol(device: GBDevice): SoundcoreQ30Protocol = SoundcoreQ30Protocol(device)

    override fun supportsExtendedInfoRequest(): Boolean = false

    override fun supportsSessionInitRequest(): Boolean = false

    override fun getDefaultCapabilities(): Set<Capability> = setOf(
        Capability.NoiseControl,
        Capability.AmbientSound,
        Capability.NoiseCancellationModes,
    )
}
