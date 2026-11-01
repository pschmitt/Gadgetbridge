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
package nodomain.freeyourgadget.gadgetbridge.devices.soundcore

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.devices.SoundcoreMotionPlusProtocol
import java.util.regex.Pattern

class SoundcoreMotionPlusCoordinator : SoundcoreEarbudsCoordinator() {
    override fun getModelName(): String = "A3116"

    override fun getSupportedDeviceName(): Pattern = Pattern.compile("^Soundcore Motion\\+$", Pattern.CASE_INSENSITIVE)

    override fun getDeviceNameResource(): Int = R.string.devicetype_soundcore_motion_plus

    override fun getDefaultIconResource(): Int = R.drawable.ic_device_speaker

    override fun getBondingStyle(): Int = BONDING_STYLE_ASK

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind =
        DeviceCoordinator.DeviceKind.SPEAKER

    override fun getBatteryCount(device: GBDevice): Int = 1

    override fun supportsPowerOff(device: GBDevice): Boolean = true

    override fun getDefaultCapabilities(): Set<Capability> = setOf(
        Capability.VoicePrompts,
        Capability.AutoPowerOff,
        Capability.AudioEqualizer,
        Capability.Volume,
    )

    override fun getVolumeMax(): Int = 16

    override fun getSupportedEqualizerBands(): IntArray = intArrayOf(
        80, 150, 300, 500, 700, 1000, 5000, 8000, 12000,
    )

    override fun getCustomEqualizerPresetNames(): Array<Int> = arrayOf(
        R.string.soundcore_equalizer_preset_bass_off,
        R.string.soundcore_equalizer_preset_voice,
        R.string.soundcore_equalizer_preset_heavy,
        R.string.soundcore_equalizer_preset_classic,
        R.string.soundcore_equalizer_preset_original,
    )

    override fun createProtocol(device: GBDevice): SoundcoreMotionPlusProtocol =
        SoundcoreMotionPlusProtocol(device)

    override fun supportsSessionInitRequest(): Boolean = false
}
