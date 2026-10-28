/*  Copyright (C) 2024-2026 Severin von Wnuck-Lipinski, David Giron

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
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.devices.SoundcoreLibertyProtocol
import java.util.regex.Pattern

class SoundcoreLiberty3ProCoordinator : SoundcoreEarbudsCoordinator() {
    override fun getModelName(): String = "A3952"

    override fun getSupportedDeviceName(): Pattern = Pattern.compile("Soundcore Liberty 3 Pro")

    override fun getDeviceNameResource(): Int = R.string.devicetype_soundcore_liberty3_pro

    override fun createProtocol(device: GBDevice): SoundcoreLibertyProtocol =
        SoundcoreLibertyProtocol(device)

    override fun supportsExtendedInfoRequest(): Boolean = false

    override fun supportsSessionInitRequest(): Boolean = false

    override fun getDefaultCapabilities(): Set<Capability> = setOf(
        Capability.NoiseControl,
        Capability.AmbientSound,
        Capability.AdaptiveNoiseCancellation,
        Capability.WindNoiseReduction,
        Capability.VocalMode,
        Capability.TouchControls,
        Capability.TouchControlTapDisable,
        Capability.TouchTone,
        Capability.WearingDetection,
        Capability.WearingTone,
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
    )
}
