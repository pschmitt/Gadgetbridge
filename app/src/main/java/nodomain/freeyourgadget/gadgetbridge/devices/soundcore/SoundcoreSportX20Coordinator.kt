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
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.devices.SoundcoreSportX20Protocol
import java.util.regex.Pattern

class SoundcoreSportX20Coordinator : SoundcoreEarbudsCoordinator() {
    override fun getModelName(): String = "A3968"

    override fun getSupportedDeviceName(): Pattern = Pattern.compile("soundcore Sport X20")

    override fun getDeviceNameResource(): Int = R.string.devicetype_soundcore_sport_x20

    override fun getDefaultCapabilities(): Set<Capability> = setOf(
        Capability.FindDevice,
        Capability.Multipoint,
        Capability.NoiseControl,
        Capability.AmbientSound,
        Capability.AdaptiveNoiseCancellation,
        Capability.WindNoiseReduction,
        Capability.VocalMode,
        Capability.TouchControls,
        Capability.SurroundSound,
        Capability.FitTest,
        Capability.AutoPowerOff,
        Capability.TouchTone,
        Capability.AudioEqualizer,
    )

    override fun getCustomEqualizerPresetArray(): Int = R.array.soundcore_sport_x20_equalizer_preset_names

    override val supportedButtonActions: List<ButtonAction> = listOf(
        ButtonAction.SINGLE_TAP,
        ButtonAction.DOUBLE_TAP,
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

    override fun createProtocol(device: GBDevice): SoundcoreSportX20Protocol =
        SoundcoreSportX20Protocol(device)
}
