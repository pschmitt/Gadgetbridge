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
package nodomain.freeyourgadget.gadgetbridge.activities.soundpeats

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import kotlin.math.roundToInt
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SOUNDPEATS_EQ_DRAFT
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SOUNDPEATS_EQ_MASTER
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SOUNDPEATS_EQ_PRESET
import nodomain.freeyourgadget.gadgetbridge.devices.soundpeats.SoundpeatsCustomEqStore
import nodomain.freeyourgadget.gadgetbridge.devices.soundpeats.SoundpeatsEqImport
import nodomain.freeyourgadget.gadgetbridge.devices.soundpeats.SoundpeatsEqPresets
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.GB
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getParcelableCompat
import androidx.core.content.edit

/**
 * Graphical editor for a Soundpeats custom equalizer profile.
 *
 * The eight graphic bands are edited by dragging the points in [SoundpeatsEqualizerView]; the
 * profile can then be saved under a name and applied straight to the earbuds.
 */
class SoundpeatsEqEditorActivity : AbstractGBActivity() {
    private lateinit var device: GBDevice
    private lateinit var equalizerView: SoundpeatsEqualizerView
    private lateinit var masterSeekBar: SeekBar
    private lateinit var masterLabel: TextView
    private val bandSliders = mutableListOf<SoundpeatsBandSlider>()
    private val bandValueLabels = mutableListOf<TextView>()
    private var syncingSliders = false

    private val bandKeys = SoundpeatsCustomEqStore.EDIT_BANDS.indices.map { SoundpeatsCustomEqStore.bandKey(it) }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importProfile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val parcelable = intent.getParcelableCompat<GBDevice>(GBDevice.EXTRA_DEVICE)
        if (parcelable == null) {
            finish()
            return
        }
        device = parcelable

        val density = resources.displayMetrics.density
        val padding = (16 * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        equalizerView = SoundpeatsEqualizerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setGains(readGains())
            onGainsChanged = { gains, released ->
                if (!syncingSliders) {
                    syncingSliders = true
                    gains.forEachIndexed { index, gain ->
                        bandSliders.getOrNull(index)?.value = gain.toFloat()
                        bandValueLabels.getOrNull(index)?.text = formatDb(gain.roundToInt())
                    }
                    syncingSliders = false
                }
                writeGains(gains)
                if (released) {
                    applyDraft()
                }
            }
        }
        root.addView(equalizerView)

        // breathing room between the graph and the slider stack
        root.addView(
            android.view.View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (20 * density).toInt(),
                )
            }
        )

        val gains = readGains()
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, 0, 0, (18 * density).toInt())
        }

        SoundpeatsCustomEqStore.EDIT_BANDS.forEachIndexed { index, frequency ->
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding((6 * density).toInt(), 0, (6 * density).toInt(), 0)
            }

            val valueLabel = TextView(this).apply {
                text = formatDb(gains.getOrElse(index) { 0.0 }.roundToInt())
                gravity = Gravity.CENTER
            }
            bandValueLabels.add(valueLabel)

            val slider = SoundpeatsBandSlider(this).apply {
                minDb = SoundpeatsCustomEqStore.MIN_GAIN_DB.toFloat()
                maxDb = SoundpeatsCustomEqStore.MAX_GAIN_DB.toFloat()
                value = gains.getOrElse(index) { 0.0 }.toFloat()
                layoutParams = LinearLayout.LayoutParams(
                    (32 * density).toInt(),
                    (160 * density).toInt(),
                )
                onValueChanged = { value, released ->
                    if (!syncingSliders) {
                        valueLabel.text = formatDb(value.roundToInt())
                        val updated = equalizerView.getGains().toMutableList()
                        updated[index] = value.toDouble()
                        syncingSliders = true
                        equalizerView.setGains(updated)
                        syncingSliders = false
                    }
                    if (released) {
                        writeGains(equalizerView.getGains())
                        applyDraft()
                    }
                }
            }
            bandSliders.add(slider)

            column.addView(
                TextView(this).apply {
                    text = SoundpeatsCustomEqStore.bandLabel(frequency)
                    gravity = Gravity.CENTER
                }
            )
            column.addView(slider)
            column.addView(valueLabel)
            strip.addView(column)
        }

        root.addView(
            android.widget.HorizontalScrollView(this).apply {
                // keep the scrollbar always visible: it can be dragged, so the sliders below it are
                // not touched by accident
                isHorizontalScrollBarEnabled = true
                isScrollbarFadingEnabled = false
                setPadding(0, 0, 0, (30 * density).toInt())
                addView(strip)
            }
        )

        root.addView(
            TextView(this).apply {
                text = getString(R.string.soundpeats_eq_master_gain)
                setPadding(0, padding, 0, 0)
            }
        )

        masterLabel = TextView(this).apply {
            gravity = Gravity.END
            text = formatDb(readMaster())
        }
        root.addView(masterLabel)

        masterSeekBar = SeekBar(this).apply {
            max = (SoundpeatsCustomEqStore.MAX_MASTER_GAIN_DB - SoundpeatsCustomEqStore.MIN_MASTER_GAIN_DB)
            progress = (readMaster() - SoundpeatsCustomEqStore.MIN_MASTER_GAIN_DB).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    masterLabel.text = formatDb(masterFromProgress(progress))
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    writeMaster(masterFromProgress(seekBar?.progress ?: 0))
                    applyDraft()
                }
            })
        }
        root.addView(masterSeekBar)

        root.addView(
            android.view.View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (24 * density).toInt(),
                )
            }
        )

        root.addView(
            MaterialButton(this).apply {
                text = getString(R.string.soundpeats_custom_eq_save)
                setOnClickListener { promptForName() }
            }
        )

        root.addView(
            MaterialButton(this).apply {
                text = getString(R.string.soundpeats_custom_eq_import)
                setOnClickListener { importLauncher.launch(arrayOf("text/plain", "application/octet-stream", "*/*")) }
            }
        )

        root.addView(
            MaterialButton(this).apply {
                text = getString(R.string.soundpeats_custom_eq_reset)
                setOnClickListener {
                    val flat = List(SoundpeatsCustomEqStore.EDIT_BANDS.size) { 0.0 }
                    equalizerView.setGains(flat)
                    syncingSliders = true
                    bandSliders.forEachIndexed { index, slider ->
                        slider.value = flat[index].toFloat()
                        bandValueLabels.getOrNull(index)?.text = formatDb(flat[index].roundToInt())
                    }
                    syncingSliders = false
                    writeGains(flat)
                    applyDraft()
                }
            }
        )

        setContentView(android.widget.ScrollView(this).apply { addView(root) })
        supportActionBar?.setTitle(R.string.soundpeats_custom_eq)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun prefs() = GBApplication.getDeviceSpecificSharedPrefs(device.address)

    private fun readGains(): List<Double> =
        bandKeys.map { prefs().getInt(it, 0).toDouble() }

    private fun writeGains(gains: List<Double>) {
        prefs().edit {
            gains.forEachIndexed { index, gain ->
                putInt(bandKeys[index], gain.toInt())
            }
        }
    }

    private fun readMaster(): Int = prefs().getInt(PREF_SOUNDPEATS_EQ_MASTER, SoundpeatsEqPresets.MASTER_GAIN_DB.toInt())

    private fun writeMaster(value: Int) {
        prefs().edit { putInt(PREF_SOUNDPEATS_EQ_MASTER, value) }
    }

    private fun masterFromProgress(progress: Int): Int =
        SoundpeatsCustomEqStore.MIN_MASTER_GAIN_DB + progress

    private fun formatDb(value: Int): String = getString(R.string.soundpeats_db_value, value)

    /** Sends the current draft to the earbuds without saving it as a profile. */
    private fun applyDraft() {
        GBApplication.deviceService(device).onSendConfiguration(PREF_SOUNDPEATS_EQ_DRAFT)
    }

    private fun promptForName() {
        val input = EditText(this).apply { hint = getString(R.string.soundpeats_custom_eq_name) }

        AlertDialog.Builder(this)
            .setTitle(R.string.soundpeats_custom_eq_save)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim().ifEmpty {
                    getString(R.string.soundpeats_custom_eq)
                }
                val profile = SoundpeatsCustomEqStore.save(
                    prefs = prefs(),
                    name = name,
                    masterGainDb = readMaster().toDouble(),
                    gains = equalizerView.getGains(),
                )
                prefs().edit {
                    putString(PREF_SOUNDPEATS_EQ_PRESET, SoundpeatsCustomEqStore.customValue(profile.id))
            }
                GBApplication.deviceService(device).onSendConfiguration(PREF_SOUNDPEATS_EQ_PRESET)
                GB.toast(this, getString(R.string.soundpeats_custom_eq_saved, name), Toast.LENGTH_SHORT, GB.INFO)
            }
            .show()
    }

    /** Reads an AutoEQ style parametric profile and loads it into the editor. */
    private fun importProfile(uri: android.net.Uri) {
        val content = try {
            contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            null
        }

        val filters = content?.let { SoundpeatsEqImport.parse(it) }.orEmpty()
        if (filters.isEmpty()) {
            GB.toast(this, getString(R.string.soundpeats_custom_eq_import_failed), Toast.LENGTH_LONG, GB.ERROR)
            return
        }

        val gains = SoundpeatsEqImport.toBandGains(filters)
        equalizerView.setGains(gains)
        writeGains(gains)
        applyDraft()
        GB.toast(this, getString(R.string.soundpeats_custom_eq_import_done, filters.size), Toast.LENGTH_SHORT, GB.INFO)
    }

    companion object {
        private const val UNUSED = 0
    }
}
