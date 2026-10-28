/*  Copyright (C) 2026 José Rebelo, Arjan Schrijver

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
package nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.screens

import android.text.InputType
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsScope


/**
 * Adds a [ScreenSetting] containing several settings for inactivity warnings.
 * This is a direct translation of devicesettings_inactivity.xml
 */
fun DeviceSettingsScope.inactivity() {
    screen(
        key = "screen_inactivity",
        title = R.string.mi2_prefs_inactivity_warnings,
        summary = R.string.mi2_prefs_inactivity_warnings_summary,
        icon = R.drawable.ic_chair,
    ) {
        switchSetting(
            key = "inactivity_warnings_enable",
            title = R.string.mi2_prefs_inactivity_warnings,
            summary = R.string.mi2_prefs_inactivity_warnings_summary,
            defaultValue = false,
        )
        text(
            key = "inactivity_warnings_threshold",
            title = R.string.mi2_prefs_inactivity_warnings_threshold,
            summary = R.string.mi2_prefs_inactivity_warnings_summary,
            defaultValue = "60",
            inputType = InputType.TYPE_CLASS_NUMBER,
            dependency = "inactivity_warnings_enable",
        )
        time(
            key = "inactivity_warnings_start",
            title = R.string.mi2_prefs_do_not_disturb_start,
            defaultValue = "06:00",
            dependency = "inactivity_warnings_enable",
        )
        time(
            key = "inactivity_warnings_end",
            title = R.string.mi2_prefs_do_not_disturb_end,
            defaultValue = "23:00",
            dependency = "inactivity_warnings_enable",
        )
    }
}
