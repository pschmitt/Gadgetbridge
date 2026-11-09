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
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.devices.SoundcoreSportX20Protocol
import nodomain.freeyourgadget.gadgetbridge.activities.multipoint.MultipointPairingActivity
import java.util.regex.Pattern

class SoundcoreR50iNCCoordinator : SoundcoreEarbudsCoordinator() {
    override fun isExperimental(): Boolean = true

    override fun getModelName(): String = "A3959"

    override fun getSupportedDeviceName(): Pattern = Pattern.compile("^soundcore R50i NC$")

    override fun getDeviceNameResource(): Int = R.string.devicetype_soundcore_r50i_nc

    override fun getDefaultIconResource(): Int = R.drawable.ic_device_galaxy_buds

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind =
        DeviceCoordinator.DeviceKind.EARBUDS

    override fun getBatteryCount(device: GBDevice): Int = 2

    override fun getDefaultCapabilities(): Set<Capability> = setOf(
        Capability.FindDevice,
        Capability.Multipoint,
        Capability.NoiseControl,
        Capability.NoiseCancellationModes,
        Capability.AdaptiveNoiseCancellation,
        Capability.AmbientSound,
        Capability.Transparency,
        Capability.WindNoiseReduction,
        Capability.TouchTone,
        Capability.TouchControls,
        Capability.SurroundSound,
        Capability.FitTest,
        Capability.AutoPowerOff,
        Capability.AudioEqualizer,
        Capability.BatteryLowTone,
        Capability.GamingMode,
    )

    override val supportedButtonActions: List<ButtonAction> = listOf(
        ButtonAction.SINGLE_TAP,
        ButtonAction.DOUBLE_TAP,
        ButtonAction.TRIPLE_TAP,
        ButtonAction.LONG_PRESS,
    )

    override val supportedTapFunctions: List<TapFunction> = listOf(
        TapFunction.VOLUME_DOWN,
        TapFunction.VOLUME_UP,
        TapFunction.MEDIA_NEXT,
        TapFunction.MEDIA_PREV,
        TapFunction.PLAYPAUSE,
        TapFunction.VOICE_ASSISTANT,
        TapFunction.AMBIENT_SOUND_CONTROL,
        TapFunction.NONE,
    )
}
