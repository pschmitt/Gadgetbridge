/*  Copyright (C) 2020-2024 Andreas Shimokawa, Damien Gaignon, Daniel Dakhno,
    José Rebelo, Petr Vaněk, Taavi Eomäe

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
package nodomain.freeyourgadget.gadgetbridge.devices.itag

import android.bluetooth.le.ScanFilter
import java.util.regex.Pattern
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ListEntry
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLEDeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.itag.ITagSupport

class ITagCoordinator : AbstractBLEDeviceCoordinator() {

    override fun getSupportedDeviceName(): Pattern =
        Pattern.compile("itag.*", Pattern.CASE_INSENSITIVE)

    // Some iTag devices do not support bonding but some do
    override fun getBondingStyle(): Int = BONDING_STYLE_ASK

    override fun createBLEScanFilters(): Collection<ScanFilter> =
        listOf(ScanFilter.Builder()
            .setDeviceName("iTag")
            .setDeviceName("iTAG")
            .setDeviceName("ITAG")
            .setDeviceName("ITag")
            .setDeviceName("Itag")
            .setDeviceName("itag")
            .build())

    override fun getManufacturer(): String = "Unknown" // TODO: Show chip manufacturer?

    override fun supportsRealtimeData(device: GBDevice): Boolean = false // TODO: RSSI

    override fun supportsFindDevice(device: GBDevice): Boolean = true

    override fun getDeviceSupportClass(device: GBDevice): Class<out DeviceSupport> =
        ITagSupport::class.java

    override fun getDeviceNameResource(): Int = R.string.devicetype_itag

    override fun getDefaultIconResource(): Int = R.drawable.ic_device_itag

    override fun getDeviceKind(device: GBDevice): DeviceCoordinator.DeviceKind =
        DeviceCoordinator.DeviceKind.UNKNOWN

    override fun getDeviceSettings(device: GBDevice): DeviceSettingsSpec = deviceSettings {
        list(
            key = ITagConstants.PREF_ALERT_LEVEL,
            title = R.string.pref_itag_alert_level,
            icon = R.drawable.ic_watch_vibration,
            entries = listOf(
                ListEntry.Res("high", R.string.pref_itag_alert_level_high),
                ListEntry.Res("mild", R.string.pref_itag_alert_level_mild)
            ),
            defaultValue = "high",
            connectedOnly = false
        )
        list(
            key = ITagConstants.PREF_BUTTON_EVENT,
            title = R.string.pref_itag_button_press,
            icon = R.drawable.ic_touch,
            entries = listOf(
                ListEntry.Res("findphone", R.string.menuitem_findphone),
                ListEntry.Res("takephoto", R.string.menuitem_takephoto)
            ),
            defaultValue = "findphone",
            connectedOnly = false
        )
        screen(
            key = DeviceSpecificSettingsScreen.CONNECTION.key,
            title = R.string.pref_header_connection,
            icon = R.drawable.ic_mtu,
        ) {
            switchSetting(
                key = ITagConstants.PREF_BEEP_ON_DISCONNECT,
                title = R.string.pref_itag_beep_on_disconnect,
                icon = R.drawable.ic_watch_vibration,
                defaultValue = false,
                connectedOnly = false
            )
        }
    }
}
