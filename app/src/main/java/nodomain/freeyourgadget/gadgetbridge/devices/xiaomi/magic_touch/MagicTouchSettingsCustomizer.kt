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

import androidx.preference.Preference
import kotlinx.parcelize.Parcelize
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsCustomizer
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsHandler
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.Prefs

/**
 * Syncs the massager state when the settings screen opens: the device only
 * pushes property changes and battery during charge/unplug, so the power /
 * mode / heat / timer preferences would otherwise show stale values.
 */
@Parcelize
class MagicTouchSettingsCustomizer(val device: GBDevice) : DeviceSpecificSettingsCustomizer {

    override fun onPreferenceChange(preference: Preference, handler: DeviceSpecificSettingsHandler) {
        // Nothing to do; preference writes go through onSendConfiguration
    }

    override fun getPreferenceKeysWithSummary(): Set<String> {
        return emptySet()
    }

    override fun customizeSettings(
        handler: DeviceSpecificSettingsHandler,
        genericDevicePrefs: Prefs,
        rootKey: String?,
    ) {
        if (rootKey == null) {
            GBApplication.deviceService(device).onReadConfiguration(null)
        }
    }
}
