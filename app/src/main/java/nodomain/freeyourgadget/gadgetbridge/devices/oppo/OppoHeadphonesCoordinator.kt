/*  Copyright (C) 2024 José Rebelo
    Copyright (C) 2026 NTeditor

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.devices.oppo

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.util.Pair
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsCustomizer
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsScope
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.enumInfo
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.enumList
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.multiEnumList
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.multipointPairing
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLClassicDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControl
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControlOption
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControlToggleGroup
import nodomain.freeyourgadget.gadgetbridge.devices.cards.QuickControls
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceCandidate
import nodomain.freeyourgadget.gadgetbridge.model.BatteryConfig
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.OppoHeadphonesSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.OppoUuid
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.AncConfigValue
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.EarbudsStatusSide
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.EarbudsStatusValue
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.TouchConfigSide
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.TouchConfigType
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.TouchConfigValue
import java.nio.ByteOrder
import java.util.Locale

abstract class OppoHeadphonesCoordinator : AbstractBLClassicDeviceCoordinator() {
    override fun getManufacturer(): String = "Oppo"

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> =
        OppoHeadphonesSupport::class.java

    override fun getDefaultIconResource(): Int = R.drawable.ic_device_nothingear

    override fun getBatteryCount(device: GBDevice): Int = 3

    @SuppressLint("MissingPermission") // if we're here, we have permission
    override fun supports(candidate: GBDeviceCandidate): Boolean {
        if (!super.supports(candidate)) return false
        val majorDeviceClass = candidate.device?.bluetoothClass?.majorDeviceClass
        return majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO
    }

    override fun getBatteryConfig(device: GBDevice): Array<BatteryConfig> = arrayOf(
        BatteryConfig(0, R.drawable.ic_nothing_ear_l, R.string.left_earbud),
        BatteryConfig(1, R.drawable.ic_nothing_ear_r, R.string.right_earbud),
        BatteryConfig(2, R.drawable.ic_tws_case, R.string.battery_case)
    )

    protected abstract val touchOptions: Map<Pair<TouchConfigSide, TouchConfigType>, List<TouchConfigValue>>

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec = deviceSettings {
        noiseControlOptions(device)
        if (supportsGameMode(device)) {
            switchSetting(
                key = OppoHeadphonesPreferences.GAME_MODE,
                title = R.string.prefs_game_mode,
                icon = R.drawable.ic_videogame,
            )
        }
        if (supportsLdac(device)) {
            switchSetting(
                key = OppoHeadphonesPreferences.LDAC,
                title = R.string.soundcore_ldac_mode_title,
                icon = R.drawable.ic_music_note,
            )
        }
        if (supportsMultipoint(device)) {
            multipointPairing()
        }
        touchOptions(device)
        xmlScreen(
            DeviceSpecificSettingsScreen.CALLS_AND_NOTIFICATIONS,
            R.xml.devicesettings_headphones,
        )
    }

    private fun DeviceSettingsScope.noiseControlOptions(device: GBDevice) {
        if (!supportsAnc(device)) {
            return
        }

        category(
            key = OppoHeadphonesPreferences.NOISE_CONTROL_HEADER,
            title = R.string.prefs_noise_control
        ) {
            enumList<AncConfigValue>(
                key = OppoHeadphonesPreferences.ANC_SELECTOR,
                title = R.string.prefs_noise_control,
                icon = R.drawable.ic_surround,
                defaultValue = AncConfigValue.OFF,
            )

            if (supportsAncLevel(device)) {
                enumList<AncConfigValue.Level>(
                    key = OppoHeadphonesPreferences.ANC_LEVEL_SELECTOR,
                    title = R.string.prefs_active_noise_cancelling_level,
                    defaultValue = AncConfigValue.Level.HIGH,
                    dependency = OppoHeadphonesPreferences.ANC_SELECTOR,
                    visibleWhen = { prefs ->
                        val modePreference =
                            prefs.getString(OppoHeadphonesPreferences.ANC_SELECTOR, null)
                        val mode = AncConfigValue.fromPrefId(modePreference)
                        mode == AncConfigValue.ON
                    })
                enumInfo<AncConfigValue.Level>(
                    key = OppoHeadphonesPreferences.ANC_LEVEL_DYNAMIC,
                    title = R.string.prefs_active_noise_cancelling_effective_level,
                    dependency = OppoHeadphonesPreferences.ANC_LEVEL_SELECTOR,
                    visibleWhen = { prefs ->
                        val modePreference =
                            prefs.getString(OppoHeadphonesPreferences.ANC_SELECTOR, null)
                        val mode = AncConfigValue.fromPrefId(modePreference)

                        val modeLevelPreference =
                            prefs.getString(OppoHeadphonesPreferences.ANC_LEVEL_SELECTOR, null)
                        val modeLevel = AncConfigValue.Level.fromPrefId(modeLevelPreference)

                        mode == AncConfigValue.ON && modeLevel == AncConfigValue.Level.DYNAMIC
                    })
            }
        }
    }

    private fun DeviceSettingsScope.touchOptions(device: GBDevice) {
        DeviceSpecificSettingsScreen.TOUCH_OPTIONS.screen {
            touchOptions.entries.groupBy({ it.key.first }, { it.key.second to it.value })
                .forEach { (side, typeGroups) ->
                    category(
                        key = "pref_key_header_oppo_${side.name.lowercase(Locale.ROOT)}",
                        title = side.label,
                    ) {
                        typeGroups.forEach { (type, values) ->
                            enumList<TouchConfigValue>(
                                key = OppoHeadphonesPreferences.getTouchKey(side, type),
                                title = type.label,
                                icon = type.icon,
                                defaultValue = TouchConfigValue.OFF,
                                filter = { it in values })
                        }
                    }
                }
            if (supportsAnc(device)) {
                touchOptionsAncCycleModes()
            }
            if (supportsFindPhone(device)) {
                switchSetting(
                    key = OppoHeadphonesPreferences.FIND_PHONE,
                    title = R.string.prefs_find_phone,
                    icon = R.drawable.ic_vibration,
                )
            }
        }
    }

    private fun DeviceSettingsScope.touchOptionsAncCycleModes() {
        multiEnumList<AncConfigValue>(
            key = OppoHeadphonesPreferences.ANC_TOUCH_CYCLE_MODES,
            title = R.string.prefs_noise_modes_to_cycle,
            icon = R.drawable.ic_cycle,
            defaultValue = setOf(AncConfigValue.ON, AncConfigValue.TRANSPARENCY),
            visibleWhen = { prefs ->
                touchOptions.entries.any { (key, _) ->
                    val prefKey = OppoHeadphonesPreferences.getTouchKey(key.first, key.second)
                    val prefValue = prefs.getString(prefKey, TouchConfigValue.OFF.prefId)
                    val value = TouchConfigValue.fromPrefId(prefValue)
                    value == TouchConfigValue.NOISE_CONTROL
                }
            })
    }

    override fun getDeviceSpecificSettingsCustomizer(device: GBDevice): DeviceSpecificSettingsCustomizer =
        OppoHeadphonesSettingsCustomizer()

    override fun getQuickControls(device: GBDevice): List<QuickControl> = listOf(
        QuickControlToggleGroup.forPreference(
            key = OppoHeadphonesPreferences.ANC_SELECTOR,
            title = R.string.prefs_noise_control,
            icon = R.drawable.ic_surround,
            options = AncConfigValue.entries.map {
                QuickControlOption(it.prefId, it.shortLabel, it.icon)
            },
            visible = { device -> supportsAnc(device) },
            defaultValue = AncConfigValue.OFF.prefId,
        ),
        *QuickControls.fromDeviceSettings(
            device = device, keys = listOf(
                OppoHeadphonesPreferences.GAME_MODE, OppoHeadphonesPreferences.LDAC
            )
        ).toTypedArray(),
    )

    final override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind =
        DeviceCoordinator.DeviceKind.EARBUDS

    open fun ctrlUuid(device: GBDevice): OppoUuid = OppoUuid.VENDOR_RFCOMM
    open fun multipointMacOrder(device: GBDevice): ByteOrder = ByteOrder.BIG_ENDIAN
    open fun canApplyAncMode(
        device: GBDevice,
        earbudsStatus: Map<EarbudsStatusSide, EarbudsStatusValue>,
        mode: AncConfigValue
    ): Boolean = true

    open fun supportsLdac(device: GBDevice): Boolean = false
    open fun supportsMultipoint(device: GBDevice): Boolean = false
    open fun supportsGameMode(device: GBDevice): Boolean = false
    open fun supportsAnc(device: GBDevice): Boolean = false
    open fun supportsAncLevel(device: GBDevice): Boolean = false
    open fun supportsFindPhone(device: GBDevice): Boolean = false

    override fun supportsAppNotifications(device: GBDevice): Boolean = true

    override fun supportsPhoneCalls(device: GBDevice): Boolean = true

    override fun supportsCallControl(device: GBDevice): Boolean = true
}
