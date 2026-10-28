package nodomain.freeyourgadget.gadgetbridge.devices.test

import android.widget.Toast
import androidx.core.content.edit
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsActivity
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControl
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControlButton
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControlButtons
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControlDialog
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControlOption
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControlSlider
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControlSwitch
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControlToggleGroup
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControlTrailingAction
import nodomain.freeyourgadget.gadgetbridge.util.GB

private const val PREF_TEST_QUICK_CONTROL_MODE = "test_quick_control_mode"
private const val PREF_TEST_QUICK_CONTROL_VOLUME = "test_quick_control_volume"
private const val PREF_TEST_QUICK_CONTROL_EQUALIZER = "test_quick_control_equalizer"
private const val PREF_TEST_QUICK_CONTROL_VIBRATION = "test_quick_control_vibration"
private const val PREF_TEST_QUICK_CONTROL_WIND_NOISE_REDUCTION = "test_quick_control_wind_noise_reduction"
private const val PREF_TEST_QUICK_CONTROL_PLAYING = "test_quick_control_playing"

fun testDeviceQuickControls(): List<QuickControl> = listOf(
    QuickControlButtons(
        id = "test_quick_control_media",
        icon = R.drawable.ic_play,
        title = R.string.prefs_media_controls,
        buttons = listOf(
            QuickControlButton(R.string.pref_media_previous, { R.drawable.ic_skip_previous }) { _, context ->
                GB.toast(context, R.string.pref_media_previous, Toast.LENGTH_SHORT, GB.INFO)
            },
            QuickControlButton(
                R.string.moondrop_touch_action_play_pause,
                { device->
                    if (GBApplication.getDevicePrefs(device).getBoolean(PREF_TEST_QUICK_CONTROL_PLAYING, false)) {
                        R.drawable.ic_pause
                    } else {
                        R.drawable.ic_play
                    }
                },
            ) { device, _ ->
                val prefs = GBApplication.getDeviceSpecificSharedPrefs(device.address)
                prefs.edit {
                    putBoolean(
                        PREF_TEST_QUICK_CONTROL_PLAYING,
                        !prefs.getBoolean(PREF_TEST_QUICK_CONTROL_PLAYING, false)
                    )
                }
            },
            QuickControlButton(R.string.pref_media_next, { R.drawable.ic_skip_next }) { _, context ->
                GB.toast(context, R.string.pref_media_next, Toast.LENGTH_SHORT, GB.INFO)
            },
        ),
    ),
    QuickControlToggleGroup.forPreference(
        key = PREF_TEST_QUICK_CONTROL_MODE,
        title = R.string.sony_ambient_sound,
        icon = R.drawable.ic_hearing,
        defaultValue = "low",
        options = listOf(
            QuickControlOption("off", R.string.off, R.drawable.ic_hearing_disabled),
            QuickControlOption("low", R.string.low, R.drawable.ic_hearing),
            QuickControlOption("high", R.string.high, R.drawable.ic_noise_control_on),
        ),
    ),
    QuickControlSlider.forPreference(
        key = PREF_TEST_QUICK_CONTROL_VOLUME,
        icon = R.drawable.ic_volume_up,
        title = R.string.pref_title_touch_volume,
        min = 0,
        max = 10,
        defaultValue = 5,
        visible = { device ->
            GBApplication.getDevicePrefs(device).getString(PREF_TEST_QUICK_CONTROL_MODE, "low") != "off"
        },
    ),
    QuickControlSwitch.forPreference(
        key = PREF_TEST_QUICK_CONTROL_WIND_NOISE_REDUCTION,
        icon = R.drawable.ic_air,
        title = R.string.sony_ambient_sound_wind_noise_reduction,
        defaultValue = false,
    ),
    QuickControlDialog.forPreference(
        key = PREF_TEST_QUICK_CONTROL_EQUALIZER,
        icon = R.drawable.ic_graphic_eq,
        title = R.string.pref_header_equalizer,
        defaultValue = "standard",
        options = listOf(
            QuickControlOption("standard", R.string.equalizer_preset_standard),
            QuickControlOption("vocal", R.string.sony_equalizer_preset_vocal),
            QuickControlOption("bass", R.string.sony_equalizer_preset_bass_boost),
            QuickControlOption("custom", R.string.custom),
        ),
        trailingAction = QuickControlTrailingAction(
            icon = R.drawable.ic_settings,
            description = R.string.pref_header_equalizer,
            visible = { _, value -> value == "custom" },
            onClick = { device, context -> DeviceSettingsActivity.start(context, device) },
        ),
    ),
    QuickControlSlider.forPreference(
        key = PREF_TEST_QUICK_CONTROL_VIBRATION,
        icon = R.drawable.ic_vibration,
        title = R.string.pref_vibration_intensity,
        min = 0,
        max = 100,
        step = 10,
        continuous = true,
        defaultValue = 50,
        optional = true,
    ),
)
