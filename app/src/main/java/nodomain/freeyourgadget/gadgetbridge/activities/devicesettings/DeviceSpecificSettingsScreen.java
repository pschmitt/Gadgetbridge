/*  Copyright (C) 2024 José Rebelo

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
package nodomain.freeyourgadget.gadgetbridge.activities.devicesettings;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.XmlRes;

import nodomain.freeyourgadget.gadgetbridge.R;

public enum DeviceSpecificSettingsScreen {
    ADVANCED_SETTINGS("pref_screen_advanced_settings", R.xml.devicesettings_root_advanced_settings, R.string.advanced_settings, R.drawable.ic_warning),
    ACTIVITY_INFO("pref_screen_activity_info", R.xml.devicesettings_root_activity_info, R.string.activity_info, R.drawable.ic_activity_unknown_small),
    AUDIO("pref_screen_audio", R.xml.devicesettings_root_audio, R.string.pref_header_audio, R.drawable.ic_music_note),
    AUTHENTICATION("pref_screen_authentication", R.xml.devicesettings_root_authentication, R.string.pref_header_authentication, R.drawable.ic_vpn_key),
    BATTERY("pref_screen_battery", R.xml.devicesettings_root_battery, R.string.battery, R.drawable.ic_battery),
    CALENDAR("pref_screen_calendar", R.xml.devicesettings_root_calendar, R.string.pref_header_calendar, R.drawable.ic_calendar_today),
    CALLS_AND_NOTIFICATIONS("pref_screen_calls_and_notifications", R.xml.devicesettings_root_calls_and_notifications, R.string.pref_header_calls_and_notifications, R.drawable.ic_notifications),
    CONNECTION("pref_screen_connection", R.xml.devicesettings_root_connection, R.string.pref_header_connection, R.drawable.ic_mtu),
    INTERNET("pref_screen_internet", R.xml.devicesettings_root_internet, R.string.permission_internet_access_title, R.drawable.ic_language),
    DASHBOARD("pref_screen_dashboard", R.xml.devicesettings_root_dashboard, R.string.pref_header_dashboard, R.drawable.ic_dashboard),
    DEVICE_CARD("pref_screen_device_card", R.xml.devicesettings_root_device_card, R.string.pref_header_device_card, R.drawable.ic_devices_other),
    DEVELOPER("pref_screen_developer", R.xml.devicesettings_root_developer, R.string.pref_title_developer_settings, R.drawable.ic_developer_mode),
    DISPLAY("pref_screen_display", R.xml.devicesettings_root_display, R.string.pref_header_display, R.drawable.ic_device_display),
    GENERIC("pref_screen_generic", R.xml.devicesettings_root_generic, R.string.pref_header_generic, R.drawable.ic_settings),
    LOCATION("pref_screen_location", R.xml.devicesettings_root_location, R.string.pref_header_location, R.drawable.ic_gps_location),
    NOTIFICATIONS("pref_screen_notifications", R.xml.devicesettings_root_notifications, R.string.pref_header_notifications, R.drawable.ic_notifications),
    DATE_TIME("pref_screen_date_time", R.xml.devicesettings_root_date_time, R.string.dateformat_date_time, R.drawable.ic_timer),
    WORKOUT("pref_screen_workout", R.xml.devicesettings_root_workout, R.string.menuitem_workout, R.drawable.ic_activity_unknown_small),
    HEALTH("pref_screen_health", R.xml.devicesettings_root_health, R.string.pref_header_health, R.drawable.ic_health),
    TOUCH_OPTIONS("pref_screen_touch_options", R.xml.devicesettings_root_touch_options, R.string.prefs_galaxy_touch_options, R.drawable.ic_touch),
    SOUND("pref_screen_sound", R.xml.devicesettings_root_sound, R.string.pref_header_sound, R.drawable.ic_volume_up),
    EXPERIMENTAL("pref_screen_experimental", R.xml.devicesettings_root_experimental, R.string.pref_experimental_settings_title, R.drawable.ic_warning_gray),
    ;

    private final String key;
    @XmlRes
    private final int xml;
    @StringRes
    private final int title;
    @DrawableRes
    private final int icon;

    DeviceSpecificSettingsScreen(final String key, final int xml, @StringRes final int title, @DrawableRes final int icon) {
        this.key = key;
        this.xml = xml;
        this.title = title;
        this.icon = icon;
    }

    @NonNull
    public String getKey() {
        return key;
    }

    public int getXml() {
        return xml;
    }

    @StringRes
    public int getTitle() {
        return title;
    }

    @DrawableRes
    public int getIcon() {
        return icon;
    }

    public static DeviceSpecificSettingsScreen fromXml(final int xml) {
        for (final DeviceSpecificSettingsScreen screen : DeviceSpecificSettingsScreen.values()) {
            if (screen.xml == xml) {
                return screen;
            }
        }

        return null;
    }

    @Nullable
    public static DeviceSpecificSettingsScreen fromKey(@NonNull final String key) {
        for (final DeviceSpecificSettingsScreen screen : DeviceSpecificSettingsScreen.values()) {
            if (screen.key.equals(key)) {
                return screen;
            }
        }

        return null;
    }
}
