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

import kotlin.math.abs
import kotlin.math.ln

/**
 * Imports third-party parametric equalizer profiles.
 *
 * The supported format is the one AutoEQ and most measurement tools export as `ParametricEQ.txt`:
 *
 * ```
 * Filter 1: ON PK Fc 62 Hz Gain -2.0 dB Q 0.70
 * Filter 2: ON LSC Fc 105 Hz Gain 3.0 dB Q 0.70
 * ```
 *
 * Only the filter centre frequency and gain are used: the earbuds apply a fixed Q per slot, so the
 * filters are folded onto the eight editable bands by picking the closest one.
 */
object SoundpeatsEqImport {
    data class Filter(val frequencyHz: Double, val gainDb: Double, val q: Double)

    private val filterLine = Regex(
        """Fc\s+([0-9.]+)\s*Hz\s+Gain\s+(-?[0-9.]+)\s*dB(?:\s+Q\s+([0-9.]+))?""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String): List<Filter> =
        text.lineSequence().mapNotNull { line ->
            val match = filterLine.find(line) ?: return@mapNotNull null
            val frequency = match.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
            val gain = match.groupValues[2].toDoubleOrNull() ?: return@mapNotNull null
            val q = match.groupValues[3].toDoubleOrNull() ?: 0.7
            Filter(frequency, gain, q)
        }.toList()

    /**
     * Folds the filters onto the editable bands: each filter goes to the closest band and the bands
     * no filter is near stay flat.
     */
    fun toBandGains(filters: List<Filter>): List<Double> {
        val gains = MutableList(SoundpeatsCustomEqStore.EDIT_BANDS.size) { 0.0 }

        filters.forEach { filter ->
            val closest = SoundpeatsCustomEqStore.EDIT_BANDS.indices.minByOrNull { index ->
                abs(octaves(SoundpeatsCustomEqStore.EDIT_BANDS[index].toDouble(), filter.frequencyHz))
            } ?: return@forEach

            gains[closest] = filter.gainDb.coerceIn(
                SoundpeatsCustomEqStore.MIN_GAIN_DB,
                SoundpeatsCustomEqStore.MAX_GAIN_DB,
            )
        }

        return gains
    }

    private fun octaves(a: Double, b: Double): Double {
        if (a <= 0.0 || b <= 0.0) {
            return Double.MAX_VALUE
        }
        return ln(a / b) / ln(2.0)
    }
}
