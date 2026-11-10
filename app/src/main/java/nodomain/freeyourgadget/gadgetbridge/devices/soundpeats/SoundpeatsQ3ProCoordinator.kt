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

import java.util.Locale
import java.util.regex.Pattern
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SOUNDPEATS_ANC_MODE
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SOUNDPEATS_DUAL_CONNECT
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SOUNDPEATS_EQ_PRESET
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SOUNDPEATS_GAME_MODE
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SOUNDPEATS_MOVIE_MODE
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SOUNDPEATS_PROMPT_VOLUME
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SOUNDPEATS_TOUCH_LOCK
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SOUNDPEATS_WATER_DRAINAGE
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ListEntry
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.enumList
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.activities.soundpeats.SoundpeatsEqEditorActivity
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLClassicDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControl
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControls
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.BatteryConfig
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundpeats.SoundpeatsQ3ProSupport
import androidx.core.content.edit

/**
 * SOUNDPEATS Q3 Pro (vendor model id `S12`).
 *
 * The protocol was mapped from captured traffic and from the capability list the vendor publishes;
 * the transport (BLE GATT vs SPP) and the exact setter payloads have not been observed on the wire
 * yet, so the coordinator is flagged as experimental.
 */
open class SoundpeatsQ3ProCoordinator : AbstractBLClassicDeviceCoordinator() {
    override fun getSupportedDeviceName(): Pattern =
        Pattern.compile("^\\s*SOUNDPEATS Q3 Pro\\s*$", Pattern.CASE_INSENSITIVE)

    override fun getManufacturer(): String = "Soundpeats"

    override fun createDeviceSupport(device: GBDevice): DeviceSupport = SoundpeatsQ3ProSupport()

    override fun getDeviceNameResource(): Int = R.string.devicetype_soundpeats_q3_pro

    override fun getDefaultIconResource(): Int = R.drawable.ic_device_headphones

    override fun getBondingStyle(): Int = BONDING_STYLE_NONE

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind =
        DeviceCoordinator.DeviceKind.EARBUDS

    override fun isExperimental(): Boolean = true

    // The device card creates one item per battery index, so this has to match getBatteryConfig.
    override fun getBatteryCount(device: GBDevice): Int = 3

    override fun getBatteryConfig(device: GBDevice): Array<BatteryConfig> = arrayOf(
        BatteryConfig(0, R.drawable.ic_nothing_ear_l, R.string.left_earbud),
        BatteryConfig(1, R.drawable.ic_nothing_ear_r, R.string.right_earbud),
        BatteryConfig(2, R.drawable.ic_buds_pro_case, R.string.battery_case),
    )

    override fun supportsOSBatteryLevel(device: GBDevice): Boolean = true

    /** The card exposes the standard find device action, which rings and stops the ring. */
    override fun supportsFindDevice(device: GBDevice): Boolean = true

    /** Left and right ear ring are separate commands, so each earbud can be rung on its own. */
    override fun supportsFindDevicePerEarbud(device: GBDevice): Boolean = true

    /** The earbuds only send a battery level as a reply, so it has to be polled. */
    override fun addBatteryPollingSettings(): Boolean = true

    override fun supportsAppNotifications(device: GBDevice): Boolean = true

    override fun supportsPhoneCalls(device: GBDevice): Boolean = true

    override fun supportsCallControl(device: GBDevice): Boolean = true

    /** Battery of both earbuds plus the case are on the card, next to ANC and equalizer shortcuts. */
    override fun getQuickControls(device: GBDevice): List<QuickControl> =
        QuickControls.fromDeviceSettings(
            device = device,
            keys = listOf(PREF_SOUNDPEATS_ANC_MODE, PREF_SOUNDPEATS_EQ_PRESET),
        )

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec = deviceSettings {
        screen(
            key = DeviceSpecificSettingsScreen.SOUND.key,
            title = R.string.pref_header_sound,
            icon = R.drawable.ic_volume_up,
        ) {
            enumList<SoundpeatsAncMode>(
                key = PREF_SOUNDPEATS_ANC_MODE,
                title = R.string.prefs_active_noise_cancelling,
                icon = R.drawable.ic_surround,
                defaultValue = SoundpeatsAncMode.OFF,
            )
            list(
                key = PREF_SOUNDPEATS_EQ_PRESET,
                title = R.string.pref_header_equalizer,
                icon = R.drawable.ic_equalizer,
                defaultValue = SoundpeatsEqPresets.Preset.CLASSIC.name.lowercase(Locale.ROOT),
                entriesProvider = { prefs ->
                    SoundpeatsEqPresets.Preset.entries.map {
                        ListEntry.Res(it.name.lowercase(Locale.ROOT), it.label)
                    } + SoundpeatsCustomEqStore.list(prefs.preferences).map {
                        ListEntry.Text(SoundpeatsCustomEqStore.customValue(it.id), it.name)
                    }
                },
            )
            switchSetting(
                key = PREF_SOUNDPEATS_GAME_MODE,
                title = R.string.prefs_game_mode,
                icon = R.drawable.ic_videogame,
                defaultValue = false,
            )
            switchSetting(
                key = PREF_SOUNDPEATS_MOVIE_MODE,
                title = R.string.soundpeats_movie_mode,
                summary = R.string.soundpeats_movie_mode_summary,
                icon = R.drawable.ic_videogame,
                defaultValue = false,
            )
            seekbar(
                key = PREF_SOUNDPEATS_PROMPT_VOLUME,
                title = R.string.earfun_voice_prompt_volume,
                icon = R.drawable.ic_volume_up,
                min = 0,
                max = 100,
                defaultValue = 50,
                showValue = true,
            )
            switchSetting(
                key = PREF_SOUNDPEATS_WATER_DRAINAGE,
                title = R.string.soundpeats_water_drainage,
                summary = R.string.soundpeats_water_drainage_summary,
                icon = R.drawable.ic_volume_up,
                defaultValue = false,
            )
            category(
                key = "soundpeats_custom_eq_category",
                title = R.string.soundpeats_custom_eq,
            ) {
                externalSettings(
                    key = "soundpeats_custom_eq_editor",
                    title = R.string.soundpeats_custom_eq_edit,
                    icon = R.drawable.ic_equalizer,
                    connectedOnly = true,
                    activityClass = SoundpeatsEqEditorActivity::class.java,
                )
                action(
                    key = "soundpeats_custom_eq_delete",
                    title = R.string.soundpeats_custom_eq_delete,
                    icon = R.drawable.ic_delete,
                    visibleWhen = { SoundpeatsCustomEqStore.customIdOf(it.getString(PREF_SOUNDPEATS_EQ_PRESET, null)) != null },
                    onClick = { _, device -> deleteCustomEq(device) },
                )
            }
        }

        screen(
            key = DeviceSpecificSettingsScreen.CONNECTION.key,
            title = R.string.pref_header_connection,
            icon = R.drawable.ic_mtu,
        ) {
            // The protocol only exposes a dual connection on/off (0x0314/0x0315). The earbuds do not
            // answer the paired device list request (0x0352), so MultipointPairingActivity would show
            // a switch and an unreachable empty list - the vendor app has nothing to show there
            // either. This stays a plain switch.
            switchSetting(
                key = PREF_SOUNDPEATS_DUAL_CONNECT,
                title = R.string.dual_device_mode_title,
                summary = R.string.dual_device_mode_summary,
                icon = R.drawable.ic_link,
                defaultValue = false,
            )
        }

        screen(
            key = DeviceSpecificSettingsScreen.CALLS_AND_NOTIFICATIONS.key,
            title = R.string.pref_header_calls_and_notifications,
            icon = R.drawable.ic_notifications,
        ) {
            switchSetting(
                key = PREF_SOUNDPEATS_TOUCH_LOCK,
                title = R.string.soundpeats_touch_lock,
                icon = R.drawable.ic_touch,
                defaultValue = false,
            )
        }

        xmlScreen(
            DeviceSpecificSettingsScreen.CALLS_AND_NOTIFICATIONS,
            R.xml.devicesettings_headphones,
            connectedOnly = false,
        )
    }

    /** Removes the selected custom profile and falls back to a factory preset. */
    private fun deleteCustomEq(device: GBDevice?): Boolean {
        if (device == null) {
            return false
        }
        val prefs = GBApplication.getDeviceSpecificSharedPrefs(device.address)
        val id = SoundpeatsCustomEqStore.customIdOf(prefs.getString(PREF_SOUNDPEATS_EQ_PRESET, null))
            ?: return false

        SoundpeatsCustomEqStore.delete(prefs, id)
        prefs.edit {
            putString(PREF_SOUNDPEATS_EQ_PRESET, SoundpeatsEqPresets.Preset.CLASSIC.name.lowercase(Locale.ROOT))
        }
        GBApplication.deviceService(device).onSendConfiguration(PREF_SOUNDPEATS_EQ_PRESET)
        return true
    }

}
