package nodomain.freeyourgadget.gadgetbridge.devices.teufel

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.enumList
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.volume
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLClassicDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControl
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControls
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.teufel.TeufelMyndAutoOff
import nodomain.freeyourgadget.gadgetbridge.service.devices.teufel.TeufelMyndSource
import nodomain.freeyourgadget.gadgetbridge.service.devices.teufel.TeufelMyndSupport
import java.util.regex.Pattern

class TeufelMyndCoordinator : AbstractBLClassicDeviceCoordinator() {
    override fun getManufacturer(): String {
        return "Teufel"
    }

    override fun getSupportedDeviceName(): Pattern? {
        return Pattern.compile("^MYND$")
    }

    override fun getDeviceNameResource(): Int {
        return R.string.devicetype_teufel_mynd
    }

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind {
        return DeviceCoordinator.DeviceKind.SPEAKER
    }

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport?> {
        return TeufelMyndSupport::class.java
    }

    override fun suggestUnbindBeforePair(): Boolean {
        return false
    }

    override fun getDefaultIconResource(): Int {
        return R.drawable.ic_device_speaker
    }

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec = deviceSettings {
        enumList<TeufelMyndSource>(
            key = DeviceSettingsPreferenceConst.PREF_MEDIA_SOURCE,
            title = R.string.media_source,
            icon = R.drawable.ic_music_note,
            defaultValue = TeufelMyndSource.BLUETOOTH,
        )
        volume(max = 100, defaultValue = 50)
        switchSetting(
            key = DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_MUTE,
            title = R.string._pebble_watch_mute,
            icon = R.drawable.ic_volume_off,
        )
        screen(
            key = "pref_screen_teufel_mynd_equalizer",
            title = R.string.pref_header_equalizer,
            icon = R.drawable.ic_equalizer,
        ) {
            seekbar(
                key = DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_EQ_BASS,
                title = R.string.haylou_s35_anc_eq_bass,
                icon = R.drawable.ic_graphic_eq,
                min = TeufelMyndSupport.EQ_GAIN_MIN,
                max = TeufelMyndSupport.EQ_GAIN_MAX,
                defaultValue = 0,
            )
            seekbar(
                key = DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_EQ_TREBLE,
                title = R.string.ugreen_equalizer_treble,
                icon = R.drawable.ic_graphic_eq,
                min = TeufelMyndSupport.EQ_GAIN_MIN,
                max = TeufelMyndSupport.EQ_GAIN_MAX,
                defaultValue = 0,
            )
        }

        switchSetting(
            key = DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_SOUND_ICONS,
            title = R.string.teufel_mynd_sound_icons,
            icon = R.drawable.ic_notifications,
            defaultValue = true,
        )
        seekbar(
            key = DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_LED_BRIGHTNESS,
            title = R.string.teufel_mynd_led_brightness,
            icon = R.drawable.ic_brightness_medium,
            max = 100,
            defaultValue = 100,
        )

        enumList<TeufelMyndAutoOff>(
            key = DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_AUTO_OFF,
            title = R.string.soundcore_auto_power_off_title,
            icon = R.drawable.ic_power_settings_new,
            defaultValue = TeufelMyndAutoOff.MINUTES_15,
        )

        DeviceSpecificSettingsScreen.BATTERY.screen {
            switchSetting(
                key = DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_ECO_MODE,
                title = R.string.teufel_mynd_eco_mode,
                icon = R.drawable.ic_battery_saver,
            )
            switchSetting(
                key = DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_BATTERY_FRIENDLY_CHARGING,
                title = R.string.teufel_mynd_battery_friendly_charging,
                icon = R.drawable.ic_battery_charging,
            )
        }

        DeviceSpecificSettingsScreen.CONNECTION.screen {
            switchSetting(
                key = DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_MULTIPOINT,
                title = R.string.bluetooth_multipoint,
                icon = R.drawable.ic_bluetooth_connected,
            )
            switchSetting(
                key = DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_PARTY_LINK,
                title = R.string.teufel_mynd_party_link,
                icon = R.drawable.ic_speaker,
            )
        }

        xmlScreen(
            DeviceSpecificSettingsScreen.CALLS_AND_NOTIFICATIONS,
            R.xml.devicesettings_headphones,
            connectedOnly = false,
        )
    }

    override fun getQuickControls(device: GBDevice): List<QuickControl> {
        return QuickControls.fromDeviceSettings(
            device,
            keys = listOf(
                DeviceSettingsPreferenceConst.PREF_MEDIA_SOURCE,
                DeviceSettingsPreferenceConst.PREF_VOLUME,
            ),
            optionalKeys = listOf(
                DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_MUTE,
                DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_PARTY_LINK,
                DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_EQ_BASS,
                DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_EQ_TREBLE,
            ),
        )
    }

    override fun supportsAppNotifications(device: GBDevice): Boolean = true

    override fun supportsPhoneCalls(device: GBDevice): Boolean = true

    override fun supportsCallControl(device: GBDevice): Boolean = true
}
