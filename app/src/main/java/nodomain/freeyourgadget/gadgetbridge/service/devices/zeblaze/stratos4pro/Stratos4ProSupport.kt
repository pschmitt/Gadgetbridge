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

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventCameraRemote
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventFindPhone
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventMusicControl
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventNotificationControl
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo
import nodomain.freeyourgadget.gadgetbridge.devices.GpxRouteInstallHandler
import nodomain.freeyourgadget.gadgetbridge.devices.zeblaze.stratos4pro.Stratos4ProGpxRouteInstallHandler
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.Alarm
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState
import nodomain.freeyourgadget.gadgetbridge.model.CallSpec
import nodomain.freeyourgadget.gadgetbridge.model.CannedMessagesSpec
import nodomain.freeyourgadget.gadgetbridge.model.Contact
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService
import nodomain.freeyourgadget.gadgetbridge.model.MusicSpec
import nodomain.freeyourgadget.gadgetbridge.model.MusicStateSpec
import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec
import nodomain.freeyourgadget.gadgetbridge.model.NotificationType
import nodomain.freeyourgadget.gadgetbridge.model.RecordedDataTypes
import nodomain.freeyourgadget.gadgetbridge.model.WorldClock
import nodomain.freeyourgadget.gadgetbridge.model.weather.Weather
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLESingleDeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder
import nodomain.freeyourgadget.gadgetbridge.util.GB
import nodomain.freeyourgadget.gadgetbridge.util.LimitedQueue
import nodomain.freeyourgadget.gadgetbridge.util.MediaManager
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar
import java.util.GregorianCalendar
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Zeblaze Stratos 4 Pro. Frames: `01 | CMD | MODE | FIELD | LEN | VALUE...`, see
 * Stratos4ProProtocol.kt. Traffic runs on DATA 0x56ff (write 34f1, notify 34f2); CMD 0x55ff is
 * subscribed too.
 *
 * Feature areas live in helpers: [Stratos4ProHistorySync], [Stratos4ProWorkoutSync],
 * [Stratos4ProSettings], [Stratos4ProCalls] (incl. classic BT call audio), [Stratos4ProLights],
 * [Stratos4ProRecordings], [Stratos4ProReplyIcon].
 * This class keeps the link, the dispatch and the small features.
 */
class Stratos4ProSupport : AbstractBTLESingleDeviceSupport(LOG) {
    init {
        addSupportedService(UUID_SERVICE_CMD)
        addSupportedService(UUID_SERVICE_DATA)
        // Debug log service: the LEDs and the reply app icon.
        addSupportedService(Stratos4ProLights.UUID_SERVICE_LOG)
    }

    /** Main looper; every helper posts here, so dispose clears them all. */
    internal val handler = Handler(Looper.getMainLooper())
    internal val chunks = Stratos4ProChunkAssembler()
    internal val history = Stratos4ProHistorySync(this)
    internal val workouts = Stratos4ProWorkoutSync(this)
    internal val settings = Stratos4ProSettings(this)
    internal val calls = Stratos4ProCalls(this)
    private val recordings = Stratos4ProRecordings(this)

    /** LEDs. Never drop the link for the wake-lock release mid-sync. */
    private val lights = Stratos4ProLights(this) { history.isBusy() || recordings.isBusy() }
    private val replyIcon = Stratos4ProReplyIcon(this, lights)

    /** Phone numbers for SMS replies keyed by the handle sent to the watch. */
    private val smsReplyNumbers = LimitedQueue<Int, String>(16)

    private var lastMusicSpec: MusicSpec? = null
    private var lastMusicPlaying = false

    /** Music frames the watch has on this link; media apps repeat unchanged updates. */
    private var lastMusicFrames: List<ByteArray>? = null

    override fun useAutoConnect(): Boolean {
        return true
    }

    override fun initializeDevice(builder: TransactionBuilder): TransactionBuilder {
        builder.setDeviceState(GBDevice.State.INITIALIZING)

        // The support is reused across reconnects: drop the old link's timers, each helper
        // restarts its own below. A sync cut by a disconnect is gone on the watch too.
        handler.removeCallbacksAndMessages(null)
        history.onConnect()
        workouts.onConnect()
        recordings.onConnect()
        replyIcon.onConnect()
        chunks.clear()

        builder.requestMtu(247)

        // Subscribe to both notify characteristics (CMD 0x35f2 and DATA 0x34f2).
        builder.notify(UUID_CHARACTERISTIC_CMD_READ, true)
        builder.notify(UUID_CHARACTERISTIC_DATA_READ, true)

        builder.writeData(
            // Device info (firmware etc.).
            cmdGetAll(CMD_DEVICE_INFO),
            // Enable the watch's "find phone" feature (otherwise the watch button is greyed out).
            cmdSetField(CMD_DEVICE_CONFIG, SET_FIND_PHONE_ENABLE, 0x01),
            // Call reminder on: while off the watch acks caller frames but shows nothing, and
            // another app can leave it off. Read back so the log shows what stuck.
            cmdSetField(CMD_NOTIFICATION_CONFIG2, SET_CALL_REMIND, 0x01),
            cmdGetField(CMD_NOTIFICATION_CONFIG2, SET_CALL_REMIND),
            // The watch's alarms, so ones edited on the watch appear in Gadgetbridge (read-only sync).
            cmdGetAll(CMD_ALARM),
        )

        if (GBApplication.getPrefs().syncTime()) {
            setTime(builder)
        }
        settings.sendTimeFormat(builder)

        // Today's step total.
        builder.writeData(cmdGetAll(CMD_ACTIVITY_DAY))

        // Current media, so the music screen is not empty until the next play/pause.
        val mediaManager = MediaManager(context)
        mediaManager.refresh()
        lastMusicFrames = null
        mediaManager.bufferMusicSpec?.let { spec ->
            lastMusicSpec = spec
            lastMusicPlaying = mediaManager.bufferMusicStateSpec?.state?.toInt() == MusicStateSpec.STATE_PLAYING
            changedMusicFrames(spec.artist, spec.track)?.let { builder.writeData(it) }
        }

        // Sedentary schedule, so the settings show the watch's values.
        builder.writeData(cmdGetAll(CMD_HEALTH_REMINDERS))
        settings.writeInitialReads(builder)

        calls.onConnect()

        // No login handshake: the watch takes commands once notifications are on.
        builder.setDeviceState(GBDevice.State.INITIALIZED)

        // Settings the watch derives kcal/distance from, then the history sync. On the init builder
        // so they run after MTU + notify.
        settings.writeOnConnect(builder)
        history.fetch(builder, workouts = true)

        // Last, so a strobe cut by a dropped link restarts after the rest.
        lights.onConnect(builder)

        return builder
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): Boolean {
        when (characteristic.uuid) {
            UUID_CHARACTERISTIC_DATA_READ, UUID_CHARACTERISTIC_CMD_READ -> {
                handlePacket(value)
                return true
            }

            Stratos4ProLights.UUID_CHARACTERISTIC_LOG_NOTIFY -> {
                lights.onNotify(value)
                return true
            }
        }
        return super.onCharacteristicChanged(gatt, characteristic, value)
    }

    private fun handlePacket(value: ByteArray) {
        if (value.size < 3 || value[0] != PKT_HEADER) {
            LOG.debug("Ignoring unexpected packet: {}", value.toHex())
            return
        }
        // Continuation/terminator packet: 01 CMD MODE fd <cksum>
        if (value.size >= 4 && value[3] == PKT_TERMINATOR) {
            return
        }
        when (value[1]) {
            CMD_DEVICE_INFO -> handleDeviceInfo(value)
            CMD_ACTIVITY_DAY -> history.handleDaySummary(value)
            CMD_MUSIC_CONTROL -> handleMusicControl(value)
            CMD_ALARM -> handleAlarmData(value)
            CMD_HEALTH_REMINDERS -> settings.handleHealthReminders(value)
            // a5 carries pushes (MODE_REPORT: camera, find phone, wear) and GET replies: split on
            // mode.
            CMD_DEVICE_CONFIG -> if (value[2] == MODE_REPORT) {
                handleDeviceControl(value)
            } else {
                settings.handleDeviceConfig(value)
            }
            CMD_HEALTH_MONITOR -> settings.handleHealthMonitor(value)
            CMD_HEALTH_STREAM -> history.handleHealthStream(value)
            CMD_FITNESS_INDEX -> history.handleFitnessIndex(value)
            CMD_DND -> settings.handleDnd(value)
            CMD_WORLD_CLOCK -> settings.handleWorldClock(value)
            CMD_BT3 -> calls.handleBt3(value)
            CMD_WORKOUT -> workouts.handleWorkout(value)
            CMD_GPS -> handleGps(value)
            CMD_CONTACTS -> calls.handleContacts(value)
            // `aa 01 01 <z>` = call reminder state, `ac 05/06` = reply, else a bare SET ack.
            CMD_NOTIFICATION_CONFIG2 ->
                if (value.size >= 6 && value[2] == MODE_GET && value[3] == SET_CALL_REMIND) {
                    LOG.debug("call reminder: {}", value[5].toInt() == 1)
                } else if (value.size >= 5 && value[2] == MODE_REPORT && (value[3] == PUSH_REPLY || value[3] == PUSH_REPLY_BUILTIN)) {
                    handleNotificationReply(value)
                } else {
                    LOG.debug("notification ack: {}", value.toHex())
                }
            CMD_NOTIFICATION -> LOG.debug("notification ack: {}", value.toHex())
            CMD_TIME -> handleTime(value)
            CMD_TEMPERATURE -> LOG.debug("temperature reply: {}", value.toHex())
            CMD_EVENT_LOG -> history.handleEventLog(value)
            CMD_WORKOUT_DETECTION -> settings.handleWorkoutDetection(value)
            CMD_MORNING_REPORT -> settings.handleMorningReport(value)
            CMD_SPORT_MENU -> settings.handleSportMenu(value)
            CMD_FILE -> recordings.handle(value)
            else -> LOG.debug("Unhandled cmd 0x{}: {}", hex(value[1]), value.toHex())
        }
    }

    override fun onSendConfiguration(config: String) {
        when (config) {
            Stratos4ProLights.PREF_WHITE_TOGGLE -> lights.toggle(Stratos4ProLights.Light.WHITE)
            Stratos4ProLights.PREF_RED_TOGGLE -> lights.toggle(Stratos4ProLights.Light.RED)
            Stratos4ProLights.PREF_WHITE_STROBE_MS -> lights.onStrobeSettingChanged(Stratos4ProLights.Light.WHITE)
            Stratos4ProLights.PREF_RED_STROBE_MS -> lights.onStrobeSettingChanged(Stratos4ProLights.Light.RED)
            Stratos4ProLights.PREF_KEEPALIVE_S -> lights.onKeepaliveSettingChanged()
            else -> if (!calls.onSendConfiguration(config) && !settings.onSendConfiguration(config) &&
                !replyIcon.onSendConfiguration(config)) {
                super.onSendConfiguration(config)
            }
        }
    }

    override fun onFetchRecordedData(dataTypes: Int) {
        if (dataTypes and RecordedDataTypes.TYPE_AUDIO_REC != 0) {
            recordings.fetch()
            if (dataTypes == RecordedDataTypes.TYPE_AUDIO_REC) return
        }
        // Today's totals + last HR, then the per-minute history, sleep and stress files.
        queueFrames("fetch steps", cmdGetAll(CMD_ACTIVITY_DAY))
        history.fetch(workouts = true)
    }

    override fun dispose() {
        synchronized(ConnectionMonitor) {
            calls.dispose()
            settings.dispose()
            lights.dispose()
            handler.removeCallbacksAndMessages(null)
            super.dispose()
        }
    }

    // --- Music (e2)

    /** Music remote button: "01 e2 ac 01 02 <code> 00" (device -> phone). */
    private fun handleMusicControl(value: ByteArray) {
        if (value.size < 6 || value[3] != PUSH_MUSIC_BUTTON || value[4] != 0x02.toByte()) {
            return
        }
        val event = when (u8(value, 5)) {
            0x01 -> GBDeviceEventMusicControl.Event.PLAY
            0x02 -> GBDeviceEventMusicControl.Event.PAUSE
            0x03 -> GBDeviceEventMusicControl.Event.PREVIOUS
            0x04 -> GBDeviceEventMusicControl.Event.NEXT
            0x05 -> GBDeviceEventMusicControl.Event.VOLUMEUP
            0x06 -> GBDeviceEventMusicControl.Event.VOLUMEDOWN
            else -> return
        }
        evaluateGBDeviceEvent(GBDeviceEventMusicControl(event))
    }

    override fun onSetPhoneVolume(volume: Float) {
        // The volume bar is in the music frame (field05): re-send the track.
        lastMusicSpec?.let { sendMusicInfo(it.artist, it.track) }
    }

    override fun onSetMusicInfo(musicSpec: MusicSpec) {
        lastMusicSpec = musicSpec
        sendMusicInfo(musicSpec.artist, musicSpec.track)
    }

    override fun onSetMusicState(stateSpec: MusicStateSpec) {
        // Re-send so the play/pause icon (field03) updates.
        lastMusicPlaying = stateSpec.state.toInt() == MusicStateSpec.STATE_PLAYING
        sendMusicInfo(lastMusicSpec?.artist, lastMusicSpec?.track)
    }

    private fun sendMusicInfo(artist: String?, title: String?) {
        changedMusicFrames(artist, title)?.let { queueFrames("set music info", it) }
    }

    /** [musicFrames], or null if they equal the last frames sent. */
    private fun changedMusicFrames(artist: String?, title: String?): List<ByteArray>? {
        val frames = musicFrames(artist, title)
        val last = lastMusicFrames
        if (last != null && last.size == frames.size && last.indices.all { last[it].contentEquals(frames[it]) }) return null
        lastMusicFrames = frames
        return frames
    }

    /** Music info to the watch: "01 e2 abab 00 ab 01 <artist> ab 02 <title> ab 03/04/05 <state>". */
    private fun musicFrames(artistRaw: String?, titleRaw: String?): List<ByteArray> {
        val artist = (artistRaw ?: "").take(32).toByteArray(Charsets.UTF_16BE)
        val title = (titleRaw ?: "").take(48).toByteArray(Charsets.UTF_16BE)
        val data = ByteArrayOutputStream()
        data.write(byteArrayOf(MODE_SET, 0x01, artist.size.toByte())); data.write(artist)
        data.write(byteArrayOf(MODE_SET, 0x02, title.size.toByte())); data.write(title)
        // Icon flag: 03 pause icon (playing), 02 play icon (paused).
        data.write(byteArrayOf(MODE_SET, 0x03, 0x01, if (lastMusicPlaying) 0x03 else 0x02))
        // Volume bar: 04 max, 05 current, on the phone's media scale so one key press is one step.
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val volMax = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceIn(1, 63)
        val volCur = audio.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, volMax)
        data.write(byteArrayOf(MODE_SET, 0x04, 0x01, volMax.toByte()))
        data.write(byteArrayOf(MODE_SET, 0x05, 0x01, volCur.toByte()))
        return chunkedTlv(CMD_MUSIC_CONTROL, data.toByteArray())
    }

    // --- Notifications (b0 / b2)

    /**
     * `b0 abab` notification, chunked: `01 app | 02 sender | 03 body | 04 id | 05 number | 06
     * package | 07 title | 08 icon | 09 vibrate | 0a reply handle:4`. Texts are capped at 122
     * UTF-16 units, cut ones end in "...".
     */
    override fun onNotification(notificationSpec: NotificationSpec) {
        val handle = replyHandle(notificationSpec)
        smsReplyNumber(notificationSpec)?.let { smsReplyNumbers.add(handle, it) }
        val appIcon = watchAppIcon(notificationSpec.type)
        val swapped = handle != 0 && appIcon != 7 && devicePrefs.getBoolean(PREF_REPLY_ALL_APPS, false)
        val icon = if (swapped) 7 else appIcon
        val app = notificationSpec.sourceName ?: context.getString(R.string.pref_title_notifications_sms)
            .takeIf { notificationSpec.type == NotificationType.GENERIC_SMS }
        val title = notificationSpec.title ?: notificationSpec.sender ?: notificationSpec.sourceName ?: ""
        val data = notificationBody(
            appName = notificationSpec.sourceName,
            sender = notificationSpec.sender ?: notificationSpec.phoneNumber,
            body = notificationSpec.body ?: "",
            id = watchNotificationId(notificationSpec.id),
            number = notificationSpec.phoneNumber,
            sourcePackage = notificationSpec.sourceAppId,
            title = if (swapped && app != null) "$app: $title" else title,
            icon = icon,
            handle = handle,
        )
        replyIcon.sendNotification(icon, appIcon) { queueFrames("send notification", chunkedTlv(CMD_NOTIFICATION, data)) }
    }

    /**
     * `b0 abab` body, fields as on [onNotification]; empty texts are omitted. The watch offers a
     * reply only with a non-zero [handle] and icon 7 (the firmware checks for type 7).
     */
    internal fun notificationBody(
        appName: String?, sender: String?, body: String?, id: Int, number: String?,
        sourcePackage: String?, title: String?, icon: Int, handle: Int = 0,
    ): ByteArray {
        val data = ByteArrayOutputStream()
        fun text(field: Int, s: String?, max: Int) {
            if (s.isNullOrEmpty()) return
            val cut = if (s.length > max) s.take(max - 3) + "..." else s
            val bytes = cut.toByteArray(Charsets.UTF_16BE)
            data.write(byteArrayOf(MODE_SET, field.toByte(), bytes.size.toByte())); data.write(bytes)
        }
        text(0x01, appName, 40)
        text(0x02, sender, 40)
        text(0x03, body, NOTIFICATION_TEXT_LEN)
        data.write(byteArrayOf(MODE_SET, 0x04, 0x01, id.toByte()))
        text(0x05, number, 20)
        text(0x06, sourcePackage, 60)
        text(0x07, title, NOTIFICATION_TEXT_LEN)
        data.write(byteArrayOf(MODE_SET, 0x08, 0x01, icon.toByte()))
        data.write(byteArrayOf(MODE_SET, 0x09, 0x01, 0x01))
        if (handle != 0) { data.write(byteArrayOf(MODE_SET, 0x0a, 0x04)); data.write(be32Bytes(handle)) }
        return data.toByteArray()
    }

    /** `b2 ac 05` canned / `ac 06` built-in reply, chunked; handle as sent, see [replyHandle]. */
    private fun handleNotificationReply(value: ByteArray) {
        val p = chunks.collect(value, 5) ?: return
        val (handle, text) = parseNotificationReply(p, builtIn = value[3] == PUSH_REPLY_BUILTIN) ?: run {
            LOG.warn("notification reply length mismatch, dropped: {}", p.size)
            return
        }
        val event = GBDeviceEventNotificationControl()
        event.event = GBDeviceEventNotificationControl.Event.REPLY
        val number = smsReplyNumbers.lookup(handle.toInt())
        if (number != null) {
            event.phoneNumber = number
        } else {
            event.handle = handle
        }
        event.reply = text
        evaluateGBDeviceEvent(event)
    }

    /** Dismiss the watch's copy when the phone notification goes. */
    override fun onDeleteNotification(id: Int) {
        queueFrames("delete notification", deleteNotificationFrame(watchNotificationId(id)))
    }

    /** The watch keeps a one-byte id; [CALLER_NOTICE_ID] is kept free for the caller notice. */
    private fun watchNotificationId(id: Int): Int = Math.floorMod(id, CALLER_NOTICE_ID)

    /** Watch icon id per notification type; 4 = generic (0 is dropped by the watch). */
    private fun watchAppIcon(type: NotificationType?): Int = when (type) {
        NotificationType.QQ -> 1
        NotificationType.WECHAT -> 2
        NotificationType.GENERIC_SMS -> 3
        NotificationType.FACEBOOK -> 5
        NotificationType.TWITTER -> 6
        NotificationType.WHATSAPP -> 7
        NotificationType.SKYPE -> 8
        NotificationType.FACEBOOK_MESSENGER -> 9
        NotificationType.GOOGLE_HANGOUTS -> 10
        NotificationType.LINE -> 11
        NotificationType.LINKEDIN -> 12
        NotificationType.INSTAGRAM -> 13
        NotificationType.VIBER -> 14
        NotificationType.KAKAO_TALK -> 15
        NotificationType.VK -> 16
        NotificationType.SNAPCHAT -> 17
        NotificationType.GMAIL, NotificationType.GENERIC_EMAIL -> 19
        NotificationType.YAHOO_MAIL -> 58
        NotificationType.GOOGLE_MESSENGER -> 3
        NotificationType.TUMBLR -> 21
        NotificationType.PINTEREST -> 22
        NotificationType.YOUTUBE -> 23
        NotificationType.TELEGRAM -> 24
        NotificationType.OUTLOOK -> 30
        NotificationType.AMAZON -> 38
        NotificationType.GENERIC_CALENDAR, NotificationType.BUSINESS_CALENDAR -> 44
        NotificationType.SLACK -> 60
        else -> 4
    }

    // --- Calls, contacts, quick replies (c0), see Stratos4ProCalls

    override fun onSetCallState(callSpec: CallSpec) = calls.onSetCallState(callSpec)

    /**
     * `c0 ab 06 01 00` start, the list as field 07, `c0 ab 09 01 01` commit. List entries
     * `<numberLen><number UTF-16BE><nameLen><name UTF-16BE>`, sent as
     * `01 c0 ab 07 <offset:2> <len> <slice>` (offset = byte offset into the list), closed by
     * `01 c0 ab 07 fd <xor>`.
     */
    override fun onSetContacts(contacts: ArrayList<out Contact>) {
        val payload = ByteArrayOutputStream()
        for (contact in contacts) {
            val number = (contact.number ?: "").take(64).toByteArray(Charsets.UTF_16BE)
            val name = (contact.name ?: "").take(64).toByteArray(Charsets.UTF_16BE)
            if (number.isEmpty()) continue
            payload.write(number.size); payload.write(number)
            payload.write(name.size); payload.write(name)
        }
        val list = chunkedFrames(
            CMD_CONTACTS, MODE_SET, SET_CONTACTS_LIST, payload.toByteArray(), maxSlice = 230, emptyFrame = true
        ) { _, off, len -> byteArrayOf(hi(off), lo(off), len.toByte()) }
        queueFrames(
            "set contacts",
            listOf(cmdSetField(CMD_CONTACTS, SET_CONTACTS_START, 0x00)) + list +
                    listOf(cmdSetField(CMD_CONTACTS, SET_CONTACTS_COMMIT, 0x01))
        )
    }

    /**
     * Quick replies, `c0` for rejected calls, `b2` for notifications (watch keeps 15): chunked
     * `ab 0a <idx> <total:2> [<len><UTF-16BE>]*`, closed by `ab 0a fd <xor>`, then `ab 09 01 <on>`.
     */
    override fun onSetCannedMessages(cannedMessagesSpec: CannedMessagesSpec) {
        val (cmd, slots) = when (cannedMessagesSpec.type) {
            CannedMessagesSpec.TYPE_REJECTEDCALLS -> CMD_CONTACTS to CANNED_REPLY_SLOTS
            CannedMessagesSpec.TYPE_GENERIC -> CMD_NOTIFICATION_CONFIG2 to 15
            else -> return
        }
        val messages = cannedMessagesSpec.cannedMessages?.filter { it.isNotBlank() }.orEmpty().take(slots)
        val data = quickReplyBody(messages, CANNED_REPLY_LEN)
        queueFrames("set quick replies", chunkedFrames(cmd, MODE_SET, SET_QUICK_REPLIES, data, emptyFrame = true))
        queueFrames(
            "enable quick replies",
            cmdSetField(cmd, SET_CONTACTS_COMMIT, if (messages.isEmpty()) 0 else 1)
        )
        LOG.debug("quick replies {} -> {}", hex(cmd), messages.size)
    }

    // --- a5 pushes: camera, find phone, wear

    override fun onFindDevice(start: Boolean) {
        queueFrames("find device $start", cmdSetField(CMD_DEVICE_CONFIG, SET_FIND_WATCH, if (start) 0x01 else 0x00))
    }

    override fun onFindPhone(start: Boolean) {
        queueFrames("app ring $start", cmdSetField(CMD_DEVICE_CONFIG, SET_APP_RING_STATUS, if (start) 0x01 else 0x00))
    }

    /**
     * `01 a5 ab 03 01 <1 enter | 0 exit>`: the watch offers its shutter only while the phone camera
     * is open.
     */
    override fun onCameraStatusChange(event: GBDeviceEventCameraRemote.Event, filename: String?) {
        val state = when (event) {
            GBDeviceEventCameraRemote.Event.OPEN_CAMERA -> 1
            GBDeviceEventCameraRemote.Event.CLOSE_CAMERA -> 0
            else -> return
        }
        queueFrames("camera status", cmdSetField(CMD_DEVICE_CONFIG, SET_CAMERA_STATUS, state.toByte()))
    }

    /**
     * `a5` pushes (`01 a5 ac <field> <len> <value>`): 01 camera, 02 find phone, 05 wear, 08
     * language; settings changed on the watch go to [Stratos4ProSettings.handleConfigChanged].
     */
    private fun handleDeviceControl(value: ByteArray) {
        if (value.size < 6 || value[2] != MODE_REPORT) return
        val v = u8(value, 5)
        when (value[3]) {
            PUSH_CAMERA -> {
                val event = GBDeviceEventCameraRemote()
                event.event = when (v) {
                    1 -> GBDeviceEventCameraRemote.Event.OPEN_CAMERA
                    2 -> GBDeviceEventCameraRemote.Event.TAKE_PICTURE
                    3 -> GBDeviceEventCameraRemote.Event.CLOSE_CAMERA
                    else -> GBDeviceEventCameraRemote.Event.UNKNOWN
                }
                evaluateGBDeviceEvent(event)
            }

            PUSH_FIND_PHONE -> {
                val event = GBDeviceEventFindPhone()
                event.event = if (v != 0) GBDeviceEventFindPhone.Event.START else GBDeviceEventFindPhone.Event.STOP
                evaluateGBDeviceEvent(event)
            }

            PUSH_WEAR_STATE -> LOG.debug("wear state: {}", if (v == 1) "on wrist" else "off wrist")

            PUSH_LANGUAGE_CHANGED -> LOG.debug("language changed on watch: {}", v)

            else -> if (!settings.handleConfigChanged(value[3], v)) {
                LOG.debug("a5 push field {}: {}", hex(value[3]), value.toHex())
            }
        }
    }

    // --- Alarms (c7)

    /**
     * `01 c7 abab 00 [per slot: ab 01 02 <idx><idx> | ab 02 02 <idx><weekdayMask> |
     * ab 03 02 <idx><enabled> | ab 04 03 <idx><HH><MM> | ab 05 <1 + nameLen> <idx><name> |
     * ab 06 02 <idx><sound>]` +
     * terminator. Chunked: names can push it past one MTU.
     */
    override fun onSetAlarms(alarms: ArrayList<out Alarm>) {
        val data = ByteArrayOutputStream()
        for ((i, alarm) in alarms.withIndex()) {
            val idx = (i + 1).toByte()
            var mask = 0
            for ((bit, day) in ALARM_DAYS.withIndex()) {
                if (alarm.repetition and day.toInt() != 0) mask = mask or (1 shl bit)
            }
            data.write(byteArrayOf(MODE_SET, FIELD_ALARM_INDEX, 0x02, idx, idx))
            data.write(byteArrayOf(MODE_SET, FIELD_ALARM_DAYS, 0x02, idx, mask.toByte()))
            data.write(byteArrayOf(MODE_SET, FIELD_ALARM_ENABLED, 0x02, idx, if (alarm.enabled) 0x01 else 0x00))
            data.write(byteArrayOf(MODE_SET, FIELD_ALARM_TIME, 0x03, idx, alarm.hour.toByte(), alarm.minute.toByte()))
            // An empty name is just the index.
            val name = (alarm.title ?: "").take(ALARM_NAME_LEN).toByteArray(Charsets.UTF_16BE)
            data.write(byteArrayOf(MODE_SET, FIELD_ALARM_NAME, (name.size + 1).toByte(), idx)); data.write(name)
            data.write(byteArrayOf(MODE_SET, FIELD_ALARM_SOUND, 0x02, idx, alarmSoundMode(alarm.soundCode)))
        }
        queueFrames("set alarms", chunkedTlv(CMD_ALARM, data.toByteArray()))
    }

    /** Read reply "01 c7 aaaa 00 [0f aa 01 01 <idx> aa 02 01 <mask> aa 03 01 <en> aa 04 02 <HH><MM>]*". */
    private fun handleAlarmData(value: ByteArray) {
        if (value.size >= 4 && value[2] == MODE_REPORT) {
            // `c7 ac 01`: an alarm was edited on the watch - pull the list again.
            LOG.info("alarms changed on watch, re-reading")
            queueFrames("re-read alarms", cmdGetAll(CMD_ALARM))
            return
        }
        if (value.size >= 4 && value[2] == MODE_GET && value[3] == FIELD_ALARM_CAPS) {
            // `<len> <maxCount> <bits>`; header-only reply = old firmware, 5 alarms.
            if (value.size >= 6) {
                LOG.debug("alarm caps: max={} bits={}", u8(value, 5),
                    if (value.size > 6) value.copyOfRange(6, value.size).toHex() else "-")
            } else {
                LOG.debug("alarm caps: header only (5 slots)")
            }
            return
        }
        if (value.size < 7 || value[2] != MODE_GET || value[4] == PKT_TERMINATOR) return
        val parsed = HashMap<Int, IntArray>() // position -> [hour, minute, mask, enabled]
        val names = HashMap<Int, String>()
        var cur: IntArray? = null
        var curPos = -1
        var i = 4 // after "01 c7 aaaa"; status(00) and per-alarm separators(0f) are skipped below
        while (i < value.size) {
            if (value[i] != MODE_GET) { i += 1; continue }
            if (i + 3 > value.size) break
            val field = value[i + 1]
            val len = u8(value, i + 2)
            if (i + 3 + len > value.size) break
            if (len == 0) { i += 3; continue }
            when (field) {
                FIELD_ALARM_INDEX -> {
                    curPos = u8(value, i + 3) - 1
                    cur = intArrayOf(0, 0, 0, 0)
                    if (curPos >= 0) parsed[curPos] = cur
                }
                FIELD_ALARM_DAYS -> cur?.set(2, u8(value, i + 3))
                FIELD_ALARM_ENABLED -> cur?.set(3, u8(value, i + 3))
                FIELD_ALARM_TIME -> {
                    cur?.set(0, u8(value, i + 3))
                    if (len >= 2) cur?.set(1, u8(value, i + 4))
                }
                // `<idx> <UTF-16BE name>`; the index-only form is an empty name.
                FIELD_ALARM_NAME -> if (len > 1 && curPos >= 0) {
                    names[curPos] = String(value, i + 4, len - 1, Charsets.UTF_16BE)
                }
            }
            i += 3 + len
        }
        if (parsed.isNotEmpty()) storeAlarmsFromWatch(parsed, names)
    }

    private fun storeAlarmsFromWatch(parsed: Map<Int, IntArray>, names: Map<Int, String>) {
        // Update GB's alarm DB only: re-sending could loop and overwrite a watch edit.
        // getAlarmsWithDefaults, since a device whose alarm screen was never opened has no rows and
        // getAlarms would drop them all.
        var stored = 0
        for (dbAlarm in DBHelper.getAlarmsWithDefaults(device)) {
            val a = parsed[dbAlarm.position] ?: continue
            dbAlarm.unused = false
            dbAlarm.enabled = a[3] != 0
            dbAlarm.hour = a[0]
            dbAlarm.minute = a[1]
            var rep = 0
            for ((bit, day) in ALARM_DAYS.withIndex()) {
                if (a[2] and (1 shl bit) != 0) rep = rep or day.toInt()
            }
            dbAlarm.repetition = rep
            names[dbAlarm.position]?.let { dbAlarm.title = it }
            DBHelper.store(dbAlarm)
            stored++
        }
        LOG.info("loaded {} alarms from watch, stored {}", parsed.size, stored)
        // Refresh the alarm UI from the DB (does not re-send to the watch).
        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent(DeviceService.ACTION_SAVE_ALARMS))
    }

    // --- Weather (e0), world clocks (ed)

    /** Current weather, then the forecast as a separate message. */
    override fun onSendWeather() {
        val weather = Weather.getWeatherSpec() ?: return
        queueFrames("send weather", chunkedTlv(CMD_WEATHER, Stratos4ProWeather.currentBody(weather)))
        queueFrames("send weather forecast", chunkedTlv(CMD_WEATHER, Stratos4ProWeather.forecastBody(weather)))
    }

    override fun onSetWorldClocks(clocks: ArrayList<out WorldClock>) = settings.onSetWorldClocks(clocks)

    // --- Navigation routes (e9)

    /** A GPX or FIT file from the route installer: all track points as one route. */
    override fun onInstallApp(uri: Uri, options: Bundle) {
        val route = Stratos4ProGpxRouteInstallHandler(uri, context)
        val gpx = route.gpxFile
        val points = gpx?.tracks.orEmpty().flatMap { t -> t.trackSegments.flatMap { it.trackPoints } }
        if (gpx == null || points.size < 2) {
            GB.toast(context.getString(R.string.route_upload_failed), Toast.LENGTH_LONG, GB.ERROR)
            return
        }
        val name = options.getString(GpxRouteInstallHandler.EXTRA_TRACK_NAME)?.takeIf { it.isNotBlank() } ?: route.name
        val distance = points.zipWithNext { a, b -> a.getDistance(b) }.sum().roundToInt()
        val kept = simplifyRoute(points, ROUTE_MAX_POINTS)
        LOG.info("route '{}': {} m, {} of {} points", name, distance, kept.size, points.size)
        val time = (gpx.time?.time ?: System.currentTimeMillis()) / 1000
        queueFrames("send route", routeFrames(time, distance, name, kept))
    }

    /** `e9 ac 05 01 <status>` after a route write; other e9 frames are not used. */
    private fun handleGps(value: ByteArray) {
        if (value.size < 6 || value[2] != MODE_REPORT || value[3] != PUSH_ROUTE_STATUS) return
        val (msg, severity) = when (u8(value, 5)) {
            1 -> R.string.route_upload_completed to GB.INFO
            2 -> R.string.stratos4pro_route_exists to GB.INFO
            else -> R.string.route_upload_failed to GB.ERROR
        }
        GB.toast(context.getString(msg), Toast.LENGTH_SHORT, severity)
    }

    // --- Heart rate (e1), see Stratos4ProSettings

    override fun onSetHeartRateMeasurementInterval(seconds: Int) {
        // The fragment has already saved PREF_HEARTRATE_MEASUREMENT_INTERVAL.
        settings.sendContinuousHeartRate()
    }

    override fun onHeartRateTest() {
        // One-click measurement; the result arrives as `e1 ac 03`.
        queueFrames("HR test", byteArrayOf(PKT_HEADER, CMD_HEALTH_MONITOR, MODE_SET, SET_E1_ONE_CLICK_MEASURE))
    }

    // --- Time (a3), device info (a4)

    /** `01 a3 01 04 <unix:4> 10 02 <tz>`: UTC unix time, zone `[0x80 | hours][minutes]` east of UTC. */
    private fun setTime(builder: TransactionBuilder) {
        val now = GregorianCalendar.getInstance()
        val epoch = (now.timeInMillis / 1000L).toInt()
        // e.g. UTC+2 -> 0x82 0x00.
        val offsetMinutes = (now.get(Calendar.ZONE_OFFSET) + now.get(Calendar.DST_OFFSET)) / 60_000
        val tzHours = abs(offsetMinutes) / 60
        val tzMinutes = abs(offsetMinutes) % 60
        val tzSign = if (offsetMinutes >= 0) 0x80 else 0x00
        val buf = ByteBuffer.allocate(12).order(ByteOrder.BIG_ENDIAN)
        buf.put(PKT_HEADER)
        buf.put(CMD_TIME)
        buf.put(FIELD_TIME)
        buf.put(0x04)
        buf.putInt(epoch)
        buf.put(FIELD_TIMEZONE)
        buf.put(0x02)
        buf.put((tzSign or tzHours).toByte())
        buf.put(tzMinutes.toByte())
        builder.writeData(buf.array())
    }

    /**
     * `a3` has no mode byte; byte 2 is the field. A time SET is echoed verbatim; `04`/`08` ack the
     * date/time format writes.
     */
    private fun handleTime(value: ByteArray) {
        when (value[2]) {
            FIELD_TIME -> if (value.size >= 8) {
                val watchTime = be32(value, 4)
                val drift = System.currentTimeMillis() / 1000L - watchTime
                LOG.debug("time set acked, watch echoed unix {} ({} s behind now)", watchTime, drift)
            }

            FIELD_DATE_FORMAT, FIELD_TIME_FORMAT -> LOG.debug("time/date format acked: {}", value.toHex())
            else -> LOG.debug("a3 frame: {}", value.toHex())
        }
    }

    private fun handleDeviceInfo(value: ByteArray) {
        if (value.size >= 5 && value[2] == MODE_REPORT) {
            // Pushed status update (e.g. on charger connect): 01 a4 ac <field> <len> <battery-blob>
            val len = u8(value, 4)
            if (value.size >= 5 + len) {
                parseBattery(value.copyOfRange(5, 5 + len))
            }
            return
        }
        // Full device info reply: 01 a4 aa aa 00 [ aa <field> <len> <val...> ]*
        val fields = parseTlv(value, 5)
        fields[FIELD_FIRMWARE]?.let { fw ->
            val info = GBDeviceEventVersionInfo()
            info.fwVersion = String(fw, Charsets.UTF_8)
            fields[FIELD_MODEL]?.let { info.hwVersion = String(it, Charsets.UTF_8) }
            evaluateGBDeviceEvent(info)
            LOG.info("firmware={}", info.fwVersion)
        }
        fields[FIELD_BATTERY]?.let { parseBattery(it) }
    }

    /** Battery blob "01 01 <level> 02 01 <charging>" (sub-TLV). */
    private fun parseBattery(blob: ByteArray) {
        val tlv = parseSubTlv(blob)
        val level = tlv[0x01]?.let { u8(it, 0) } ?: -1
        val charging = tlv[0x02]?.let { u8(it, 0) } ?: 0
        if (level in 0..100) {
            val event = GBDeviceEventBatteryInfo()
            event.level = level
            event.state = if (charging != 0) BatteryState.BATTERY_CHARGING else BatteryState.BATTERY_NORMAL
            evaluateGBDeviceEvent(event)
            LOG.info("battery={}% charging={}", level, charging != 0)
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(Stratos4ProSupport::class.java)

        /** Alarm name limit; the watch face has room for about this much. */
        const val ALARM_NAME_LEN = 12
        /** Notification title/body cap, UTF-16 units. */
        const val NOTIFICATION_TEXT_LEN = 122
        /** `c0 ab 0a` quick replies; the slot count is a guess. */
        const val CANNED_REPLY_SLOTS = 16
        const val CANNED_REPLY_LEN = 40
        /** World clock limits; the watch reports its own max via `ed aa 02`. */
        const val WORLD_CLOCK_SLOTS = 5
        const val WORLD_CLOCK_LABEL_LEN = 20
        /** Watch-side id of the caller notice. Real notifications use 0..253, see watchNotificationId. */
        const val CALLER_NOTICE_ID = 254
        /** Send every notification with a reply action as WhatsApp (icon 7), the only one the watch replies to. */
        const val PREF_REPLY_ALL_APPS = "stratos4pro_reply_all_apps"

        /** Gadgetbridge alarm day bits, indexed by the watch's mask bit (0 = Sun). */
        private val ALARM_DAYS = byteArrayOf(
            Alarm.ALARM_SUN, Alarm.ALARM_MON, Alarm.ALARM_TUE, Alarm.ALARM_WED,
            Alarm.ALARM_THU, Alarm.ALARM_FRI, Alarm.ALARM_SAT,
        )
    }
}
