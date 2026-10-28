/*  Copyright (C) 2026 Vladimir Tasic

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
package nodomain.freeyourgadget.gadgetbridge.devices.zeblaze.stratos4pro

import android.text.InputType
import android.widget.EditText
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.ConfigureContacts
import nodomain.freeyourgadget.gadgetbridge.activities.ConfigureWorldClocks
import nodomain.freeyourgadget.gadgetbridge.activities.app_specific_notifications.AppSpecificNotificationSettingsActivity
import nodomain.freeyourgadget.gadgetbridge.activities.audiorecordings.AudioRecordingsActivity
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsScreen
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsScope
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.Language
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ListEntry
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.PasswordMode
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.passwordScreen
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.sendAppNotifications
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components.volume
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro.MORNING_ITEMS
import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro.Stratos4ProCalls
import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro.Stratos4ProLanguage
import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro.Stratos4ProLights
import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro.Stratos4ProReplyIcon
import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro.Stratos4ProSettings
import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro.Stratos4ProSupport
import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro.Stratos4ProWorkoutParser
import nodomain.freeyourgadget.gadgetbridge.util.preferences.MinMaxTextWatcher

/** Stress and SpO2 auto-measure intervals the firmware accepts, in minutes. */
private val MEASUREMENT_INTERVALS = listOf(
    5 to R.string.interval_five_minutes,
    10 to R.string.interval_ten_minutes,
    30 to R.string.interval_thirty_minutes,
    60 to R.string.interval_1_hour,
)

/** Labels for [MORNING_ITEMS], same order. */
private val MORNING_ITEM_LABELS = listOf(
    R.string.battery,
    R.string.menuitem_sleep,
    R.string.steps,
    R.string.yesterdays_activity,
    R.string.menuitem_cycles,
    R.string.menuitem_events,
)

internal fun stratos4ProDeviceSettings(
    device: GBDevice,
    coordinator: Stratos4ProCoordinator,
): DeviceSettingsSpec = deviceSettings {
    DeviceSpecificSettingsScreen.CALLS_AND_NOTIFICATIONS.screen(
        // `c0 ab 0a`: SMS quick replies for a rejected call. `b2 ab 0a`: quick replies for
        // notifications. `ea`: off / always / scheduled + start/end.
        xmlSubScreens = listOf(
            R.xml.devicesettings_canned_dismisscall_16,
            R.xml.devicesettings_canned_reply_16,
            R.xml.devicesettings_ak102_dnd,
        ),
    ) {
        sendAppNotifications()
        externalSettings(
            key = DeviceSettingsPreferenceConst.PREFS_PER_APP_NOTIFICATION_SETTINGS,
            title = R.string.prefs_wena3_notification_per_app_settings_title,
            icon = R.drawable.ic_widgets,
            activityClass = AppSpecificNotificationSettingsActivity::class.java,
        )
        externalSettings(
            key = DeviceSettingsPreferenceConst.PREF_CONTACTS,
            title = R.string.pref_contacts_title,
            summary = R.string.pref_contacts_summary,
            icon = R.drawable.ic_person,
            activityClass = ConfigureContacts::class.java,
        )
        switchSetting(
            key = Stratos4ProSupport.PREF_REPLY_ALL_APPS,
            title = R.string.stratos4pro_pref_reply_all_apps_title,
            summary = R.string.stratos4pro_pref_reply_all_apps_summary,
            icon = R.drawable.ic_reply,
        )
        if (coordinator.supportsLights(device)) {
            switchSetting(
                key = Stratos4ProReplyIcon.PREF_REPLY_ICON_APP,
                title = R.string.stratos4pro_pref_reply_icon_app_title,
                summary = R.string.stratos4pro_pref_reply_icon_app_summary,
                icon = R.drawable.ic_notifications,
                dependency = Stratos4ProSupport.PREF_REPLY_ALL_APPS,
            )
        }
    }

    // `e1`: HR interval (0 = off), HR alerts, auto stress; `c4`: continuous SpO2, SpO2 alert,
    // scientific sleep, sedentary reminder.
    DeviceSpecificSettingsScreen.HEALTH.screen(
        xmlSubScreens = listOf(R.xml.devicesettings_inactivity_sheduled),
    ) {
        list(
            key = DeviceSettingsPreferenceConst.PREF_HEARTRATE_MEASUREMENT_INTERVAL,
            title = R.string.prefs_title_heartrate_measurement_interval,
            icon = R.drawable.ic_heartrate,
            entries = coordinator.getHeartRateMeasurementIntervals().map { ListEntry.Res(it.intervalSeconds.toString(), it.label) },
            defaultValue = "0",
        )
        list(
            key = DeviceSettingsPreferenceConst.PREF_HEARTRATE_ALERT_HIGH_THRESHOLD,
            title = R.string.prefs_heartrate_alert_high_threshold,
            icon = R.drawable.ic_heartrate,
            entriesRes = R.array.prefs_miband_heartrate_high_alert_threshold_with_off,
            entryValuesRes = R.array.prefs_miband_heartrate_high_alert_threshold_with_off_values,
            defaultValue = "0",
        )
        list(
            key = DeviceSettingsPreferenceConst.PREF_HEARTRATE_ALERT_LOW_THRESHOLD,
            title = R.string.prefs_heartrate_alert_low_threshold,
            icon = R.drawable.ic_heartrate,
            entriesRes = R.array.prefs_miband_heartrate_low_alert_threshold,
            entryValuesRes = R.array.prefs_miband_heartrate_low_alert_threshold_values,
            defaultValue = "0",
        )
        switchSetting(
            key = DeviceSettingsPreferenceConst.PREF_HEARTRATE_STRESS_MONITORING,
            title = R.string.prefs_stress_monitoring_title,
            summary = R.string.prefs_stress_monitoring_description,
            icon = R.drawable.ic_mood_bad,
        )
        list(
            key = Stratos4ProSettings.PREF_STRESS_INTERVAL,
            title = R.string.pref_title_time_interval,
            icon = R.drawable.ic_mood_bad,
            entries = MEASUREMENT_INTERVALS.map { (minutes, label) -> ListEntry.Res(minutes.toString(), label) },
            defaultValue = "10",
            dependency = DeviceSettingsPreferenceConst.PREF_HEARTRATE_STRESS_MONITORING,
        )
        switchSetting(
            key = DeviceSettingsPreferenceConst.PREF_SPO2_ALL_DAY_MONITORING,
            title = R.string.prefs_spo2_monitoring_title,
            summary = R.string.prefs_spo2_monitoring_description,
            icon = R.drawable.ic_spo2,
        )
        list(
            key = DeviceSettingsPreferenceConst.PREF_SPO2_MEASUREMENT_INTERVAL,
            title = R.string.pref_title_time_interval,
            icon = R.drawable.ic_spo2,
            entries = MEASUREMENT_INTERVALS.map { (minutes, label) -> ListEntry.Res((minutes * 60).toString(), label) },
            defaultValue = "3600",
            dependency = DeviceSettingsPreferenceConst.PREF_SPO2_ALL_DAY_MONITORING,
        )
        // The watch accepts 80/85/90 (and 75).
        list(
            key = DeviceSettingsPreferenceConst.PREF_SPO2_LOW_ALERT_THRESHOLD,
            title = R.string.prefs_spo2_alert_threshold,
            icon = R.drawable.ic_spo2,
            entriesRes = R.array.prefs_spo2_alert_threshold,
            entryValuesRes = R.array.prefs_spo2_alert_threshold_values,
            defaultValue = "0",
        )
        // The sleep stages Gadgetbridge shows are recorded by this.
        switchSetting(
            key = Stratos4ProSettings.PREF_SCI_SLEEP,
            title = R.string.stratos4pro_pref_scientific_sleep_title,
            summary = R.string.stratos4pro_pref_scientific_sleep_summary,
            icon = R.drawable.ic_activity_sleep,
            defaultValue = true,
        )
    }

    // `c9`: automatic workout detection. `ec`: sport menu, once the watch has listed its sports.
    DeviceSpecificSettingsScreen.WORKOUT.screen {
        switchSetting(
            key = Stratos4ProSettings.PREF_WORKOUT_DETECTION,
            title = R.string.pref_workout_detection_title,
            summary = R.string.pref_workout_detection_summary,
            icon = R.drawable.ic_activity_running,
        )
        val ids = GBApplication.getDevicePrefs(device).getString(Stratos4ProSettings.PREF_SPORT_IDS, "")
            .split(",").mapNotNull { it.toIntOrNull() }
        if (ids.isNotEmpty()) {
            // Several ids share one ActivityKind; those labels get the id appended.
            val labels = ids.map { Stratos4ProWorkoutParser.sportTypeToActivityKind(it).getLabel(GBApplication.getContext()) }
            sortableList(
                key = Stratos4ProSettings.PREF_SPORT_MENU,
                title = R.string.mi5_prefs_workout_activity_types,
                icon = R.drawable.ic_list_numbered,
                entries = ids.zip(labels) { id, label ->
                    ListEntry.Text(id.toString(), if (labels.count { it == label } > 1) "$label ($id)" else label)
                },
            )
        }
    }

    // `a5`: raise to wake, screen timeout, language, password, brightness.
    DeviceSpecificSettingsScreen.DISPLAY.screen(
        xmlSubScreens = listOf(R.xml.devicesettings_screen_brightness),
    ) {
        switchSetting(
            key = DeviceSettingsPreferenceConst.PREF_LIFTWRIST_NOSHED,
            title = R.string.mi2_prefs_activate_display_on_lift,
            icon = R.drawable.ic_arrow_upward,
        )
        // Exactly the options from `a5 aa 0b`.
        list(
            key = DeviceSettingsPreferenceConst.PREF_SCREEN_TIMEOUT,
            title = R.string.prefs_screen_timeout,
            icon = R.drawable.ic_hourglass_empty,
            entriesProvider = { prefs ->
                prefs.getString(Stratos4ProSettings.PREF_SCREEN_TIMEOUT_OPTIONS, "5,10,15,30,60")
                    .split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .map { ListEntry.Text(it.toString(), GBApplication.getContext().getString(R.string.seconds_value_unit, it)) }
            },
            defaultValue = "5",
        )
        // "auto" plus the known ids the watch listed in `a5 aa 03` (all known ones until it has).
        list(
            key = DeviceSettingsPreferenceConst.PREF_LANGUAGE,
            title = R.string.pref_title_language,
            icon = R.drawable.ic_language,
            entriesProvider = { prefs ->
                val ids = prefs.getString(Stratos4ProSettings.PREF_LANGUAGES, "")
                    .split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                Language.entries
                    .filter { lang ->
                        val id = Stratos4ProLanguage.idForPref(lang.code)
                        lang == Language.AUTO || id != null && (ids.isEmpty() || id in ids)
                    }
                    .map { ListEntry.Res(it.code, it.label) }
            },
            defaultValue = DeviceSettingsPreferenceConst.PREF_LANGUAGE_AUTO,
        )
        // Phone-timed brightness ends, see Stratos4ProSettings.checkBrightnessSchedule.
        switchSetting(
            key = Stratos4ProSettings.PREF_BRIGHTNESS_SCHEDULE,
            title = R.string.stratos4pro_pref_brightness_schedule_title,
            summary = R.string.stratos4pro_pref_brightness_schedule_summary,
            icon = R.drawable.ic_brightness_auto,
        )
        time(
            key = Stratos4ProSettings.PREF_BRIGHTNESS_MAX_AT,
            title = R.string.stratos4pro_pref_brightness_max_at,
            icon = R.drawable.ic_wb_sunny,
            defaultValue = "09:00",
            dependency = Stratos4ProSettings.PREF_BRIGHTNESS_SCHEDULE,
        )
        time(
            key = Stratos4ProSettings.PREF_BRIGHTNESS_MIN_AT,
            title = R.string.stratos4pro_pref_brightness_min_at,
            icon = R.drawable.ic_nights_stay,
            defaultValue = "20:00",
            dependency = Stratos4ProSettings.PREF_BRIGHTNESS_SCHEDULE,
        )
        passwordScreen(PasswordMode.NUMBERS_6)
    }

    // `a3 08` / `a3 04`: 12/24 h clock and day-month order.
    DeviceSpecificSettingsScreen.DATE_TIME.screen {
        list(
            key = DeviceSettingsPreferenceConst.PREF_TIMEFORMAT,
            title = R.string.pref_title_timeformat,
            icon = R.drawable.ic_access_time,
            entriesRes = R.array.pref_timeformat_entries,
            entryValuesRes = R.array.pref_timeformat_values,
            defaultValue = DeviceSettingsPreferenceConst.PREF_TIMEFORMAT_AUTO,
        )
        list(
            key = DeviceSettingsPreferenceConst.PREF_DATEFORMAT,
            title = R.string.miband2_prefs_dateformat,
            icon = R.drawable.ic_access_time,
            entriesRes = R.array.dateformat_day_month_order_entries,
            entryValuesRes = R.array.dateformat_day_month_order_values,
            defaultValue = DeviceSettingsPreferenceConst.PREF_DATEFORMAT_AUTO,
        )
    }

    // `a5 ab 0e`: speaker volume 0..15 (media audio and mute have no GB pref).
    DeviceSpecificSettingsScreen.SOUND.screen {
        volume(max = 15, defaultValue = 11)
    }

    // `ed` world clocks, `a5 ab 0b` camera, `a5 ab 05` disconnect reminder, `c2` 0a wear hand,
    // `ca` morning report.
    DeviceSpecificSettingsScreen.GENERIC.screen {
        externalSettings(
            key = DeviceSettingsPreferenceConst.PREF_WORLD_CLOCKS,
            title = R.string.pref_world_clocks_title,
            summary = R.string.pref_world_clocks_summary,
            icon = R.drawable.ic_access_time,
            activityClass = ConfigureWorldClocks::class.java,
        )
        switchSetting(
            key = DeviceSettingsPreferenceConst.PREF_CAMERA_REMOTE,
            title = R.string.pref_camera_remote_title,
            summary = R.string.pref_camera_remote_summary,
            icon = R.drawable.ic_camera_remote,
        )
        switchSetting(
            key = DeviceSettingsPreferenceConst.PREF_DISCONNECTNOTIF_NOSHED,
            title = R.string.prefs_disconnect_notification,
            summary = R.string.prefs_disconnect_notification_summary,
            icon = R.drawable.ic_signal_cellular_connected_no_internet_4_bar,
        )
        list(
            key = DeviceSettingsPreferenceConst.PREF_WEARLOCATION,
            title = R.string.prefs_wearside,
            icon = R.drawable.ic_switch_left,
            entriesRes = R.array.wearside,
            entryValuesRes = R.array.wearside_values,
            defaultValue = "left",
        )
        switchSetting(
            key = DeviceSettingsPreferenceConst.MORNING_UPDATES_ENABLED,
            title = R.string.pref_morning_updates_title,
            summary = R.string.pref_morning_updates_summary,
            icon = R.drawable.ic_wb_sunny,
        )
        sortableList(
            key = DeviceSettingsPreferenceConst.MORNING_UPDATES_CATEGORIES_SORTABLE,
            title = R.string.pref_morning_updates_categories_title,
            icon = R.drawable.ic_list_numbered,
            entries = MORNING_ITEMS.zip(MORNING_ITEM_LABELS) { value, label -> ListEntry.Res(value, label) },
            defaultValue = MORNING_ITEMS,
            dependency = DeviceSettingsPreferenceConst.MORNING_UPDATES_ENABLED,
        )
    }

    externalSettings(
        key = "pref_key_audio_recordings",
        title = R.string.audio_recordings,
        icon = R.drawable.ic_microphone,
        activityClass = AudioRecordingsActivity::class.java,
    )
    callAudio(device)
    if (coordinator.supportsLights(device)) {
        lights()
    }
}

/** Dual-mode chip with BT calling (CoD 0x240704): classic bond phone-side, calls over HFP. */
private fun DeviceSettingsScope.callAudio(device: GBDevice) {
    category(key = "stratos4pro_audio_header", title = R.string.ak102_prefs_audio_header) {
        switchSetting(
            key = Stratos4ProCalls.PREF_AUDIO_ON_CALL,
            title = R.string.stratos4pro_pref_audio_on_call_title,
            summary = R.string.stratos4pro_pref_audio_on_call_summary,
            icon = R.drawable.ic_phone,
            disableDependentsState = true,
        )
        switchSetting(
            key = Stratos4ProCalls.PREF_AUDIO_AUTO_CONNECT,
            title = R.string.stratos4pro_pref_audio_auto_connect_title,
            summary = R.string.stratos4pro_pref_audio_auto_connect_summary,
            icon = R.drawable.ic_bluetooth_connected,
            dependency = Stratos4ProCalls.PREF_AUDIO_ON_CALL,
        )
        switchSetting(
            key = Stratos4ProCalls.PREF_AUDIO_SCHEDULE,
            title = R.string.stratos4pro_pref_audio_schedule_title,
            summary = R.string.stratos4pro_pref_audio_schedule_summary,
            icon = R.drawable.ic_timer,
            dependency = Stratos4ProCalls.PREF_AUDIO_AUTO_CONNECT,
        )
        time(
            key = Stratos4ProCalls.PREF_AUDIO_SCHEDULE_START,
            title = R.string.mi2_prefs_do_not_disturb_start,
            defaultValue = "08:00",
            dependency = Stratos4ProCalls.PREF_AUDIO_SCHEDULE,
        )
        time(
            key = Stratos4ProCalls.PREF_AUDIO_SCHEDULE_END,
            title = R.string.mi2_prefs_do_not_disturb_end,
            defaultValue = "22:00",
            dependency = Stratos4ProCalls.PREF_AUDIO_SCHEDULE,
        )
        action(
            key = Stratos4ProCalls.PREF_AUDIO_PAIR,
            title = R.string.ak102_pref_audio_pair_title,
            summary = R.string.stratos4pro_pref_audio_pair_summary,
            icon = R.drawable.ic_bluetooth,
        ) { _, _ ->
            GBApplication.deviceService(device).onSendConfiguration(Stratos4ProCalls.PREF_AUDIO_PAIR)
            true
        }
        action(
            key = Stratos4ProCalls.PREF_AUDIO_UNPAIR,
            title = R.string.ak102_pref_audio_unpair_title,
            summary = R.string.ak102_pref_audio_unpair_summary,
            icon = R.drawable.ic_bluetooth_disabled,
        ) { _, _ ->
            GBApplication.deviceService(device).onSendConfiguration(Stratos4ProCalls.PREF_AUDIO_UNPAIR)
            true
        }
    }
}

/**
 * Strobe times for the card's light buttons: digits, empty or 0 = steady. 1..STROBE_MIN_MS is
 * clamped; the summary shows the real value.
 */
private fun DeviceSettingsScope.lights() {
    category(key = "stratos4pro_lights_header", title = R.string.stratos4pro_prefs_lights_header) {
        for ((key, title) in listOf(
            Stratos4ProLights.PREF_WHITE_STROBE_MS to R.string.stratos4pro_pref_light_white_strobe_title,
            Stratos4ProLights.PREF_RED_STROBE_MS to R.string.stratos4pro_pref_light_red_strobe_title,
        )) {
            text(
                key = key,
                title = title,
                icon = R.drawable.ic_flashlight_on,
                maxLength = 5,
                inputType = InputType.TYPE_CLASS_NUMBER,
                onBindEditText = { bindNumberField(it, Stratos4ProLights.STROBE_MAX_MS) },
                summaryProvider = { context, prefs ->
                    val ms = Stratos4ProLights.parseStrobeMs(prefs.getString(key, null))
                    if (ms == 0) {
                        context.getString(R.string.stratos4pro_pref_light_strobe_off)
                    } else {
                        context.getString(R.string.stratos4pro_pref_light_strobe_summary, ms)
                    }
                },
            )
        }
        text(
            key = Stratos4ProLights.PREF_KEEPALIVE_S,
            title = R.string.stratos4pro_pref_light_keepalive_title,
            icon = R.drawable.ic_flashlight_on,
            defaultValue = Stratos4ProLights.KEEPALIVE_DEFAULT_S.toString(),
            maxLength = 2,
            inputType = InputType.TYPE_CLASS_NUMBER,
            onBindEditText = { bindNumberField(it, Stratos4ProLights.KEEPALIVE_MAX_S) },
            summaryProvider = { context, prefs ->
                val s = Stratos4ProLights.parseKeepaliveS(prefs.getString(Stratos4ProLights.PREF_KEEPALIVE_S, null))
                if (s == 0) {
                    context.getString(R.string.stratos4pro_pref_light_keepalive_off)
                } else {
                    context.getString(R.string.stratos4pro_pref_light_keepalive_summary, s)
                }
            },
        )
    }
}

private fun bindNumberField(editText: EditText, max: Int) {
    editText.addTextChangedListener(MinMaxTextWatcher(editText, 0, max, true))
    editText.setSelection(editText.text.length)
}
