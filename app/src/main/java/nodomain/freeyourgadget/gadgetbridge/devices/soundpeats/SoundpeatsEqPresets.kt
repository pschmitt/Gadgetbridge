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
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundpeats.EqBand
import java.util.Locale

/**
 * Built-in equalizer presets for Soundpeats devices, taken from the vendor catalog for the Q3 Pro
 * (`GET /api/app/v1/sound-effects/presets/S12`) and verified against a live capture of the app
 * applying "Classic" to the device.
 *
 * The device equalizer has **16 slots**: the eight parametric bands the app exposes plus eight
 * graphic bands. The full table is [SLOT_FREQUENCIES]; unused slots are sent with 0 dB and
 * [FILLER_Q]. Presets that use a frequency outside the table (none of the built-in ones except a
 * single HIFI band) are placed on the closest slot.
 */
object SoundpeatsEqPresets {
    /** Slot order used by the device, as observed on the wire. 2000 Hz appears twice on purpose. */
    val SLOT_FREQUENCIES = listOf(
        20, 62, 125, 180, 250, 420, 500, 1000, 2000, 2000, 2800, 3800, 4000, 8000, 8200, 14000,
    )

    /** Q used for slots that the preset does not touch. */
    const val FILLER_Q = 0.8

    /**
     * Headroom the earbuds are always given. Captured traffic shows every equalizer frame the
     * vendor app sends carrying -2 dB of master gain, whatever the preset is, so this is not a
     * per-preset value.
     */
    const val MASTER_GAIN_DB = -2.0

    enum class Preset(
        private val frequencies: List<Int>,
        private val gains: List<Double>,
        private val qs: List<Double>,
        override val label: Int,
    ) : LabeledEntry {
        CLASSIC(
            listOf(20, 180, 420, 2000, 2800, 3800, 8200, 14000),
            listOf(-5.7, -9.4, -11.0, -4.2, 3.8, -3.2, -2.0, 3.5),
            listOf(0.2, 1.0, 0.5, 3.0, 1.2, 3.0, 4.0, 0.1),
            R.string.soundcore_equalizer_preset_classic,
        ),
        TREBLE(
            listOf(62, 125, 250, 500, 1000, 2000, 4000, 8000),
            listOf(0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 2.0, 2.0),
            listOf(0.8, 0.8, 0.8, 0.8, 0.8, 0.8, 0.8, 0.8),
            R.string.soundpeats_eq_preset_treble,
        ),
        BASS_BOOST(
            listOf(62, 125, 250, 500, 1000, 2000, 4000, 8000),
            listOf(2.0, 2.0, 2.0, 1.0, 0.0, 0.0, 0.0, 0.0),
            listOf(0.8, 0.8, 0.8, 0.8, 0.8, 0.8, 0.8, 0.8),
            R.string.pref_title_equalizer_bass_boost,
        ),
        BASS_REDUCTION(
            listOf(62, 125, 250, 500, 1000, 2000, 4000, 8000),
            listOf(-2.0, -2.0, -2.0, -1.0, 0.0, 0.0, 0.0, 0.0),
            listOf(0.8, 0.8, 0.8, 0.8, 0.8, 0.8, 0.8, 0.8),
            R.string.soundpeats_eq_preset_bass_reduction,
        ),
        ROCK(
            listOf(62, 125, 250, 500, 1000, 2000, 4000, 8000),
            listOf(1.0, 2.0, 2.0, 2.0, -4.0, 1.0, 2.0, 2.0),
            listOf(0.8, 0.8, 0.8, 0.8, 0.8, 0.8, 0.8, 0.8),
            R.string.soundpeats_eq_preset_rock,
        ),
        POPULAR(
            listOf(62, 125, 250, 500, 1000, 2000, 4000, 8000),
            listOf(-2.0, 1.0, 2.0, 2.0, 1.0, -2.0, -1.0, -2.0),
            listOf(0.8, 0.8, 0.8, 0.8, 0.8, 0.8, 0.8, 0.8),
            R.string.soundpeats_eq_preset_popular,
        ),
        ELECTRONIC(
            listOf(62, 125, 250, 500, 1000, 2000, 4000, 8000),
            listOf(2.0, 2.0, 1.0, -1.0, 1.0, 0.0, 1.0, 3.0),
            listOf(0.8, 0.8, 0.8, 0.8, 0.8, 0.8, 0.8, 0.8),
            R.string.nothing_equalizer_electronic,
        ),
        FOLK(
            listOf(62, 125, 250, 500, 1000, 2000, 4000, 8000),
            listOf(-4.0, 4.0, -2.0, -1.0, 1.0, 2.0, 2.0, -1.0),
            listOf(0.8, 0.8, 0.8, 0.8, 0.8, 0.8, 0.8, 0.8),
            R.string.soundpeats_eq_preset_folk,
        ),
        CLASSICAL(
            listOf(62, 125, 250, 500, 1000, 2000, 4000, 8000),
            listOf(2.0, 2.0, 1.0, 1.0, -1.0, -1.0, -2.0, -3.0),
            listOf(0.8, 0.8, 0.8, 0.8, 0.8, 0.8, 0.8, 0.8),
            R.string.soundpeats_eq_preset_classical,
        ),
        ACG(
            listOf(62, 125, 250, 500, 1000, 2000, 4000, 8000),
            listOf(3.0, 2.0, -5.0, -3.0, 2.0, 3.0, -4.0, 3.0),
            listOf(0.7, 1.0, 3.0, 1.5, 1.0, 1.2, 4.0, 1.0),
            R.string.soundpeats_eq_preset_acg,
        ),
        HIFI(
            listOf(62, 250, 600, 1000, 2800, 4000, 8000, 14000),
            listOf(2.0, -5.0, -4.0, 1.0, 4.0, -5.0, 3.0, 8.0),
            listOf(0.7, 2.0, 1.0, 1.0, 1.0, 4.0, 2.0, 1.5),
            R.string.soundpeats_eq_preset_hifi,
        ),
        BOOK_WHISPER(
            listOf(62, 125, 250, 500, 1000, 2000, 4000, 8000),
            listOf(-12.0, 2.0, 2.0, 1.0, 1.0, 4.0, 2.0, -12.0),
            listOf(0.8, 0.8, 0.8, 0.8, 0.8, 0.8, 0.8, 0.8),
            R.string.soundpeats_eq_preset_book_whisper,
        ),
        ;

        /** Expands the preset onto the device's 16 slots, in wire order. */
        fun toBands(): List<EqBand> {
            val used = BooleanArray(frequencies.size)
            return SLOT_FREQUENCIES.mapIndexed { slot, slotFreq ->
                val match = frequencies.indices.firstOrNull {
                    !used[it] && frequencies[it] == slotFreq
                }
                if (match != null) {
                    used[match] = true
                    EqBand(
                        index = slot + 1,
                        freqHz = slotFreq,
                        gainDb = gains[match],
                        q = qs[match],
                    )
                } else {
                    EqBand(index = slot + 1, freqHz = slotFreq, gainDb = 0.0, q = FILLER_Q)
                }
            }
        }

        companion object {
            fun fromPreference(value: String?): Preset =
                entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: CLASSIC

            fun toPreference(preset: Preset): String = preset.name.lowercase(Locale.ROOT)
        }
    }
}
