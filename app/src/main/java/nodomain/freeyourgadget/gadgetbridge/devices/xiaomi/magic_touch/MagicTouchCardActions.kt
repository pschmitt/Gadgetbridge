package nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.magic_touch

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCardAction
import nodomain.freeyourgadget.gadgetbridge.devices.deviceCardAction
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.magic_touch.MagicTouchJj02Support
import androidx.core.content.edit
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.magic_touch.MagicTouchMiotSpec.PID_TARGET_TIME_MAX
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.magic_touch.MagicTouchMiotSpec.PID_TARGET_TIME_MIN

private fun prefsOf(device: GBDevice) =
    GBApplication.getDeviceSpecificSharedPrefs(device.address)

/**
 * Resolves the [R.array] label whose matching value is stored under [prefKey],
 * so card labels never hardcode the value -> name mapping.
 */
private fun prefLabel(
    context: android.content.Context,
    prefs: android.content.SharedPreferences,
    prefKey: String,
    entriesRes: Int,
    valuesRes: Int,
): String? {
    val value = prefs.getString(prefKey, null) ?: return null
    val index = context.resources.getStringArray(valuesRes).indexOf(value)
    if (index < 0) {
        return null
    }
    return context.resources.getStringArray(entriesRes).getOrNull(index)
}

private fun isPowerOn(device: GBDevice): Boolean =
    prefsOf(device).getBoolean(MagicTouchJj02Support.PREF_POWER, false)

private fun callConfig(device: GBDevice, key: String) {
    GBApplication.deviceService(device)?.onSendConfiguration(key)
}

fun magicTouchCustomActions(): List<DeviceCardAction> = listOf(
    deviceCardAction {
        id = "magic_touch_power"
        icon = { R.drawable.ic_power_settings_new }
        description = { _, context -> context.getString(R.string.magic_touch_jj02_power_title) }
        label = { device, context ->
            val on = prefsOf(device).getBoolean(MagicTouchJj02Support.PREF_POWER, false)
            context.getString(if (on) R.string.on else R.string.off)
        }
        isVisible = { it.isInitialized }
        onClick = { device, _ ->
            val cur = prefsOf(device).getBoolean(MagicTouchJj02Support.PREF_POWER, false)
            prefsOf(device).edit { putBoolean(MagicTouchJj02Support.PREF_POWER, !cur) }
            callConfig(device, "power")
        }
    },
    deviceCardAction {
        id = "magic_touch_mode"
        icon = { R.drawable.ic_mode_fan }
        description = { _, context -> context.getString(R.string.magic_touch_jj02_mode_title) }
        label = { device, context ->
            prefLabel(
                context,
                prefsOf(device),
                MagicTouchJj02Support.PREF_MODE,
                R.array.magic_touch_jj02_mode_entries,
                R.array.magic_touch_jj02_mode_values,
            ) ?: "?"
        }
        isVisible = { it.isInitialized && isPowerOn(it) }
        onClick = { device, context ->
            val activity = context as? android.app.Activity
            if (activity == null) {
                val cur = prefsOf(device).getString(MagicTouchJj02Support.PREF_MODE, "1")?.toIntOrNull() ?: 1
                val next = if (cur >= 4) 1 else cur + 1
                prefsOf(device).edit { putString(MagicTouchJj02Support.PREF_MODE, next.toString()) }
                callConfig(device, "mode")
            } else {
                val names = context.resources.getStringArray(R.array.magic_touch_jj02_mode_entries)
                val values = context.resources.getStringArray(R.array.magic_touch_jj02_mode_values)
                val cur = prefsOf(device).getString(MagicTouchJj02Support.PREF_MODE, "1")
                val checked = values.indexOf(cur).takeIf { it >= 0 } ?: 0
                android.app.AlertDialog.Builder(activity)
                    .setTitle(R.string.magic_touch_jj02_mode_title)
                    .setSingleChoiceItems(names, checked) { dialog, which ->
                        prefsOf(device).edit {
                            putString(MagicTouchJj02Support.PREF_MODE, values[which])
                        }
                        callConfig(device, "mode")
                        dialog.dismiss()
                    }
                    .show()
            }
        }
    },
    deviceCardAction {
        id = "magic_touch_heating"
        icon = { R.drawable.ic_mode_heat }
        description = { _, context -> context.getString(R.string.magic_touch_jj02_heating_title) }
        label = { device, context ->
            val on = prefsOf(device).getBoolean(MagicTouchJj02Support.PREF_HEATING, false)
            if (!on) {
                context.getString(R.string.off)
            } else {
                val level = prefLabel(
                    context,
                    prefsOf(device),
                    MagicTouchJj02Support.PREF_HEAT_LEVEL,
                    R.array.magic_touch_jj02_heat_level_entries,
                    R.array.magic_touch_jj02_heat_level_values,
                )
                if (level != null) "On $level" else context.getString(R.string.on)
            }
        }
        isVisible = { it.isInitialized && isPowerOn(it) }
        onClick = { device, context ->
            val activity = context as? android.app.Activity
            if (activity == null) {
                val cur = prefsOf(device).getBoolean(MagicTouchJj02Support.PREF_HEATING, false)
                prefsOf(device).edit { putBoolean(MagicTouchJj02Support.PREF_HEATING, !cur) }
                callConfig(device, "heat")
            } else {
                val levels = context.resources.getStringArray(R.array.magic_touch_jj02_heat_level_entries)
                val levelValues = context.resources.getStringArray(R.array.magic_touch_jj02_heat_level_values)
                val items = arrayOf(context.getString(R.string.off)) + levels
                val prefs = prefsOf(device)
                val heatingOn = prefs.getBoolean(MagicTouchJj02Support.PREF_HEATING, false)
                val level = prefs.getString(MagicTouchJj02Support.PREF_HEAT_LEVEL, levelValues.first())
                val checked = if (!heatingOn) {
                    0
                } else {
                    levelValues.indexOf(level).takeIf { it >= 0 }?.plus(1) ?: 1
                }
                android.app.AlertDialog.Builder(activity)
                    .setTitle(R.string.magic_touch_jj02_heating_title)
                    .setSingleChoiceItems(items, checked) { dialog, which ->
                        if (which == 0) {
                            prefs.edit { putBoolean(MagicTouchJj02Support.PREF_HEATING, false) }
                        } else {
                            prefs.edit {
                                putBoolean(MagicTouchJj02Support.PREF_HEATING, true)
                                    .putString(MagicTouchJj02Support.PREF_HEAT_LEVEL, levelValues[which - 1])}
                        }
                        callConfig(device, "heat")
                        dialog.dismiss()
                    }
                    .show()
            }
        }
    },
    deviceCardAction {
        id = "magic_touch_timer"
        icon = { R.drawable.ic_timer }
        description = { _, context -> context.getString(R.string.magic_touch_jj02_timer_title) }
        label = { device, context ->
            val minutes = prefsOf(device).getString(MagicTouchJj02Support.PREF_TIMER, "15")?.toIntOrNull() ?: 15
            context.getString(R.string.num_min, minutes)
        }
        isVisible = { it.isInitialized && isPowerOn(it) }
        onClick = { device, context ->
            val cur = (prefsOf(device).getString(MagicTouchJj02Support.PREF_TIMER, "15")?.toIntOrNull() ?: 15)
                .coerceIn(PID_TARGET_TIME_MIN, PID_TARGET_TIME_MAX)
            val activity = context as? android.app.Activity
            if (activity == null) {
                val next = if (cur >= PID_TARGET_TIME_MAX) PID_TARGET_TIME_MIN else cur + 1
                prefsOf(device).edit {
                    putString(MagicTouchJj02Support.PREF_TIMER, next.toString())
                }
                callConfig(device, "timer")
            } else {
                val density = activity.resources.displayMetrics.density
                val pad = (24 * density).toInt()
                val valueView = android.widget.TextView(activity).apply {
                    text = activity.getString(R.string.num_min, cur)
                    textSize = 16f
                }
                val seekBar = android.widget.SeekBar(activity).apply {
                    max = PID_TARGET_TIME_MAX - PID_TARGET_TIME_MIN
                    progress = cur - PID_TARGET_TIME_MIN
                    setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(bar: android.widget.SeekBar, value: Int, fromUser: Boolean) {
                            valueView.text = activity.getString(R.string.num_min,
                                value + PID_TARGET_TIME_MIN
                            )
                        }

                        override fun onStartTrackingTouch(bar: android.widget.SeekBar) {}

                        override fun onStopTrackingTouch(bar: android.widget.SeekBar) {}
                    })
                }
                val container = android.widget.LinearLayout(activity).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(pad, (pad / 2), pad, 0)
                    addView(valueView)
                    addView(seekBar)
                }
                android.app.AlertDialog.Builder(activity)
                    .setTitle(R.string.magic_touch_jj02_timer_title)
                    .setView(container)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val minutes = seekBar.progress + PID_TARGET_TIME_MIN
                        prefsOf(device).edit {
                            putString(MagicTouchJj02Support.PREF_TIMER, minutes.toString())
                        }
                        callConfig(device, "timer")
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    },
)
