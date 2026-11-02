/*  Copyright (C) 2026 fds2610

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
package nodomain.freeyourgadget.gadgetbridge.devices.ninebot

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.PasswordMode
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.enumList
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.passwordScreen
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLEDeviceCoordinator
import android.content.Context
import android.graphics.Color
import android.content.SharedPreferences
import com.google.android.material.color.MaterialColors
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCardAction
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.cards.ActionCardItem
import nodomain.freeyourgadget.gadgetbridge.devices.cards.DeviceCardItem
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.ninebot.NinebotChargeLimit
import nodomain.freeyourgadget.gadgetbridge.service.devices.ninebot.NinebotKers
import nodomain.freeyourgadget.gadgetbridge.service.devices.ninebot.NinebotRideMode
import nodomain.freeyourgadget.gadgetbridge.service.devices.ninebot.NinebotScooterSupport
import java.util.regex.Pattern

/**
 * Ninebot / Segway KickScooters with the classic Ninebot protocol, tested with the F2 Pro (BLE name `NAGV` and ten
 * more characters).
 */
class NinebotScooterCoordinator : AbstractBLEDeviceCoordinator() {
    override fun getSupportedDeviceName(): Pattern = Pattern.compile("^NAGV[0-9A-F]{10}$")

    override fun getManufacturer(): String = "Segway-Ninebot"

    override fun getDeviceNameResource(): Int = R.string.devicetype_ninebot_kickscooter

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport?> =
        NinebotScooterSupport::class.java

    override fun isExperimental(): Boolean = true

    // The default device card switches the scooter off via [NinebotScooterSupport.onPowerOff]
    override fun supportsPowerOff(device: GBDevice): Boolean = true

    // The app pairs with the scooter by itself in the protocol, there is no Bluetooth bonding
    override fun getBondingStyle(): Int = BONDING_STYLE_NONE

    override fun getDefaultIconResource(): Int = R.drawable.ic_device_scooter

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind = DeviceCoordinator.DeviceKind.SCOOTER

    // The battery comes from the default items and is drawn as a battery icon with the percentage
    override fun getCardItems(device: GBDevice): List<DeviceCardItem> =
        super.getCardItems(device) + listOf(
            ActionCardItem(
                InfoAction(
                    DeviceSettingsPreferenceConst.PREF_NINEBOT_RANGE, R.drawable.ic_distance, R.string.ninebot_range_title
                ) { it.getString(DeviceSettingsPreferenceConst.PREF_NINEBOT_RANGE, null) },
                visible = { it.isInitialized },
            ),
            ActionCardItem(ModeAction(), visible = { it.isInitialized }),
            ActionCardItem(SearchAction(), visible = { true }),
            ActionCardItem(PowerOnAction()),
        )

    /** The current riding mode as text (Eco, D, S). A click switches to the next one. */
    private class ModeAction : DeviceCardAction {
        private val order = listOf(NinebotRideMode.ECO, NinebotRideMode.DYNAMIC, NinebotRideMode.SPORT)

        override fun getId(): String = DeviceSettingsPreferenceConst.PREF_NINEBOT_MODE

        override fun getIcon(device: GBDevice) = R.drawable.ic_speed

        private fun current(device: GBDevice): NinebotRideMode? {
            val name = GBApplication.getDeviceSpecificSharedPrefs(device.address)
                .getString(DeviceSettingsPreferenceConst.PREF_NINEBOT_MODE, null)
            return order.find { it.name.lowercase() == name }
        }

        override fun getDescription(device: GBDevice, context: Context): String =
            context.getString(R.string.ninebot_mode_title) +
                (current(device)?.let { ": " + context.getString(it.label) } ?: "")

        override fun getText(device: GBDevice, context: Context): String = current(device)?.shortName ?: "–"

        override fun isVisible(device: GBDevice) = device.isInitialized

        override fun onClick(device: GBDevice, context: Context) {
            val next = current(device)?.let { order[(order.indexOf(it) + 1) % order.size] } ?: order.first()
            GBApplication.getDeviceSpecificSharedPrefs(device.address).edit()
                .putString(DeviceSettingsPreferenceConst.PREF_NINEBOT_MODE, next.name.lowercase()).apply()
            GBApplication.deviceService(device).onSendConfiguration(DeviceSettingsPreferenceConst.PREF_NINEBOT_MODE)
            device.sendDeviceUpdateIntent(context)
        }
    }

    /** Shows a value that the support stores in the device preferences as the label of the icon. */
    private class InfoAction(
        private val key: String,
        private val icon: Int,
        private val title: Int,
        private val value: (SharedPreferences) -> String?,
    ) : DeviceCardAction {
        override fun getId(): String = key

        override fun getIcon(device: GBDevice) = icon

        override fun getDescription(device: GBDevice, context: Context): String = context.getString(title)

        override fun getLabel(device: GBDevice, context: Context): String? =
            value(GBApplication.getDeviceSpecificSharedPrefs(device.address))

        override fun isVisible(device: GBDevice) = device.isInitialized

        // Nothing to do, the values are updated by the poll of the support
        override fun onClick(device: GBDevice, context: Context) {}
    }

    /** Toggles the search for the scooter, which is off by default because it costs battery. */
    private class SearchAction : DeviceCardAction {
        override fun getId(): String = DeviceSettingsPreferenceConst.PREF_NINEBOT_SEARCH

        override fun getIcon(device: GBDevice) = R.drawable.ic_bluetooth_searching

        override fun getDescription(device: GBDevice, context: Context): String =
            context.getString(R.string.ninebot_search_title)

        private fun enabled(device: GBDevice) =
            GBApplication.getDeviceSpecificSharedPrefs(device.address)
                .getBoolean(DeviceSettingsPreferenceConst.PREF_NINEBOT_SEARCH, false)

        override fun getLabel(device: GBDevice, context: Context): String =
            context.getString(if (enabled(device)) R.string.on else R.string.off)

        override fun getIconTint(device: GBDevice, context: Context): Int? =
            if (enabled(device)) MaterialColors.getColor(context, R.attr.accent_color, 0) else null

        override fun isVisible(device: GBDevice) = true

        override fun onClick(device: GBDevice, context: Context) {
            val enable = !enabled(device)
            GBApplication.getDeviceSpecificSharedPrefs(device.address).edit()
                .putBoolean(DeviceSettingsPreferenceConst.PREF_NINEBOT_SEARCH, enable).apply()
            if (device.isConnected || device.isConnecting) {
                GBApplication.deviceService(device).onSendConfiguration(DeviceSettingsPreferenceConst.PREF_NINEBOT_SEARCH)
            } else if (enable) {
                // The support starts the search itself once it exists
                GBApplication.deviceService(device).connect()
            }
            device.sendDeviceUpdateIntent(context)
        }
    }

    /** Connects if necessary, and switches the scooter on as soon as the connection is ready. */
    private class PowerOnAction : DeviceCardAction {
        override fun getId(): String = DeviceSettingsPreferenceConst.PREF_NINEBOT_POWER_ON

        override fun getIcon(device: GBDevice) = R.drawable.ic_power_settings_new

        override fun getDescription(device: GBDevice, context: Context): String =
            context.getString(R.string.ninebot_power_on_title)

        override fun getIconTint(device: GBDevice, context: Context): Int? = COLOR_ON

        override fun isVisible(device: GBDevice) = true

        override fun onClick(device: GBDevice, context: Context) {
            if (device.isInitialized) {
                GBApplication.deviceService(device).onSendConfiguration(DeviceSettingsPreferenceConst.PREF_NINEBOT_POWER_ON)
            } else {
                GBApplication.getDeviceSpecificSharedPrefs(device.address).edit()
                    .putLong(NinebotScooterSupport.PREF_POWER_ON_PENDING, System.currentTimeMillis()).apply()
                if (!device.isConnecting && !device.isConnected) {
                    GBApplication.deviceService(device).connect()
                }
            }
        }
    }

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec = deviceSettings {
        screen(
            key = "ninebot_lock",
            title = R.string.ninebot_code_protection_title,
            icon = R.drawable.ic_lock_open,
        ) {
            info(
                key = DeviceSettingsPreferenceConst.PREF_NINEBOT_LOCK_STATE,
                title = R.string.ninebot_lock_state_title,
                icon = R.drawable.ic_lock_open,
            )
            action(
                key = DeviceSettingsPreferenceConst.PREF_NINEBOT_UNLOCK,
                title = R.string.ninebot_unlock_title,
                summary = R.string.ninebot_unlock_summary,
                icon = R.drawable.ic_lock_open,
                onClick = { _, device ->
                    GBApplication.deviceService(device).onSendConfiguration(DeviceSettingsPreferenceConst.PREF_NINEBOT_UNLOCK)
                    true
                },
            )
            switchSetting(
                key = DeviceSettingsPreferenceConst.PREF_NINEBOT_AUTO_UNLOCK,
                title = R.string.ninebot_auto_unlock_title,
                summary = R.string.ninebot_auto_unlock_summary,
                icon = R.drawable.ic_lock_open,
                connectedOnly = false,
            )
            switchSetting(
                key = DeviceSettingsPreferenceConst.PREF_NINEBOT_SEARCH,
                title = R.string.ninebot_search_title,
                summary = R.string.ninebot_search_summary,
                icon = R.drawable.ic_bluetooth_searching,
                connectedOnly = false,
            )
        }

        passwordScreen(PasswordMode.NUMBERS_4_DIGITS_0_TO_9)

        enumList<NinebotKers>(
            key = DeviceSettingsPreferenceConst.PREF_NINEBOT_KERS,
            title = R.string.ninebot_kers_title,
            defaultValue = NinebotKers.MEDIUM,
            icon = R.drawable.ic_bolt,
        )
        enumList<NinebotChargeLimit>(
            key = DeviceSettingsPreferenceConst.PREF_NINEBOT_CHARGE_LIMIT,
            title = R.string.ninebot_charge_limit_title,
            defaultValue = NinebotChargeLimit.LIMIT_100,
            icon = R.drawable.ic_battery_charging_full,
        )

        screen(
            key = "ninebot_status",
            title = R.string.ninebot_range_title,
            icon = R.drawable.ic_speed,
        ) {
            info(
                key = DeviceSettingsPreferenceConst.PREF_NINEBOT_RANGE,
                title = R.string.ninebot_range_title,
                icon = R.drawable.ic_distance,
            )
            info(
                key = DeviceSettingsPreferenceConst.PREF_NINEBOT_ODOMETER,
                title = R.string.ninebot_odometer_title,
                icon = R.drawable.ic_distance_total,
            )
            info(
                key = DeviceSettingsPreferenceConst.PREF_NINEBOT_TEMPERATURE,
                title = R.string.ninebot_temperature_title,
                icon = R.drawable.ic_thermometer,
            )
        }

        action(
            key = DeviceSettingsPreferenceConst.PREF_NINEBOT_POWER_ON,
            title = R.string.ninebot_power_on_title,
            summary = R.string.ninebot_power_on_summary,
            icon = R.drawable.ic_power_settings_new,
            onClick = { _, device ->
                GBApplication.deviceService(device).onSendConfiguration(DeviceSettingsPreferenceConst.PREF_NINEBOT_POWER_ON)
                true
            },
        )
        action(
            key = DeviceSettingsPreferenceConst.PREF_NINEBOT_POWER_OFF,
            title = R.string.ninebot_power_off_title,
            summary = R.string.ninebot_power_off_summary,
            icon = R.drawable.ic_power_settings_new,
            confirmationMessage = R.string.ninebot_power_off_summary,
            onClick = { _, device ->
                GBApplication.deviceService(device).onSendConfiguration(DeviceSettingsPreferenceConst.PREF_NINEBOT_POWER_OFF)
                true
            },
        )
    }

    private companion object {
        val COLOR_ON = Color.parseColor("#43A047")
    }
}
