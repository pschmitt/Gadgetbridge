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
import nodomain.freeyourgadget.gadgetbridge.activities.CalendarSelectionActivity
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsScope


/**
 * Adds several settings for calendar synchronisation.
 * This is a direct translation of devicesettings_sync_calendar.xml
 */
fun DeviceSettingsScope.syncCalendar() {
    switchSetting(
        key = "sync_calendar",
        title = R.string.pref_title_sync_caldendar,
        summary = R.string.pref_summary_sync_calendar,
        icon = R.drawable.ic_calendar_sync,
        defaultValue = false,
    )
    externalSettings(
        key = "calendar_blacklist",
        title = R.string.pref_title_calendar_sync_choose,
        summary = R.string.pref_summary_calendar_sync_choose,
        icon = R.drawable.ic_calendar_month,
        dependency = "sync_calendar",
        activityClass = CalendarSelectionActivity::class.java,
    )
    text(
        key = "calendar_lookahead_days",
        title = R.string.pref_title_calendar_lookahead,
        icon = R.drawable.ic_calendar_to,
        defaultValue = "7",
        inputType = InputType.TYPE_CLASS_NUMBER,
        maxLength = 3,
        dependency = "sync_calendar",
    )
    switchSetting(
        key = "sync_birthdays",
        title = R.string.pref_title_sync_birthdays,
        summary = R.string.pref_summary_sync_birthdays,
        icon = R.drawable.ic_person,
        defaultValue = false,
        dependency = "sync_calendar",
    )
    switchSetting(
        key = "calendar_sync_canceled",
        title = R.string.pref_title_calendar_sync_canceled,
        summary = R.string.pref_summary_calendar_sync_canceled,
        icon = R.drawable.ic_calendar_cancel,
        defaultValue = true,
        dependency = "sync_calendar",
    )
    switchSetting(
        key = "calendar_sync_declined",
        title = R.string.pref_title_calendar_sync_declined,
        summary = R.string.pref_summary_calendar_sync_declined,
        icon = R.drawable.ic_calendar_cancel,
        defaultValue = true,
        dependency = "sync_calendar",
    )
    switchSetting(
        key = "calendar_sync_all_day",
        title = R.string.pref_title_calendar_sync_all_day,
        summary = R.string.pref_summary_calendar_sync_all_day,
        icon = R.drawable.ic_calendar_from,
        defaultValue = true,
        dependency = "sync_calendar",
    )
    switchSetting(
        key = "calendar_sync_focus_time",
        title = R.string.pref_title_calendar_sync_focus_time,
        summary = R.string.pref_summary_calendar_sync_focus_time,
        icon = R.drawable.ic_focus,
        defaultValue = true,
        dependency = "sync_calendar",
    )
    switchSetting(
        key = "calendar_sync_working_location",
        title = R.string.pref_title_calendar_sync_working_location,
        summary = R.string.pref_summary_calendar_sync_working_location,
        icon = R.drawable.ic_gps_location,
        defaultValue = true,
        dependency = "sync_calendar",
    )
}
