/*  Copyright (C) 2019-2026 Andreas Shimokawa, Arjan Schrijver, Carsten
    Pfeiffer, Damien Gaignon, Daniel Dakhno, Hasan Ammar, José Rebelo, Morten
    Rieger Hannemose, Petr Vaněk

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
package nodomain.freeyourgadget.gadgetbridge.devices.qhybrid

import android.text.InputType
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.autoRemoveNotifications
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.rejectCallMethod
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.transliteration
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.vibrationStrength
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.screens.inactivity
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.screens.syncCalendar
import nodomain.freeyourgadget.gadgetbridge.util.Version

fun qhybridDeviceSettings(
    isHybridHR: Boolean,
    firmwareVersion: Version?
): DeviceSettingsSpec = deviceSettings {
    /**
     * Generic settings
     */
    screen(
        key = DeviceSpecificSettingsScreen.GENERIC.key,
        title = R.string.pref_header_generic,
        icon = R.drawable.ic_settings,
    ) {
        if (!isHybridHR) {
            list(
                key = "top_button_function",
                title = R.string.pref_title_qhybrid_top_button_function,
                icon = R.drawable.ic_edit,
                entriesRes = R.array.qhybrid_button_functions,
                entryValuesRes = R.array.qhybrid_button_functions_values,
            )
            list(
                key = "middle_button_function",
                title = R.string.pref_title_qhybrid_middle_button_function,
                icon = R.drawable.ic_edit,
                entriesRes = R.array.qhybrid_button_functions,
                entryValuesRes = R.array.qhybrid_button_functions_values,
            )
            list(
                key = "bottom_button_function",
                title = R.string.pref_title_qhybrid_bottom_button_function,
                icon = R.drawable.ic_edit,
                entriesRes = R.array.qhybrid_button_functions,
                entryValuesRes = R.array.qhybrid_button_functions_values,
            )
            switchSetting(
                key = "use_activity_hand_as_notification_counter",
                title = R.string.qhybrid_use_activity_hand_as_notification_counter,
                icon = R.drawable.ic_notifications,
                defaultValue = false,
            )
            info(
                key = "time_offset",
                title = R.string.qhybrid_time_shift,
                icon = R.drawable.ic_access_time,
                defaultValue = "00:00",
            )
            info(
                key = "second_tz_offset",
                title = R.string.qhybrid_second_timezone_offset_relative_to_utc,
                icon = R.drawable.ic_access_time,
                defaultValue = "00:00",
            )
            seekbar(
                key = "vibration_strength",
                title = R.string.pref_title_vibration_strength,
                icon = R.drawable.ic_vibration,
                defaultValue = 2,
                max = 3,
                showValue = true,
            )
        }
        if (isHybridHR) {
            if (firmwareVersion?.smallerThan(Version("3.0")) == true) {
                screen(
                    key = "button_configuration",
                    title = R.string.pref_title_physical_buttons,
                    summary = R.string.pref_summary_physical_buttons,
                    icon = R.drawable.ic_smart_button,
                ) {
                    list(
                        key = "button_1_function_short",
                        title = R.string.pref_title_upper_button_function_short,
                        entriesRes = R.array.pref_hybridhr_buttonfunctions,
                        entryValuesRes = R.array.pref_hybridhr_buttonfunctions_values,
                        defaultValue = "weatherApp",
                    )
                    list(
                        key = "button_1_function_long",
                        title = R.string.pref_title_upper_button_function_long,
                        entriesRes = R.array.pref_hybridhr_buttonfunctions,
                        entryValuesRes = R.array.pref_hybridhr_buttonfunctions_values,
                        defaultValue = "weatherApp",
                    )
                    list(
                        key = "button_2_function_short",
                        title = R.string.pref_title_middle_button_function_short,
                        entriesRes = R.array.pref_hybridhr_buttonfunctions,
                        entryValuesRes = R.array.pref_hybridhr_buttonfunctions_values,
                        defaultValue = "commuteApp",
                    )
                    info(
                        key = "button_2_function_long",
                        title = R.string.pref_title_middle_button_function_long,
                        summary = R.string.menuitem_menu,
                    )
                    list(
                        key = "button_3_function_short",
                        title = R.string.pref_title_lower_button_function_short,
                        entriesRes = R.array.pref_hybridhr_buttonfunctions,
                        entryValuesRes = R.array.pref_hybridhr_buttonfunctions_values,
                        defaultValue = "musicApp",
                    )
                    list(
                        key = "button_3_function_long",
                        title = R.string.pref_title_lower_button_function_long,
                        entriesRes = R.array.pref_hybridhr_buttonfunctions,
                        entryValuesRes = R.array.pref_hybridhr_buttonfunctions_values,
                        defaultValue = "musicApp",
                    )
                    info(
                        key = "button_1_function_long_warning",
                        summary = R.string.fossil_hr_button_config_info,
                    )
                }
                externalSettings(
                    key = "pref_key_qhybrid_commute_actions",
                    title = R.string.qhybrid_pref_title_actions,
                    summary = R.string.qhybrid_pref_summary_actions,
                    icon = R.drawable.ic_pending_actions,
                    activityClass = CommuteActionsActivity::class.java,
                )
            }
            if (firmwareVersion?.greaterOrEqualThan(Version("3.0")) == true) {
                screen(
                    key = "button_configuration",
                    title = R.string.pref_title_physical_buttons,
                    summary = R.string.pref_summary_physical_buttons,
                    icon = R.drawable.ic_smart_button,
                ) {
                    list(
                        key = "button_1_function_short",
                        title = R.string.pref_title_upper_button_function_short,
                        entriesRes = R.array.pref_hybridhr_buttonfunctions,
                        entryValuesRes = R.array.pref_hybridhr_buttonfunctions_values,
                        defaultValue = "weatherApp",
                    )
                    list(
                        key = "button_1_function_long",
                        title = R.string.pref_title_upper_button_function_long,
                        entriesRes = R.array.pref_hybridhr_buttonfunctions,
                        entryValuesRes = R.array.pref_hybridhr_buttonfunctions_values,
                        defaultValue = "weatherApp",
                    )
                    list(
                        key = "button_2_function_short",
                        title = R.string.pref_title_middle_button_function_short,
                        entriesRes = R.array.pref_hybridhr_buttonfunctions,
                        entryValuesRes = R.array.pref_hybridhr_buttonfunctions_values,
                        defaultValue = "launcherApp",
                    )
                    list(
                        key = "button_2_function_long",
                        title = R.string.pref_title_middle_button_function_long,
                        entriesRes = R.array.pref_hybridhr_buttonfunctions,
                        entryValuesRes = R.array.pref_hybridhr_buttonfunctions_values,
                        defaultValue = "launcherApp",
                    )
                    list(
                        key = "button_3_function_short",
                        title = R.string.pref_title_lower_button_function_short,
                        entriesRes = R.array.pref_hybridhr_buttonfunctions,
                        entryValuesRes = R.array.pref_hybridhr_buttonfunctions_values,
                        defaultValue = "musicApp",
                    )
                    list(
                        key = "button_3_function_long",
                        title = R.string.pref_title_lower_button_function_long,
                        entriesRes = R.array.pref_hybridhr_buttonfunctions,
                        entryValuesRes = R.array.pref_hybridhr_buttonfunctions_values,
                        defaultValue = "musicApp",
                    )
                    info(
                        key = "button_1_function_long_warning",
                        summary = R.string.fossil_hr_button_config_info,
                    )
                }
            }
        }
        externalSettings(
            key = "pref_key_qhybrid_calibration",
            title = R.string.qhybrid_title_calibration,
            summary = R.string.qhybrid_summary_calibration,
            icon = R.drawable.ic_angle_calibrate,
            activityClass = CalibrationActivity::class.java,
        )
        if (isHybridHR) {
            if (firmwareVersion?.smallerThan(Version("2.20")) == true) {
                externalSettings(
                    key = "qhybrid_watchface_old_fw",
                    title = R.string.qhybrid_title_watchface,
                    summary = R.string.qhybrid_watchface_configuration_old_firmware,
                    icon = R.drawable.ic_watch,
                    activityClass = HRConfigActivity::class.java,
                )
                switchSetting(
                    key = "force_white_color_scheme",
                    title = R.string.pref_title_force_white_color_scheme,
                    summary = R.string.pref_summary_force_white_color_scheme,
                    icon = R.drawable.ic_filter_b_and_w,
                    defaultValue = false,
                )
                switchSetting(
                    key = "widget_draw_circles",
                    title = R.string.pref_qhybrid_title_widget_draw_circles,
                    icon = R.drawable.ic_circle,
                    defaultValue = false,
                )
            }
            if (firmwareVersion?.greaterOrEqualThan(Version("3.0")) == true) {
                screen(
                    key = "navigation_app_config",
                    title = R.string.pref_title_fossil_hr_navigation_instructions,
                    summary = R.string.pref_summary_fossil_hr_navigation_instructions,
                    icon = R.drawable.baseline_merge_24,
                ) {
                    switchSetting(
                        key = "fossil_hr_nav_auto_foreground",
                        title = R.string.pref_title_fossil_hr_nav_foreground,
                        summary = R.string.pref_summary_fossil_hr_nav_foreground,
                        icon = R.drawable.ic_info,
                        defaultValue = true,
                    )
                    switchSetting(
                        key = "fossil_hr_nav_vibrate",
                        title = R.string.pref_title_fossil_hr_nav_vibrate,
                        summary = R.string.pref_summary_fossil_hr_nav_vibrate,
                        icon = R.drawable.ic_action_find_lost_device,
                        defaultValue = true,
                    )
                }
            }
            syncCalendar()
        }
    }

    /**
     * Health settings
     */
    if (isHybridHR) {
        screen(
            key = DeviceSpecificSettingsScreen.HEALTH.key,
            title = R.string.pref_header_health,
            icon = R.drawable.ic_health,
        ) {
            screen(
                key = "workout_detection_settings",
                title = R.string.pref_workout_detection_title,
                summary = R.string.pref_workout_detection_summary,
                icon = R.drawable.ic_activity_unknown_small,
            ) {
                category(
                    key = "pref_workout_detection_running",
                    title = R.string.activity_type_running,
                ) {
                    switchSetting(
                        key = "activity_recognize_running_enabled",
                        title = R.string.pref_workout_detection_enabled,
                        summary = R.string.pref_workout_detection_summary,
                        icon = R.drawable.ic_activity_running,
                        defaultValue = false,
                    )
                    switchSetting(
                        key = "activity_recognize_running_ask_first",
                        title = R.string.pref_workout_detection_ask_first,
                        summary = R.string.pref_workout_detection_ask_first_summary,
                        icon = R.drawable.ic_warning_gray,
                        defaultValue = false,
                        dependency = "activity_recognize_running_enabled",
                    )
                    text(
                        key = "activity_recognize_running_minutes",
                        title = R.string.pref_workout_detection_time,
                        summary = R.string.pref_workout_detection_time_summary,
                        icon = R.drawable.ic_timer,
                        defaultValue = "3",
                        inputType = InputType.TYPE_CLASS_NUMBER,
                        dependency = "activity_recognize_running_enabled",
                    )
                }
                category(
                    key = "pref_workout_detection_biking",
                    title = R.string.activity_type_biking,
                ) {
                    switchSetting(
                        key = "activity_recognize_biking_enabled",
                        title = R.string.pref_workout_detection_enabled,
                        summary = R.string.pref_workout_detection_summary,
                        icon = R.drawable.ic_activity_biking,
                        defaultValue = false,
                    )
                    switchSetting(
                        key = "activity_recognize_biking_ask_first",
                        title = R.string.pref_workout_detection_ask_first,
                        summary = R.string.pref_workout_detection_ask_first_summary,
                        icon = R.drawable.ic_warning_gray,
                        defaultValue = false,
                        dependency = "activity_recognize_biking_enabled",
                    )
                    text(
                        key = "activity_recognize_biking_minutes",
                        title = R.string.pref_workout_detection_time,
                        summary = R.string.pref_workout_detection_time_summary,
                        icon = R.drawable.ic_timer,
                        defaultValue = "5",
                        inputType = InputType.TYPE_CLASS_NUMBER,
                        dependency = "activity_recognize_biking_enabled",
                    )
                }
                category(
                    key = "pref_workout_detection_walking",
                    title = R.string.activity_type_walking,
                ) {
                    switchSetting(
                        key = "activity_recognize_walking_enabled",
                        title = R.string.pref_workout_detection_enabled,
                        summary = R.string.pref_workout_detection_summary,
                        icon = R.drawable.ic_activity_walking,
                        defaultValue = false,
                    )
                    switchSetting(
                        key = "activity_recognize_walking_ask_first",
                        title = R.string.pref_workout_detection_ask_first,
                        summary = R.string.pref_workout_detection_ask_first_summary,
                        icon = R.drawable.ic_warning_gray,
                        defaultValue = false,
                        dependency = "activity_recognize_walking_enabled",
                    )
                    text(
                        key = "activity_recognize_walking_minutes",
                        title = R.string.pref_workout_detection_time,
                        summary = R.string.pref_workout_detection_time_summary,
                        icon = R.drawable.ic_timer,
                        defaultValue = "10",
                        inputType = InputType.TYPE_CLASS_NUMBER,
                        dependency = "activity_recognize_walking_enabled",
                    )
                }
                category(
                    key = "pref_workout_detection_rowing",
                    title = R.string.activity_type_rowing,
                ) {
                    switchSetting(
                        key = "activity_recognize_rowing_enabled",
                        title = R.string.pref_workout_detection_enabled,
                        summary = R.string.pref_workout_detection_summary,
                        icon = R.drawable.ic_activity_rowing,
                        defaultValue = false,
                    )
                    switchSetting(
                        key = "activity_recognize_rowing_ask_first",
                        title = R.string.pref_workout_detection_ask_first,
                        summary = R.string.pref_workout_detection_ask_first_summary,
                        icon = R.drawable.ic_warning_gray,
                        defaultValue = false,
                        dependency = "activity_recognize_rowing_enabled",
                    )
                    text(
                        key = "activity_recognize_rowing_minutes",
                        title = R.string.pref_workout_detection_time,
                        summary = R.string.pref_workout_detection_time_summary,
                        icon = R.drawable.ic_timer,
                        defaultValue = "3",
                        inputType = InputType.TYPE_CLASS_NUMBER,
                        dependency = "activity_recognize_rowing_enabled",
                    )
                }
            }
            inactivity()
        }

        /**
         * Notifications settings
         */
        screen(
            key = DeviceSpecificSettingsScreen.NOTIFICATIONS.key,
            title = R.string.pref_header_notifications,
            icon = R.drawable.ic_notifications,
            xmlSubScreens = listOf(R.xml.devicesettings_canned_dismisscall_16),
        ) {
            vibrationStrength(2, 3)
            autoRemoveNotifications()
            rejectCallMethod()
            transliteration()
            switchSetting(
                key = "use_custom_deviceicon",
                title = R.string.pref_title_custom_deviceicon,
                summary = R.string.pref_summary_custom_deviceicon,
                icon = R.drawable.ic_devices_other,
                defaultValue = true,
            )
        }
    } else {
        externalSettings(
            key = "notifications",
            title = R.string.pref_header_notifications,
            icon = R.drawable.ic_notifications,
            activityClass = QHybridNotificationsConfigActivity::class.java,
        )
    }

    /**
     * Developer settings
     */
    screen(
        key = DeviceSpecificSettingsScreen.DEVELOPER.key,
        title = R.string.pref_header_development,
        icon = R.drawable.ic_developer_mode,
    ) {
        switchSetting(
            key = "save_raw_activity_files",
            title = R.string.pref_qhybrid_save_raw_activity_files,
            icon = R.drawable.ic_date_range,
            defaultValue = false,
        )
        if (isHybridHR) {
            switchSetting(
                key = "dangerous_external_intents",
                title = R.string.qhybrid_pref_title_external_intents,
                summary = R.string.qhybrid_pref_summary_external_intents,
                icon = R.drawable.ic_warning_gray,
                defaultValue = false,
            )
            externalSettings(
                key = "qhybrid_file_management",
                title = R.string.qhybrid_title_file_management,
                summary = R.string.qhybrid_summary_file_management,
                icon = R.drawable.ic_file_upload,
                activityClass = FileManagementActivity::class.java,
            )
            switchSetting(
                key = "enable_on_device_confirmation",
                title = R.string.qhybrid_title_on_device_confirmation,
                summary = R.string.qhybrid_summary_on_device_confirmation,
                icon = R.drawable.ic_vpn_key,
                defaultValue = true,
            )
            text(
                key = "voice_service_package",
                title = R.string.voice_service_package_title,
                summary = R.string.voice_service_package_summary,
                icon = R.drawable.ic_voice,
            )
            text(
                key = "voice_service_class",
                title = R.string.voice_service_class_title,
                summary = R.string.voice_service_class_summary,
                icon = R.drawable.ic_voice,
            )
            text(
                key = "calendar_sync_events_amount",
                title = R.string.pref_calendar_amount_events_to_sync_title,
                icon = R.drawable.ic_calendar_sync,
                defaultValue = "5",
                inputType = InputType.TYPE_CLASS_NUMBER,
            )
            text(
                key = "calendar_sync_event_title_length",
                title = R.string.pref_calendar_maximum_title_characters_title,
                icon = R.drawable.ic_calendar_sync,
                defaultValue = "40",
                inputType = InputType.TYPE_CLASS_NUMBER,
            )
            text(
                key = "calendar_sync_event_desc_length",
                title = R.string.pref_calendar_maximum_description_characters_title,
                icon = R.drawable.ic_calendar_sync,
                defaultValue = "40",
                inputType = InputType.TYPE_CLASS_NUMBER,
            )
            text(
                key = "calendar_sync_target_app",
                title = R.string.pref_calendar_target_app_title,
                icon = R.drawable.ic_calendar_sync,
                defaultValue = "calendarApp",
            )
        }
    }
}
