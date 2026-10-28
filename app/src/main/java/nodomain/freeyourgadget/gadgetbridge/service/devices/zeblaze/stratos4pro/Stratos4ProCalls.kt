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

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventCallControl
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventNotificationControl
import nodomain.freeyourgadget.gadgetbridge.model.CallSpec
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder
import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro.Stratos4ProSupport.Companion.CALLER_NOTICE_ID
import nodomain.freeyourgadget.gadgetbridge.util.GB
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getParcelableCompat
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.time.LocalTime

/**
 * Calls. Over BLE (`c0`): with HFP up Android rings the watch itself; these frames add the
 * caller, and show it when only BLE is up. Call audio: a phone-side classic bond plus a radio
 * keepalive (`eb`), see [repairBt3].
 */
internal class Stratos4ProCalls(support: Stratos4ProSupport) : Stratos4ProFeature(support) {
    /**
     * Incoming: chunked `c0 abab`, `ab 01 <name> ab 02 <number>`. Answered `c0 ab 05 01 01`,
     * ended/rejected `c0 ab 05 01 00`. Nothing for outgoing calls.
     */
    fun onSetCallState(callSpec: CallSpec) {
        when (callSpec.command) {
            CallSpec.CALL_INCOMING -> {
                // First, so the radio is already coming up when the watch rings.
                val audioComing = startCallAudio()
                val body = ByteArrayOutputStream()
                val name = (callSpec.name?.takeIf { it.isNotBlank() } ?: callSpec.number ?: "").take(40).toByteArray(Charsets.UTF_16BE)
                val number = (callSpec.number ?: "").take(20).toByteArray(Charsets.UTF_16BE)
                if (name.isNotEmpty()) { body.write(byteArrayOf(MODE_SET, CALLER_NAME, name.size.toByte())); body.write(name) }
                if (number.isNotEmpty()) { body.write(byteArrayOf(MODE_SET, CALLER_NUMBER, number.size.toByte())); body.write(number) }
                if (body.size() == 0) return
                // The watch picks the call screen when the frame lands: before HFP it is
                // notify-only (reject, no answer) and stays so after HFP; re-sending, or closing
                // (phase 0) and re-sending, does not help. So hold the frame until HFP, with a
                // plain notice meanwhile.
                dropPendingCaller()
                val frame = body.toByteArray()
                if (!audioComing || hfpConnected) {
                    queueFrames("incoming call", chunkedTlv(CMD_CONTACTS, frame))
                } else {
                    LOG.debug("caller notice sent, call screen once call audio is up")
                    pendingCaller = frame
                    sendCallerNotice(callSpec)
                    handler.postDelayed(sendPendingCaller, CALLER_WAIT_MS)
                }
            }

            CallSpec.CALL_START, CallSpec.CALL_ACCEPT -> {
                dropPendingCaller()
                sendCallPhase(1)
            }
            CallSpec.CALL_END, CallSpec.CALL_REJECT -> {
                dropPendingCaller()
                endCallAudio()
                sendCallPhase(0)
            }
            else -> {}
        }
    }

    private fun sendCallPhase(phase: Int) {
        queueFrames("call phase $phase", cmdSetField(CMD_CONTACTS, SET_CALL_PHASE, phase.toByte()))
    }

    /** Number for a quick reply; `ac 01` arrives just before `ac 02`. */
    private var quickReplyNumber: String? = null

    /**
     * `c0` pushes: `ac 03 <1 hang up | 2 mute>`; `ac 01 <number>` + `ac 02 <text>` = SMS quick
     * reply.
     */
    fun handleContacts(value: ByteArray) {
        if (value.size < 5 || value[2] != MODE_REPORT) return
        val len = u8(value, 4)
        if (len == 0) return
        if (value.size < 5 + len) {
            LOG.warn("c0 push: short frame {}", value.toHex())
            return
        }
        when (value[3]) {
            PUSH_CALL_CONTROL -> {
                val event = GBDeviceEventCallControl()
                event.event = when (value[5].toInt() and 0xff) {
                    1 -> GBDeviceEventCallControl.Event.REJECT
                    2 -> GBDeviceEventCallControl.Event.IGNORE
                    else -> return
                }
                LOG.info("call control from watch: {}", event.event)
                evaluateGBDeviceEvent(event)
            }

            PUSH_REPLY_NUMBER -> quickReplyNumber = String(value, 5, len, Charsets.UTF_16BE)

            PUSH_REPLY_TEXT -> {
                val text = String(value, 5, len, Charsets.UTF_16BE)
                val number = quickReplyNumber ?: run {
                    LOG.warn("quick reply without a number, dropped")
                    return
                }
                quickReplyNumber = null
                LOG.info("SMS quick reply, {} chars", text.length)
                val event = GBDeviceEventNotificationControl()
                event.event = GBDeviceEventNotificationControl.Event.REPLY
                event.phoneNumber = number
                event.reply = text
                evaluateGBDeviceEvent(event)
            }

            else -> LOG.debug("c0 push field {}: {}", hex(value[3]), value.toHex())
        }
    }

    // --- Classic BT (eb) ---
    //
    // The watch powers its BR/EDR radio down after ~5 min idle, dropping HFP. Nothing reports it
    // (`eb` only answers `aa 01`, no push), so poll and switch it back on; the watch then
    // reconnects and Android attaches HFP. Profiles cannot be connected from here (HFP needs
    // MODIFY_PHONE_STATE) and there is no A2DP sink.

    /** Watchdog state, all mutated on the handler thread. */
    private var bt3LastRepairAt = 0L
    private var bt3Failures = 0
    private var bt3WatchdogOn = false

    /**
     * Whether the WATCH thinks it is paired; the phone's bond can disagree. Null until the first
     * `eb aa 01` reply.
     */
    private var bt3Paired: Boolean? = null

    /** Last evaluated schedule window state, null until the watchdog has looked once. */
    private var bt3WindowOpen: Boolean? = null

    /**
     * 0 while the keepalive window is open, else ms until it opens. No schedule or zero length =
     * always open.
     */
    private fun audioWindowOpensInMs(): Long {
        if (!devicePrefs.getBoolean(PREF_AUDIO_SCHEDULE, false)) return 0L
        val start = devicePrefs.getLocalTime(PREF_AUDIO_SCHEDULE_START, "08:00")
        val end = devicePrefs.getLocalTime(PREF_AUDIO_SCHEDULE_END, "22:00")
        if (start == end) return 0L
        val now = LocalTime.now()
        // A window that wraps past midnight (22:00-07:00) is inverted, not empty.
        val open = if (start < end) now >= start && now < end else now >= start || now < end
        if (open) return 0L
        val secondsOfDay = 24 * 60 * 60
        val until = (start.toSecondOfDay() - now.toSecondOfDay() + secondsOfDay) % secondsOfDay
        return until * 1000L
    }

    /** The 24/7 keepalive is on. "Only for calls" overrides it (the UI greys it out too). */
    private fun keepaliveEnabled(): Boolean =
        devicePrefs.getBoolean(PREF_AUDIO_AUTO_CONNECT, false) &&
                !devicePrefs.getBoolean(PREF_AUDIO_ON_CALL, false)

    /** Call audio wanted now: a call in "only for calls" mode, or keepalive inside the window. */
    private fun audioKeepaliveWanted(): Boolean =
        callAudioActive || (keepaliveEnabled() && audioWindowOpensInMs() == 0L)

    // "Only for calls". The watch does not wake its radio for a call, so the phone does when it
    // rings. Radio off -> HFP up in 1-3 s. During the call audioKeepaliveWanted() is true, so drop
    // repair applies. After it nothing is sent; the watch idles the radio off in ~5 min.

    /** A call is ringing or in progress and we switched the radio on for it. Handler thread. */
    private var callAudioActive = false

    /** Caller frame held back until call audio is up, see onSetCallState. Main thread. */
    private var pendingCaller: ByteArray? = null

    /** HFP to the watch is up: audioReceiver events, seeded by the watch's `eb aa 01` reply. */
    private var hfpConnected = false

    /** The caller notice (a plain notification) is on the watch for the held call. Main thread. */
    private var callerNoticeShown = false

    private val sendPendingCaller = Runnable {
        val body = pendingCaller ?: return@Runnable
        pendingCaller = null
        val builder = createTransactionBuilder("incoming call")
        // The call screen replaces the notice; drop it so it does not stay in the list.
        if (callerNoticeShown) {
            callerNoticeShown = false
            builder.writeData(deleteNotificationFrame(CALLER_NOTICE_ID))
        }
        builder.writeData(chunkedTlv(CMD_CONTACTS, body)).queue()
    }

    /**
     * Plain notification for the caller, so the watch buzzes while the call frame waits for HFP. A
     * separate screen from the call screen, so it does not pin the notify-only one.
     */
    private fun sendCallerNotice(callSpec: CallSpec) {
        val name = callSpec.name?.takeIf { it.isNotBlank() } ?: callSpec.number ?: ""
        val data = support.notificationBody(
            appName = null, sender = name, body = context.getString(R.string.stratos4pro_incoming_call),
            id = CALLER_NOTICE_ID, number = callSpec.number, sourcePackage = null, title = name,
            icon = 4,
        )
        queueFrames("caller notice", chunkedTlv(CMD_NOTIFICATION, data))
        callerNoticeShown = true
    }

    private fun dropPendingCaller() {
        handler.removeCallbacks(sendPendingCaller)
        pendingCaller = null
        // Call ended or answered before HFP came up: close the notice with it.
        if (callerNoticeShown) {
            callerNoticeShown = false
            queueFrames("drop caller notice", deleteNotificationFrame(CALLER_NOTICE_ID))
        }
    }

    /** @return true when the radio is being brought up for this call. */
    private fun startCallAudio(): Boolean {
        if (!devicePrefs.getBoolean(PREF_AUDIO_ON_CALL, false)) return false
        if (audioBondState() != BluetoothDevice.BOND_BONDED || bt3Paired == false) {
            LOG.debug("incoming call, but call audio is not paired")
            return false
        }
        callAudioActive = true
        handler.removeCallbacks(callAudioExpire)
        handler.postDelayed(callAudioExpire, CALL_AUDIO_MAX_MS)
        // A new call is not the watch refusing us, so old backoff must not delay it.
        bt3Failures = 0
        bt3LastRepairAt = 0L
        registerAudioReceiver()
        repairBt3("incoming call")
        return true
    }

    private fun endCallAudio() {
        handler.removeCallbacks(callAudioExpire)
        if (!callAudioActive) return
        callAudioActive = false
        LOG.debug("call over, leaving the watch to power classic BT down")
        if (!audioKeepaliveWanted()) {
            handler.removeCallbacks(bt3DropRepair)
            handler.removeCallbacks(bt3Confirm)
        }
    }

    /** In case the end of the call never reaches us (filtered, or we were disconnected). */
    private val callAudioExpire = Runnable {
        if (callAudioActive) LOG.warn("no end of call seen, releasing call audio")
        endCallAudio()
    }

    /** `aa 01` chunked -> `[?][btSwitch][paired][connected][mac:6][name:20]` */
    fun handleBt3(value: ByteArray) {
        if (value.size < 5 || value[2] != MODE_GET || value[3] != FIELD_BT3_STATE) return
        val p = chunks.collect(value, 5) ?: return
        if (p.size < 4) {
            LOG.warn("classic BT state: short payload {}", p.toHex())
            return
        }
        LOG.debug(
            "classic BT: switch={} paired={} connected={} mac={} name={}",
            p[1].toInt() and 0xff, p[2].toInt() and 0xff, p[3].toInt() and 0xff,
            if (p.size >= 10) p.copyOfRange(4, 10).toHex() else "-",
            if (p.size > 10) p.copyOfRange(10, p.size).takeWhile { it.toInt() != 0 }
                .toByteArray().toString(Charsets.US_ASCII) else "-"
        )
        val on = p[1].toInt() != 0
        val paired = p[2].toInt() != 0
        val connected = p[3].toInt() != 0
        handler.post {
            hfpConnected = connected
            onBt3State(on, paired)
        }
    }

    private fun onBt3State(on: Boolean, paired: Boolean) {
        bt3Paired = paired
        if (on) {
            bt3Failures = 0
            return
        }
        if (!paired || !audioKeepaliveWanted()) return
        // Still off right after we asked for it on: the watch refused, so back off harder.
        if (bt3LastRepairAt != 0L &&
            System.currentTimeMillis() - bt3LastRepairAt < BT3_QUICK_DROP_MS
        ) {
            bt3Failures++
        }
        repairBt3("poll")
    }

    /** Switch the watch's classic radio on. Rate limited so a refusal cannot become a loop. */
    private fun repairBt3(reason: String) {
        if (!isConnected()) return
        val now = System.currentTimeMillis()
        val gap = BT3_BACKOFF_MS[bt3Failures.coerceIn(0, BT3_BACKOFF_MS.size - 1)]
        if (bt3LastRepairAt != 0L && now - bt3LastRepairAt < gap) {
            LOG.debug("bt3 repair ({}) suppressed for another {} ms",
                reason, gap - (now - bt3LastRepairAt))
            return
        }
        bt3LastRepairAt = now
        LOG.info("switching watch classic BT on ({})", reason)
        queueFrames("BT3 on", cmdSetField(CMD_BT3, SET_BT3_SWITCH, 0x01))
        handler.removeCallbacks(bt3Confirm)
        handler.postDelayed(bt3Confirm, BT3_SETTLE_MS)
    }

    private fun queryBt3State() {
        if (!isConnected()) return
        queueGetField(CMD_BT3, FIELD_BT3_STATE)
    }

    /** Read back after a repair to see if it took. */
    private val bt3Confirm = Runnable { queryBt3State() }

    /** Safety net for drops the broadcasts miss. */
    private val bt3Poll = object : Runnable {
        override fun run() {
            if (!isConnected() || !keepaliveEnabled()) {
                bt3WatchdogOn = false
                return
            }
            val opensIn = audioWindowOpensInMs()
            val open = opensIn == 0L
            if (bt3WindowOpen != open) {
                // Only a real closed -> open edge repairs; the first tick knows nothing yet and
                // must keep the backoff.
                val reopened = bt3WindowOpen == false
                bt3WindowOpen = open
                if (devicePrefs.getBoolean(PREF_AUDIO_SCHEDULE, false)) {
                    LOG.info("call audio window {}", if (open) "open" else "closed")
                }
                if (reopened) {
                    // A closed window is not a refusal, so old backoff must not delay the reopen.
                    bt3Failures = 0
                    bt3LastRepairAt = 0L
                    repairBt3("window open")
                }
            }
            if (!open) {
                // Window closed: send nothing, let the radio idle off, keep ticking. Clamped so the
                // opening is not missed.
                handler.postDelayed(this, minOf(BT3_POLL_MS, opensIn))
                return
            }
            // Never during a history sync (a stalled one does not count).
            if (!history.isBusy()) queryBt3State()
            handler.postDelayed(this, BT3_POLL_MS)
        }
    }

    private fun startBt3Watchdog() {
        registerAudioReceiver()
        if (bt3WatchdogOn) return
        bt3WatchdogOn = true
        handler.postDelayed(bt3Poll, BT3_POLL_MS)
    }

    /** Stop the keepalive. Nothing is sent; the watch idles the radio off. */
    private fun stopBt3Watchdog() {
        bt3WatchdogOn = false
        bt3WindowOpen = null
        handler.removeCallbacks(bt3Poll)
        handler.removeCallbacks(bt3Confirm)
        handler.removeCallbacks(bt3DropRepair)
    }

    /** Ask the watch to switch classic BT on and start pairing, then bond from the phone side. */
    private fun requestWatchBt3Pairing(builder: TransactionBuilder) {
        builder.writeData(
            cmdSetField(CMD_BT3, SET_BT3_SWITCH, 0x01),
            cmdSetField(CMD_BT3, SET_BT3_PAIR, 0x02, 0x00, *bt3PairToken()),
        )
    }

    /**
     * Four random bytes the watch ties the pairing to. Generated once and reused: a new token per
     * attempt looks like another phone. Zeros are ignored.
     */
    private fun bt3PairToken(): ByteArray {
        val prefs = devicePrefs.preferences
        prefs.getString(PREF_AUDIO_PAIR_TOKEN, null)?.let { hex ->
            if (hex.length == 8) return GB.hexStringToByteArray(hex)
        }
        val token = ByteArray(4).also { SecureRandom().nextBytes(it) }
        prefs.edit().putString(PREF_AUDIO_PAIR_TOKEN, token.toHex()).apply()
        LOG.info("generated classic pairing token {}", token.toHex())
        return token
    }

    // --- Classic BT call audio (phone-side bond) ---
    //
    // Calls run over classic HFP, which Android attaches once the watch's BR/EDR link is up; there
    // is no BLE command for it. This driver owns the bond and the radio keepalive (see repairBt3).

    private var audioReceiver: BroadcastReceiver? = null
    private var audioPairRequested = false

    /** Set while waiting for a stale bond to go away so pairing can start clean. */
    private var audioRebondPending = false
    private var audioBondWaits = 0

    @SuppressLint("MissingPermission")
    private fun audioBondState(): Int =
        bluetoothAdapter.getRemoteDevice(device.address).bondState

    /** Bond the classic side; the watch connects and Android attaches HFP once bonded. */
    @SuppressLint("MissingPermission")
    private fun pairAudio() {
        val btDevice = bluetoothAdapter.getRemoteDevice(device.address)
        val bonded = btDevice.bondState == BluetoothDevice.BOND_BONDED
        if (bonded && bt3Paired != false) {
            // Both sides agree (or the watch has not said): just wake the link, skipping backoff.
            bt3Failures = 0
            bt3LastRepairAt = 0L
            startBt3Watchdog()
            repairBt3("manual")
            return
        }
        if (bonded) {
            // Phone bonded, watch forgot: drop the stale bond and let BOND_NONE restart pairing,
            // else the radio toggles forever.
            LOG.info("phone is bonded but watch reports unpaired, rebonding")
            GB.toast(context.getString(R.string.stratos4pro_audio_rebonding), 3, GB.INFO)
            registerAudioReceiver()
            audioRebondPending = true
            if (!removeAudioBond()) {
                audioRebondPending = false
                startAudioBond(btDevice)
            }
            return
        }
        startAudioBond(btDevice)
    }

    /**
     * Put the watch in pairing mode, then bond once it listens. Paging it earlier ends in
     * AUTH_FAILED with no prompt, so the bond starts inside the transaction after the writes.
     */
    @SuppressLint("MissingPermission")
    private fun startAudioBond(btDevice: BluetoothDevice) {
        audioPairRequested = true
        registerAudioReceiver()
        if (!isConnected()) {
            // removeBond drops the LE link too. Pairing mode needs BLE, so wait for the link.
            if (audioBondWaits < AUDIO_BOND_MAX_WAITS) {
                audioBondWaits++
                LOG.debug("waiting for BLE link before pairing audio")
                handler.postDelayed({ startAudioBond(btDevice) }, AUDIO_BOND_WAIT_MS)
                return
            }
            LOG.warn("no BLE link, pairing audio blind")
        }
        audioBondWaits = 0
        val prep = createTransactionBuilder("watch BT3 pairing")
        requestWatchBt3Pairing(prep)
        prep.sleep(AUDIO_PAIR_MODE_MS)
        prep.run(Runnable { createAudioBond(btDevice) })
        prep.queue()
    }

    @SuppressLint("MissingPermission")
    private fun createAudioBond(btDevice: BluetoothDevice) {
        val started = try {
            // Hidden createBond(int), 1 = TRANSPORT_BREDR: bond the classic side, not LE.
            BluetoothDevice::class.java.getMethod("createBond", Int::class.javaPrimitiveType)
                .invoke(btDevice, 1) as Boolean
        } catch (e: Exception) {
            LOG.warn("createBond(transport) unavailable, falling back to createBond()", e)
            btDevice.createBond()
        }
        LOG.info("audio bond started: {}", started)
        GB.toast(context.getString(R.string.ak102_audio_pairing_started), 3, GB.INFO)
    }

    /** Remove the classic bond (hidden removeBond). */
    private fun removeAudioBond(): Boolean = try {
        BluetoothDevice::class.java.getMethod("removeBond")
            .invoke(bluetoothAdapter.getRemoteDevice(device.address)) as Boolean
    } catch (e: Exception) {
        LOG.warn("removeBond failed", e)
        false
    }

    private fun unpairAudio() {
        audioRebondPending = false
        if (removeAudioBond()) {
            GB.toast(context.getString(R.string.ak102_audio_unpaired), 3, GB.INFO)
        }
    }

    /** Repair scheduled from a link drop, delayed so the watch settles first. */
    private val bt3DropRepair = Runnable { repairBt3("link drop") }

    /**
     * Bond completion and classic link state. The watch drops the link silently on its idle timer;
     * these broadcasts are the only prompt signal, [bt3Poll] covers misses.
     */
    private fun registerAudioReceiver() {
        if (audioReceiver != null) return
        audioReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val btDevice = intent.getParcelableCompat<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (btDevice == null || !btDevice.address.equals(device.address, ignoreCase = true)) {
                    return
                }
                when (intent.action) {
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val state = intent.getIntExtra(
                            BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR
                        )
                        when {
                            state == BluetoothDevice.BOND_NONE && audioRebondPending -> {
                                audioRebondPending = false
                                LOG.info("stale audio bond removed, pairing again")
                                startAudioBond(btDevice)
                            }

                            state == BluetoothDevice.BOND_BONDED && audioPairRequested -> {
                                audioPairRequested = false
                                // The watch's "unpaired" from before the bond is stale now.
                                bt3Paired = null
                                LOG.info("audio bond complete")
                                GB.toast(
                                    context.getString(R.string.ak102_audio_connected),
                                    3, GB.INFO
                                )
                                startBt3Watchdog()
                            }
                        }
                    }

                    BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                        LOG.info("HFP state {}", state)
                        hfpConnected = state == BluetoothProfile.STATE_CONNECTED
                        // HFP up means the watch knows this phone.
                        if (hfpConnected) bt3Paired = true
                        if (hfpConnected && pendingCaller != null) {
                            handler.removeCallbacks(sendPendingCaller)
                            handler.postDelayed(sendPendingCaller, CALLER_AFTER_HFP_MS)
                        }
                        if (state == BluetoothProfile.STATE_DISCONNECTED) onAudioLinkDropped()
                    }

                    // Fires for the LE link as well; EXTRA_TRANSPORT exists since Android 13,
                    // before that assume classic and let repairBt3 reject it if we are offline.
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        val transport = intent.getIntExtra(
                            "android.bluetooth.device.extra.TRANSPORT", BluetoothDevice.TRANSPORT_BREDR
                        )
                        if (transport == BluetoothDevice.TRANSPORT_BREDR) {
                            hfpConnected = false
                            onAudioLinkDropped()
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        }
        context.registerReceiver(audioReceiver, filter)
    }

    private fun onAudioLinkDropped() {
        if (!audioKeepaliveWanted()) return
        // Dropped again right after we switched the radio on: the watch is refusing, back off.
        if (bt3LastRepairAt != 0L &&
            System.currentTimeMillis() - bt3LastRepairAt < BT3_QUICK_DROP_MS
        ) {
            bt3Failures++
        }
        handler.removeCallbacks(bt3DropRepair)
        handler.postDelayed(bt3DropRepair, BT3_DROP_SETTLE_MS)
    }

    /**
     * Call audio keepalive. New link: reset backoff; the `eb aa 01` read at init drives the first
     * repair. Posted: the watchdog state lives on the handler.
     */
    fun onConnect() {
        handler.post {
            endCallAudio()
            // Unknown until the watch answers on this link.
            bt3Paired = null
            if (keepaliveEnabled()) {
                bt3Failures = 0
                bt3LastRepairAt = 0L
                startBt3Watchdog()
            } else if (devicePrefs.getBoolean(PREF_AUDIO_ON_CALL, false)) {
                // Only for calls: no polling, but still hear about drops during a call.
                registerAudioReceiver()
            }
        }
    }

    /** Pref change -> call audio. False if [key] is not a call audio pref. */
    fun onSendConfiguration(key: String): Boolean {
        when (key) {
            PREF_AUDIO_PAIR -> pairAudio()
            PREF_AUDIO_UNPAIR -> unpairAudio()
            PREF_AUDIO_AUTO_CONNECT, PREF_AUDIO_ON_CALL -> {
                if (keepaliveEnabled()) {
                    bt3Failures = 0
                    bt3LastRepairAt = 0L
                    bt3WindowOpen = null
                    startBt3Watchdog()
                    if (audioKeepaliveWanted() && audioBondState() == BluetoothDevice.BOND_BONDED) {
                        repairBt3("pref on")
                    }
                } else {
                    stopBt3Watchdog()
                    if (devicePrefs.getBoolean(PREF_AUDIO_ON_CALL, false)) registerAudioReceiver()
                }
            }

            PREF_AUDIO_SCHEDULE,
            PREF_AUDIO_SCHEDULE_START,
            PREF_AUDIO_SCHEDULE_END -> {
                // Re-run the poll now so an edit takes effect and gets logged.
                bt3WindowOpen = null
                if (bt3WatchdogOn) {
                    handler.removeCallbacks(bt3Poll)
                    handler.post(bt3Poll)
                }
                // An edit into the window means audio now: repair right away.
                if (audioKeepaliveWanted() && audioBondState() == BluetoothDevice.BOND_BONDED) {
                    bt3Failures = 0
                    bt3LastRepairAt = 0L
                    repairBt3("schedule change")
                }
            }

            else -> return false
        }
        return true
    }

    /** Callbacks go with the shared handler in [Stratos4ProSupport.dispose]. */
    fun dispose() {
        stopBt3Watchdog()
        callAudioActive = false
        pendingCaller = null
        callerNoticeShown = false
        hfpConnected = false
        audioReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                LOG.debug("audio receiver already unregistered")
            }
            audioReceiver = null
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(Stratos4ProCalls::class.java)

        // Bluetooth Classic call-audio preference keys (phone-side bond, no protocol).
        const val PREF_AUDIO_AUTO_CONNECT = "stratos4pro_audio_auto_connect"
        const val PREF_AUDIO_PAIR = "stratos4pro_audio_pair"
        const val PREF_AUDIO_UNPAIR = "stratos4pro_audio_unpair"
        const val PREF_AUDIO_PAIR_TOKEN = "stratos4pro_audio_pair_token"

        // Optional time window to keep call audio alive in, so the radio is not held up all night.
        const val PREF_AUDIO_SCHEDULE = "stratos4pro_audio_schedule"
        const val PREF_AUDIO_SCHEDULE_START = "stratos4pro_audio_schedule_start"
        const val PREF_AUDIO_SCHEDULE_END = "stratos4pro_audio_schedule_end"

        /** Leave the classic radio to the watch and switch it on only when a call rings. */
        const val PREF_AUDIO_ON_CALL = "stratos4pro_audio_on_call"

        /** Upper bound for one call, in case its end never reaches the driver. */
        const val CALL_AUDIO_MAX_MS = 3 * 60 * 60_000L

        /**
         * Longest the caller frame waits for HFP before it is shown without call audio. Measured
         * radio off -> HFP up: 1.4-3.2 s. The caller notice covers the wait.
         */
        const val CALLER_WAIT_MS = 6_000L

        /** Gap after HFP connects before the caller frame, so the watch has taken the link. */
        const val CALLER_AFTER_HFP_MS = 500L

        // Watchdog. Measured: HFP up 5m00s, then the watch drops the link and clears btSwitch, no
        // BLE push.
        /** Safety-net poll of `eb aa 01` while call audio is enabled. */
        const val BT3_POLL_MS = 5 * 60_000L

        /** Delay between switching the radio on and reading the state back. */
        const val BT3_SETTLE_MS = 3_000L

        /** Delay after a link drop before switching the radio back on. */
        const val BT3_DROP_SETTLE_MS = 2_000L

        /** A drop or a still-off read this soon after a repair means the watch refused it. */
        const val BT3_QUICK_DROP_MS = 30_000L

        /** Minimum gap between repairs, escalating while the watch keeps refusing. */
        val BT3_BACKOFF_MS = longArrayOf(60_000L, 5 * 60_000L, 15 * 60_000L, 30 * 60_000L)

        /** Time for the watch to enter pairing mode before the phone pages it. */
        const val AUDIO_PAIR_MODE_MS = 1_500

        /** Poll interval and cap while waiting for the LE link to come back after removeBond. */
        const val AUDIO_BOND_WAIT_MS = 1_000L
        const val AUDIO_BOND_MAX_WAITS = 20
    }
}
