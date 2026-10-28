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
import java.util.regex.Pattern

open class SoundcoreLifeTuneXRCoordinator : SoundcoreQ30Coordinator() {
    override fun getModelName(): String = "A3029"

    override fun getSupportedDeviceName(): Pattern = Pattern.compile("Soundcore Life Tune XR")

    override fun getDeviceNameResource(): Int = R.string.devicetype_soundcore_life_tune_xr

    override fun getDefaultCapabilities(): Set<Capability> = setOf(
        Capability.NoiseControl,
        Capability.AmbientSound,
        Capability.NoiseCancellationModes,
    )
}
