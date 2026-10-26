/*  Copyright (C) 2026 Francesco Colasuonno

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
    along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.magic_touch

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsCustomizer
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLEDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCardAction
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.magic_touch.MagicTouchJj02Support
import java.util.regex.Pattern

/**
 * Mijia Smart Neck and Shoulder Massager (xiaomi.magic_touch.jj02).
 *
 * The device advertises as `xiaomi.magic_touch.jj02` and speaks Xiaomi BLE
 * StandardAuth + encrypted miot (see [MagicTouchMiotSpec]). Login needs the
 * 12-byte Mi Home cloud token (device settings); afterwards power, mode, heat,
 * timer and battery all work over the encrypted channel.
 */
class MagicTouchJj02Coordinator : AbstractBLEDeviceCoordinator() {
    override fun getSupportedDeviceName(): Pattern = Pattern.compile("^xiaomi\\.magic_touch\\.jj02.*")

    override fun getDeviceNameResource(): Int = R.string.devicetype_magic_touch_jj02

    override fun getManufacturer(): String = "Xiaomi"

    override fun getBondingStyle(): Int {
        // No bonding: the vendor app never pairs (zero SMP packets in captures)
        // and the device rejects pairing attempts. All writes are commands
        // (write-without-response), like the vendor app.
        return BONDING_STYLE_NONE
    }

    override fun isExperimental(): Boolean = true

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind =
        DeviceCoordinator.DeviceKind.UNKNOWN

    override fun createDeviceSupport(device: GBDevice): DeviceSupport =
        MagicTouchJj02Support()

    override fun getCustomActions(): List<DeviceCardAction> = magicTouchCustomActions()

    override fun addBatteryPollingSettings(): Boolean {
        // The battery level only arrives during charge/unplug otherwise.
        return true
    }

    override fun getDeviceSpecificSettingsCustomizer(device: GBDevice): DeviceSpecificSettingsCustomizer {
        return MagicTouchSettingsCustomizer(device)
    }

    override fun requiresAuthKey(): Boolean {
        // 12-byte Mi Home cloud token, entered at discovery or later in settings.
        return true
    }

    override fun validateAuthKey(authKey: String): Boolean {
        // 12-byte Mi Home cloud token (optionally `0x`-prefixed hex).
        return parseCloudToken(authKey) != null
    }

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec = deviceSettings {
        screen(
            key = DeviceSpecificSettingsScreen.AUTHENTICATION.key,
            title = R.string.pref_header_authentication,
            icon = R.drawable.ic_vpn_key,
        ) {
            text(
                key = DeviceSettingsPreferenceConst.PREF_AUTH_KEY,
                title = R.string.magic_touch_jj02_bind_key_title,
                summary = R.string.magic_touch_jj02_bind_key_summary,
                icon = R.drawable.ic_vpn_key,
                connectedOnly = false,
            )
        }
        // The on-device MiOT customName property (siid 4 piid 11), not the
        // Bluetooth name: the massager advertises read-only as
        // `xiaomi.magic_touch.jj02`, so the standard deviceName() does not
        // apply here.
        text(
            key = MagicTouchJj02Support.PREF_CUSTOM_NAME,
            title = R.string.magic_touch_custom_name_title,
            summary = R.string.magic_touch_custom_name_summary,
            maxLength = 32,
        )
        list(
            key = MagicTouchJj02Support.PREF_DEFAULT_MODE,
            title = R.string.magic_touch_default_mode_title,
            entriesRes = R.array.magic_touch_jj02_mode_entries,
            entryValuesRes = R.array.magic_touch_jj02_mode_values,
            defaultValue = "1",
        )
        switchSetting(
            key = MagicTouchJj02Support.PREF_BEEP,
            title = R.string.magic_touch_beep_title,
            summary = R.string.magic_touch_beep_summary,
            defaultValue = true,
        )
        list(
            key = MagicTouchJj02Support.PREF_HEAD_INIT,
            title = R.string.magic_touch_head_init_title,
            entriesRes = R.array.magic_touch_jj02_head_init_entries,
            entryValuesRes = R.array.magic_touch_jj02_head_init_values,
            defaultValue = "1",
        )
    }

    override fun getDefaultIconResource(): Int = R.drawable.ic_device_scannable
}
