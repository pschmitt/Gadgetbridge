/*  Copyright (C) 2024-2026 Severin von Wnuck-Lipinski, David Giron

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
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.devices.SoundcoreMotion300Protocol
import java.util.regex.Pattern

class SoundcoreMotion300Coordinator : SoundcoreEarbudsCoordinator() {
    override fun getModelName(): String = "A3135"

    override fun getSupportedDeviceName(): Pattern = Pattern.compile("soundcore Motion 300")

    override fun getDeviceNameResource(): Int = R.string.devicetype_soundcore_motion300

    override fun getDefaultIconResource(): Int = R.drawable.ic_device_speaker

    override fun getBondingStyle(): Int = BONDING_STYLE_ASK

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind =
        DeviceCoordinator.DeviceKind.SPEAKER

    override fun getBatteryCount(device: GBDevice): Int = 1

    override fun supportsPowerOff(device: GBDevice): Boolean = true

    override fun getAutoPowerOffMinutes(): Array<Int> = arrayOf(5, 10, 20, 60)

    override fun getDefaultCapabilities(): Set<Capability> = setOf(
        Capability.AutoPowerOff,
        Capability.VoicePrompts,
        Capability.ButtonBrightness,
        Capability.LDAC,
        Capability.AudioEqualizer,
        Capability.AdaptiveDirection,
    )

    override fun getEqualizerVolumeRange(): IntRange = 0..120

    override fun getSupportedEqualizerBands(): IntArray = intArrayOf(
        80, 150, 300, 600, 1200, 2500, 5000, 9000, 13000,
    )

    override fun getSupportedEqualizerBandsRanges(): Array<IntArray> = arrayOf(
        intArrayOf(48, 51, 55, 59, 63, 68, 72, 80, 83, 89),
        intArrayOf(95, 102, 109, 117, 125, 134, 144, 150, 165, 177),
        intArrayOf(190, 203, 218, 233, 250, 268, 287, 300, 330, 353),
        intArrayOf(379, 406, 435, 467, 500, 536, 574, 600, 659, 706),
        intArrayOf(759, 813, 871, 933, 1000, 1070, 1100, 1200, 1300, 1400),
        intArrayOf(1500, 1600, 1750, 1860, 2000, 2150, 2300, 2500, 2600, 2800),
        intArrayOf(3000, 3200, 3500, 3700, 4000, 4300, 4600, 5000, 5300, 5600),
        intArrayOf(6000, 6500, 7000, 7500, 8000, 8500, 9000, 9800, 10500, 11300),
        intArrayOf(12100, 13000, 14000, 15000, 16000, 17100, 18300, 20000),
    )

    override fun getCustomEqualizerPresetNames(): Array<Int> = arrayOf(
        R.string.soundcore_equalizer_preset_signature,
        R.string.soundcore_equalizer_preset_xtra_bass,
        R.string.soundcore_equalizer_preset_voice,
        R.string.soundcore_equalizer_preset_balanced,
    )

    override fun createProtocol(device: GBDevice): SoundcoreMotion300Protocol =
        SoundcoreMotion300Protocol(device)

    override fun supportsExtendedInfoRequest(): Boolean = false

    override fun supportsSessionInitRequest(): Boolean = false

    override fun getConnectDelayMillis(): Int = 500
}
