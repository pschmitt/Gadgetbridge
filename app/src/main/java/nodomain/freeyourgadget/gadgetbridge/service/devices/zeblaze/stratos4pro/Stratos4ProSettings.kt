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
package nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.ContextCompat
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.SettingsActivity
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.model.WorldClock
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder
import nodomain.freeyourgadget.gadgetbridge.util.GB
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

/**
 * Settings: Gadgetbridge prefs -> watch (`send*`), and the watch's replies and change pushes ->
 * prefs, so the settings screen shows what the watch has.
 */
internal class Stratos4ProSettings(support: Stratos4ProSupport) : Stratos4ProFeature(support) {
    private fun editPrefs(): SharedPreferences.Editor = devicePrefs.preferences.edit()

    private fun bool(key: String, def: Boolean): Byte = if (devicePrefs.getBoolean(key, def)) 1 else 0

    /** Settings the watch derives kcal/distance from, sent on every connect. */
    fun writeOnConnect(builder: TransactionBuilder) {
        sendUserInfo(builder)
        sendStepGoal(builder)
        sendDistanceUnit(builder)
        sendTemperatureUnit(builder)
        sendCameraPermission(builder)
        // After the init burst.
        handler.postDelayed({ checkBrightnessSchedule() }, 5_000L)
    }

    /**
     * Settings reads in paced groups: the firmware drops the tail of a long burst (of ~25 back to
     * back the last five, incl. `c5 aa 01`, went unanswered).
     */
    fun writeInitialReads(builder: TransactionBuilder) {
        val configReads = listOf(
            listOf(
                cmdGetField(CMD_DEVICE_CONFIG, FIELD_RAISE_TO_WAKE),
                cmdGetField(CMD_DEVICE_CONFIG, FIELD_SCREEN_TIMEOUT),
                cmdGetField(CMD_DEVICE_CONFIG, FIELD_SCREEN_TIMEOUT_LIST),
                cmdGetField(CMD_DEVICE_CONFIG, FIELD_LANGUAGE),
            ),
            listOf(
                cmdGetField(CMD_HEALTH_MONITOR, FIELD_E1_CONT_HR),
                cmdGetField(CMD_HEALTH_MONITOR, FIELD_E1_HR_INTERVAL),
                cmdGetField(CMD_HEALTH_MONITOR, FIELD_E1_CONT_SPO2),
                cmdGetField(CMD_HEALTH_MONITOR, FIELD_E1_SPO2_INTERVAL),
            ),
            listOf(
                cmdGetField(CMD_HEALTH_MONITOR, FIELD_E1_AUTO_STRESS),
                cmdGetField(CMD_HEALTH_MONITOR, FIELD_E1_STRESS_INTERVAL),
                cmdGetField(CMD_HEALTH_MONITOR, FIELD_E1_HR_HIGH_ALERT),
                cmdGetField(CMD_HEALTH_MONITOR, FIELD_E1_HR_LOW_ALERT),
            ),
            listOf(
                cmdGetField(CMD_HEALTH_REMINDERS, FIELD_SPO2_ALERT),
                cmdGetField(CMD_DND, FIELD_DND),
                cmdGetField(CMD_MORNING_REPORT, FIELD_MORNING_ENABLED),
                cmdGetField(CMD_WORKOUT_DETECTION, FIELD_WORKOUT_DETECTION),
            ),
            listOf(
                cmdGetField(CMD_WORLD_CLOCK, FIELD_WORLD_CLOCK_SLOTS),
                cmdGetField(CMD_BT3, FIELD_BT3_STATE),
                cmdGetField(CMD_DEVICE_CONFIG, FIELD_PASSWORD),
            ),
            // Informational. `fb` (temperature) is never answered, so not asked.
            listOf(
                cmdGetField(CMD_ALARM, FIELD_ALARM_CAPS),
                cmdGetField(CMD_DEVICE_CONFIG, FIELD_DEFAULT_CONFIG),
                cmdGetField(CMD_DEVICE_CONFIG, FIELD_SCREEN_INFO),
                cmdGetField(CMD_DEVICE_CONFIG, FIELD_SOUND_VIBRATION),
            ),
        )
        for (group in configReads) {
            builder.writeData(group)
            builder.sleep(INIT_BURST_PACING_MS)
        }
    }

    /**
     * Chunked info reads, only after the sync: in the init burst they ate the `c5 aa 01` request.
     * The language list is fixed per firmware, read until seen once.
     */
    fun readDeferredLists() {
        handler.postDelayed({
            if (!isConnected()) return@postDelayed
            val frames = ArrayList<ByteArray>()
            if (devicePrefs.getString(PREF_LANGUAGES, "").isNullOrEmpty()) {
                frames.add(cmdGetField(CMD_DEVICE_CONFIG, FIELD_LANGUAGE_LIST))
            }
            frames.add(cmdGetField(CMD_WORLD_CLOCK, FIELD_WORLD_CLOCK_LIST))
            frames.add(cmdGetField(CMD_MORNING_REPORT, FIELD_MORNING_ITEMS))
            if (devicePrefs.getString(PREF_SPORT_IDS, "").isNullOrEmpty()) {
                for (field in listOf(FIELD_SPORT_MENU_MAX, FIELD_SPORT_MENU_MIN, FIELD_SPORT_IDS)) frames.add(cmdGetField(CMD_SPORT_MENU, field))
            }
            frames.add(cmdGetField(CMD_SPORT_MENU, FIELD_SPORT_MENU))
            queueFrames("read lists", frames)
        }, 2_000L)
    }

    /** Pref change -> watch. False if [key] is not a setting of this class. */
    fun onSendConfiguration(key: String): Boolean {
        when (key) {
            DeviceSettingsPreferenceConst.PREF_INACTIVITY_ENABLE,
            DeviceSettingsPreferenceConst.PREF_INACTIVITY_THRESHOLD,
            DeviceSettingsPreferenceConst.PREF_INACTIVITY_START,
            DeviceSettingsPreferenceConst.PREF_INACTIVITY_END,
            DeviceSettingsPreferenceConst.PREF_INACTIVITY_MO,
            DeviceSettingsPreferenceConst.PREF_INACTIVITY_TU,
            DeviceSettingsPreferenceConst.PREF_INACTIVITY_WE,
            DeviceSettingsPreferenceConst.PREF_INACTIVITY_TH,
            DeviceSettingsPreferenceConst.PREF_INACTIVITY_FR,
            DeviceSettingsPreferenceConst.PREF_INACTIVITY_SA,
            DeviceSettingsPreferenceConst.PREF_INACTIVITY_SU -> sendSedentaryReminder()

            DeviceSettingsPreferenceConst.PREF_LIFTWRIST_NOSHED -> sendRaiseToWake()
            DeviceSettingsPreferenceConst.PREF_SCREEN_TIMEOUT -> sendScreenTimeout()

            DeviceSettingsPreferenceConst.PREF_HEARTRATE_MEASUREMENT_INTERVAL -> sendContinuousHeartRate()
            DeviceSettingsPreferenceConst.PREF_SPO2_ALL_DAY_MONITORING -> sendContinuousSpo2()
            DeviceSettingsPreferenceConst.PREF_SPO2_MEASUREMENT_INTERVAL -> sendSpo2Interval()
            PREF_STRESS_ALL_DAY, PREF_STRESS_INTERVAL -> sendAutoStress()
            PREF_SCI_SLEEP -> sendSciSleep()
            DeviceSettingsPreferenceConst.PREF_SCREEN_BRIGHTNESS -> sendScreenBrightness()
            PREF_BRIGHTNESS_SCHEDULE, PREF_BRIGHTNESS_MAX_AT, PREF_BRIGHTNESS_MIN_AT -> checkBrightnessSchedule(skipDue = true)
            DeviceSettingsPreferenceConst.PREF_VOLUME -> sendVolume()
            DeviceSettingsPreferenceConst.PREF_HEARTRATE_ALERT_HIGH_THRESHOLD,
            DeviceSettingsPreferenceConst.PREF_HEARTRATE_ALERT_LOW_THRESHOLD -> sendHeartRateAlerts()

            DeviceSettingsPreferenceConst.PREF_DO_NOT_DISTURB,
            DeviceSettingsPreferenceConst.PREF_DO_NOT_DISTURB_START,
            DeviceSettingsPreferenceConst.PREF_DO_NOT_DISTURB_END -> sendDnd()

            DeviceSettingsPreferenceConst.PREF_SPO2_LOW_ALERT_THRESHOLD -> sendSpo2Alert()
            DeviceSettingsPreferenceConst.PREF_PASSWORD_ENABLED,
            DeviceSettingsPreferenceConst.PREF_PASSWORD -> sendPassword()
            DeviceSettingsPreferenceConst.PREF_TIMEFORMAT,
            DeviceSettingsPreferenceConst.PREF_DATEFORMAT -> sendTimeFormat(force = true)
            DeviceSettingsPreferenceConst.PREF_WEARLOCATION -> sendUserInfo()

            SettingsActivity.PREF_UNIT_DISTANCE -> sendDistanceUnit()
            SettingsActivity.PREF_UNIT_WEIGHT -> sendWeightUnit()
            SettingsActivity.PREF_UNIT_TEMPERATURE -> sendTemperatureUnit()
            DeviceSettingsPreferenceConst.PREF_LANGUAGE -> sendLanguage()
            DeviceSettingsPreferenceConst.PREF_DISCONNECTNOTIF_NOSHED -> sendDisconnectReminder()
            DeviceSettingsPreferenceConst.PREF_CAMERA_REMOTE -> sendCameraPermission()
            DeviceSettingsPreferenceConst.MORNING_UPDATES_ENABLED -> sendMorningReport()
            DeviceSettingsPreferenceConst.MORNING_UPDATES_CATEGORIES_SORTABLE -> sendMorningItems()
            PREF_WORKOUT_DETECTION -> sendWorkoutDetection()
            PREF_SPORT_MENU -> sendSportMenu()

            ActivityUser.PREF_USER_HEIGHT_CM,
            ActivityUser.PREF_USER_WEIGHT_KG,
            ActivityUser.PREF_USER_DATE_OF_BIRTH,
            ActivityUser.PREF_USER_GENDER -> sendUserInfo()
            ActivityUser.PREF_USER_STEPS_GOAL,
            ActivityUser.PREF_USER_CALORIES_BURNT,
            ActivityUser.PREF_USER_DISTANCE_METERS,
            ActivityUser.PREF_USER_ACTIVETIME_MINUTES -> sendStepGoal()

            else -> return false
        }
        return true
    }

    // --- c4 health reminders

    /**
     * `c4` health reminders. 01 sedentary `enable | interval:2 | fromH | fromM | toH | toM |
     * weekdayMask`, 02 scientific sleep. Mirrored into prefs.
     */
    fun handleHealthReminders(value: ByteArray) {
        if (value.size < 5) return
        // `aa 03` / `ac 03`: low SpO2 alert `<enable><limit>`. Continuous SpO2 reads via `e1 aa
        // 11`.
        if (value[3] == FIELD_SPO2_ALERT && (value[2] == MODE_GET || value[2] == MODE_REPORT)) {
            if (value.size < 7) {
                LOG.warn("SpO2 alert: short frame {}", value.toHex())
                return
            }
            val enabled = value[5].toInt() == 1
            val limit = u8(value, 6)
            LOG.debug("SpO2 low alert: enabled={} limit={}", enabled, limit)
            val editor = editPrefs()
                .putString(DeviceSettingsPreferenceConst.PREF_SPO2_LOW_ALERT_THRESHOLD, (if (enabled) limit else 0).toString())
            if (limit > 0) editor.putInt(PREF_SPO2_LOW_LIMIT_RAW, limit)
            editor.apply()
            return
        }
        if (value[2] != MODE_GET) return
        val fields = parseTlv(value, 5)
        fields[FIELD_C4_SCI_SLEEP]?.firstOrNull()?.let { raw ->
            val enabled = raw.toInt() == 1
            LOG.debug("scientific sleep: {}", enabled)
            editPrefs().putBoolean(PREF_SCI_SLEEP, enabled).apply()
        }
        val blob = fields[FIELD_SEDENTARY] ?: return
        if (blob.size < 8) {
            LOG.warn("sedentary: short value {}", blob.toHex())
            return
        }
        val enabled = blob[0].toInt() == 1
        val interval = u16(blob, 1)
        val fromHour = u8(blob, 3)
        val fromMin = u8(blob, 4)
        val toHour = u8(blob, 5)
        val toMin = u8(blob, 6)
        val mask = u8(blob, 7)
        LOG.debug(
            "sedentary reminder: enabled={} every {}min {}:{} - {}:{} mask=0x{}",
            enabled, interval, fromHour, fromMin, toHour, toMin, Integer.toHexString(mask)
        )
        editPrefs().apply {
            putBoolean(DeviceSettingsPreferenceConst.PREF_INACTIVITY_ENABLE, enabled)
            putString(DeviceSettingsPreferenceConst.PREF_INACTIVITY_THRESHOLD, interval.toString())
            putString(DeviceSettingsPreferenceConst.PREF_INACTIVITY_START, hhMm(fromHour, fromMin))
            putString(DeviceSettingsPreferenceConst.PREF_INACTIVITY_END, hhMm(toHour, toMin))
            // Weekday mask bit order matches the alarm mask: bit0=Sun .. bit6=Sat.
            for ((bit, key) in WEEKDAY_PREF_KEYS.withIndex()) {
                putBoolean(key, (mask and (1 shl bit)) != 0)
            }
            apply()
        }
    }

    /** Send the sedentary reminder back to the watch from the Gadgetbridge preferences. */
    private fun sendSedentaryReminder() {
        val prefs = devicePrefs
        val enabled = prefs.getBoolean(DeviceSettingsPreferenceConst.PREF_INACTIVITY_ENABLE, false)
        val interval = prefs.getString(DeviceSettingsPreferenceConst.PREF_INACTIVITY_THRESHOLD, "60")?.toIntOrNull() ?: 60
        val (fromHour, fromMin) = parseHhMm(prefs.getString(DeviceSettingsPreferenceConst.PREF_INACTIVITY_START, "08:00"), 8, 0)
        val (toHour, toMin) = parseHhMm(prefs.getString(DeviceSettingsPreferenceConst.PREF_INACTIVITY_END, "20:00"), 20, 0)
        var mask = 0
        for ((bit, key) in WEEKDAY_PREF_KEYS.withIndex()) {
            if (prefs.getBoolean(key, true)) mask = mask or (1 shl bit)
        }
        val payload = byteArrayOf(
            PKT_HEADER, CMD_HEALTH_REMINDERS, MODE_SET, MODE_SET, 0x00,
            MODE_SET, FIELD_SEDENTARY, 0x08,
            (if (enabled) 1 else 0).toByte(), hi(interval), lo(interval),
            fromHour.toByte(), fromMin.toByte(), toHour.toByte(), toMin.toByte(), mask.toByte()
        )
        // Single `ab` before the terminator, unlike the other multi-field writes; the watch takes it.
        queueFrames(
            "set sedentary reminder",
            payload, byteArrayOf(PKT_HEADER, CMD_HEALTH_REMINDERS, MODE_SET, PKT_TERMINATOR, xor(payload))
        )
        LOG.debug(
            "sedentary reminder -> enabled={} every {}min {}:{}-{}:{} mask=0x{}",
            enabled, interval, fromHour, fromMin, toHour, toMin, Integer.toHexString(mask)
        )
    }

    /**
     * Scientific sleep `01 c4 ab 02 01 <en>`: the recording the sleep file comes from, so default
     * on. Read back from the `c4` GET-all.
     */
    private fun sendSciSleep() {
        val enabled = bool(PREF_SCI_SLEEP, true)
        queueFrames(
            "set scientific sleep",
            cmdSetField(CMD_HEALTH_REMINDERS, SET_C4_SCI_SLEEP, enabled), cmdGetAll(CMD_HEALTH_REMINDERS)
        )
        LOG.debug("scientific sleep -> {}", enabled)
    }

    /** Continuous SpO2 lives on `c4`: `01 c4 ab 03 01 <en>`. Read back via `e1 aa 11`. */
    private fun sendContinuousSpo2() {
        val enabled = bool(DeviceSettingsPreferenceConst.PREF_SPO2_ALL_DAY_MONITORING, false)
        queueFrames(
            "set continuous SpO2",
            cmdSetField(CMD_HEALTH_REMINDERS, SET_C4_CONT_SPO2, enabled), cmdGetField(CMD_HEALTH_MONITOR, FIELD_E1_CONT_SPO2)
        )
        LOG.debug("continuous SpO2 -> {}", enabled)
    }

    /**
     * Low SpO2 alert `01 c4 ab 04 02 <enabled><limit>`, read back via `c4 aa 03`. Never a 0 limit,
     * see [sendHeartRateAlerts].
     */
    private fun sendSpo2Alert() {
        val threshold = devicePrefs.getString(DeviceSettingsPreferenceConst.PREF_SPO2_LOW_ALERT_THRESHOLD, "0")?.toIntOrNull() ?: 0
        val limit = if (threshold > 0) threshold else devicePrefs.getInt(PREF_SPO2_LOW_LIMIT_RAW, DEFAULT_SPO2_LOW_LIMIT)
        queueFrames(
            "set SpO2 alert",
            cmdSetField(CMD_HEALTH_REMINDERS, SET_C4_SPO2_ALERT, if (threshold > 0) 1 else 0, limit.coerceIn(1, 100).toByte()),
            cmdGetField(CMD_HEALTH_REMINDERS, FIELD_SPO2_ALERT)
        )
        LOG.debug("SpO2 alert -> enabled={} limit={}", threshold > 0, limit)
    }

    // --- c9 workout detection

    /** `aa 01` / `ac 01` -> pref. Acks are only logged. */
    fun handleWorkoutDetection(value: ByteArray) {
        if (value.size >= 6 && value[2] != MODE_SET && value[3] == FIELD_WORKOUT_DETECTION) {
            readBoolPref(value, PREF_WORKOUT_DETECTION, "workout detection")
        } else {
            LOG.debug("workout detection frame: {}", value.toHex())
        }
    }

    private fun sendWorkoutDetection() {
        queueFrames(
            "set workout detection",
            cmdSetField(CMD_WORKOUT_DETECTION, FIELD_WORKOUT_DETECTION, bool(PREF_WORKOUT_DETECTION, false)),
            cmdGetField(CMD_WORKOUT_DETECTION, FIELD_WORKOUT_DETECTION)
        )
    }

    // --- ca morning report

    /** Switch and item replies -> prefs; `ac` pushes are taken the same way. Acks are only logged. */
    fun handleMorningReport(value: ByteArray) {
        val items = parseMorningItems(value)
        when {
            items != null -> {
                LOG.debug("morning report items: {}", items)
                editPrefs().putString(DeviceSettingsPreferenceConst.MORNING_UPDATES_CATEGORIES_SORTABLE, items.joinToString(",")).apply()
            }
            value.size >= 6 && value[2] != MODE_SET && value[3] == FIELD_MORNING_ENABLED -> {
                LOG.debug("morning report: {}", value[5].toInt() == 1)
                editPrefs().putBoolean(DeviceSettingsPreferenceConst.MORNING_UPDATES_ENABLED, value[5].toInt() == 1).apply()
            }
            else -> LOG.debug("morning report frame: {}", value.toHex())
        }
    }

    private fun sendMorningReport() {
        queueFrames(
            "set morning report",
            cmdSetField(CMD_MORNING_REPORT, FIELD_MORNING_ENABLED, bool(DeviceSettingsPreferenceConst.MORNING_UPDATES_ENABLED, false)),
            cmdGetField(CMD_MORNING_REPORT, FIELD_MORNING_ENABLED)
        )
    }

    /** Read back after the write: the screen shows the order the watch kept. */
    private fun sendMorningItems() {
        // Unset = all on; an empty value = all off.
        val selected = devicePrefs.getString(DeviceSettingsPreferenceConst.MORNING_UPDATES_CATEGORIES_SORTABLE, null)?.split(",") ?: MORNING_ITEMS
        queueFrames("set morning items", morningItemsFrames(selected) + cmdGetField(CMD_MORNING_REPORT, FIELD_MORNING_ITEMS))
        LOG.debug("morning report items -> {}", selected)
    }

    // --- ec sport menu

    /** Max, min, all ids and menu -> prefs; `ac` = changed on the watch, read it back. */
    fun handleSportMenu(value: ByteArray) {
        if (value.size < 5) return
        when {
            value[2] == MODE_REPORT -> if (value[4] != PKT_TERMINATOR) queueGetField(CMD_SPORT_MENU, FIELD_SPORT_MENU)
            value[2] != MODE_GET -> LOG.debug("sport menu frame: {}", value.toHex())
            value[3] == FIELD_SPORT_MENU_MAX || value[3] == FIELD_SPORT_MENU_MIN -> {
                val n = tlvInt(value) ?: return
                editPrefs().putInt(if (value[3] == FIELD_SPORT_MENU_MAX) PREF_SPORT_MENU_MAX else PREF_SPORT_MENU_MIN, n).apply()
            }
            value[3] == FIELD_SPORT_IDS || value[3] == FIELD_SPORT_MENU -> {
                val ids = parseSportIds(chunks.collect(value, 5) ?: return) ?: return
                LOG.debug("sport {}: {}", if (value[3] == FIELD_SPORT_IDS) "ids" else "menu", ids)
                editPrefs().putString(if (value[3] == FIELD_SPORT_IDS) PREF_SPORT_IDS else PREF_SPORT_MENU, ids.joinToString(",")).apply()
            }
        }
    }

    /** Read back after the write. The watch caps the count but does not check the minimum. */
    private fun sendSportMenu() {
        val ids = devicePrefs.getString(PREF_SPORT_MENU, "").split(",").mapNotNull { it.toIntOrNull() }
        val min = devicePrefs.getInt(PREF_SPORT_MENU_MIN, 3)
        if (ids.size < min) {
            GB.toast(context.getString(R.string.select_at_least_option, min), 3, GB.WARN)
        } else {
            queueFrames("set sport menu", sportMenuFrames(ids.take(devicePrefs.getInt(PREF_SPORT_MENU_MAX, 24))))
        }
        queueGetField(CMD_SPORT_MENU, FIELD_SPORT_MENU)
    }

    // --- e1 health monitor

    /**
     * `e1` health monitoring: auto-measure toggles, HR alerts, stress history. GET and SET ids
     * differ (FIELD_E1_* / SET_E1_*).
     */
    fun handleHealthMonitor(value: ByteArray) {
        if (value.size < 5) return
        when (value[2]) {
            MODE_GET -> if (value[3] == FIELD_STRESS_HISTORY) {
                history.handleStressHistory(value)
            } else {
                handleMonitorField(value[3], value)
            }

            MODE_REPORT -> when (value[3]) {
                FIELD_E1_HR_REPORT -> history.handleLiveHeartRate(value)
                // Changed on the watch. Same payload as the GET reply, same parser.
                else -> E1_PUSH_TO_FIELD[value[3]]?.let { handleMonitorField(it, value) }
                    ?: LOG.debug("e1 push field {}: {}", hex(value[3]), value.toHex())
            }

            else -> {}
        }
    }

    /** An `e1` GET reply (or the push mirroring it) for [field]. */
    private fun handleMonitorField(field: Byte, value: ByteArray) {
        when (field) {
            FIELD_E1_CONT_HR -> {
                // GB: interval 0 = off. The interval comes in 0b, so only force 0 when the
                // watch says off.
                val enabled = (tlvInt(value) ?: return) == 1
                LOG.debug("continuous HR: {}", enabled)
                if (!enabled) {
                    editPrefs().putString(DeviceSettingsPreferenceConst.PREF_HEARTRATE_MEASUREMENT_INTERVAL, "0").apply()
                }
            }
            FIELD_E1_CONT_SPO2 -> readBoolPref(value, DeviceSettingsPreferenceConst.PREF_SPO2_ALL_DAY_MONITORING, "continuous SpO2")
            FIELD_E1_AUTO_STRESS -> readBoolPref(value, PREF_STRESS_ALL_DAY, "auto stress")
            FIELD_E1_HR_INTERVAL -> {
                val minutes = tlvInt(value) ?: return
                LOG.debug("HR interval: {} min", minutes)
                editPrefs().putString(DeviceSettingsPreferenceConst.PREF_HEARTRATE_MEASUREMENT_INTERVAL, (minutes * 60).toString()).apply()
            }
            // GB's SpO2 interval pref is seconds; ours for stress is minutes.
            FIELD_E1_SPO2_INTERVAL -> {
                val minutes = tlvInt(value) ?: return
                LOG.debug("SpO2 interval: {} min", minutes)
                editPrefs().putString(DeviceSettingsPreferenceConst.PREF_SPO2_MEASUREMENT_INTERVAL, (minutes * 60).toString()).apply()
            }
            FIELD_E1_STRESS_INTERVAL -> {
                val minutes = tlvInt(value) ?: return
                LOG.debug("stress interval: {} min", minutes)
                editPrefs().putString(PREF_STRESS_INTERVAL, minutes.toString()).apply()
            }

            FIELD_E1_HR_HIGH_ALERT, FIELD_E1_HR_LOW_ALERT -> {
                val len = u8(value, 4)
                if (len < 2 || value.size < 7) return
                val enabled = value[5].toInt() == 1
                val limit = u8(value, 6)
                val high = field == FIELD_E1_HR_HIGH_ALERT
                LOG.debug("HR {} alert: enabled={} limit={}", if (high) "high" else "low", enabled, limit)
                // GB: threshold 0 = off, but the watch keeps its limit when off. Store both.
                val editor = editPrefs().putString(
                    if (high) DeviceSettingsPreferenceConst.PREF_HEARTRATE_ALERT_HIGH_THRESHOLD
                    else DeviceSettingsPreferenceConst.PREF_HEARTRATE_ALERT_LOW_THRESHOLD,
                    (if (enabled) limit else 0).toString()
                )
                if (limit > 0) {
                    editor.putInt(if (high) PREF_HR_HIGH_LIMIT_RAW else PREF_HR_LOW_LIMIT_RAW, limit)
                }
                editor.apply()
            }

            else -> LOG.debug("e1 get field {}: {}", hex(field), value.toHex())
        }
    }

    /** 1-byte bool GET reply -> device pref. */
    private fun readBoolPref(value: ByteArray, prefKey: String, label: String) {
        val enabled = (tlvInt(value) ?: return) == 1
        LOG.debug("{}: {}", label, enabled)
        editPrefs().putBoolean(prefKey, enabled).apply()
    }

    /** Continuous heart rate: `01 e1 ab 02 01 <en>`, optionally with interval `ab 0b`. */
    fun sendContinuousHeartRate() {
        val intervalSec = devicePrefs.getString(DeviceSettingsPreferenceConst.PREF_HEARTRATE_MEASUREMENT_INTERVAL, "0")
            ?.toIntOrNull() ?: 0
        val enabled = intervalSec > 0
        val minutes = (intervalSec / 60).coerceIn(0, 255)
        val body = ByteArrayOutputStream()
        body.write(byteArrayOf(MODE_SET, SET_E1_CONT_HR, 0x01, if (enabled) 1 else 0))
        if (minutes > 0) body.write(byteArrayOf(MODE_SET, SET_E1_HR_INTERVAL, 0x01, minutes.toByte()))
        queueFrames("set continuous HR", chunkedTlv(CMD_HEALTH_MONITOR, body.toByteArray()))
        queueGetField(CMD_HEALTH_MONITOR, FIELD_E1_CONT_HR)
        LOG.debug("continuous HR -> enabled={} every {} min", enabled, minutes)
    }

    /** SpO2 auto-measure interval: `01 e1 ab 0c 01 <min>`. */
    private fun sendSpo2Interval() {
        val seconds = devicePrefs.getString(DeviceSettingsPreferenceConst.PREF_SPO2_MEASUREMENT_INTERVAL, null)?.toIntOrNull()
        val minutes = nearestInterval(seconds?.div(60) ?: DEFAULT_SPO2_INTERVAL_MIN)
        writeField("set SpO2 interval", CMD_HEALTH_MONITOR, SET_E1_SPO2_INTERVAL, FIELD_E1_SPO2_INTERVAL, minutes.toByte())
    }

    /** Snap to a supported interval; the firmware was only seen with these. */
    private fun nearestInterval(minutes: Int): Int =
        MEASUREMENT_INTERVALS_MIN.minByOrNull { abs(it - minutes) } ?: DEFAULT_SPO2_INTERVAL_MIN

    /** Auto stress + interval: `01 e1 abab 00 ab 07 01 <en> ab 0d 01 <min>`. */
    private fun sendAutoStress() {
        val enabled = devicePrefs.getBoolean(PREF_STRESS_ALL_DAY, false)
        val minutes = nearestInterval(devicePrefs.getString(PREF_STRESS_INTERVAL, null)?.toIntOrNull() ?: DEFAULT_STRESS_INTERVAL_MIN)
        val body = byteArrayOf(
            MODE_SET, SET_E1_AUTO_STRESS, 0x01, if (enabled) 1 else 0,
            MODE_SET, SET_E1_STRESS_INTERVAL, 0x01, minutes.toByte(),
        )
        queueFrames("set auto stress", chunkedTlv(CMD_HEALTH_MONITOR, body))
        queueGetField(CMD_HEALTH_MONITOR, FIELD_E1_AUTO_STRESS)
        queueGetField(CMD_HEALTH_MONITOR, FIELD_E1_STRESS_INTERVAL)
        LOG.debug("auto stress -> enabled={} every {} min", enabled, minutes)
    }

    /**
     * HR alerts `01 e1 ab 03/04 02 <enabled><limit>`. GB's off is threshold 0, but the watch stores
     * flag and limit apart and a 0 wipes the limit. So off keeps the last limit and clears the
     * flag.
     */
    private fun sendHeartRateAlerts() {
        val high = devicePrefs.getString(DeviceSettingsPreferenceConst.PREF_HEARTRATE_ALERT_HIGH_THRESHOLD, "0")?.toIntOrNull() ?: 0
        val low = devicePrefs.getString(DeviceSettingsPreferenceConst.PREF_HEARTRATE_ALERT_LOW_THRESHOLD, "0")?.toIntOrNull() ?: 0
        val highLimit = if (high > 0) high else devicePrefs.getInt(PREF_HR_HIGH_LIMIT_RAW, DEFAULT_HR_HIGH_LIMIT)
        val lowLimit = if (low > 0) low else devicePrefs.getInt(PREF_HR_LOW_LIMIT_RAW, DEFAULT_HR_LOW_LIMIT)
        queueFrames(
            "set HR alerts",
            cmdSetField(CMD_HEALTH_MONITOR, SET_E1_HR_HIGH_ALERT, if (high > 0) 1 else 0, highLimit.coerceIn(1, 255).toByte()),
            cmdSetField(CMD_HEALTH_MONITOR, SET_E1_HR_LOW_ALERT, if (low > 0) 1 else 0, lowLimit.coerceIn(1, 255).toByte()),
            cmdGetField(CMD_HEALTH_MONITOR, FIELD_E1_HR_HIGH_ALERT),
            cmdGetField(CMD_HEALTH_MONITOR, FIELD_E1_HR_LOW_ALERT),
        )
        LOG.debug("HR alerts -> high={}/{} low={}/{}", high > 0, highLimit, low > 0, lowLimit)
    }

    // --- a5 device config

    /**
     * `a5` device config. Single-field GETs answer `01 a5 aa <field> <len> <value>` (no doubled
     * mode). Field 03 is chunked: index in the length slot, `fd` closes.
     */
    fun handleDeviceConfig(value: ByteArray) {
        if (value.size < 6 || value[2] != MODE_GET) return
        val field = value[3]
        if (field == FIELD_DEFAULT_CONFIG) {
            // Chunked sub-TLV of 1-byte feature switches.
            val p = chunks.collect(value, 5) ?: return
            val out = subTlvList(p).joinToString(" ") { (f, v) ->
                "${DEFAULT_CONFIG_NAMES[f] ?: "f$f"}=${if (v.isNotEmpty()) u8(v, 0) else -1}"
            }
            LOG.info("default configuration: {}", out)
            return
        }
        if (field == FIELD_LANGUAGE_LIST) {
            // Chunked, one language id per byte, no count. Stored so the language list offers only
            // these.
            val ids = (chunks.collect(value, 5) ?: return).map { it.toInt() and 0xff }
            LOG.info("watch supports {} languages: {}", ids.size, ids)
            if (ids.isNotEmpty()) {
                editPrefs().putString(PREF_LANGUAGES, ids.joinToString(",")).apply()
            }
            return
        }
        val len = u8(value, 4)
        if (value.size < 5 + len) {
            LOG.warn("a5 field {}: short frame {}", hex(field), value.toHex())
            return
        }
        val blob = value.copyOfRange(5, 5 + len)
        if (blob.isEmpty()) return
        when (field) {
            FIELD_RAISE_TO_WAKE -> {
                val enabled = blob[0].toInt() == 1
                LOG.debug("raise to wake: {}", enabled)
                editPrefs().putBoolean(DeviceSettingsPreferenceConst.PREF_LIFTWRIST_NOSHED, enabled).apply()
            }

            FIELD_SCREEN_TIMEOUT -> {
                val seconds = u8(blob, 0)
                LOG.debug("screen timeout: {}s", seconds)
                editPrefs().putString(DeviceSettingsPreferenceConst.PREF_SCREEN_TIMEOUT, seconds.toString()).apply()
            }

            FIELD_SCREEN_INFO -> {
                // 01 brightness, 02 autoBrightness, 03 supported brightness levels,
                // 04 touchScreen, 05 coverToOffScreen.
                val tlv = parseSubTlv(blob)
                val brightness = tlv[1]?.let { u8(it, 0) }
                val auto = tlv[2]?.let { it[0].toInt() == 1 }
                val levels = tlv[3]?.map { it.toInt() and 0xff }
                LOG.debug(
                    "screen info: brightness={} auto={} levels={} touch={} coverOff={}",
                    brightness, auto, levels, tlv[4]?.get(0), tlv[5]?.get(0)
                )
                val editor = editPrefs()
                brightness?.let { editor.putInt(DeviceSettingsPreferenceConst.PREF_SCREEN_BRIGHTNESS, it) }
                if (!levels.isNullOrEmpty()) {
                    editor.putString(PREF_BRIGHTNESS_LEVELS, levels.joinToString(","))
                }
                editor.apply()
            }

            FIELD_LANGUAGE -> {
                // Log only: storing it would pin the "auto" (phone locale) language pref.
                LOG.debug("watch language id: {}", u8(blob, 0))
            }

            FIELD_PASSWORD -> {
                // `<enable> [<d1>..<d6>]`, one digit per byte.
                val enabled = blob[0].toInt() == 1
                val digits = if (enabled && blob.size >= 7) blob.copyOfRange(1, 7).joinToString("") { (it.toInt() and 0xff).toString() } else null
                LOG.debug("lock screen password: enabled={}", enabled)
                val editor = editPrefs().putBoolean(DeviceSettingsPreferenceConst.PREF_PASSWORD_ENABLED, enabled)
                if (digits != null) editor.putString(DeviceSettingsPreferenceConst.PREF_PASSWORD, digits)
                editor.apply()
            }

            FIELD_SOUND_VIBRATION -> {
                // 01 mediaAudio, 02 muteMode, 03 nested { 01 volume }.
                val tlv = parseSubTlv(blob)
                val volume = tlv[3]?.let { parseSubTlv(it)[1] }?.let { u8(it, 0) }
                LOG.debug(
                    "sound/vibration: mediaAudio={} muteMode={} volume={}",
                    tlv[1]?.get(0), tlv[2]?.get(0), volume
                )
                volume?.let { editPrefs().putInt(DeviceSettingsPreferenceConst.PREF_VOLUME, it).apply() }
            }

            FIELD_SCREEN_TIMEOUT_LIST -> {
                // uint16 timeouts the firmware accepts; the settings list is built from them.
                val options = (0 until len / 2).map { u16(blob, it * 2) }
                LOG.info("screen timeout options: {}", options)
                if (options.isNotEmpty()) {
                    editPrefs().putString(PREF_SCREEN_TIMEOUT_OPTIONS, options.joinToString(",")).apply()
                }
            }

            else -> LOG.debug("a5 field {}: {}", hex(field), blob.toHex())
        }
    }

    /** `a5` push for a setting changed on the watch: store it, or re-read so the settings follow. */
    fun handleConfigChanged(field: Byte, v: Int): Boolean {
        when (field) {
            PUSH_SCREEN_TIMEOUT_CHANGED -> {
                LOG.debug("screen timeout changed on watch: {}s", v)
                editPrefs().putString(DeviceSettingsPreferenceConst.PREF_SCREEN_TIMEOUT, v.toString()).apply()
            }
            PUSH_RAISE_TO_WAKE_CHANGED -> queueGetField(CMD_DEVICE_CONFIG, FIELD_RAISE_TO_WAKE)
            PUSH_PASSWORD_CHANGED -> queueGetField(CMD_DEVICE_CONFIG, FIELD_PASSWORD)
            PUSH_SCREEN_INFO_CHANGED -> queueGetField(CMD_DEVICE_CONFIG, FIELD_SCREEN_INFO)
            PUSH_SOUND_CHANGED -> queueGetField(CMD_DEVICE_CONFIG, FIELD_SOUND_VIBRATION)
            else -> return false
        }
        return true
    }

    /**
     * Write one field and read it back, so the log shows what stuck. `a5` SET and GET ids differ,
     * hence [setField] / [getField].
     */
    private fun writeField(taskName: String, cmd: Byte, setField: Byte, getField: Byte, vararg value: Byte) {
        queueFrames(taskName, cmdSetField(cmd, setField, *value), cmdGetField(cmd, getField))
        LOG.debug("{} -> cmd={} set={} get={} value={}", taskName, hex(cmd), hex(setField), hex(getField), value.toHex())
    }

    /** Toggle "raise to wake" on the watch. */
    private fun sendRaiseToWake() {
        writeField(
            "set raise to wake", CMD_DEVICE_CONFIG, SET_RAISE_TO_WAKE, FIELD_RAISE_TO_WAKE,
            bool(DeviceSettingsPreferenceConst.PREF_LIFTWRIST_NOSHED, false)
        )
    }

    /** Set how long the screen stays lit, in seconds. */
    private fun sendScreenTimeout() {
        val seconds = devicePrefs.getString(DeviceSettingsPreferenceConst.PREF_SCREEN_TIMEOUT, "5")?.toIntOrNull() ?: 5
        writeField("set screen timeout", CMD_DEVICE_CONFIG, SET_SCREEN_TIMEOUT, FIELD_SCREEN_TIMEOUT, seconds.toByte())
    }

    /**
     * Brightness `01 a5 ab 0d 03 01 01 <level>`. Only the levels from `a5 aa 0f` field 03 are
     * accepted (20..100 by 20), so snap to the nearest. The firmware ignores auto (02).
     */
    private fun sendScreenBrightness() {
        val levels = devicePrefs.getString(PREF_BRIGHTNESS_LEVELS, "")
            ?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.filter { it > 0 }.orEmpty()
        val wanted = devicePrefs.getInt(DeviceSettingsPreferenceConst.PREF_SCREEN_BRIGHTNESS, 60)
        val brightness = if (levels.isEmpty()) wanted.coerceIn(1, 100)
        else levels.minByOrNull { abs(it - wanted) } ?: wanted
        writeField(
            "set brightness", CMD_DEVICE_CONFIG, SET_SCREEN_INFO, FIELD_SCREEN_INFO,
            0x01, 0x01, brightness.toByte(),
        )
    }

    private var brightnessReceiver: BroadcastReceiver? = null
    private val brightnessAction by lazy { "${context.packageName}.stratos4pro.BRIGHTNESS.${device.address}" }
    private val brightnessIntent by lazy {
        PendingIntent.getBroadcast(context, 0, Intent(brightnessAction).setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE)
    }

    private fun cancelBrightnessSchedule() {
        context.getSystemService(AlarmManager::class.java).cancel(brightnessIntent)
    }

    fun dispose() {
        cancelBrightnessSchedule()
        brightnessReceiver?.let { context.unregisterReceiver(it) }
        brightnessReceiver = null
    }

    /**
     * Scheduled brightness: highest level from one time, lowest from another, sent once per switch
     * and only then. A switch missed while disconnected is sent on reconnect. [skipDue]: a settings
     * edit, mark the last switch done without sending. Arms an alarm for the next switch.
     */
    private fun checkBrightnessSchedule(skipDue: Boolean = false) {
        cancelBrightnessSchedule()
        if (!devicePrefs.getBoolean(PREF_BRIGHTNESS_SCHEDULE, false)) return
        val maxAt = devicePrefs.getLocalTime(PREF_BRIGHTNESS_MAX_AT, "09:00")
        val minAt = devicePrefs.getLocalTime(PREF_BRIGHTNESS_MIN_AT, "20:00")
        if (maxAt == minAt) return
        val now = LocalDateTime.now()
        val (last, max) = lastBrightnessSwitch(now, maxAt, minAt)
        val lastS = last.atZone(ZoneId.systemDefault()).toEpochSecond()
        if (lastS > devicePrefs.getLong(PREF_BRIGHTNESS_SENT, 0L)) when {
            skipDue -> editPrefs().putLong(PREF_BRIGHTNESS_SENT, lastS).apply()
            isConnected() -> {
                LOG.info("scheduled brightness {}", if (max) "max" else "min")
                // The nearest supported level is sent, so 100 / 1 pick the ends.
                editPrefs().putLong(PREF_BRIGHTNESS_SENT, lastS)
                    .putInt(DeviceSettingsPreferenceConst.PREF_SCREEN_BRIGHTNESS, if (max) 100 else 1).apply()
                sendScreenBrightness()
            }
        }
        val next = nextBrightnessSwitch(now, maxAt, minAt).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (brightnessReceiver == null) {
            brightnessReceiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) = checkBrightnessSchedule()
            }
            val filter = IntentFilter(brightnessAction)
            // Re-arm as exact once access is granted; a revoke kills the app, the reconnect re-arms.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                filter.addAction(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
            }
            ContextCompat.registerReceiver(context, brightnessReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        // Allow-while-idle, else a plain alarm can wait for the screen; inexact: up to an hour late.
        val am = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, brightnessIntent)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, brightnessIntent)
            LOG.warn("brightness schedule: no exact alarm access, switches may be late")
            if (skipDue) GB.toast(context, context.getString(R.string.stratos4pro_brightness_schedule_inexact), 1, GB.WARN)
        }
    }

    /**
     * Lock password: `01 a5 ab 0c 07 01 <d1>..<d6>` on, `01 a5 ab 0c 01 00` off. Anything but 6
     * digits is refused here.
     */
    private fun sendPassword() {
        val enabled = devicePrefs.getBoolean(DeviceSettingsPreferenceConst.PREF_PASSWORD_ENABLED, false)
        val code = devicePrefs.getString(DeviceSettingsPreferenceConst.PREF_PASSWORD, "") ?: ""
        val value = if (enabled) {
            if (code.length != 6 || !code.all { it.isDigit() }) {
                LOG.warn("password must be exactly 6 digits, not sending")
                GB.toast(context.getString(R.string.stratos4pro_password_six_digits), 3, GB.WARN)
                return
            }
            byteArrayOf(0x01) + ByteArray(6) { (code[it] - '0').toByte() }
        } else {
            byteArrayOf(0x00)
        }
        queueFrames(
            "set password",
            cmdSetField(CMD_DEVICE_CONFIG, SET_PASSWORD, *value), cmdGetField(CMD_DEVICE_CONFIG, FIELD_PASSWORD)
        )
        LOG.debug("password -> enabled={}", enabled)
    }

    /** Volume 0..15: `01 a5 ab 0e <len> 03 03 01 01 <volume>` - the volume is nested one level down. */
    private fun sendVolume() {
        val volume = devicePrefs.getInt(DeviceSettingsPreferenceConst.PREF_VOLUME, 11).coerceIn(0, 15)
        writeField(
            "set volume", CMD_DEVICE_CONFIG, SET_SOUND_VIBRATION, FIELD_SOUND_VIBRATION,
            0x03, 0x03, 0x01, 0x01, volume.toByte(),
        )
    }

    /** `01 a5 ab 06 01 <language id>`; "auto" or unknown follows the phone, else English. */
    private fun sendLanguage() {
        val pref = devicePrefs.getString(DeviceSettingsPreferenceConst.PREF_LANGUAGE, "auto") ?: "auto"
        val id = Stratos4ProLanguage.idForPref(pref)
            ?: Locale.getDefault().let { Stratos4ProLanguage.id(it.language, it.country) }
            ?: 2
        writeField("set language", CMD_DEVICE_CONFIG, SET_LANGUAGE, FIELD_LANGUAGE, id.toByte())
    }

    /** `01 a5 ab 05 01 <en>` */
    private fun sendDisconnectReminder() {
        queueFrames(
            "set disconnect reminder",
            cmdSetField(CMD_DEVICE_CONFIG, SET_DISCONNECT_REMIND, bool(DeviceSettingsPreferenceConst.PREF_DISCONNECTNOTIF_NOSHED, false))
        )
    }

    /** Let the watch show its camera-remote control when Gadgetbridge's camera remote is enabled. */
    private fun sendCameraPermission(b: TransactionBuilder? = null) {
        val frame = cmdSetField(CMD_DEVICE_CONFIG, SET_CAMERA_PERMISSION, bool(DeviceSettingsPreferenceConst.PREF_CAMERA_REMOTE, false))
        if (b != null) b.writeData(frame) else queueFrames("camera permission", frame)
    }

    /** `01 a5 ab 07 01 <0 metric|1 imperial>` */
    private fun sendDistanceUnit(b: TransactionBuilder? = null) {
        val imperial = GBApplication.getPrefs().getString(SettingsActivity.PREF_UNIT_DISTANCE, "metric") == "imperial"
        val frame = cmdSetField(CMD_DEVICE_CONFIG, SET_LENGTH_UNIT, if (imperial) 1 else 0)
        if (b != null) b.writeData(frame) else queueFrames("set distance unit", frame)
        LOG.debug("distance unit -> {}", if (imperial) "imperial" else "metric")
    }

    /** `01 a5 ab 09 01 <0 kg|1 lb>` */
    private fun sendWeightUnit() {
        val lb = GBApplication.getPrefs().getString(SettingsActivity.PREF_UNIT_WEIGHT, "kg") == "lb"
        queueFrames("set weight unit", cmdSetField(CMD_DEVICE_CONFIG, SET_WEIGHT_UNIT, if (lb) 1 else 0))
    }

    /** Weather temperature unit on the watch: `01 e0 ab 02 01 <0 C|1 F>`. */
    private fun sendTemperatureUnit(b: TransactionBuilder? = null) {
        val f = GBApplication.getPrefs().getString(SettingsActivity.PREF_UNIT_TEMPERATURE, "c").equals("f", ignoreCase = true)
        val frame = cmdSetField(CMD_WEATHER, SET_TEMPERATURE_UNIT, if (f) 1 else 0)
        if (b != null) b.writeData(frame) else queueFrames("set temperature unit", frame)
    }

    // --- a3 time format

    /**
     * `01 a3 [04 01 <dateFormat>] 08 01 <timeFormat>`. Time 0 system, 1 12h, 2 24h. Date 1
     * YYYY-MM-DD, 2 MM-DD-YYYY, 3 DD-MM-YYYY, 4 MM-DD weekday. Both prefs "auto": nothing sent.
     */
    fun sendTimeFormat(b: TransactionBuilder? = null, force: Boolean = false) {
        val timePref = devicePrefs.getString(DeviceSettingsPreferenceConst.PREF_TIMEFORMAT, DeviceSettingsPreferenceConst.PREF_TIMEFORMAT_AUTO)
        val datePref = devicePrefs.getString(DeviceSettingsPreferenceConst.PREF_DATEFORMAT, DeviceSettingsPreferenceConst.PREF_DATEFORMAT_AUTO)
        val timeAuto = timePref == DeviceSettingsPreferenceConst.PREF_TIMEFORMAT_AUTO
        val dateAuto = datePref == DeviceSettingsPreferenceConst.PREF_DATEFORMAT_AUTO
        if (!force && timeAuto && dateAuto) return
        val timeFormat = when {
            timePref == DeviceSettingsPreferenceConst.PREF_TIMEFORMAT_12H -> 1
            timePref == DeviceSettingsPreferenceConst.PREF_TIMEFORMAT_24H -> 2
            android.text.format.DateFormat.is24HourFormat(context) -> 2
            else -> 1
        }
        val dateFormat = when (datePref) {
            DeviceSettingsPreferenceConst.PREF_DATEFORMAT_DAY_MONTH -> 3
            DeviceSettingsPreferenceConst.PREF_DATEFORMAT_MONTH_DAY -> 2
            else -> 0
        }
        val frame = if (dateFormat != 0) {
            byteArrayOf(PKT_HEADER, CMD_TIME, FIELD_DATE_FORMAT, 0x01, dateFormat.toByte(), FIELD_TIME_FORMAT, 0x01, timeFormat.toByte())
        } else {
            byteArrayOf(PKT_HEADER, CMD_TIME, FIELD_TIME_FORMAT, 0x01, timeFormat.toByte())
        }
        if (b != null) b.writeData(frame) else queueFrames("set time format", frame)
        LOG.debug("time format -> time={} date={}", timeFormat, dateFormat)
    }

    // --- ea do not disturb

    /** `01 ea aa|ab|ac 01 08 <allDay><timed><fromH><fromM><toH><toM><closeVibration><closeMsg>` */
    fun handleDnd(value: ByteArray) {
        if (value.size < 4 || (value[2] != MODE_GET && value[2] != MODE_REPORT) || value[3] != FIELD_DND) return
        if (value.size < 13 || u8(value, 4) < 8) {
            LOG.warn("DND: short frame {}", value.toHex())
            return
        }
        val allDay = value[5].toInt() == 1
        val timed = value[6].toInt() == 1
        val mode = when {
            allDay -> DeviceSettingsPreferenceConst.PREF_DO_NOT_DISTURB_ALWAYS
            timed -> DeviceSettingsPreferenceConst.PREF_DO_NOT_DISTURB_SCHEDULED
            else -> DeviceSettingsPreferenceConst.PREF_DO_NOT_DISTURB_OFF
        }
        val from = hhMm(u8(value, 7), u8(value, 8))
        val to = hhMm(u8(value, 9), u8(value, 10))
        LOG.debug("DND: {} {}-{} closeVib={} closeMsg={}", mode, from, to, value[11], value[12])
        editPrefs()
            .putString(DeviceSettingsPreferenceConst.PREF_DO_NOT_DISTURB, mode)
            .putString(DeviceSettingsPreferenceConst.PREF_DO_NOT_DISTURB_START, from)
            .putString(DeviceSettingsPreferenceConst.PREF_DO_NOT_DISTURB_END, to)
            // No GB pref for these, but the watch round-trips them: keep its values so the schedule
            // write does not flip them.
            .putBoolean(PREF_DND_CLOSE_VIBRATION, value[11].toInt() == 1)
            .putBoolean(PREF_DND_CLOSE_MESSAGE, value[12].toInt() == 1)
            .apply()
    }

    private fun sendDnd() {
        val prefs = devicePrefs
        val mode = prefs.getString(DeviceSettingsPreferenceConst.PREF_DO_NOT_DISTURB, DeviceSettingsPreferenceConst.PREF_DO_NOT_DISTURB_OFF)
        val (fromH, fromM) = parseHhMm(prefs.getString(DeviceSettingsPreferenceConst.PREF_DO_NOT_DISTURB_START, "22:00"), 22, 0)
        val (toH, toM) = parseHhMm(prefs.getString(DeviceSettingsPreferenceConst.PREF_DO_NOT_DISTURB_END, "06:00"), 6, 0)
        val allDay = mode == DeviceSettingsPreferenceConst.PREF_DO_NOT_DISTURB_ALWAYS
        val timed = mode == DeviceSettingsPreferenceConst.PREF_DO_NOT_DISTURB_SCHEDULED
        // Keep what the watch reported; 1 would silently turn vibration/message reminders off.
        queueFrames(
            "set DND",
            cmdSetField(
                CMD_DND, FIELD_DND,
                if (allDay) 1 else 0, if (timed) 1 else 0,
                fromH.toByte(), fromM.toByte(), toH.toByte(), toM.toByte(),
                bool(PREF_DND_CLOSE_VIBRATION, false), bool(PREF_DND_CLOSE_MESSAGE, false),
            ),
            cmdGetField(CMD_DND, FIELD_DND)
        )
        LOG.debug("DND -> {} {}:{}-{}:{}", mode, fromH, fromM, toH, toM)
    }

    // --- ed world clocks

    /** `aa 02` = max slots; `aa 01` chunked list of `01 <len> <city UTF-16BE>` / `02 02 <tz><frac>`. */
    fun handleWorldClock(value: ByteArray) {
        if (value.size < 5 || value[2] != MODE_GET) return
        when (value[3]) {
            FIELD_WORLD_CLOCK_SLOTS -> {
                val slots = tlvInt(value) ?: return
                LOG.debug("world clock slots: {}", slots)
                if (slots > 0) editPrefs().putInt(PREF_WORLD_CLOCK_SLOTS, slots).apply()
            }
            FIELD_WORLD_CLOCK_LIST -> {
                // Chunked; the first chunk starts with a 2-byte total length like our SET does.
                val payload = chunks.collect(value, 5) ?: return
                val clocks = ArrayList<String>()
                var city = ""
                for ((f, v) in subTlvList(payload, if (payload.size >= 2) 2 else 0)) {
                    when (f) {
                        1 -> city = String(v, Charsets.UTF_16BE)
                        2 -> if (v.size >= 2) {
                            val tz = u8(v, 0)
                            val hours = if (tz >= 128) tz - 128 else -tz
                            clocks.add("$city UTC${if (hours >= 0) "+" else ""}$hours.${u8(v, 1)}")
                        }
                    }
                }
                LOG.debug("world clocks on watch: {}", clocks)
            }
        }
    }

    fun onSetWorldClocks(clocks: List<WorldClock>) {
        val body = ByteArrayOutputStream()
        val slots = devicePrefs.getInt(PREF_WORLD_CLOCK_SLOTS, Stratos4ProSupport.WORLD_CLOCK_SLOTS)
        for (clock in clocks.filter { it.enabled == true }.take(slots)) {
            val tz = TimeZone.getTimeZone(clock.timeZoneId)
            val offsetMin = tz.getOffset(System.currentTimeMillis()) / 60_000
            val hours = offsetMin / 60
            val frac = abs(offsetMin % 60) * 100 / 60
            val name = (clock.label?.takeIf { it.isNotBlank() } ?: clock.code ?: clock.timeZoneId)
                .take(Stratos4ProSupport.WORLD_CLOCK_LABEL_LEN).toByteArray(Charsets.UTF_16BE)
            body.write(byteArrayOf(0x01, name.size.toByte())); body.write(name)
            body.write(byteArrayOf(0x02, 0x02, (if (hours >= 0) 128 + hours else -hours).toByte(), frac.toByte()))
        }
        val payload = body.toByteArray()
        // Chunked on (cmd, field) rather than on a doubled mode byte, with a 2-byte length prefix.
        val data = byteArrayOf(hi(payload.size), lo(payload.size)) + payload
        queueFrames("set world clocks", chunkedFrames(CMD_WORLD_CLOCK, MODE_SET, SET_WORLD_CLOCKS, data, emptyFrame = true))
        LOG.debug("world clocks -> {}", clocks.count { it.enabled == true })
    }

    // --- c1 goals, c2 user info

    /**
     * `c2 abab` user info:
     *
     * `01 age | 02 birthday <year:2><month><day> | 03 gender (1 m, 2 f) | 04 height cm |
     *  05 VO2max | 06 VO2max time | 07 run stride cm | 08 walk stride cm | 09 weight kg |
     *  0a wear hand (1 right, 2 left)`
     *
     * Strides are height times a per-gender factor. VO2max is not sent.
     */
    private fun sendUserInfo(b: TransactionBuilder? = null) {
        val user = ActivityUser()
        val female = user.gender == ActivityUser.GENDER_FEMALE
        val height = user.heightCm.coerceIn(0, 255)
        val dob = user.dateOfBirth
        val walkStride = (height * (if (female) 0.410267f else 0.4151582f)).toInt().coerceIn(0, 255)
        val runStride = (height * (if (female) 0.5047813f else 0.5461055f)).toInt().coerceIn(0, 255)
        val leftHand = devicePrefs.getString(DeviceSettingsPreferenceConst.PREF_WEARLOCATION, "left") == "left"
        val body = byteArrayOf(
            MODE_SET, 0x01, 0x01, user.age.coerceIn(0, 255).toByte(),
            MODE_SET, 0x02, 0x04, hi(dob.year), lo(dob.year), dob.monthValue.toByte(), dob.dayOfMonth.toByte(),
            MODE_SET, 0x03, 0x01, if (female) 2 else 1,
            MODE_SET, 0x04, 0x01, height.toByte(),
            MODE_SET, 0x07, 0x01, runStride.toByte(),
            MODE_SET, 0x08, 0x01, walkStride.toByte(),
            MODE_SET, 0x09, 0x01, user.weightKg.coerceIn(0, 255).toByte(),
            MODE_SET, 0x0a, 0x01, if (leftHand) 2 else 1,
        )
        val frames = chunkedTlv(CMD_USER_INFO, body)
        if (b != null) b.writeData(frames) else queueFrames("set user info", frames)
        LOG.debug(
            "user info -> {}cm {}kg {}y {} {} hand",
            height, user.weightKg, user.age, if (female) "F" else "M", if (leftHand) "left" else "right"
        )
    }

    /**
     * `c1 abab` goals: `01 motionType | 02 goalType | 03 steps:4 | 04 kcal:2 | 05 distance m:4 | 06
     * active min:2`. motionType and goalType (daily) are 1.
     */
    private fun sendStepGoal(b: TransactionBuilder? = null) {
        val user = ActivityUser()
        val steps = user.stepsGoal
        val kcal = user.caloriesBurntGoal.coerceIn(0, 0xffff)
        val meters = user.distanceGoalMeters
        val minutes = user.activeTimeGoalMinutes.coerceIn(0, 0xffff)
        val body = byteArrayOf(MODE_SET, 0x01, 0x01, 0x01) +
                byteArrayOf(MODE_SET, 0x02, 0x01, 0x01) +
                byteArrayOf(MODE_SET, 0x03, 0x04) + be32Bytes(steps) +
                byteArrayOf(MODE_SET, 0x04, 0x02, hi(kcal), lo(kcal)) +
                byteArrayOf(MODE_SET, 0x05, 0x04) + be32Bytes(meters) +
                byteArrayOf(MODE_SET, 0x06, 0x02, hi(minutes), lo(minutes))
        val frames = chunkedTlv(CMD_MOTION_GOAL, body)
        if (b != null) b.writeData(frames) else queueFrames("set goals", frames)
        LOG.debug("goals -> {} steps, {} kcal, {} m, {} min", steps, kcal, meters, minutes)
    }

    private fun parseHhMm(value: String?, defHour: Int, defMin: Int): Pair<Int, Int> {
        val parts = value?.split(":") ?: return defHour to defMin
        if (parts.size != 2) return defHour to defMin
        return (parts[0].toIntOrNull() ?: defHour) to (parts[1].toIntOrNull() ?: defMin)
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(Stratos4ProSettings::class.java)

        /** Pause between init read groups; without it the watch drops the tail of the burst. */
        const val INIT_BURST_PACING_MS = 150

        /** Supported auto-measure intervals, minutes (one byte). */
        val MEASUREMENT_INTERVALS_MIN = intArrayOf(5, 10, 30, 60)
        const val DEFAULT_SPO2_INTERVAL_MIN = 60
        const val DEFAULT_STRESS_INTERVAL_MIN = 10
        const val PREF_STRESS_INTERVAL = "stratos4pro_stress_measurement_interval"

        const val PREF_SCI_SLEEP = "stratos4pro_scientific_sleep"
        const val PREF_WORKOUT_DETECTION = "stratos4pro_workout_detection"
        /** Comma-separated sport ids: all the watch offers (`ec aa 03`), and its menu in order. */
        const val PREF_SPORT_IDS = "stratos4pro_sport_ids"
        const val PREF_SPORT_MENU = "stratos4pro_sport_menu"
        const val PREF_SPORT_MENU_MIN = "stratos4pro_sport_menu_min"
        const val PREF_SPORT_MENU_MAX = "stratos4pro_sport_menu_max"
        const val PREF_SPO2_LOW_LIMIT_RAW = "stratos4pro_spo2_low_limit_raw"
        const val DEFAULT_SPO2_LOW_LIMIT = 90

        /** Comma-separated screen timeout options read from `a5 aa 0b`. */
        const val PREF_SCREEN_TIMEOUT_OPTIONS = "stratos4pro_screen_timeout_options"
        /** Comma-separated language ids from `a5 aa 03`. */
        const val PREF_LANGUAGES = "stratos4pro_languages"
        const val PREF_BRIGHTNESS_SCHEDULE = "stratos4pro_brightness_schedule"
        const val PREF_BRIGHTNESS_MAX_AT = "stratos4pro_brightness_max_at"
        const val PREF_BRIGHTNESS_MIN_AT = "stratos4pro_brightness_min_at"
        /** Epoch s of the last switch sent. */
        const val PREF_BRIGHTNESS_SENT = "stratos4pro_brightness_sent"

        /** Last switch at or before [now], true = highest level. Needs [maxAt] != [minAt]. */
        fun lastBrightnessSwitch(now: LocalDateTime, maxAt: LocalTime, minAt: LocalTime): Pair<LocalDateTime, Boolean> =
            listOf(maxAt to true, minAt to false)
                .flatMap { (t, max) -> listOf(now.toLocalDate().atTime(t) to max, now.toLocalDate().minusDays(1).atTime(t) to max) }
                .filter { it.first <= now }
                .maxBy { it.first }

        /** First switch after [now]. */
        fun nextBrightnessSwitch(now: LocalDateTime, maxAt: LocalTime, minAt: LocalTime): LocalDateTime =
            listOf(maxAt, minAt).minOf { t -> now.toLocalDate().atTime(t).let { if (it > now) it else it.plusDays(1) } }

        const val PREF_BRIGHTNESS_LEVELS = "stratos4pro_brightness_levels"

        // The watch keeps alert limit and flag apart; GB's off is 0. Remember the limit so
        // disabling does not write 0.
        const val PREF_HR_HIGH_LIMIT_RAW = "stratos4pro_hr_high_limit_raw"
        const val PREF_HR_LOW_LIMIT_RAW = "stratos4pro_hr_low_limit_raw"
        const val DEFAULT_HR_HIGH_LIMIT = 140
        const val DEFAULT_HR_LOW_LIMIT = 50

        // DND sub-flags the watch round-trips but Gadgetbridge has no preference for.
        const val PREF_DND_CLOSE_VIBRATION = "stratos4pro_dnd_close_vibration"
        const val PREF_DND_CLOSE_MESSAGE = "stratos4pro_dnd_close_message"
        /** Auto stress measurement toggle (GB has no generic key for it). */
        const val PREF_STRESS_ALL_DAY = DeviceSettingsPreferenceConst.PREF_HEARTRATE_STRESS_MONITORING
        /** Watch-reported world clock slot count (`ed aa 02`). */
        const val PREF_WORLD_CLOCK_SLOTS = "stratos4pro_world_clock_slots"

        /** Gadgetbridge inactivity weekday prefs, indexed by the watch's mask bit (0=Sun). */
        val WEEKDAY_PREF_KEYS = arrayOf(
            DeviceSettingsPreferenceConst.PREF_INACTIVITY_SU,
            DeviceSettingsPreferenceConst.PREF_INACTIVITY_MO,
            DeviceSettingsPreferenceConst.PREF_INACTIVITY_TU,
            DeviceSettingsPreferenceConst.PREF_INACTIVITY_WE,
            DeviceSettingsPreferenceConst.PREF_INACTIVITY_TH,
            DeviceSettingsPreferenceConst.PREF_INACTIVITY_FR,
            DeviceSettingsPreferenceConst.PREF_INACTIVITY_SA,
        )

        /** `e1` change pushes -> the GET field whose payload they mirror. */
        private val E1_PUSH_TO_FIELD = mapOf(
            PUSH_E1_HR_HIGH_CHANGED to FIELD_E1_HR_HIGH_ALERT,
            PUSH_E1_HR_LOW_CHANGED to FIELD_E1_HR_LOW_ALERT,
            PUSH_E1_HR_INTERVAL_CHANGED to FIELD_E1_HR_INTERVAL,
            PUSH_E1_SPO2_INTERVAL_CHANGED to FIELD_E1_SPO2_INTERVAL,
            PUSH_E1_STRESS_INTERVAL_CHANGED to FIELD_E1_STRESS_INTERVAL,
            PUSH_E1_CONT_HR_CHANGED to FIELD_E1_CONT_HR,
            PUSH_E1_AUTO_STRESS_CHANGED to FIELD_E1_AUTO_STRESS,
        )

        private val DEFAULT_CONFIG_NAMES = mapOf(
            1 to "sciSleep", 2 to "continuousHR", 3 to "runningCourse", 4 to "autoHR",
            5 to "jumpRope", 6 to "scientificFatLoss", 7 to "fitnessAge",
        )
    }
}
