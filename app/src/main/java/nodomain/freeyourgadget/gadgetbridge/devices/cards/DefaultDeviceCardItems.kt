package nodomain.freeyourgadget.gadgetbridge.devices.cards

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.NumberPicker
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import com.jaredrummler.android.colorpicker.MaterialColorPickerDialog
import nodomain.freeyourgadget.gadgetbridge.BuildConfig
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.ConfigureAlarms
import nodomain.freeyourgadget.gadgetbridge.activities.ConfigureReminders
import nodomain.freeyourgadget.gadgetbridge.activities.HeartRateDialog
import nodomain.freeyourgadget.gadgetbridge.activities.VibrationActivity
import nodomain.freeyourgadget.gadgetbridge.activities.charts.ActivityChartsActivity
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsActivity
import nodomain.freeyourgadget.gadgetbridge.activities.gallery.GalleryActivity
import nodomain.freeyourgadget.gadgetbridge.activities.install.InstallLauncher
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutListActivity
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCardAction
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType
import nodomain.freeyourgadget.gadgetbridge.model.FindDeviceTarget
import nodomain.freeyourgadget.gadgetbridge.model.RecordedDataTypes
import nodomain.freeyourgadget.gadgetbridge.util.GB
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Builds the default list of [DeviceCardItem]s for a device, from a coordinator's capabilities.
 */
object DefaultDeviceCardItems {
    @JvmStatic
    fun build(coordinator: DeviceCoordinator, device: GBDevice): List<DeviceCardItem> {
        val items = mutableListOf<DeviceCardItem>()
        val initialized: (GBDevice) -> Boolean = { it.isInitialized }
        val always: (GBDevice) -> Boolean = { true }

        for (i in 0 until coordinator.getBatteryCount(device)) {
            items.add(BatteryCardItem(i))
        }

        @Suppress("DEPRECATION")
        if (coordinator.getSupportedDeviceSpecificSettings(device) != null) {
            items.add(ActionCardItem(settingsAction(), visible = always))
        }

        if (device.getExtraInfo("fm_frequency") != null) {
            items.add(ActionCardItem(fmFrequencyAction(), visible = initialized))
        }

        if (coordinator.supportsLedColor(device)) {
            items.add(ledColorItem(coordinator))
        }

        if (coordinator.supportsDataFetching(device)) {
            items.add(
                ActionCardItem(
                    fetchActivityAction(),
                    busyIndicator = true,
                    visible = initialized
                )
            )
        }

        if (coordinator.supportsScreenshots(device)) {
            items.add(ActionCardItem(screenshotAction(), visible = initialized))
        }

        if (coordinator.supportsGallery(device)) {
            items.add(
                ActionCardItem(
                    DeviceCardAction.forActivity(
                        R.drawable.ic_photo_library,
                        R.string.photo_gallery,
                        GalleryActivity::class.java
                    ),
                    id = "gallery",
                    visible = initialized,
                )
            )
        }

        if (coordinator.supportsAppsManagement(device)) {
            coordinator.getAppsManagementActivity(device)?.let { activityClass ->
                items.add(
                    ActionCardItem(
                        DeviceCardAction.forActivity(
                            R.drawable.ic_action_manage_apps,
                            R.string.title_activity_appmanager,
                            activityClass
                        ),
                        id = "apps",
                        visible = initialized,
                    )
                )
            }
        }

        if (coordinator.getAlarmSlotCount(device) > 0) {
            items.add(
                ActionCardItem(
                    DeviceCardAction.forActivity(
                        R.drawable.ic_access_alarms,
                        R.string.controlcenter_start_configure_alarms,
                        ConfigureAlarms::class.java
                    ),
                    id = "alarms",
                    visible = always,
                )
            )
        }

        if (coordinator.getReminderSlotCount(device) > 0) {
            items.add(
                ActionCardItem(
                    DeviceCardAction.forActivity(
                        R.drawable.ic_device_set_reminders,
                        R.string.controlcenter_start_configure_reminders,
                        ConfigureReminders::class.java
                    ),
                    id = "reminders",
                    visible = always,
                )
            )
        }

        if (coordinator.supportsCharts(device)) {
            items.add(
                ActionCardItem(
                    DeviceCardAction.forActivity(
                        R.drawable.ic_activity_graphs,
                        R.string.charts,
                        ActivityChartsActivity::class.java
                    ),
                    id = "charts",
                    visible = always,
                )
            )
        }

        if (coordinator.supportsRecordedActivities(device)) {
            items.add(
                ActionCardItem(
                    DeviceCardAction.forActivity(
                        R.drawable.ic_activity_tracks,
                        R.string.controlcenter_start_activity_tracks,
                        WorkoutListActivity::class.java
                    ),
                    id = "workouts",
                    visible = always,
                )
            )
        }

        if (coordinator.supportsFindDevice(device)) {
            items.add(ActionCardItem(findDeviceAction(coordinator), id = "find_device", visible = initialized))
        }

        coordinator.calibrationActivity?.let { activityClass ->
            items.add(
                ActionCardItem(
                    DeviceCardAction.forActivity(
                        R.drawable.ic_activity_unknown,
                        R.string.controlcenter_calibrate_device,
                        activityClass
                    ),
                    id = "calibration",
                    visible = initialized,
                )
            )
        }

        if (coordinator.supportsRealtimeData(device)) {
            items.add(
                HeartRateCardItem(
                    visible = initialized,
                    liveOnly = coordinator.supportsLiveOnlyHeartRateDisplay(device),
                    onClick = ::heartRateTestOnClick,
                )
            )
        }

        if (coordinator.supportsPowerOff(device)) {
            items.add(ActionCardItem(powerOffAction(), visible = initialized))
        }

        @Suppress("DEPRECATION")
        coordinator.customActions.forEach {
            items.add(ActionCardItem(it))
        }

        if (BuildConfig.DEBUG) {
            items.add(ActionCardItem(testNewFunctionAction(), visible = initialized, optional = true))
            if (coordinator.supportsFlashing(device)) {
                items.add(ActionCardItem(installFileAction(), visible = initialized, optional = true))
            }
        }

        items.add(MoreCardItem)

        return items
    }

    private fun snackBarAnchor(context: Context): View =
        (context as Activity).window.decorView.findViewById(android.R.id.content)

    private fun settingsAction(): DeviceCardAction = object : DeviceCardAction {
        override fun getId(): String = "device_specific_settings"
        override fun getIcon(device: GBDevice) = R.drawable.ic_settings
        override fun getDescription(device: GBDevice, context: Context): String =
            context.getString(R.string.title_activity_device_specific_settings)

        override fun onClick(device: GBDevice, context: Context) {
            DeviceSettingsActivity.start(context, device)
        }
    }

    private fun fetchActivityAction(): DeviceCardAction = object : DeviceCardAction {
        override fun getId(): String = "sync"
        override fun getIcon(device: GBDevice) = R.drawable.ic_refresh
        override fun getDescription(device: GBDevice, context: Context): String =
            context.getString(R.string.controlcenter_fetch_activity_data)

        override fun onClick(device: GBDevice, context: Context) {
            Snackbar.make(snackBarAnchor(context), R.string.busy_task_fetch_activity_data, Snackbar.LENGTH_SHORT).show()
            GBApplication.deviceService(device).onFetchRecordedData(RecordedDataTypes.TYPE_SYNC)
        }
    }

    private fun screenshotAction(): DeviceCardAction = object : DeviceCardAction {
        override fun getId(): String = "screenshot"
        override fun getIcon(device: GBDevice) = R.drawable.ic_screenshot
        override fun getDescription(device: GBDevice, context: Context): String =
            context.getString(R.string.controlcenter_take_screenshot)

        override fun onClick(device: GBDevice, context: Context) {
            Snackbar.make(
                snackBarAnchor(context),
                R.string.controlcenter_snackbar_requested_screenshot,
                Snackbar.LENGTH_SHORT
            ).show()
            GBApplication.deviceService(device).onScreenshotReq()
        }
    }

    private fun powerOffAction(): DeviceCardAction = object : DeviceCardAction {
        override fun getId(): String = "power_off"
        override fun getIcon(device: GBDevice) = R.drawable.ic_power_settings_new
        override fun getDescription(device: GBDevice, context: Context): String =
            context.getString(R.string.controlcenter_power_off)

        override fun onClick(device: GBDevice, context: Context) {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.controlcenter_power_off_confirm_title)
                .setMessage(R.string.controlcenter_power_off_confirm_description)
                .setIcon(R.drawable.ic_power_settings_new)
                .setPositiveButton(R.string.yes) { _, _ -> GBApplication.deviceService(device).onPowerOff() }
                .setNegativeButton(R.string.no, null)
                .show()
        }
    }

    private fun findDeviceAction(coordinator: DeviceCoordinator): DeviceCardAction = object : DeviceCardAction {
        override fun getId(): String = "find_device"
        override fun getIcon(device: GBDevice) = R.drawable.ic_action_find_lost_device
        override fun getDescription(device: GBDevice, context: Context): String =
            context.getString(R.string.controlcenter_find_device)

        override fun onClick(device: GBDevice, context: Context) {
            val message = context.getString(R.string.find_lost_device_message, device.aliasOrName)
            val builder = MaterialAlertDialogBuilder(context)
                .setCancelable(true)
                .setTitle(R.string.controlcenter_find_device)
                .setNegativeButton(R.string.cancel, null)

            if (!coordinator.supportsFindDevicePerEarbud(device)) {
                if (coordinator.getDeviceKind(device) == DeviceCoordinator.DeviceKind.HEADPHONES) {
                    // For headphones, display a warning
                    builder.setMessage(message + "\n\n" + context.getString(R.string.earfun_find_headphones_hint))
                } else {
                    builder.setMessage(message)
                }
                builder.setPositiveButton(R.string.ok) { _, _ ->
                    startFindDevice(
                        device,
                        context,
                        FindDeviceTarget.ALL
                    )
                }
                    .show()
                return
            }

            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_find_device, null)
            dialogView.findViewById<TextView>(R.id.find_device_message).text = message
            val targetGroup = dialogView.findViewById<RadioGroup>(R.id.find_device_target_group)

            builder.setView(dialogView)
                .setPositiveButton(R.string.ok) { _, _ ->
                    val target = when (targetGroup.checkedRadioButtonId) {
                        R.id.find_device_target_left -> FindDeviceTarget.LEFT
                        R.id.find_device_target_right -> FindDeviceTarget.RIGHT
                        else -> FindDeviceTarget.ALL
                    }
                    startFindDevice(device, context, target)
                }
                .show()
        }
    }

    private fun startFindDevice(device: GBDevice, context: Context, target: FindDeviceTarget) {
        if (device.type == DeviceType.VIBRATISSIMO) {
            val startIntent = Intent(context, VibrationActivity::class.java)
            startIntent.putExtra(GBDevice.EXTRA_DEVICE, device)
            context.startActivity(startIntent)
            return
        }

        Snackbar.make(
            snackBarAnchor(context),
            R.string.control_center_find_lost_device,
            Snackbar.LENGTH_INDEFINITE
        ).setAction(R.string.find_lost_device_you_found_it) {
            GBApplication.deviceService(device).onFindDevice(false, target)
        }.addCallback(
            findDeviceSnackbarCallback(
                onStart = { GBApplication.deviceService(device).onFindDevice(true, target) },
                onStop = { GBApplication.deviceService(device).onFindDevice(false, target) }
            )).show()
    }

    internal fun findDeviceSnackbarCallback(onStart: () -> Unit, onStop: () -> Unit): Snackbar.Callback =
        object : Snackbar.Callback() {
            override fun onShown(snackbar: Snackbar) {
                // A replaced Snackbar must finish stopping its search before this one starts.
                onStart()
                super.onShown(snackbar)
            }

            override fun onDismissed(snackbar: Snackbar, event: Int) {
                onStop()
                super.onDismissed(snackbar, event)
            }
        }

    private fun heartRateTestOnClick(device: GBDevice, context: Context) {
        GBApplication.deviceService(device).onHeartRateTest()
        HeartRateDialog(device, context).show()
    }

    private fun testNewFunctionAction(): DeviceCardAction = object : DeviceCardAction {
        override fun getId(): String = "test_new_function"
        override fun getIcon(device: GBDevice) = R.drawable.ic_science
        override fun getDescription(device: GBDevice, context: Context): String =
            context.getString(R.string.controlcenter_test_new_function)

        override fun onClick(device: GBDevice, context: Context) {
            Snackbar.make(snackBarAnchor(context), R.string.controlcenter_test_new_function, Snackbar.LENGTH_SHORT)
                .show()
            GBApplication.deviceService(device).onTestNewFunction(null)
        }
    }

    private fun installFileAction(): DeviceCardAction = object : DeviceCardAction {
        override fun getId(): String = "install_file"
        override fun getIcon(device: GBDevice) = R.drawable.ic_file_upload
        override fun getDescription(device: GBDevice, context: Context): String =
            context.getString(R.string.open_fw_installer_info_text_title)

        override fun onClick(device: GBDevice, context: Context) {
            val activity = context as ComponentActivity
            // Registered without a lifecycle owner, so it must unregister itself after the result
            lateinit var launcher: ActivityResultLauncher<Array<String>>
            launcher = activity.activityResultRegistry.register(
                "device_card_install_file",
                ActivityResultContracts.OpenMultipleDocuments()
            ) { uris ->
                launcher.unregister()
                if (uris.isEmpty()) {
                    return@register
                }
                val intent = InstallLauncher.createIntent(activity, device, uris, Bundle.EMPTY)
                if (intent == null) {
                    GB.toast(
                        activity.getString(R.string.fwinstaller_file_not_compatible_to_device),
                        Toast.LENGTH_LONG,
                        GB.INFO
                    )
                    return@register
                }
                activity.startActivity(intent)
            }
            launcher.launch(arrayOf("*/*"))
        }
    }

    private fun fmFrequencyAction(): DeviceCardAction = object : DeviceCardAction {
        override fun getId(): String = "fm_frequency"
        private val freqMin = 87.5f
        private val freqMax = 108.0f
        private val freqMinInt = floor(freqMin.toDouble()).toInt()
        private val freqMaxInt = freqMax.roundToInt()

        override fun getIcon(device: GBDevice) = R.drawable.ic_radio
        override fun getDescription(device: GBDevice, context: Context): String =
            context.getString(R.string.controlcenter_change_fm_frequency)

        override fun getLabel(device: GBDevice, context: Context): String =
            String.format(Locale.getDefault(), "%.1f", device.getExtraInfo("fm_frequency") as Float)

        override fun onClick(device: GBDevice, context: Context) {
            val builder = MaterialAlertDialogBuilder(context)
            val inflater = LayoutInflater.from(context)
            val pickerView = inflater.inflate(R.layout.dialog_frequency_picker, null)
            builder.setTitle(R.string.preferences_fm_frequency)

            val fmPresets = floatArrayOf(
                GBApplication.getDeviceSpecificSharedPrefs(device.address).getFloat("fm_preset0", 99f),
                GBApplication.getDeviceSpecificSharedPrefs(device.address).getFloat("fm_preset1", 100f),
                GBApplication.getDeviceSpecificSharedPrefs(device.address).getFloat("fm_preset2", 101f),
            )

            val decimalPicker = pickerView.findViewById<NumberPicker>(R.id.frequency_dec)
            decimalPicker.minValue = freqMinInt
            decimalPicker.maxValue = freqMaxInt

            val fractionPicker = pickerView.findViewById<NumberPicker>(R.id.frequency_fraction)
            fractionPicker.minValue = 0
            fractionPicker.maxValue = 9

            val pickerListener = NumberPicker.OnValueChangeListener { numberPicker, _, _ ->
                when (numberPicker.value) {
                    freqMinInt -> {
                        fractionPicker.minValue = 5
                        fractionPicker.maxValue = 9
                    }

                    freqMaxInt -> {
                        fractionPicker.minValue = 0
                        fractionPicker.maxValue = 0
                    }

                    else -> {
                        fractionPicker.minValue = 0
                        fractionPicker.maxValue = 9
                    }
                }
            }
            decimalPicker.setOnValueChangedListener(pickerListener)

            val presetButtons = arrayOf<Button>(
                pickerView.findViewById(R.id.frequency_preset1),
                pickerView.findViewById(R.id.frequency_preset2),
                pickerView.findViewById(R.id.frequency_preset3),
            )

            val alert = arrayOfNulls<AlertDialog>(1)
            for (index in presetButtons.indices) {
                presetButtons[index].text = fmPresets[index].toString()
                presetButtons[index].setOnClickListener {
                    val frequency = fmPresets[index]
                    device.setExtraInfo("fm_frequency", frequency)
                    GBApplication.deviceService(device).onSetFmFrequency(frequency)
                    device.sendDeviceUpdateIntent(context)
                    alert[0]?.dismiss()
                }
                presetButtons[index].setOnLongClickListener {
                    val frequency = decimalPicker.value + (0.1f * fractionPicker.value)
                    fmPresets[index] = frequency
                    presetButtons[index].text = frequency.toString()
                    GBApplication.getDeviceSpecificSharedPrefs(device.address).edit {
                        putFloat("fm_preset$index", frequency)
                    }
                    true
                }
            }

            val frequency = device.getExtraInfo("fm_frequency") as Float
            val decimal = frequency.toInt()
            val fraction = ((frequency - decimal) * 10).roundToInt()
            decimalPicker.value = decimal
            pickerListener.onValueChange(decimalPicker, decimalPicker.value, decimal)
            fractionPicker.value = fraction

            builder.setView(pickerView)
            builder.setPositiveButton(context.resources.getString(android.R.string.ok)) { _, _ ->
                val selected = decimalPicker.value + (0.1f * fractionPicker.value)
                if (selected !in freqMin..freqMax) {
                    MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.pref_invalid_frequency_title)
                        .setMessage(R.string.pref_invalid_frequency_message)
                        .setNeutralButton(android.R.string.ok, null)
                        .show()
                } else {
                    device.setExtraInfo("fm_frequency", selected)
                    GBApplication.deviceService(device).onSetFmFrequency(selected)
                    device.sendDeviceUpdateIntent(context)
                }
            }
            builder.setNegativeButton(context.resources.getString(R.string.cancel)) { dialog, _ -> dialog.cancel() }

            alert[0] = builder.create()
            alert[0]?.show()
        }
    }

    private fun ledColorItem(coordinator: DeviceCoordinator): ColorCardItem = ColorCardItem(
        id = "led_color",
        visible = { device -> device.isInitialized && device.getExtraInfo("led_color") != null },
        color = { device -> device.getExtraInfo("led_color") as Int },
        description = { _, context -> context.getString(R.string.controlcenter_change_led_color) },
        onClick = { device, context ->
            val builder = ColorPickerDialog.newBuilder()
            builder.setDialogTitle(R.string.preferences_led_color)

            val presets = coordinator.colorPresets
            val currentColor = device.getExtraInfo("led_color") as Int
            builder.setColor(currentColor)
            builder.setShowAlphaSlider(false)
            builder.setShowColorShades(false)
            if (coordinator.supportsRgbLedColor(device)) {
                builder.setAllowCustom(true)
                if (presets.isEmpty()) {
                    builder.setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                }
            } else {
                builder.setAllowCustom(false)
            }

            if (presets.isNotEmpty()) {
                builder.setAllowPresets(true)
                builder.setPresets(presets)
            }

            val dialog = MaterialColorPickerDialog.create(builder)
            dialog.setColorPickerDialogListener(object : ColorPickerDialogListener {
                override fun onColorSelected(dialogId: Int, color: Int) {
                    device.setExtraInfo("led_color", color)
                    GBApplication.deviceService(device).onSetLedColor(color)
                    // Trigger a refresh, to avoid mutating the Drawable directly
                    device.sendDeviceUpdateIntent(context)
                }

                override fun onDialogDismissed(dialogId: Int) {
                    // Nothing to do
                }
            })
            dialog.show((context as AppCompatActivity).supportFragmentManager, "color-picker-dialog")
        },
    )
}
