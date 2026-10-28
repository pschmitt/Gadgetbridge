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
package nodomain.freeyourgadget.gadgetbridge.devices.soundcore

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import java.util.regex.Pattern

class SoundcoreP3Coordinator : SoundcoreEarbudsCoordinator() {
    // Real model: A3939011. The A3939 control service mapping is assumed and unverified.
    override fun getModelName(): String = "A3939"

    override fun getSupportedDeviceName(): Pattern =
        Pattern.compile("^soundcore P3$", Pattern.CASE_INSENSITIVE)

    override fun getDeviceNameResource(): Int = R.string.devicetype_soundcore_p3

    override fun getDefaultIconResource(): Int = R.drawable.ic_device_galaxy_buds_3_pro

    override fun isExperimental(): Boolean = true

    override fun getDefaultCapabilities(): Set<Capability> = setOf(
        Capability.FindDevice,
        Capability.NoiseControl,
    )
}
