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
package nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.magic_touch

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.edit
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.magic_touch.MagicTouchMiotSpec
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.magic_touch.parseCloudToken
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.magic_touch.MagicTouchMiotCodec.TYPE_BOOL
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.magic_touch.MagicTouchMiotCodec.TYPE_UINT8
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLESingleDeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.btle.BLETypeConversions
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi_scooters.XiaomiScooterCrypto
import nodomain.freeyourgadget.gadgetbridge.util.CryptoUtils
import nodomain.freeyourgadget.gadgetbridge.util.GB
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.ArrayDeque
import java.util.UUID

/**
 * Mijia Smart Neck and Shoulder Massager (xiaomi.magic_touch.jj02).
 *
 * Xiaomi BLE StandardAuth (no ECDH):
 * - Login channel is UUID 0x0019 for BOTH directions (pre-key echo, SEND,
 *   randoms, confirms all go here as write-commands). UUID 0x0017 exists on
 *   the device but the vendor app never touches it.
 * - 6-byte transport frames are defined in [MagicTouchPacket] (SEND
 *   `00 00 00 <op> 01 00` op 0x0b/0x0a, RCV_RDY/RCV_OK, ACK, OPEN_CMD);
 *   (7-byte variants are silently ignored.)
 * - Session key: HKDF-SHA256(IKM = 12-byte cloud token,
 *   salt = appRandom || devRandom, info = "mible-login-info", L = 64);
 *   in-key [0:16] / out-key [16:32] / in-nonce [32:36] / out-nonce [36:40].
 * - Confirms: devConfirm = HMAC(in-key, devRandom||appRandom),
 *   appConfirm = HMAC(out-key, appRandom||devRandom).
 * - Data: AES-128-CCM, 12-byte nonce (prefix4 || 0000 || u16LE counter),
 *   4-byte tag, wire = u16LE(counter) || CT || tag.
 * - Operation on CMD 0x001a / REPORT 0x001b carries MiOT siid/piid payloads
 *   (see [MagicTouchMiotSpec]); PING `00 00 01 05 01 00` is answered with
 *   PONG `00 00 05 04 01 00`.
 */
class MagicTouchJj02Support : AbstractBTLESingleDeviceSupport(LOG),
    SharedPreferences.OnSharedPreferenceChangeListener {
    private var authStep = AuthStep.IDLE
    private var appRandom: ByteArray? = null
    private var lastDevRandom: ByteArray? = null

    /** Session key material derived from appRandom + lastDevRandom; null
     * until [onDeviceRandom] derives it, invalidated on every re-init. */
    private var authSession: AuthSession? = null
    private var keyExchangeEchoes = 0
    private var appCmdCounter = 0
    private var miotMsgId = 0
    private val cmdQueue = ArrayDeque<ByteArray>()
    private var cmdBusy = false
    private var pendingCmd: ByteArray? = null
    private var pendingCounter = 0

    // The device never pushes the battery level outside charge/unplug events,
    // so poll it on interval when the standard battery polling setting is on.
    private val batteryPollHandler = Handler(Looper.getMainLooper())
    private val batteryPollRunnable = Runnable {
        sendMiotCommand(MagicTouchMiotCodec.buildBatteryLevelGet(nextMsgId()))
        rearmBatteryPoll()
    }

    /** Rearm after login succeeds and after every battery level report. */
    private fun rearmBatteryPoll() {
        batteryPollHandler.removeCallbacks(batteryPollRunnable)
        if (devicePrefs.batteryPollingEnabled) {
            val minutes = devicePrefs.batteryPollingIntervalMinutes
            if (minutes > 0) {
                batteryPollHandler.postDelayed(batteryPollRunnable, minutes * 60_000L)
            }
        }
    }

    /** Tracks whether heat is currently on (from sends + telemetry) so the
     * level set is only paired with heat_on when starting from off. */
    private var heatingActive = false

    /** Last-known power state (0 off, 1 on, 2 on charger, 3 full); see
     * [isCharging] and [syncProperty]. */
    private var lastPower: Int? = null

    /** Last-known battery state from siid 5 piid 2 (0/1 normal, 2 charging);
     * see [emitBatteryEvent] and [syncProperty]. */
    private var lastBatteryStatus: Int? = null

    /** Last-known battery percentage from siid 5 piid 1003. */
    private var lastBatteryPct: Int? = null

    init {
        addSupportedService(UUID_SERVICE_XIAOMI)
    }

    override fun useAutoConnect(): Boolean = true

    override fun onServicesDiscovered(gatt: BluetoothGatt) {
        val sb = StringBuilder("GATT table:")
        for (service: BluetoothGattService in gatt.services) {
            sb.append("\n  service ").append(service.uuid)
            for (characteristic: BluetoothGattCharacteristic in service.characteristics) {
                // The vendor app only ever uses write-commands (0x52).
                if (service.uuid == UUID_SERVICE_XIAOMI) {
                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                }
                sb.append("\n    char ").append(characteristic.uuid)
                    .append(" props=0x").append(Integer.toHexString(characteristic.properties))
            }
        }
        LOG.debug(sb.toString())
        super.onServicesDiscovered(gatt)
    }

    override fun initializeDevice(builder: TransactionBuilder): TransactionBuilder {
        authStep = AuthStep.IDLE
        appRandom = null
        lastDevRandom = null
        authSession = null
        keyExchangeEchoes = 0
        appCmdCounter = 0
        miotMsgId = 0
        cmdQueue.clear()
        cmdBusy = false
        pendingCmd = null
        heatingActive = false
        lastPower = null
        lastBatteryStatus = null
        lastBatteryPct = null

        // Defensive: never stack listeners across reconnects/leaked supports.
        // The guard itself is static so even two live instances cannot ping-pong.
        activeInstance = this
        try {
            devicePrefs().unregisterOnSharedPreferenceChangeListener(this)
        } catch (e: Exception) {
            LOG.debug("Unregister prefs listener failed: {}", e.message)
        }
        devicePrefs().registerOnSharedPreferenceChangeListener(this)

        builder.setDeviceState(GBDevice.State.INITIALIZING)

        // Exact vendor-app order: subscribe report/cmd/info, version query,
        // subscribe the login channel, read firmware, MTU, then AUTH_START.
        // AUTH_CTRL notifications are enabled later, just before LOGIN_START.
        builder.notify(UUID_CHARACTERISTIC_REPORT, true)
        builder.notify(UUID_CHARACTERISTIC_COMMAND, true)
        builder.notify(UUID_CHARACTERISTIC_DEVICE_INFO, true)
        // 0x00 first (device answers 00 01 03, arming the channel), then 0x03.
        builder.write(UUID_CHARACTERISTIC_DEVICE_INFO, 0x00.toByte())
        builder.write(UUID_CHARACTERISTIC_DEVICE_INFO, 0x03.toByte())
        builder.notify(UUID_CHARACTERISTIC_LOGIN, true)
        builder.read(UUID_CHARACTERISTIC_FIRMWARE_VERSION)
        builder.requestMtu(247)

        if (getToken() == null) {
            // Add-first-key-later: stay connected with the unencrypted channels
            // and let login start as soon as the token is saved. The device is
            // not usable until then, so report it as requiring authentication.
            LOG.info("No cloud token configured, skipping login (set it in device settings)")
            builder.setDeviceState(GBDevice.State.AUTHENTICATION_REQUIRED)
            GB.toast(
                context,
                R.string.magic_touch_jj02_bind_key_summary,
                Toast.LENGTH_LONG,
                GB.WARN
            )
            return builder
        }

        // Initialization finishes asynchronously on LOGIN_OK -- see the login
        // handshake section below (and onLoginOk()).
        startLogin(builder)
        return builder
    }

    override fun dispose() {
        batteryPollHandler.removeCallbacksAndMessages(null)
        try {
            devicePrefs().unregisterOnSharedPreferenceChangeListener(this)
        } catch (e: Exception) {
            LOG.debug("Unregister prefs listener failed: {}", e.message)
        }
        if (activeInstance === this) {
            activeInstance = null
        }
        super.dispose()
    }

    /**
     * Single entry for UI commands (card icons via ConfigurationAction,
     * dashboard widgets). Values encode in the key; toggles/cycles read
     * last-known state. All sends go through the gated setX helpers.
     */
    override fun onSendConfiguration(config: String) {
        LOG.debug("onSendConfiguration: {}", config)
        if (activeInstance !== this) {
            LOG.warn("Ignoring config from stale instance")
            return
        }
        if (authStep != AuthStep.DONE) {
            notReady()
            return
        }
        val prefs = devicePrefs()
        when (config) {
            "power" -> setPower(prefs.getBoolean(PREF_POWER, false))
            "mode" -> {
                val m = prefs.getString(PREF_MODE, null)?.toIntOrNull() ?: 1
                if (isValidValue(MagicTouchMiotSpec.PID_MODE, m)) setMode(m)
            }

            "heat" -> {
                val on = prefs.getBoolean(PREF_HEATING, false)
                val level = prefs.getString(PREF_HEAT_LEVEL, null)?.toIntOrNull() ?: 1
                if (!on) {
                    setHeating(false)
                    heatingActive = false
                } else {
                    if (!heatingActive) {
                        setHeating(true)
                        heatingActive = true
                    }
                    if (isValidValue(MagicTouchMiotSpec.PID_HEAT_LEVEL, level)) setHeatLevel(level)
                }
            }

            "level" -> {
                val l = prefs.getString(PREF_HEAT_LEVEL, null)?.toIntOrNull() ?: 1
                if (isValidValue(MagicTouchMiotSpec.PID_HEAT_LEVEL, l)) setHeatLevel(l)
            }

            "timer" -> {
                val t = prefs.getString(PREF_TIMER, null)?.toIntOrNull() ?: 15
                if (isValidValue(MagicTouchMiotSpec.PID_TARGET_TIME, t)) setTimerMinutes(t)
            }

            PREF_CUSTOM_NAME -> {
                val name = prefs.getString(PREF_CUSTOM_NAME, "")?.trim() ?: ""
                if (name.isNotEmpty()) {
                    try {
                        setCustomName(name)
                    } catch (e: IllegalArgumentException) {
                        LOG.warn("Invalid custom name: {}", e.message)
                    }
                }
            }

            PREF_DEFAULT_MODE -> {
                val m = prefs.getString(PREF_DEFAULT_MODE, null)?.toIntOrNull() ?: 1
                if (isValidValue(MagicTouchMiotSpec.PID_DEFAULT_MODE, m)) setDefaultMode(m)
            }

            PREF_BEEP -> setDefaultBeep(prefs.getBoolean(PREF_BEEP, true))
            PREF_HEAD_INIT -> {
                val h = prefs.getString(PREF_HEAD_INIT, null)?.toIntOrNull() ?: 1
                if (isValidValue(MagicTouchMiotSpec.PID_MASSAGE_HEAD_INIT, h)) setHeadInit(h)
            }

            else -> LOG.warn("Unknown configuration key: {}", config)
        }
    }

    /** The device only pushes property changes and battery during
     * charge/unplug; everything else is read on demand. The settings screen
     * triggers this via [nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.magic_touch.MagicTouchSettingsCustomizer]
     * (rootKey == null). */
    override fun onReadConfiguration(config: String?) {
        if (activeInstance !== this) {
            LOG.warn("Ignoring config read from stale instance")
            return
        }
        if (authStep != AuthStep.DONE) {
            notReady()
            return
        }
        refreshAll()
    }

    private fun devicePrefs(): SharedPreferences {
        return GBApplication.getDeviceSpecificSharedPrefs(gbDevice.address)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ): Boolean {
        when (characteristic.uuid) {
            UUID_CHARACTERISTIC_AUTH_CTRL -> {
                handleAuthCtrl(value)
                return true
            }

            UUID_CHARACTERISTIC_LOGIN -> {
                handleLoginChannel(value)
                return true
            }

            UUID_CHARACTERISTIC_COMMAND -> {
                handleCommandChannel(value)
                return true
            }

            UUID_CHARACTERISTIC_REPORT -> {
                handleReport(value)
                return true
            }

            else -> {
                LOG.warn("NOTIFY {} len={}: {}", characteristic.uuid, value.size, GB.hexdump(value))
                return true
            }
        }
    }

    // =======================================================================
    // Login handshake (Xiaomi BLE StandardAuth) -- complete phone <-> device flow,
    // in capture order. Sends are write-commands on the login channel (UUID 0x0019);
    // AUTH_START is the only write to AUTH_CTRL (0x0010).
    // Notation: "->" = phone writes, "<-" = device notifies.
    // The right column names the handler in this file and the resulting auth step;
    // [handleLoginChannel] does the actual frame-to-state dispatch.
    //
    //   -> AUTH_START 0xa4            startLogin(builder)
    //                                 -> EXPECT_KEY_EXCHANGE
    //
    //   <- 00 00 04 <pre-key PDU>     handleLoginChannel(): echo as 00 00 05
    //   -> 00 00 05 <echo>
    //   <- 00 00 04 <pre-key PDU>
    //   -> 00 00 05 <echo>            sendLoginStart()
    //                                 -> EXPECT_SEND_RDY
    //
    //   <- RCV_RDY (00 00 01 01)      sendAppRandom()
    //   -> 01 00 <appRandom>
    //                                 -> EXPECT_DEVICE_RANDOM
    //
    //   <- 00 00 02 0d <devRandom>    onDeviceRandom()
    //   -> ACK                        + HKDF session/nonces
    //                                 -> EXPECT_DEVICE_CONFIRM
    //
    //   <- 00 00 02 0c <devConfirm>   onDeviceConfirm()
    //   -> ACK, SEND_INFO             + verify HMAC(in, dev||app)
    //                                 -> EXPECT_CONFIRM_RDY
    //
    //   <- RCV_RDY                    sendAppConfirm()
    //   -> 01 00 <appConfirm>         + HMAC(out, app||dev)
    //                                 -> EXPECT_CONFIRM_OK
    //
    //   <- RCV_OK (00 00 01 00)       -> EXPECT_LOGIN_OK
    //
    //   <- LOGIN_OK (21 00 00 00)     onLoginOk()
    //                                 -> DONE -> INITIALIZED -> refreshAll()
    //   <- LOGIN_FAIL (22 00 00 00)   authFailed(): disconnect
    // =======================================================================

    /** Login state machine */
    private enum class AuthStep {
        /** Connected, not logging in yet (login is triggered below). */
        IDLE,

        /** AUTH_START sent; echoing the device pre-key PDUs. */
        EXPECT_KEY_EXCHANGE,

        /** LOGIN_START + SEND_KEY sent; waiting for RCV_RDY. */
        EXPECT_SEND_RDY,

        /** appRandom sent; waiting for the device random. */
        EXPECT_DEVICE_RANDOM,

        /** Session keys derived; waiting for the device confirm. */
        EXPECT_DEVICE_CONFIRM,

        /** devConfirm accepted, ACK + SEND_INFO sent; waiting for RCV_RDY. */
        EXPECT_CONFIRM_RDY,

        /** appConfirm sent; waiting for RCV_OK. */
        EXPECT_CONFIRM_OK,

        /** Waiting for LOGIN_OK / LOGIN_FAIL on AUTH_CTRL. */
        EXPECT_LOGIN_OK,

        /** Session established; encrypted operation channel active. */
        DONE,
    }

    /**
     * Step 1 of the login handshake: send AUTH_START, then wait for the device
     * pre-key exchange (EXPECT_KEY_EXCHANGE). Login continues in
     * [handleLoginChannel] and finishes asynchronously on LOGIN_OK.
     * [initializeDevice] writes into its own transaction; the standalone
     * overload below is used when the token is saved after connecting.
     */
    private fun startLogin(builder: TransactionBuilder) {
        authStep = AuthStep.EXPECT_KEY_EXCHANGE
        keyExchangeEchoes = 0
        builder.write(UUID_CHARACTERISTIC_AUTH_CTRL, MagicTouchPacket.AUTH_START)
    }

    /** Kick off the login on an already-connected device (token was set later). */
    private fun startLogin() {
        val builder = createTransactionBuilder("magic_touch_start_login")
        startLogin(builder)
        builder.queue()
    }

    private fun handleAuthCtrl(value: ByteArray) {
        when (MagicTouchPacket.decode(value)?.first) {
            MagicTouchPacket.LOGIN_OK -> onLoginOk()
            MagicTouchPacket.LOGIN_FAIL -> authFailed("device rejected login (LOGIN_FAIL)")
            else -> logUnhandledFrame("AUTH_CTRL", value)
        }
    }

    /** Uniform warn for notify frames that match no known packet or state. */
    private fun logUnhandledFrame(channel: String, value: ByteArray) {
        LOG.warn("Unhandled {} frame in step {}: {}", channel, authStep, GB.hexdump(value))
    }

    private fun handleLoginChannel(value: ByteArray) {
        val (pkt, payload) = MagicTouchPacket.decode(value) ?: run {
            logUnhandledFrame("login", value)
            return
        }
        when (pkt) {
            MagicTouchPacket.PREKEY_PUSH -> {
                // Pre-key exchange: echo back with type 04 -> 05 on the SAME channel.
                if (authStep != AuthStep.EXPECT_KEY_EXCHANGE) {
                    LOG.warn("Unexpected key-exchange PDU in step {}", authStep)
                    return
                }
                write(UUID_CHARACTERISTIC_LOGIN, MagicTouchPacket.PREKEY_ECHO, payload)
                keyExchangeEchoes++
                // The second PDU (index byte 0x01) completes it.
                if ((payload.isNotEmpty() && payload[0] == 0x01.toByte()) || keyExchangeEchoes >= 2) {
                    sendLoginStart()
                }
            }

            MagicTouchPacket.DEVICE_RANDOM -> {
                if (authStep != AuthStep.EXPECT_DEVICE_RANDOM) {
                    LOG.warn("Unexpected device random in step {}", authStep)
                    return
                }
                onDeviceRandom(payload)
            }

            MagicTouchPacket.DEVICE_CONFIRM -> {
                if (authStep != AuthStep.EXPECT_DEVICE_CONFIRM) {
                    LOG.warn("Unexpected device confirm in step {}", authStep)
                    return
                }
                onDeviceConfirm(payload)
            }

            MagicTouchPacket.RCV_RDY -> when (authStep) {
                AuthStep.EXPECT_SEND_RDY -> sendAppRandom()
                AuthStep.EXPECT_CONFIRM_RDY -> sendAppConfirm()
                else -> LOG.warn("Unexpected RCV_RDY in auth step {}", authStep)
            }

            MagicTouchPacket.RCV_OK -> {
                if (authStep == AuthStep.EXPECT_CONFIRM_OK) {
                    authStep = AuthStep.EXPECT_LOGIN_OK
                }
                LOG.debug("RCV_OK in step {}", authStep)
            }

            MagicTouchPacket.PING -> {
                // Device keepalive: answer or it stops talking on long sessions.
                write(UUID_CHARACTERISTIC_LOGIN, MagicTouchPacket.PONG)
            }

            else -> logUnhandledFrame("login", value)
        }
    }

    /**
     * Step 3 of the login: called after both device pre-key PDUs were echoed.
     * Vendor order: AUTH_CTRL notifications only now, then LOGIN_START and
     * SEND back-to-back in one transaction (order + pacing matter). The
     * device then replies with RCV_RDY, which drives [sendAppRandom].
     */
    private fun sendLoginStart() {
        authStep = AuthStep.EXPECT_SEND_RDY
        createTransactionBuilder("magic_touch_send_login_start")
            .notify(UUID_CHARACTERISTIC_AUTH_CTRL, true)
            .sleep(50)
            .write(UUID_CHARACTERISTIC_AUTH_CTRL, MagicTouchPacket.LOGIN_START)
            .sleep(15)
            .write(UUID_CHARACTERISTIC_LOGIN, MagicTouchPacket.SEND_KEY)
            .queue()
    }

    private fun sendAppRandom() {
        val random = ByteArray(16)
        SecureRandom().nextBytes(random)
        appRandom = random
        // Never log the random itself.
        write(UUID_CHARACTERISTIC_LOGIN, MagicTouchPacket.PREFIX_DATA, random)
        authStep = AuthStep.EXPECT_DEVICE_RANDOM
    }

    private fun onDeviceRandom(payload: ByteArray) {
        if (payload.size != 16) {
            LOG.warn("Bad devRandom length {}", payload.size)
            return
        }
        val appR = appRandom
        if (appR == null) {
            LOG.warn("devRandom before appRandom, ignoring")
            return
        }
        lastDevRandom = payload
        write(UUID_CHARACTERISTIC_LOGIN, MagicTouchPacket.ACK)
        val token = getToken()
        if (token == null) {
            authFailed("no 12-byte cloud token configured, cannot derive session key")
            return
        }
        try {
            val okm = CryptoUtils.hkdfSha256(
                token,
                appR + payload,
                HKDF_INFO.toByteArray(Charsets.US_ASCII),
                64,
            )
            authSession = AuthSession(
                inKey = okm.copyOfRange(0, 16),
                outKey = okm.copyOfRange(16, 32),
                inPrefix = okm.copyOfRange(32, 36),
                outPrefix = okm.copyOfRange(36, 40),
            )
        } catch (e: Exception) {
            authFailed("session key derivation failed: ${e.message}")
            return
        }
        authStep = AuthStep.EXPECT_DEVICE_CONFIRM
    }

    private fun onDeviceConfirm(payload: ByteArray) {
        if (payload.size != 32) {
            LOG.warn("Bad devConfirm length {}", payload.size)
            return
        }
        val session = authSession
        val appR = appRandom
        val devR = lastDevRandom
        if (session == null || appR == null || devR == null) {
            LOG.warn("devConfirm with no session, ignoring")
            return
        }
        val expected = try {
            CryptoUtils.calcHmacSha256(session.inKey, devR + appR)
        } catch (_: Exception) {
            null
        }
        if (expected == null || !MessageDigest.isEqual(expected, payload)) {
            authFailed("device confirm MAC mismatch (wrong token?)")
            return
        }
        LOG.warn("Device confirm matches")
        write(UUID_CHARACTERISTIC_LOGIN, MagicTouchPacket.ACK)
        write(UUID_CHARACTERISTIC_LOGIN, MagicTouchPacket.SEND_INFO)
        authStep = AuthStep.EXPECT_CONFIRM_RDY
    }

    private fun sendAppConfirm() {
        val session = authSession
        val appR = appRandom
        val devR = lastDevRandom
        if (session == null || appR == null || devR == null) {
            authFailed("cannot build app confirm (no session)")
            return
        }
        val confirm = try {
            CryptoUtils.calcHmacSha256(session.outKey, appR + devR)
        } catch (_: Exception) {
            null
        }
        if (confirm == null) {
            authFailed("cannot build app confirm")
            return
        }
        write(UUID_CHARACTERISTIC_LOGIN, MagicTouchPacket.PREFIX_DATA, confirm)
        authStep = AuthStep.EXPECT_CONFIRM_OK
    }

    private fun onLoginOk() {
        LOG.warn("Login successful, session established")
        authStep = AuthStep.DONE
        gbDevice.setUpdateState(GBDevice.State.INITIALIZED, context)
        // Read-only refresh so live state shows up immediately (capability,
        // full property set, then standalone battery level -- the vendor app
        // does the same on every connect). Battery arrives as a
        // GBDeviceEventBatteryInfo from the decrypted reports.
        refreshAll()
        rearmBatteryPoll()
    }

    private fun authFailed(reason: String) {
        LOG.error("Authentication failed: {}", reason)
        authStep = AuthStep.IDLE
        GB.toast(context, R.string.authentication_failed_check_key, Toast.LENGTH_LONG, GB.WARN)
        GBApplication.deviceService(device)?.disconnect()
    }

    // =======================================================================
    // Encrypted operation channel (CMD 0x001a / REPORT 0x001b)
    // =======================================================================

    /**
     * Queue a plaintext MiOT command for sending. Commands go out one at a
     * time (OPEN -> RCV_RDY -> frame -> RCV_OK), exactly like the vendor app.
     */
    fun sendMiotCommand(plaintext: ByteArray) {
        if (activeInstance !== this) {
            return
        }
        cmdQueue.addLast(plaintext)
        pumpCommandQueue()
    }

    /** On charger (battery status 2, or power state 2): the device refuses
     * all control writes. Refreshed on every bulk read via power. */
    private fun isCharging(): Boolean = lastBatteryStatus == 2 || lastPower == 2

    private fun isPoweredOn(): Boolean {
        return try {
            devicePrefs().getBoolean(PREF_POWER, false)
        } catch (_: Exception) {
            false
        }
    }

    private fun requirePoweredOn(): Boolean {
        if (!isPoweredOn()) {
            GB.toast(context, R.string.magic_touch_turn_on_first, Toast.LENGTH_LONG, GB.WARN)
            return false
        }
        return true
    }

    private fun requireNotCharging(): Boolean {
        if (isCharging()) {
            GB.toast(context, R.string.magic_touch_unplug_charger, Toast.LENGTH_LONG, GB.WARN)
            return false
        }
        return true
    }

    fun setPower(on: Boolean) {
        if (!requireNotCharging()) return
        sendSet(MagicTouchMiotSpec.PID_ON, TYPE_UINT8, if (on) 1 else 0)
    }

    fun setMode(mode: Int) {
        require(mode in 1..4) { "mode must be 1..4" }
        if (!requireNotCharging() || !requirePoweredOn()) return
        sendSet(MagicTouchMiotSpec.PID_MODE, TYPE_UINT8, mode)
    }

    fun setHeating(on: Boolean) {
        if (!requireNotCharging() || !requirePoweredOn()) return
        sendSet(MagicTouchMiotSpec.PID_HEATING, TYPE_BOOL, if (on) 1 else 0)
    }

    fun setHeatLevel(level: Int) {
        require(level in 1..2) { "heat level must be 1 (40C) or 2 (45C)" }
        if (!requireNotCharging() || !requirePoweredOn()) return
        sendSet(MagicTouchMiotSpec.PID_HEAT_LEVEL, TYPE_UINT8, level)
    }

    fun setTimerMinutes(minutes: Int) {
        require(isValidValue(MagicTouchMiotSpec.PID_TARGET_TIME, minutes)) { "timer must be 2..20 minutes" }
        if (!requireNotCharging() || !requirePoweredOn()) return
        sendSet(MagicTouchMiotSpec.PID_TARGET_TIME, TYPE_UINT8, minutes)
    }

    /** Default mode used at power-on (1–4, same values as mode). */
    fun setDefaultMode(mode: Int) {
        require(mode in 1..4) { "default mode must be 1..4" }
        if (!requireNotCharging() || !requirePoweredOn()) return
        sendSet(MagicTouchMiotSpec.PID_DEFAULT_MODE, TYPE_UINT8, mode)
    }

    /** Default beep on/off (bool wire type, like heating). */
    fun setDefaultBeep(on: Boolean) {
        if (!requireNotCharging() || !requirePoweredOn()) return
        sendSet(MagicTouchMiotSpec.PID_DEFAULT_BEEP_MODE, TYPE_BOOL, if (on) 1 else 0)
    }

    /** Massage head init (0 reserve, 1 normal, 2 more-better). */
    fun setHeadInit(mode: Int) {
        require(mode in 0..2) { "head init must be 0..2" }
        if (!requireNotCharging() || !requirePoweredOn()) return
        sendSet(MagicTouchMiotSpec.PID_MASSAGE_HEAD_INIT, TYPE_UINT8, mode)
    }

    /**
     * Rename the custom massage (customName 4/11, string). Live framing from
     * the vendor app (string marker 0xa0; length before marker;
     * `01 a0 <len>` and `01 0a <len>` forms are ignored/corrupt — do not use):
     * `LL 20 MM MM 00 01 04 0b 00 01 <len> a0 <utf8>`
     */
    fun setCustomName(name: String) {
        val bytes = name.toByteArray(Charsets.UTF_8)
        require(bytes.isNotEmpty() && bytes.size <= 32) { "name must be 1..32 UTF-8 bytes" }
        if (!requireNotCharging() || !requirePoweredOn()) return
        sendMiotCommand(
            MagicTouchMiotCodec.buildCommand(
                nextMsgId(),
                byteArrayOf(
                    MagicTouchMiotCodec.OP_SET.toByte(), 0x01.toByte(),
                    MagicTouchMiotSpec.SIID_MASSAGER_CONTROL.toByte(),
                    MagicTouchMiotSpec.PID_CUSTOM_NAME.toByte(), 0x00.toByte(),
                    0x01.toByte(), bytes.size.toByte(), 0xa0.toByte(),
                ) + bytes
            )
        )
    }

    fun refreshAll() {
        sendMiotCommand(MagicTouchMiotCodec.buildCapability())
        sendMiotCommand(MagicTouchMiotCodec.buildBulkGet(nextMsgId()))
        sendMiotCommand(MagicTouchMiotCodec.buildBatteryLevelGet(nextMsgId()))
    }

    private fun pumpCommandQueue() {
        if (cmdBusy || cmdQueue.isEmpty() || authStep != AuthStep.DONE) {
            return
        }
        cmdBusy = true
        pendingCmd = cmdQueue.removeFirst()
        pendingCounter = appCmdCounter
        write(UUID_CHARACTERISTIC_COMMAND, MagicTouchPacket.OPEN_CMD)
    }

    private fun handleCommandChannel(value: ByteArray) {
        when (MagicTouchPacket.decode(value)?.first) {
            MagicTouchPacket.RCV_RDY -> {
                val plain = pendingCmd
                val session = authSession
                if (!cmdBusy || plain == null || session == null) {
                    LOG.warn("Unexpected CMD RCV_RDY (no pending command)")
                    return
                }
                val counterBytes = BLETypeConversions.fromUint16(pendingCounter)
                val ct = XiaomiScooterCrypto.encryptCcm(
                    session.outKey,
                    XiaomiScooterCrypto.buildNonce(session.outPrefix, pendingCounter),
                    plain,
                )
                write(UUID_CHARACTERISTIC_COMMAND, MagicTouchPacket.PREFIX_DATA, counterBytes + ct)
                appCmdCounter = (pendingCounter + 1) and 0xffff
            }

            MagicTouchPacket.RCV_OK -> {
                pendingCmd = null
                cmdBusy = false
                pumpCommandQueue()
            }

            else -> logUnhandledFrame("CMD", value)
        }
    }

    private fun handleReport(value: ByteArray) {
        val decoded = MagicTouchPacket.decode(value)
        if (decoded == null || decoded.first != MagicTouchPacket.REPORT || decoded.second.size < 3) {
            logUnhandledFrame("REPORT", value)
            return
        }
        val reportPayload = decoded.second
        write(UUID_CHARACTERISTIC_REPORT, MagicTouchPacket.ACK)
        if (authStep != AuthStep.DONE) {
            return
        }
        val session = authSession
        if (session == null) {
            LOG.warn("Encrypted report with no session")
            return
        }
        val counter = BLETypeConversions.toUint16(reportPayload, 0)
        try {
            val pt = XiaomiScooterCrypto.decryptCcm(
                session.inKey,
                XiaomiScooterCrypto.buildNonce(session.inPrefix, counter),
                reportPayload.copyOfRange(2, reportPayload.size),
            )
            LOG.debug("REPORT ctr={} {}", counter, decodeMiotReport(pt))
            syncPrefsFromTelemetry(pt)
            try {
                gbDevice.sendDeviceUpdateIntent(context)
            } catch (e: Exception) {
                LOG.debug("sendDeviceUpdateIntent failed: {}", e.message)
            }
        } catch (e: Exception) {
            LOG.warn("Failed to decrypt report ctr={}: {}", counter, e.message)
        }
    }

    // =======================================================================
    // MiOT payload glue (builders and parseShort live in MagicTouchMiotCodec)
    // =======================================================================

    /** Payload/message id for the next queued frame (wraps like the vendor app). */
    private fun nextMsgId(): Int = miotMsgId++ and 0xffff

    /** Build and queue a set-properties command. */
    private fun sendSet(piid: Int, typeByte: Int, value: Int) {
        sendMiotCommand(MagicTouchMiotCodec.buildSet(nextMsgId(), piid, typeByte, value))
    }

    /** Device reports battery level as raw 0..20 steps of 5% each (e.g. 6->30);
     * values above 20 pass through as a direct percentage. */
    private fun batteryLevelPct(raw: Int): Int = if (raw in 0..20) raw * 5 else raw

    /** Best-effort human-readable rendering of a decrypted MiOT report. */
    private fun decodeMiotReport(pt: ByteArray): String {
        if (pt.size < 4 || pt[1] != 0x20.toByte()) {
            return GB.hexdump(pt)
        }
        val msgId = BLETypeConversions.toUint16(pt, 2)
        val a0 = pt.indexOf(0xa0.toByte())
        if (a0 >= 0 && a0 + 1 < pt.size) {
            val str = try {
                String(pt.copyOfRange(a0 + 1, pt.size), Charsets.US_ASCII)
            } catch (_: Exception) {
                "?"
            }
            return "msg=$msgId str=$str ${GB.hexdump(pt)}"
        }
        val short = MagicTouchMiotCodec.parseShort(pt) ?: return "msg=$msgId ${GB.hexdump(pt)}"
        val (siid, piid, valByte) = short
        if (siid == MagicTouchMiotSpec.SIID_BATTERY && piid == MagicTouchMiotSpec.PID_STATUS) {
            return "msg=$msgId battery-status=$valByte ${GB.hexdump(pt)}"
        }
        if (siid == MagicTouchMiotSpec.SIID_BATTERY && piid == MagicTouchMiotSpec.PID_BATTERY_LEVEL) {
            val pct = batteryLevelPct(valByte)
            return "msg=$msgId battery-level=$pct% (raw=$valByte) ${GB.hexdump(pt)}"
        }
        if (siid != MagicTouchMiotSpec.SIID_MASSAGER_CONTROL) {
            return "msg=$msgId siid=$siid piid=$piid val=$valByte ${GB.hexdump(pt)}"
        }
        val ok = isValidValue(piid, valByte)
        return "msg=$msgId ${propName(piid)}=$valByte${if (ok) "" else " (INVALID)"} ${GB.hexdump(pt)}"
    }

    private fun propName(piid: Int): String = when (piid) {
        MagicTouchMiotSpec.PID_MODE -> "mode(1=Gentle,2=Relax,3=Stress,4=Custom)"
        MagicTouchMiotSpec.PID_DEFAULT_MODE -> "default-mode"
        MagicTouchMiotSpec.PID_TARGET_TIME -> "timer-min"
        MagicTouchMiotSpec.PID_COUNTDOWN -> "countdown-min"
        MagicTouchMiotSpec.PID_HEATING -> "heating"
        MagicTouchMiotSpec.PID_HEAT_LEVEL -> "heat-level(1=40C,2=45C)"
        MagicTouchMiotSpec.PID_DEFAULT_HEAT_MODE -> "default-heat-mode"
        MagicTouchMiotSpec.PID_DEFAULT_BEEP_MODE -> "beep"
        MagicTouchMiotSpec.PID_MASSAGE_MANIPULATION -> "manipulation"
        MagicTouchMiotSpec.PID_ON -> "power(0=off,1=on)"
        MagicTouchMiotSpec.PID_CUSTOM_NAME -> "name"
        MagicTouchMiotSpec.PID_MASSAGE_HEAD_INIT -> "head-init"
        else -> "piid=$piid"
    }

    // =======================================================================
    // Device settings card (add-first-key-later + two-way sync)
    // =======================================================================

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        if (syncingPrefs) {
            return
        }
        if (activeInstance !== this) {
            // Stale support (leaked across reconnect/update): never send.
            return
        }
        // Settings-screen prefs send via categories below; card/widget prefs
        // are written first by their own UI then call categories directly.
        // Telemetry syncs (guarded above) never reach here.
        if (key == DeviceSettingsPreferenceConst.PREF_AUTH_KEY) {
            if (isConnected && authStep == AuthStep.IDLE) {
                LOG.warn("Token saved, starting login")
                startLogin()
            }
            return
        }
        // The preference keys double as the configuration keys, so they can be
        // forwarded as-is.
        when (key) {
            PREF_CUSTOM_NAME, PREF_DEFAULT_MODE, PREF_BEEP, PREF_HEAD_INIT ->
                onSendConfiguration(key)
        }
    }

    private fun notReady() {
        GB.toast(context, R.string.device_not_connected, Toast.LENGTH_LONG, GB.WARN)
    }

    private fun setPrefBoolean(key: String, v: Boolean) {
        syncingPrefs = true
        try {
            devicePrefs().edit { putBoolean(key, v) }
        } finally {
            syncingPrefs = false
        }
    }

    private fun setPrefString(key: String, v: String) {
        syncingPrefs = true
        try {
            devicePrefs().edit { putString(key, v) }
        } finally {
            syncingPrefs = false
        }
    }

    /** Mirror device truth into the settings card (guarded, never loops back).
     * Out-of-range values are ignored, never written (a malformed decode must
     * not flip the card and re-trigger a command). */
    private fun syncPrefsFromTelemetry(pt: ByteArray) {
        if (pt.size < 4 || pt[1] != 0x20.toByte()) {
            return
        }
        if (pt.size > 20) {
            syncBulk(pt)
            return
        }
        val short = MagicTouchMiotCodec.parseShort(pt) ?: return
        val (siid, piid, valByte) = short
        if (siid == MagicTouchMiotSpec.SIID_BATTERY && piid == MagicTouchMiotSpec.PID_STATUS) {
            if (valByte in 0..2) {
                lastBatteryStatus = valByte
                emitBatteryEvent()
            }
            return
        }
        if (siid == MagicTouchMiotSpec.SIID_BATTERY && piid == MagicTouchMiotSpec.PID_BATTERY_LEVEL) {
            val pct = batteryLevelPct(valByte)
            if (pct in 0..100) {
                lastBatteryPct = pct
                emitBatteryEvent()
            }
            return
        }
        if (siid != MagicTouchMiotSpec.SIID_MASSAGER_CONTROL || !isValidValue(piid, valByte)) {
            return
        }
        syncProperty(piid, valByte)
    }

    /** Value ranges accepted from the device (and usable for set commands). */
    private fun validRange(piid: Int): IntRange? = when (piid) {
        MagicTouchMiotSpec.PID_ON -> 0..3
        MagicTouchMiotSpec.PID_MODE -> 1..4
        MagicTouchMiotSpec.PID_DEFAULT_MODE -> 1..4
        MagicTouchMiotSpec.PID_HEATING -> 0..1
        MagicTouchMiotSpec.PID_HEAT_LEVEL -> 1..2
        MagicTouchMiotSpec.PID_TARGET_TIME -> MagicTouchMiotSpec.PID_TARGET_TIME_MIN..MagicTouchMiotSpec.PID_TARGET_TIME_MAX
        MagicTouchMiotSpec.PID_COUNTDOWN -> MagicTouchMiotSpec.PID_TARGET_TIME_MIN..MagicTouchMiotSpec.PID_TARGET_TIME_MAX
        MagicTouchMiotSpec.PID_DEFAULT_BEEP_MODE -> 0..1
        MagicTouchMiotSpec.PID_MASSAGE_HEAD_INIT -> 0..2
        else -> null
    }

    private fun isValidValue(piid: Int, v: Int): Boolean = validRange(piid)?.contains(v) == true

    /**
     * Publishes the last-known battery level/state to Gadgetbridge. Battery is
     * device-provided state, not a setting, so it is never persisted to prefs.
     */
    private fun emitBatteryEvent() {
        val event = GBDeviceEventBatteryInfo()
        event.level = lastBatteryPct ?: GBDevice.BATTERY_UNKNOWN.toInt()
        event.state = when (lastBatteryStatus) {
            2 -> BatteryState.BATTERY_CHARGING
            0, 1 -> BatteryState.BATTERY_NORMAL
            else -> BatteryState.UNKNOWN
        }
        evaluateGBDeviceEvent(event)
    }

    /** Bulk get-properties response: `04 <piid> 00` markers, 8-byte numeric
     * entries (value = last byte), string entry for piid 11. */
    private fun syncBulk(pt: ByteArray) {
        var i = 0
        while (i + 2 < pt.size) {
            // Property entries are siid + 2-byte LE piid; 1-byte ids imply <id> 00.
            if (pt[i] == MagicTouchMiotSpec.SIID_MASSAGER_CONTROL.toByte() && pt[i + 2] == 0x00.toByte()) {
                val piid = pt[i + 1].toInt() and 0xff
                if (piid == MagicTouchMiotSpec.PID_CUSTOM_NAME) {
                    // `04 0b 00 00 00 <len> a0 <str>`: mirror into settings.
                    if (i + 6 < pt.size && pt[i + 6] == 0xa0.toByte()) {
                        val len = pt[i + 5].toInt() and 0xff
                        val end = (i + 7 + len).coerceAtMost(pt.size)
                        val name = try {
                            String(pt.copyOfRange(i + 7, end), Charsets.US_ASCII)
                        } catch (_: Exception) {
                            "?"
                        }
                        LOG.debug("Device custom massage name: {}", name)
                        if (name.isNotEmpty() && name != "?") {
                            setPrefString(PREF_CUSTOM_NAME, name)
                        }
                    }
                } else if (piid in MagicTouchMiotSpec.PID_MODE..MagicTouchMiotSpec.PID_MASSAGE_HEAD_INIT && i + 7 < pt.size) {
                    syncProperty(piid, pt[i + 7].toInt() and 0xff)
                }
            }
            i++
        }
    }

    private fun syncProperty(piid: Int, v: Int) {
        if (!isValidValue(piid, v)) {
            LOG.warn("Ignoring out-of-range piid={} val={}", piid, v)
            return
        }
        // Power=2 means on charger: refresh the charging interlock on every
        // bulk read, since siid-5 status only pushes on plug/unplug events.
        // Power 0/1 proves disconnected, so a stuck charging flag is cleared.
        if (piid == MagicTouchMiotSpec.PID_ON) {
            lastPower = v
            if (v == 2 && lastBatteryStatus != 2) {
                lastBatteryStatus = 2
                emitBatteryEvent()
            } else if ((v == 0 || v == 1) && lastBatteryStatus == 2) {
                lastBatteryStatus = null
                emitBatteryEvent()
            }
        }
        when (piid) {
            MagicTouchMiotSpec.PID_ON -> setPrefBoolean(PREF_POWER, v == 1)
            MagicTouchMiotSpec.PID_MODE -> setPrefString(PREF_MODE, v.toString())
            MagicTouchMiotSpec.PID_HEATING -> {
                setPrefBoolean(PREF_HEATING, v == 1)
                heatingActive = v == 1
            }

            MagicTouchMiotSpec.PID_HEAT_LEVEL -> setPrefString(PREF_HEAT_LEVEL, v.toString())
            MagicTouchMiotSpec.PID_TARGET_TIME -> setPrefString(PREF_TIMER, v.toString())
            MagicTouchMiotSpec.PID_DEFAULT_MODE -> setPrefString(PREF_DEFAULT_MODE, v.toString())
            MagicTouchMiotSpec.PID_DEFAULT_BEEP_MODE -> setPrefBoolean(PREF_BEEP, v == 1)
            MagicTouchMiotSpec.PID_MASSAGE_HEAD_INIT -> setPrefString(PREF_HEAD_INIT, v.toString())
            // Countdown, manipulation strings: decoded + logged, no UI target.
        }
    }

    // =======================================================================
    // Helpers
    // =======================================================================

    private fun write(characteristic: UUID, data: ByteArray) {
        createTransactionBuilder("magic_touch_write_$characteristic")
            .write(characteristic, *data)
            .sleep(30)
            .queue()
    }

    /** Convenience overload: write a packet frame, optionally with payload. */
    private fun write(
        characteristic: UUID,
        packet: MagicTouchPacket,
        payload: ByteArray = ByteArray(0)
    ) {
        write(characteristic, packet.frame + payload)
    }

    /**
     * 12-byte cloud token (24 hex chars) from the standard authkey slot
     * (AuthKeyActivity + settings). This is the Mi Home "token", NOT the
     * 16-byte BLE bind key -- only the token derives the session key.
     */
    private fun getToken(): ByteArray? {
        val prefs = GBApplication.getDeviceSpecificSharedPrefs(gbDevice.address)
        val authKey = prefs?.getString(DeviceSettingsPreferenceConst.PREF_AUTH_KEY, "") ?: ""
        val token = parseCloudToken(authKey)
        if (token == null) {
            LOG.error("No 12-byte cloud token configured")
        }
        return token
    }

    /** Session key material derived from the 12-byte cloud token and the
     * exchanged randoms (HKDF-SHA256, 64 output bytes): in-key [0:16],
     * out-key [16:32], in-nonce prefix [32:36], out-nonce prefix [36:40].
     * Held as one immutable object so call sites can smart-cast from the
     * nullable [authSession] property with a single local `val session`. */
    private class AuthSession(
        val inKey: ByteArray,
        val outKey: ByteArray,
        val inPrefix: ByteArray,
        val outPrefix: ByteArray,
    )

    companion object {
        private val LOG = LoggerFactory.getLogger(MagicTouchJj02Support::class.java)

        /**
         * Static re-entrancy guard: telemetry syncs must never re-trigger sends,
         * even if two support instances (e.g. across a reconnect) are alive.
         * A per-instance flag allowed ping-pong ON/OFF loops.
         */
        private var syncingPrefs = false

        /** Only the most recently initialized instance may send; stale ones stay silent. */
        private var activeInstance: MagicTouchJj02Support? = null

        val UUID_SERVICE_XIAOMI: UUID = UUID.fromString("0000fe95-0000-1000-8000-00805f9b34fb")
        val UUID_CHARACTERISTIC_AUTH_CTRL: UUID =
            UUID.fromString("00000010-0000-1000-8000-00805f9b34fb")

        /**
         * Login channel, BOTH directions (echo, SEND, randoms, confirms).
         * The vendor app never uses UUID 0x0017; it only listens on 0x0019.
         */
        val UUID_CHARACTERISTIC_LOGIN: UUID =
            UUID.fromString("00000019-0000-1000-8000-00805f9b34fb")
        val UUID_CHARACTERISTIC_COMMAND: UUID =
            UUID.fromString("0000001a-0000-1000-8000-00805f9b34fb")
        val UUID_CHARACTERISTIC_REPORT: UUID =
            UUID.fromString("0000001b-0000-1000-8000-00805f9b34fb")
        val UUID_CHARACTERISTIC_DEVICE_INFO: UUID =
            UUID.fromString("0000001c-0000-1000-8000-00805f9b34fb")

        /** Firmware version string ("2.1.2"), read-only. */
        val UUID_CHARACTERISTIC_FIRMWARE_VERSION: UUID =
            UUID.fromString("00000004-0000-1000-8000-00805f9b34fb")

        const val PREF_POWER = "pref_magic_touch_jj02_power"
        const val PREF_MODE = "pref_magic_touch_jj02_mode"
        const val PREF_HEATING = "pref_magic_touch_jj02_heating"
        const val PREF_HEAT_LEVEL = "pref_magic_touch_jj02_heat_level"
        const val PREF_TIMER = "pref_magic_touch_jj02_timer"
        const val PREF_CUSTOM_NAME = "pref_magic_touch_jj02_custom_name"
        const val PREF_DEFAULT_MODE = "pref_magic_touch_jj02_default_mode"
        const val PREF_BEEP = "pref_magic_touch_jj02_beep"
        const val PREF_HEAD_INIT = "pref_magic_touch_jj02_head_init"

        private const val HKDF_INFO = "mible-login-info"
    }
}
