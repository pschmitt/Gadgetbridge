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

import nodomain.freeyourgadget.gadgetbridge.model.Alarm
import nodomain.freeyourgadget.gadgetbridge.model.GPSCoordinate
import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLESingleDeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.PriorityQueue
import java.util.UUID
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt

// Frames: `01 | CMD | MODE | FIELD | LEN | VALUE...`. MODE aa get, ab set, ac push; a doubled mode
// (`aa aa`, `ab ab`) addresses all fields at once as a TLV list.

internal val UUID_SERVICE_CMD: UUID = UUID.fromString("000055ff-0000-1000-8000-00805f9b34fb")
internal val UUID_CHARACTERISTIC_CMD_READ: UUID = UUID.fromString("000035f2-0000-1000-8000-00805f9b34fb")

internal val UUID_SERVICE_DATA: UUID = UUID.fromString("000056ff-0000-1000-8000-00805f9b34fb")
internal val UUID_CHARACTERISTIC_DATA_WRITE: UUID = UUID.fromString("000034f1-0000-1000-8000-00805f9b34fb")
internal val UUID_CHARACTERISTIC_DATA_READ: UUID = UUID.fromString("000034f2-0000-1000-8000-00805f9b34fb")

internal const val PKT_HEADER: Byte = 0x01
internal const val PKT_TERMINATOR: Byte = 0xfd.toByte()

internal const val MODE_GET: Byte = 0xaa.toByte()
internal const val MODE_SET: Byte = 0xab.toByte()
internal const val MODE_REPORT: Byte = 0xac.toByte()

/** Chunk index marking the end of a chunked transfer. */
internal const val CHUNK_END = 0xfd

/** The same for files with a 2-byte chunk index. */
internal const val CHUNK_END_WIDE = 0xfdfd

/** Payload bytes per chunk: MTU 247 minus the ATT header and the chunk prefix. */
internal const val CHUNK_SLICE = 239

// --- a3 time. No mode byte: byte 2 is the field.

internal const val CMD_TIME: Byte = 0xa3.toByte()
internal const val FIELD_TIME: Byte = 0x01
internal const val FIELD_DATE_FORMAT: Byte = 0x04
internal const val FIELD_TIME_FORMAT: Byte = 0x08
internal const val FIELD_TIMEZONE: Byte = 0x10

// --- a4 device info

internal const val CMD_DEVICE_INFO: Byte = 0xa4.toByte()
internal const val FIELD_MODEL: Byte = 0x02
internal const val FIELD_FIRMWARE: Byte = 0x15
internal const val FIELD_BATTERY: Byte = 0x16

// --- a5 device config

/**
 * Device config: screen, wear, language, units, password. GET (`aa`) and SET (`ab`) ids
 * DIFFER: a GET id with `ab` hits another setting (`ab 06` language, `ab 09` weight unit).
 */
internal const val CMD_DEVICE_CONFIG: Byte = 0xa5.toByte()

// a5 GET fields (mode aa)
/** Per-feature default switches. */
internal const val FIELD_DEFAULT_CONFIG: Byte = 0x01
/** Chunked list of language ids the watch firmware ships with. */
internal const val FIELD_LANGUAGE_LIST: Byte = 0x03
/** Raise to wake. */
internal const val FIELD_RAISE_TO_WAKE: Byte = 0x06
/** Current language id. */
internal const val FIELD_LANGUAGE: Byte = 0x08
/** Screen-on duration in seconds; allowed values come from FIELD_SCREEN_TIMEOUT_LIST. */
internal const val FIELD_SCREEN_TIMEOUT: Byte = 0x09
/** Chunk-free list of uint16 screen timeout options, e.g. 5/10/15/30/60 s. */
internal const val FIELD_SCREEN_TIMEOUT_LIST: Byte = 0x0b
/** Device password config. */
internal const val FIELD_PASSWORD: Byte = 0x0e
/** Screen: brightness, level list, auto brightness. */
internal const val FIELD_SCREEN_INFO: Byte = 0x0f
/** Sound: volume, mute, media audio. */
internal const val FIELD_SOUND_VIBRATION: Byte = 0x10

// a5 push fields (mode ac)
/** Camera: 1 open, 2 shutter, 3 close. */
internal const val PUSH_CAMERA: Byte = 0x01
internal const val PUSH_FIND_PHONE: Byte = 0x02
/** Raise to wake toggled on the watch. */
internal const val PUSH_RAISE_TO_WAKE_CHANGED: Byte = 0x04
/** 1 on wrist, 2 off wrist. */
internal const val PUSH_WEAR_STATE: Byte = 0x05
internal const val PUSH_LANGUAGE_CHANGED: Byte = 0x08
internal const val PUSH_SCREEN_TIMEOUT_CHANGED: Byte = 0x09
internal const val PUSH_PASSWORD_CHANGED: Byte = 0x0a
internal const val PUSH_SCREEN_INFO_CHANGED: Byte = 0x0b
internal const val PUSH_SOUND_CHANGED: Byte = 0x0c

// a5 SET fields (mode ab)
/** Raise to wake (bool). */
internal const val SET_RAISE_TO_WAKE: Byte = 0x01
/** Camera mode: 0 exit, 1 enter. */
internal const val SET_CAMERA_STATUS: Byte = 0x03
/** Find watch: 1 ring, 0 stop. */
internal const val SET_FIND_WATCH: Byte = 0x04
/** Disconnect reminder (bool). */
internal const val SET_DISCONNECT_REMIND: Byte = 0x05
/** Language id. */
internal const val SET_LANGUAGE: Byte = 0x06
/** Distance unit: 0 metric, 1 imperial. */
internal const val SET_LENGTH_UNIT: Byte = 0x07
/** Screen-on seconds. */
internal const val SET_SCREEN_TIMEOUT: Byte = 0x08
/** Weight unit: 0 kg, 1 lb. */
internal const val SET_WEIGHT_UNIT: Byte = 0x09
/** Find-phone button on the watch (bool). */
internal const val SET_FIND_PHONE_ENABLE: Byte = 0x0a
/** Camera remote allowed (bool). */
internal const val SET_CAMERA_PERMISSION: Byte = 0x0b
/** Lock password. */
internal const val SET_PASSWORD: Byte = 0x0c
/** Screen sub-TLV {01 brightness, 02 auto, 04 touch, 05 cover off}. */
internal const val SET_SCREEN_INFO: Byte = 0x0d
/** Sound sub-TLV {01 media audio, 02 mute, 03 {01 volume}}. */
internal const val SET_SOUND_VIBRATION: Byte = 0x0e
/** Phone ringing for find phone (bool); the watch find-phone screen follows. */
internal const val SET_APP_RING_STATUS: Byte = 0x12

// --- b0 / b2 notifications

internal const val CMD_NOTIFICATION: Byte = 0xb0.toByte()
/** `b2`: notification housekeeping - `ab 05 01 <id>` delete, `ab 01 01 <z>` call remind. */
internal const val CMD_NOTIFICATION_CONFIG2: Byte = 0xb2.toByte()
/** `b2 ab 01 01 <z>` set / `b2 aa 01` get: incoming call screen on/off. */
internal const val SET_CALL_REMIND: Byte = 0x01
internal const val SET_NOTIFICATION_DELETE: Byte = 0x05
/** `b2 ac 05` reply picked from the quick reply list, `ac 06` a built-in one. */
internal const val PUSH_REPLY: Byte = 0x05
internal const val PUSH_REPLY_BUILTIN: Byte = 0x06

// --- c0 contacts and calls

internal const val CMD_CONTACTS: Byte = 0xc0.toByte()
/** Incoming call `c0 abab`: caller name and number, UTF-16BE. */
internal const val CALLER_NAME: Byte = 0x01
internal const val CALLER_NUMBER: Byte = 0x02
/** Call screen phase: 1 answered, 0 ended. */
internal const val SET_CALL_PHASE: Byte = 0x05
internal const val SET_CONTACTS_START: Byte = 0x06
/** Chunked contact list, see [Stratos4ProSupport.onSetContacts]. */
internal const val SET_CONTACTS_LIST: Byte = 0x07
/** Closes a contact list write; after the quick replies (`c0` and `b2`) it switches them on (1) or off (0). */
internal const val SET_CONTACTS_COMMIT: Byte = 0x09
/** Chunked quick replies: `c0` rejected-call SMS, `b2` notification replies. */
internal const val SET_QUICK_REPLIES: Byte = 0x0a
internal const val PUSH_REPLY_NUMBER: Byte = 0x01
internal const val PUSH_REPLY_TEXT: Byte = 0x02
/** 1 hang up, 2 mute. */
internal const val PUSH_CALL_CONTROL: Byte = 0x03

// --- c1 goals, c2 user info (both `abab` multi-field)

/** Motion goals (`abab`). */
internal const val CMD_MOTION_GOAL: Byte = 0xc1.toByte()
/** User physical info (`abab`). */
internal const val CMD_USER_INFO: Byte = 0xc2.toByte()

// --- c3 day summary

internal const val CMD_ACTIVITY_DAY: Byte = 0xc3.toByte()
/**
 * Most recent heart rate reading, and the unix time it was taken. Steps are in field 0c,
 * so these are unrelated to the step count despite sharing the c3 reply.
 */
internal const val FIELD_LAST_HEART_RATE: Byte = 0x02
internal const val FIELD_LAST_HEART_RATE_TIME: Byte = 0x03
/** `c3` field 0c sub-TLV: 01 kcal, 02 distance (m), 03 elevation, 04 sleep min, 05 steps. */
internal const val FIELD_DAY_METRICS: Byte = 0x0c
/** Same sub-TLV for workout time; the per-minute frames sum to 0c + 09. */
internal const val FIELD_DAY_WORKOUT_METRICS: Byte = 0x09
internal const val SUBFIELD_KCAL: Int = 0x01
internal const val SUBFIELD_DISTANCE: Int = 0x02
internal const val SUBFIELD_STEPS: Int = 0x05

// --- c4 health reminders

/** Health reminders. Field 01 = sedentary/activity reminder, field 03 = low SpO2 alert. */
internal const val CMD_HEALTH_REMINDERS: Byte = 0xc4.toByte()
internal const val FIELD_SEDENTARY: Byte = 0x01
/**
 * `c4 ab 02 01 <en>` scientific sleep: the recording the sleep file (`c6 aa 04`) is read
 * from. Default on. Reads back as `c4` field 02 and `a5` default-config bit 1.
 */
internal const val SET_C4_SCI_SLEEP: Byte = 0x02
internal const val FIELD_C4_SCI_SLEEP: Byte = 0x02
/**
 * `c4 ab 03 01 <en>` continuous SpO2. `c4` SET ids differ from GET ids (`ab 02` is sci
 * sleep); reads back via `e1 aa 11`.
 */
internal const val SET_C4_CONT_SPO2: Byte = 0x03
/** `c4 ab 04 02 <enable><limit>` low SpO2 alert; read back with `c4 aa 03`. */
internal const val SET_C4_SPO2_ALERT: Byte = 0x04
internal const val FIELD_SPO2_ALERT: Byte = 0x03

// --- c5 / c6 history

/** Fitness frame index: `aa 01 08 <start><end>` -> frame count; `ac ac` = new data push. */
internal const val CMD_FITNESS_INDEX: Byte = 0xc5.toByte()

/** Fitness frames, sleep file, live per-minute push and stress push. */
internal const val CMD_HEALTH_STREAM: Byte = 0xc6.toByte()
/** GET: history frame `aa 01 0a <start><end><idx:2>`; PUSH: live minute `ac 01`. */
internal const val FIELD_FRAME_MINUTE: Byte = 0x01
/** PUSH: single stress reading `ac 02 05 <unix:4><value:1>`. */
internal const val FIELD_STREAM_STRESS: Byte = 0x02
/** GET: sleep file `aa 04 08 <start><end>`, 2-byte chunk index, `fdfd` end. */
internal const val FIELD_SLEEP_FILE: Byte = 0x04

// --- c7 alarms

internal const val CMD_ALARM: Byte = 0xc7.toByte()
/** `c7 aa 0a` -> `<len><maxCount><capabilityBits>`; old firmware answers header only. */
internal const val FIELD_ALARM_CAPS: Byte = 0x0a
// Per-slot alarm fields, each `<idx>` first.
internal const val FIELD_ALARM_INDEX: Byte = 0x01
/** Weekday mask, bit 0 = Sun. */
internal const val FIELD_ALARM_DAYS: Byte = 0x02
internal const val FIELD_ALARM_ENABLED: Byte = 0x03
internal const val FIELD_ALARM_TIME: Byte = 0x04
internal const val FIELD_ALARM_NAME: Byte = 0x05
/** 0 ring + vibrate, 1 ring, 2 vibrate. Set only: not in the list reply. */
internal const val FIELD_ALARM_SOUND: Byte = 0x06

/** GB alarm sound -> watch mode. The watch has no silent mode, so Off vibrates. */
internal fun alarmSoundMode(soundCode: Int): Byte = when (Alarm.ALARM_SOUND.entries.getOrNull(soundCode)) {
    Alarm.ALARM_SOUND.TONE -> 1
    Alarm.ALARM_SOUND.VIBRATION, Alarm.ALARM_SOUND.OFF -> 2
    else -> 0
}

// --- c9 workout detection

internal const val CMD_WORKOUT_DETECTION: Byte = 0xc9.toByte()
/** `ab 01 01 <on>`, read back with `aa 01`. */
internal const val FIELD_WORKOUT_DETECTION: Byte = 0x01

// --- ca morning report

internal const val CMD_MORNING_REPORT: Byte = 0xca.toByte()
/** `ab 01 01 <on>`, read back with `aa 01`. */
internal const val FIELD_MORNING_ENABLED: Byte = 0x01
/** Chunked `<len> [<id> 01 <on>]*`, both ways. */
internal const val FIELD_MORNING_ITEMS: Byte = 0x02
/** Item pref values; watch id = index + 1. */
internal val MORNING_ITEMS = listOf("battery", "sleep", "steps", "yesterdays_activity", "cycle_tracking", "event")

/** Item write: [selected] on, in their order, then the rest off. */
internal fun morningItemsFrames(selected: List<String>): List<ByteArray> {
    val on = selected.filter { it in MORNING_ITEMS }.distinct()
    val tlv = (on + (MORNING_ITEMS - on.toSet())).flatMap { listOf(MORNING_ITEMS.indexOf(it) + 1, 1, if (it in on) 1 else 0) }
    return chunkedFrames(CMD_MORNING_REPORT, MODE_SET, FIELD_MORNING_ITEMS, byteArrayOf(tlv.size.toByte()) + tlv.map { it.toByte() })
}

/** Items on in an item reply, in the watch's order; null for any other frame. */
internal fun parseMorningItems(frame: ByteArray): List<String>? {
    if (frame.size < 6 || frame[2] == MODE_SET || frame[3] != FIELD_MORNING_ITEMS || frame[4] != 0x00.toByte()) return null
    val end = minOf(frame.size, 6 + u8(frame, 5))
    return (6 until end - 2 step 3).filter { frame[it + 2].toInt() == 1 }.mapNotNull { MORNING_ITEMS.getOrNull(u8(frame, it) - 1) }
}

// --- ec sport menu

/** Sport menu: `aa 01` max, `aa 02` min, `aa 03` all ids, `aa 04` / `ab 04` / `ac 04` the menu. */
internal const val CMD_SPORT_MENU: Byte = 0xec.toByte()
internal const val FIELD_SPORT_MENU_MAX: Byte = 0x01
internal const val FIELD_SPORT_MENU_MIN: Byte = 0x02
internal const val FIELD_SPORT_IDS: Byte = 0x03
/** Chunked `<n+1> <n> <id>*`, both ways. Ids are the `e8` sport types. */
internal const val FIELD_SPORT_MENU: Byte = 0x04

internal fun sportMenuFrames(ids: List<Int>): List<ByteArray> =
    chunkedFrames(CMD_SPORT_MENU, MODE_SET, FIELD_SPORT_MENU, byteArrayOf((ids.size + 1).toByte(), ids.size.toByte()) + ids.map { it.toByte() })

/** Ids in an assembled `aa 03` / `aa 04` body; null if short. */
internal fun parseSportIds(body: ByteArray): List<Int>? {
    if (body.size < 2 || body.size < 2 + u8(body, 1)) return null
    return (2 until 2 + u8(body, 1)).map { u8(body, it) }
}

// --- d1 event log

internal const val CMD_EVENT_LOG: Byte = 0xd1.toByte()
/** GET: whole log (~8 days), 2-byte chunk index, `fdfd` end. */
internal const val FIELD_EVENT_LOG: Byte = 0x04

// --- e0 weather

internal const val CMD_WEATHER: Byte = 0xe0.toByte()
/** 0 C, 1 F. */
internal const val SET_TEMPERATURE_UNIT: Byte = 0x02

// --- e1 health monitor

/** Health monitoring cluster: auto-measure toggles, HR alerts, stress history, live HR. */
internal const val CMD_HEALTH_MONITOR: Byte = 0xe1.toByte()

// e1 GET fields (mode aa)
/** Chunked stress history file: `aa 02 08 <start:4><end:4>`. */
internal const val FIELD_STRESS_HISTORY: Byte = 0x02
internal const val FIELD_E1_HR_INTERVAL: Byte = 0x0b
internal const val FIELD_E1_SPO2_INTERVAL: Byte = 0x0c
internal const val FIELD_E1_STRESS_INTERVAL: Byte = 0x0d
internal const val FIELD_E1_CONT_HR: Byte = 0x10
internal const val FIELD_E1_CONT_SPO2: Byte = 0x11
internal const val FIELD_E1_AUTO_STRESS: Byte = 0x12
internal const val FIELD_E1_HR_HIGH_ALERT: Byte = 0x15
internal const val FIELD_E1_HR_LOW_ALERT: Byte = 0x16
// e1 push fields (mode ac)
/** Live HR during a measurement: `<n*5> [<hr:1><unix:4>]*`. */
internal const val FIELD_E1_HR_REPORT: Byte = 0x03
// e1 pushes for settings changed on the watch; payloads mirror the GET replies.
internal const val PUSH_E1_HR_HIGH_CHANGED: Byte = 0x05
internal const val PUSH_E1_HR_LOW_CHANGED: Byte = 0x06
internal const val PUSH_E1_HR_INTERVAL_CHANGED: Byte = 0x07
internal const val PUSH_E1_SPO2_INTERVAL_CHANGED: Byte = 0x08
internal const val PUSH_E1_STRESS_INTERVAL_CHANGED: Byte = 0x09
internal const val PUSH_E1_CONT_HR_CHANGED: Byte = 0x0c
internal const val PUSH_E1_AUTO_STRESS_CHANGED: Byte = 0x0d
// e1 SET fields (mode ab). `ab 01` is left out: no GET for it, so its effect cannot be
// checked.
internal const val SET_E1_CONT_HR: Byte = 0x02
internal const val SET_E1_HR_HIGH_ALERT: Byte = 0x03
internal const val SET_E1_HR_LOW_ALERT: Byte = 0x04
internal const val SET_E1_AUTO_STRESS: Byte = 0x07
internal const val SET_E1_HR_INTERVAL: Byte = 0x0b
internal const val SET_E1_SPO2_INTERVAL: Byte = 0x0c
internal const val SET_E1_STRESS_INTERVAL: Byte = 0x0d
/** Trigger a one-off HR/SpO2/stress measurement. */
internal const val SET_E1_ONE_CLICK_MEASURE: Byte = 0x12

// --- e2 music

internal const val CMD_MUSIC_CONTROL: Byte = 0xe2.toByte()
/** Remote button `ac 01 02 <code> 00`. */
internal const val PUSH_MUSIC_BUTTON: Byte = 0x01

// --- e8 workouts

/** Workout records: `aa 01` list, `aa 02` summary, `aa 03` detail, `aa 04` km split, `aa 09` GPS file. */
internal const val CMD_WORKOUT: Byte = 0xe8.toByte()
internal const val FIELD_WORKOUT_LIST: Byte = 0x01
internal const val FIELD_WORKOUT_SUMMARY: Byte = 0x02
internal const val FIELD_WORKOUT_DETAIL: Byte = 0x03
internal const val FIELD_WORKOUT_SPLIT: Byte = 0x04
internal const val FIELD_WORKOUT_GPS: Byte = 0x09

// --- e9 GPS

/** GPS data; only the navigation routes are used here. */
internal const val CMD_GPS: Byte = 0xe9.toByte()
/** Chunked route, see [routeFrames]. */
internal const val SET_ROUTE: Byte = 0x05
/** `ac 05 01 <status>` after a route write: 1 saved, 2 already on the watch. */
internal const val PUSH_ROUTE_STATUS: Byte = 0x05
/** The watch keeps 10 routes of 72 points, dropping the oldest past 10. */
internal const val ROUTE_MAX_POINTS = 72
/** UTF-16 units. */
internal const val ROUTE_MAX_NAME = 64

/**
 * Route write, body `01 <len:2> <time s>`, `02 <len:2> <distance m>`, `03 <len:2> <name
 * UTF-16BE>`, `04 <len:2> (<lat 1e-7> <lon 1e-7>)*`, big-endian. Same time, distance and name =
 * the same route. A body over 832 bytes is dropped with no reply; the limits above stay under.
 */
internal fun routeFrames(timeS: Long, distanceM: Int, name: String, points: List<GPSCoordinate>): List<ByteArray> {
    val body = ByteArrayOutputStream()
    fun tlv(tag: Int, len: Int) = body.write(byteArrayOf(tag.toByte(), hi(len), lo(len)))
    val nameBytes = name.take(ROUTE_MAX_NAME).toByteArray(Charsets.UTF_16BE)
    tlv(1, 4); body.write(be32Bytes(timeS.toInt()))
    tlv(2, 4); body.write(be32Bytes(distanceM))
    tlv(3, nameBytes.size); body.write(nameBytes)
    tlv(4, points.size * 8)
    for (p in points) {
        body.write(be32Bytes((p.latitude * 1e7).roundToInt()))
        body.write(be32Bytes((p.longitude * 1e7).roundToInt()))
    }
    return chunkedFrames(CMD_GPS, MODE_SET, SET_ROUTE, body.toByteArray())
}

/**
 * The [max] points that best keep the shape: Douglas-Peucker, always splitting the segment whose
 * farthest point is farthest off, until [max] are kept.
 */
internal fun <T : GPSCoordinate> simplifyRoute(points: List<T>, max: Int): List<T> {
    if (points.size <= max) return points
    class Split(val from: Int, val to: Int, val at: Int, val off: Double)
    fun split(from: Int, to: Int): Split? {
        if (to - from < 2) return null
        val a = points[from]
        val b = points[to]
        // Flat projection around a: fine for the distances between route points.
        val k = cos(Math.toRadians(a.latitude))
        val bx = (b.longitude - a.longitude) * k
        val by = b.latitude - a.latitude
        val len2 = bx * bx + by * by
        var at = from + 1
        var off = -1.0
        for (i in from + 1 until to) {
            val px = (points[i].longitude - a.longitude) * k
            val py = points[i].latitude - a.latitude
            val t = if (len2 == 0.0) 0.0 else ((px * bx + py * by) / len2).coerceIn(0.0, 1.0)
            val d = hypot(px - t * bx, py - t * by)
            if (d > off) { off = d; at = i }
        }
        return Split(from, to, at, off)
    }
    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.size - 1] = true
    val queue = PriorityQueue<Split>(points.size, compareByDescending { it.off })
    split(0, points.size - 1)?.let(queue::add)
    for (kept in 2 until max) {
        val s = queue.poll() ?: break
        keep[s.at] = true
        split(s.from, s.at)?.let(queue::add)
        split(s.at, s.to)?.let(queue::add)
    }
    return points.filterIndexed { i, _ -> keep[i] }
}

// --- ea do not disturb

/** Do not disturb: GET/SET field 01, 8 bytes. */
internal const val CMD_DND: Byte = 0xea.toByte()
internal const val FIELD_DND: Byte = 0x01

// --- eb classic BT

/** Classic BT (BT3) control: `aa 01` state, `ab 02` on/off, `ab 01` start pairing. */
internal const val CMD_BT3: Byte = 0xeb.toByte()
internal const val FIELD_BT3_STATE: Byte = 0x01
internal const val SET_BT3_PAIR: Byte = 0x01
internal const val SET_BT3_SWITCH: Byte = 0x02

// --- ed world clocks

/** World clock: `aa 01` list, `aa 02` max count, `ab 01` chunked set. */
internal const val CMD_WORLD_CLOCK: Byte = 0xed.toByte()
internal const val FIELD_WORLD_CLOCK_LIST: Byte = 0x01
internal const val FIELD_WORLD_CLOCK_SLOTS: Byte = 0x02
internal const val SET_WORLD_CLOCKS: Byte = 0x01

// --- f9 file service

/** Files on the watch: paths start with a drive, `D:` = `/NAND:`, `A:` = `/LITTLEFS:A`. */
internal const val CMD_FILE: Byte = 0xf9.toByte()
/** GET: list a folder. */
internal const val FILE_LIST: Byte = 0x02
/** SET: read a file; the data comes as [PUSH_FILE_DATA]. */
internal const val FILE_READ: Byte = 0x05
internal const val PUSH_FILE_DATA: Byte = 0x02

/** `01 f9 MODE FIELD <len> <path>` */
internal fun fileRequest(mode: Byte, field: Byte, path: String): ByteArray {
    val p = path.toByteArray(Charsets.UTF_8)
    return byteArrayOf(PKT_HEADER, CMD_FILE, mode, field, p.size.toByte()) + p
}

// --- fb temperature

/** Body temperature params / test / history. */
internal const val CMD_TEMPERATURE: Byte = 0xfb.toByte()

/** Guard rails so stray protocol bytes never land in the heart rate chart. */
internal const val MIN_PLAUSIBLE_BPM = 25
internal const val MAX_PLAUSIBLE_BPM = 250

// --- Frame builders

/** Build a "get all fields" request: 01 CMD MODE MODE (mode byte doubled). */
internal fun cmdGetAll(cmd: Byte): ByteArray = byteArrayOf(PKT_HEADER, cmd, MODE_GET, MODE_GET)

/** Build a "get one field" request: 01 CMD MODE FIELD. */
internal fun cmdGetField(cmd: Byte, field: Byte): ByteArray = byteArrayOf(PKT_HEADER, cmd, MODE_GET, field)

/** Single-field SET: `01 CMD ab FIELD LEN VALUE`. */
internal fun cmdSetField(cmd: Byte, field: Byte, vararg value: Byte): ByteArray =
    byteArrayOf(PKT_HEADER, cmd, MODE_SET, field, value.size.toByte()) + value

/** `01 CMD MODE FIELD 08 <start:4><end:4>` */
internal fun rangeCmd(cmd: Byte, mode: Byte, field: Byte, start: Long, end: Long): ByteArray =
    byteArrayOf(PKT_HEADER, cmd, mode, field, 0x08) + be32Bytes(start.toInt()) + be32Bytes(end.toInt())

/**
 * Chunked write: `01 CMD <tag0> <tag1> <index> <slice>` per frame, closed by
 * `01 CMD <tag0> <tag1> fd <xor>`, xor over every byte of the data frames (the repeated prefixes
 * cancel out in pairs). [index] builds the index bytes of a frame from its number, body offset
 * and slice length. [emptyFrame]: an empty body still gets one data frame.
 */
internal fun chunkedFrames(
    cmd: Byte,
    tag0: Byte,
    tag1: Byte,
    body: ByteArray,
    maxSlice: Int = CHUNK_SLICE,
    emptyFrame: Boolean = false,
    index: (idx: Int, off: Int, len: Int) -> ByteArray = { idx, _, _ -> byteArrayOf(idx.toByte()) },
): List<ByteArray> {
    val frames = ArrayList<ByteArray>()
    var x = 0
    var off = 0
    var idx = 0
    while (off < body.size || (emptyFrame && idx == 0)) {
        val end = minOf(off + maxSlice, body.size)
        val frame = byteArrayOf(PKT_HEADER, cmd, tag0, tag1) + index(idx, off, end - off) + body.copyOfRange(off, end)
        for (b in frame) x = x xor (b.toInt() and 0xff)
        frames.add(frame)
        off = end
        idx++
    }
    frames.add(byteArrayOf(PKT_HEADER, cmd, tag0, tag1, PKT_TERMINATOR, x.toByte()))
    return frames
}

/** Multi-field SET `01 CMD abab <idx> <TLVs>`, chunked when longer than one frame. */
internal fun chunkedTlv(cmd: Byte, body: ByteArray): List<ByteArray> = chunkedFrames(cmd, MODE_SET, MODE_SET, body)

internal fun TransactionBuilder.writeData(frames: List<ByteArray>): TransactionBuilder {
    for (frame in frames) write(UUID_CHARACTERISTIC_DATA_WRITE, *frame)
    return this
}

internal fun TransactionBuilder.writeData(vararg frames: ByteArray): TransactionBuilder = writeData(frames.asList())

internal fun AbstractBTLESingleDeviceSupport.queueFrames(label: String, frames: List<ByteArray>) {
    createTransactionBuilder(label).writeData(frames).queue()
}

internal fun AbstractBTLESingleDeviceSupport.queueFrames(label: String, vararg frames: ByteArray) =
    queueFrames(label, frames.asList())

internal fun AbstractBTLESingleDeviceSupport.queueGetField(cmd: Byte, field: Byte) =
    queueFrames("get ${hex(cmd)}/${hex(field)}", cmdGetField(cmd, field))

/** `01 b2 ab 05 01 <id>`: dismiss notification [watchId] on the watch. */
internal fun deleteNotificationFrame(watchId: Int): ByteArray =
    cmdSetField(CMD_NOTIFICATION_CONFIG2, SET_NOTIFICATION_DELETE, watchId.toByte())

/** Quick reply list: `<total:2> [<len><UTF-16BE>]*`, each text cut to [maxLen] chars. */
internal fun quickReplyBody(messages: List<String>, maxLen: Int): ByteArray {
    val list = ByteArrayOutputStream()
    for (m in messages) {
        val bytes = m.take(maxLen).toByteArray(Charsets.UTF_16BE)
        list.write(bytes.size); list.write(bytes)
    }
    val p = list.toByteArray()
    return byteArrayOf(hi(p.size), lo(p.size)) + p
}

private fun replyAction(n: NotificationSpec): NotificationSpec.Action? =
    n.attachedActions?.firstOrNull { it?.isReply == true }

/**
 * Number GB answers by SMS itself: its own SMS reply action, which has no handle. Null for app
 * replies and for senders that are not a phone number.
 */
internal fun smsReplyNumber(n: NotificationSpec): String? {
    val a = replyAction(n) ?: return null
    if (a.handle != 0L || a.type != NotificationSpec.Action.TYPE_SYNTHETIC_REPLY_PHONENR) return null
    return n.phoneNumber?.takeIf { s -> s.any { it.isDigit() } && s.all { it.isDigit() || it in "+ -()" } }
}

/** The reply action's handle, cut to 32 bits like GB's own lookup; SMS: the GB id; 0 = no reply. */
internal fun replyHandle(n: NotificationSpec): Int {
    val a = replyAction(n) ?: return 0
    return when {
        a.handle != 0L -> a.handle.toInt()
        smsReplyNumber(n) != null -> n.id
        else -> 0
    }
}

/**
 * `b2 ac 05/06` body `<n:2><handle:4><text>`, n = 4 + text bytes. `ac 05` text is UTF-16BE; the
 * `ac 06` built-in one ([builtIn]) is 3 byte BE code points, 00 padded.
 */
internal fun parseNotificationReply(p: ByteArray, builtIn: Boolean = false): Pair<Long, String>? {
    val n = if (p.size >= 6) u16(p, 0) else return null
    if (n < 4 || p.size < 2 + n) return null
    val text = if (builtIn) {
        buildString {
            for (i in 6 until n step 3) {
                val cp = (u8(p, i) shl 16) or u16(p, i + 1)
                if (cp != 0 && Character.isValidCodePoint(cp)) appendCodePoint(cp)
            }
        }
    } else {
        String(p, 6, n - 4, Charsets.UTF_16BE).trimEnd(Char(0))
    }
    return be32(p, 2) to text
}

// --- Parsing

internal fun u8(b: ByteArray, off: Int): Int = b[off].toInt() and 0xff

internal fun u16(b: ByteArray, off: Int): Int = (u8(b, off) shl 8) or u8(b, off + 1)

internal fun be32(b: ByteArray, off: Int): Long {
    var v = 0L
    for (k in 0 until 4) v = (v shl 8) or (b[off + k].toLong() and 0xff)
    return v
}

/** Unsigned big-endian value of the whole array (up to 4 bytes). */
internal fun ByteArray.beInt(): Int {
    var v = 0
    for (b in this) v = (v shl 8) or (b.toInt() and 0xff)
    return v
}

/** Big-endian 4-byte unix timestamp. */
internal fun be32Bytes(v: Int): ByteArray =
    byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

internal fun hi(v: Int): Byte = ((v ushr 8) and 0xff).toByte()
internal fun lo(v: Int): Byte = (v and 0xff).toByte()

internal fun xor(data: ByteArray): Byte {
    var x = 0
    for (b in data) x = x xor (b.toInt() and 0xff)
    return x.toByte()
}

/** `HH:mm` with ASCII digits in any locale; GB parses these prefs with Locale.ROOT. */
internal fun hhMm(hour: Int, minute: Int): String = String.format(Locale.ROOT, "%02d:%02d", hour, minute)

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

internal fun hex(b: Byte): String = Integer.toHexString(b.toInt() and 0xff)

/** Value of a plain `01 CMD MODE FIELD LEN VALUE` frame as a big-endian int, or null. */
internal fun tlvInt(frame: ByteArray): Int? {
    if (frame.size < 5) return null
    val len = frame[4].toInt() and 0xff
    if (len == 0 || len > 4 || frame.size < 5 + len) return null
    return frame.copyOfRange(5, 5 + len).beInt()
}

/** Parse repeating `<MODE> <field> <len> <value...>` TLV entries starting at [start]. */
internal fun parseTlv(value: ByteArray, start: Int): Map<Byte, ByteArray> {
    val out = HashMap<Byte, ByteArray>()
    var i = start
    while (i + 3 <= value.size) {
        // mode byte (aa/ab/ac), field, len
        val field = value[i + 1]
        val len = value[i + 2].toInt() and 0xff
        val from = i + 3
        val to = from + len
        if (to > value.size) break
        out[field] = value.copyOfRange(from, to)
        i = to
    }
    return out
}

/** `<field><len><value>...` from [start], in order and with repeats; stops at a truncated entry. */
internal fun subTlvList(blob: ByteArray, start: Int = 0): List<Pair<Int, ByteArray>> {
    val out = ArrayList<Pair<Int, ByteArray>>()
    var i = start
    while (i + 2 <= blob.size) {
        val field = blob[i].toInt() and 0xff
        val len = blob[i + 1].toInt() and 0xff
        if (i + 2 + len > blob.size) break
        out.add(field to blob.copyOfRange(i + 2, i + 2 + len))
        i += 2 + len
    }
    return out
}

/** `<field><len><value>...` -> map, empty values dropped. `a5` blobs nest these. */
internal fun parseSubTlv(blob: ByteArray): Map<Int, ByteArray> =
    subTlvList(blob).filter { it.second.isNotEmpty() }.toMap()
