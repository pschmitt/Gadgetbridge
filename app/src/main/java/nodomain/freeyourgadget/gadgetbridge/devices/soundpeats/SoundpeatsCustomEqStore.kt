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

import android.content.SharedPreferences
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundpeats.EqBand
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import androidx.core.content.edit

/**
 * User-created equalizer profiles.
 *
 * The earbuds take a parametric profile with as many bands as the frame declares, so the editor
 * exposes sixteen bands and the profile is sent as-is. Every frequency below is one the vendor
 * presets use with this device.
 *
 * Profiles live in the device-specific preferences as JSON, so they survive without any vendor
 * account.
 */
object SoundpeatsCustomEqStore {
    /** Bands the editor exposes, in the order they are shown. */
    val EDIT_BANDS = listOf(20, 62, 125, 180, 250, 420, 500, 600, 1000, 2000, 2800, 3800, 4000, 8000, 8200, 14000)

    const val PREF_KEY = "soundpeats_custom_eqs"

    const val MIN_GAIN_DB = -12.0
    const val MAX_GAIN_DB = 12.0

    /** Master gain range shared by the editor and the settings screen. */
    const val MIN_MASTER_GAIN_DB = -12
    const val MAX_MASTER_GAIN_DB = 6

    data class CustomEq(
        val id: String,
        val name: String,
        val masterGainDb: Double,
        /** Gains for [EDIT_BANDS], same order. */
        val gains: List<Double>,
    ) {
        fun toBands(): List<EqBand> = SoundpeatsCustomEqStore.toBands(gains)
    }

    fun list(prefs: SharedPreferences): List<CustomEq> {
        val raw = prefs.getString(PREF_KEY, null) ?: return emptyList()

        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val json = array.optJSONObject(index) ?: return@mapNotNull null
                val gains = json.optJSONArray("gains") ?: return@mapNotNull null
                CustomEq(
                    id = json.optString("id"),
                    name = json.optString("name"),
                    masterGainDb = json.optDouble("masterGain", 0.0),
                    gains = (0 until gains.length()).map { gains.optDouble(it, 0.0) },
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Stores the profile, replacing any other profile with the same name. */
    fun save(prefs: SharedPreferences, name: String, masterGainDb: Double, gains: List<Double>): CustomEq {
        val profiles = list(prefs).filterNot { it.name.equals(name, ignoreCase = true) }.toMutableList()
        val profile = CustomEq(
            id = UUID.randomUUID().toString(),
            name = name,
            masterGainDb = masterGainDb,
            gains = gains,
        )
        profiles.add(profile)
        write(prefs, profiles)
        return profile
    }

    fun delete(prefs: SharedPreferences, id: String) {
        write(prefs, list(prefs).filterNot { it.id == id })
    }

    fun find(prefs: SharedPreferences, id: String): CustomEq? = list(prefs).firstOrNull { it.id == id }

    /** Short label of a band, e.g. 62, 1k, 2.8k. */
    fun bandLabel(frequency: Int): String = when {
        frequency >= 1000 && frequency % 1000 == 0 -> "${frequency / 1000}k"
        frequency >= 1000 -> String.format(Locale.ROOT, "%.1fk", frequency / 1000.0)
        else -> frequency.toString()
    }

    /** Preference key of one editable band's gain, shared by the editor and the device support. */
    fun bandKey(index: Int): String = "soundpeats_eq_band_$index"

    /** Value stored in the equalizer preference for a custom profile. */
    fun customValue(id: String): String = "custom:$id"

    fun customIdOf(value: String?): String? =
        value?.takeIf { it.startsWith("custom:") }?.removePrefix("custom:")

    private fun write(prefs: SharedPreferences, profiles: List<CustomEq>) {
        val array = JSONArray()
        profiles.forEach { profile ->
            array.put(
                JSONObject().apply {
                    put("id", profile.id)
                    put("name", profile.name)
                    put("masterGain", profile.masterGainDb)
                    put("gains", JSONArray(profile.gains))
                }
            )
        }
        prefs.edit { putString(PREF_KEY, array.toString()) }
    }

    /** Turns the edited gains into the bands that are written to the earbuds. */
    fun toBands(gains: List<Double>): List<EqBand> =
        EDIT_BANDS.mapIndexed { index, frequency ->
            EqBand(
                index = index + 1,
                freqHz = frequency,
                gainDb = gains.getOrElse(index) { 0.0 },
                q = SoundpeatsEqPresets.FILLER_Q,
            )
        }
}
