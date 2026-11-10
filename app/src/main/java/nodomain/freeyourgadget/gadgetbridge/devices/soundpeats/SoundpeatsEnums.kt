/*  Copyright (C) 2026 Pedro

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
package nodomain.freeyourgadget.gadgetbridge.devices.soundpeats

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.LabeledEntry
import java.util.Locale

/**
 * Noise control mode, as reported by `GET_NOISE_CANCELLATION_MODE` (0x0310) in `payload[1]`.
 *
 * The Q3 Pro only implements three levels: off, noise cancelling and transparency. The adaptive and
 * weak ANC values advertised by the vendor catalog report nothing on this device.
 */
enum class SoundpeatsAncMode(
    val code: Int,
    override val label: Int,
) : LabeledEntry {
    OFF(0, R.string.off),
    STRONG(1, R.string.prefs_active_noise_cancelling),
    TRANSPARENCY(2, R.string.prefs_active_noise_cancelling_transparency),
    ;

    companion object {
        fun fromCode(code: Int): SoundpeatsAncMode =
            entries.find { it.code == (code and 0xFF) } ?: OFF

        fun fromPreference(value: String?): SoundpeatsAncMode =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: OFF

        fun toPreference(mode: SoundpeatsAncMode): String =
            mode.name.lowercase(Locale.ROOT)
    }
}
