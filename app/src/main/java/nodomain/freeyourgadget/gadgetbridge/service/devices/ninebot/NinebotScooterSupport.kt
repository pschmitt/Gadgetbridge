/*  Copyright (C) 2026 fds2610

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.ninebot

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdateDeviceInfo
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdatePreferences
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLESingleDeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder
import nodomain.freeyourgadget.gadgetbridge.util.GB
import nodomain.freeyourgadget.gadgetbridge.util.Prefs
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.ArrayDeque
import java.util.UUID

/**
 * Support for Ninebot / Segway KickScooters speaking the classic Ninebot protocol over the Nordic UART service
 * (see [NinebotProtocol]) with encrypted frames (see [NinebotCrypto]).
 *
 * A connection starts with INIT, which gives the key material of the BLE module and the serial number. The
 * scooter then has to know the app key (16 random bytes chosen and stored by Gadgetbridge): a new key is
 * announced with PING and has to be confirmed by pressing the power button on the scooter, a known key only
 * needs PAIR. Afterwards, registers are read and written with READ / WRITE.
 *
 * The scooter keeps just one app key, so using the original app in between makes a new pairing necessary.
 */
class NinebotScooterSupport : AbstractBTLESingleDeviceSupport(LOG) {
    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()

    private val crypto = NinebotCrypto()
    private val reassembler = NinebotProtocol.Reassembler()
    private var step = Step.IDLE
    private var serial = ByteArray(0)
    private var appKey: ByteArray? = null
    private var pingPayload = ByteArray(0)
    private var attempts = 0

    // While the scooter is locked or off, only the BLE module answers
    private var unlocked = true
    private var autoUnlockDone = false

    // The scooter may ignore the first unlock right after the connection, try again while it still reports locked
    private var powerOnRetries = 0

    // Requests are sent one at a time, each waits for its answer (or a timeout)
    private val queue = ArrayDeque<ByteArray>()
    private var requestPending = false

    private val requestTimeout = Runnable {
        synchronized(lock) {
            LOG.warn("No answer to request, continuing")
            requestPending = false
            sendNext()
        }
    }
    private val pairTimeout = Runnable { onPairTimeout() }

    // Keeps trying to reach the scooter while the search is switched on, independent of the connection state
    private val search = object : Runnable {
        override fun run() {
            synchronized(lock) {
                if (!searchEnabled()) return
                val state = gbDevice.state
                if (state == GBDevice.State.NOT_CONNECTED || state == GBDevice.State.WAITING_FOR_RECONNECT || state == GBDevice.State.WAITING_FOR_SCAN) {
                    LOG.debug("Searching for the scooter")
                    GBApplication.deviceService(gbDevice).connect()
                }
            }
            handler.postDelayed(this, SEARCH_INTERVAL_MS)
        }
    }
    // The lock state and the riding mode can be changed at the scooter, so look at them more often
    private val fastPoll = object : Runnable {
        override fun run() {
            synchronized(lock) {
                if (step != Step.READY) return
                enqueue(NinebotProtocol.read(NinebotProtocol.ID_BLE, NinebotProtocol.BLE_LOCK_STATE, 2))
                if (unlocked) enqueue(NinebotProtocol.read(NinebotProtocol.ID_CTRL, NinebotProtocol.CTRL_MODE, 2))
            }
            handler.postDelayed(this, FAST_POLL_INTERVAL_MS)
        }
    }
    private val poll = object : Runnable {
        override fun run() {
            synchronized(lock) {
                if (step != Step.READY) return
                requestStatus()
            }
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    init {
        addSupportedService(UUID_SERVICE_UART)
    }

    override fun useAutoConnect(): Boolean = true

    override fun initializeDevice(builder: TransactionBuilder): TransactionBuilder {
        synchronized(lock) {
            stopTimers()
            queue.clear()
            requestPending = false
            reassembler.reset()
            serial = ByteArray(0)
            appKey = null
            attempts = 0
            autoUnlockDone = false
            powerOnRetries = 0
            crypto.setName(gbDevice.name.toByteArray())
            crypto.reset()
            step = Step.INIT

            builder.setDeviceState(GBDevice.State.INITIALIZING)
            builder.notify(UUID_CHARACTERISTIC_RX, true)
            for (chunk in chunks(crypto.encrypt(NinebotProtocol.build(NinebotProtocol.ID_BLE, NinebotProtocol.CMD_INIT, 0)))) {
                builder.write(UUID_CHARACTERISTIC_TX, *chunk)
            }
            handler.postDelayed(pairTimeout, PAIR_TIMEOUT_MS)
        }
        return builder
    }

    override fun setContext(gbDevice: GBDevice, btAdapter: BluetoothAdapter, context: Context) {
        super.setContext(gbDevice, btAdapter, context)
        restartSearch()
    }

    override fun dispose() {
        synchronized(lock) {
            handler.removeCallbacks(search)
            stopTimers()
            step = Step.IDLE
        }
        super.dispose()
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ): Boolean {
        if (characteristic.uuid == UUID_CHARACTERISTIC_RX) {
            synchronized(lock) {
                val frame = reassembler.feed(value) ?: return true
                val decrypted = crypto.decrypt(frame)
                if (decrypted == null || !decrypted.authentic) {
                    LOG.warn("Dropping frame that fails the check: {}", GB.hexdump(frame))
                    return true
                }
                val message = NinebotProtocol.parse(decrypted.frame)
                if (message == null) {
                    LOG.warn("Cannot parse frame: {}", GB.hexdump(decrypted.frame))
                    return true
                }
                LOG.debug("Received {}", message)
                handleMessage(message)
            }
            return true
        }
        return super.onCharacteristicChanged(gatt, characteristic, value)
    }

    // =======================================================================
    // Connection setup
    // =======================================================================

    private fun handleMessage(message: NinebotProtocol.Message) {
        when (message.cmd) {
            NinebotProtocol.CMD_INIT -> onInit(message)
            NinebotProtocol.CMD_PING -> onPing(message)
            NinebotProtocol.CMD_PAIR -> onPair(message)
            NinebotProtocol.CMD_READ_ACK, NinebotProtocol.CMD_WRITE_ACK -> {
                handler.removeCallbacks(requestTimeout)
                requestPending = false
                if (message.cmd == NinebotProtocol.CMD_WRITE_ACK) {
                    LOG.debug("Write acknowledged: {}", message)
                }
                if (message.cmd == NinebotProtocol.CMD_READ_ACK) {
                    onRegister(message)
                }
                sendNext()
            }

            else -> if (message.src == NinebotProtocol.ID_BLE && step == Step.READY) {
                // The BLE module reports changes of its registers (e.g. the lock state) by itself
                LOG.debug("Push from BLE module: {}", message)
                enqueue(NinebotProtocol.read(NinebotProtocol.ID_BLE, NinebotProtocol.BLE_LOCK_STATE, 2))
            }
        }
    }

    private fun onInit(message: NinebotProtocol.Message) {
        if (step != Step.INIT || message.data.size < 30) {
            LOG.warn("Unexpected INIT answer in step {}: {}", step, message)
            return
        }
        crypto.setBleData(message.data.copyOfRange(0, 16))
        serial = message.data.copyOfRange(16, 30)

        val stored = storedAppKey()
        if (stored != null) {
            LOG.debug("Pairing with the stored app key")
            appKey = stored
            crypto.setAppData(stored)
            step = Step.PAIR
            attempts = 0
            handler.removeCallbacks(pairTimeout)
            handler.postDelayed(pairTimeout, RETRY_TIMEOUT_MS)
            sendUnqueued(NinebotProtocol.build(NinebotProtocol.ID_BLE, NinebotProtocol.CMD_PAIR, 0, serial))
        } else {
            LOG.debug("Announcing a new app key")
            // The original app sends 32 random bytes, of which the first 16 are the app key
            pingPayload = ByteArray(32).also { SecureRandom().nextBytes(it) }
            appKey = pingPayload.copyOf(16)
            attempts = 0
            step = Step.PING
            handler.removeCallbacks(pairTimeout)
            handler.postDelayed(pairTimeout, RETRY_TIMEOUT_MS)
            sendPing()
        }
    }

    private fun onPing(message: NinebotProtocol.Message) {
        val key = appKey
        if (step != Step.PING || key == null) {
            LOG.warn("Unexpected PING answer in step {}", step)
            return
        }
        if (message.index == 0) {
            LOG.info("Waiting for the power button to be pressed")
            handler.removeCallbacks(pairTimeout)
            handler.postDelayed(pairTimeout, BUTTON_TIMEOUT_MS)
            GB.toast(context, R.string.ninebot_press_power_button, Toast.LENGTH_LONG, GB.INFO)
            return
        }
        crypto.setAppData(key)
        step = Step.PAIR
        attempts = 0
        handler.removeCallbacks(pairTimeout)
        handler.postDelayed(pairTimeout, RETRY_TIMEOUT_MS)
        sendUnqueued(NinebotProtocol.build(NinebotProtocol.ID_BLE, NinebotProtocol.CMD_PAIR, 0, serial))
    }

    private fun onPair(message: NinebotProtocol.Message) {
        val key = appKey
        if (step != Step.PAIR || key == null || message.index != 1) {
            LOG.warn("Unexpected PAIR answer in step {}: {}", step, message)
            return
        }
        LOG.info("Paired")
        handler.removeCallbacks(pairTimeout)
        sharedPrefs().edit().putString(PREF_AUTH_KEY, GB.hexdump(key).replace(" ", "")).apply()
        step = Step.READY

        evaluateGBDeviceEvent(
            GBDeviceEventUpdateDeviceInfo("SERIAL: ", String(serial, Charsets.US_ASCII).trim { it <= ' ' })
        )
        // First, otherwise the reads that the switched off scooter does not answer delay it by several timeouts
        powerOnIfRequested()
        requestInitial()
        gbDevice.setUpdateState(GBDevice.State.INITIALIZED, context)
        handler.postDelayed(poll, POLL_INTERVAL_MS)
        handler.postDelayed(fastPoll, FAST_POLL_INTERVAL_MS)
    }

    /** The device card asked for the scooter to be switched on once the connection is up. */
    private fun powerOnIfRequested() {
        val prefs = sharedPrefs()
        val requested = prefs.getLong(PREF_POWER_ON_PENDING, 0)
        if (requested == 0L) return
        prefs.edit().remove(PREF_POWER_ON_PENDING).apply()
        if (System.currentTimeMillis() - requested < POWER_ON_REQUEST_MAX_AGE_MS) {
            LOG.info("Switching on as requested")
            autoUnlockDone = true
            powerOnRetries = POWER_ON_RETRIES
            unlock()
        }
    }

    private fun onPairTimeout() {
        synchronized(lock) {
            if (step == Step.READY || step == Step.IDLE) return
            LOG.warn("Pairing timed out in step {} (attempt {})", step, attempts)
            if (!gbDevice.isConnected) {
                // The scooter was switched off or went out of range: keep the key and leave the reconnect to the queue
                LOG.info("Connection lost while pairing, giving up this attempt")
                queue.clear()
                requestPending = false
                step = Step.IDLE
                return
            }
            if (attempts < MAX_ATTEMPTS && (step == Step.PING || step == Step.PAIR)) {
                attempts++
                GB.toast(
                    context, context.getString(R.string.ninebot_retrying, attempts, MAX_ATTEMPTS),
                    Toast.LENGTH_SHORT, GB.INFO
                )
                handler.postDelayed(pairTimeout, RETRY_TIMEOUT_MS)
                if (step == Step.PING) {
                    sendPing()
                } else {
                    sendUnqueued(NinebotProtocol.build(NinebotProtocol.ID_BLE, NinebotProtocol.CMD_PAIR, 0, serial))
                }
                return
            }
            if (step == Step.PAIR) {
                // The scooter did not accept the stored key (the original app replaced it), pair anew next time
                sharedPrefs().edit().remove(PREF_AUTH_KEY).apply()
            }
            step = Step.IDLE
            GB.toast(context, R.string.ninebot_pairing_failed, Toast.LENGTH_LONG, GB.WARN)
            GBApplication.deviceService(gbDevice).disconnect()
        }
    }

    /** Even attempts send the 32 bytes of the original app, odd ones only the 16 byte key like other libraries. */
    private fun sendPing() {
        val payload = if (attempts % 2 == 0) pingPayload else pingPayload.copyOf(16)
        sendUnqueued(NinebotProtocol.build(NinebotProtocol.ID_BLE, NinebotProtocol.CMD_PING, 0, payload))
    }

    private fun storedAppKey(): ByteArray? {
        val hex = sharedPrefs().getString(PREF_AUTH_KEY, null)?.trim().orEmpty()
        if (hex.length != 32) return null
        return runCatching { GB.hexStringToByteArray(hex) }.getOrNull()
    }

    private fun sharedPrefs() = GBApplication.getDeviceSpecificSharedPrefs(gbDevice.address)

    // =======================================================================
    // Requests
    // =======================================================================

    private fun requestInitial() {
        enqueue(NinebotProtocol.read(NinebotProtocol.ID_CTRL, NinebotProtocol.CTRL_FIRMWARE, 2))
        enqueue(NinebotProtocol.read(NinebotProtocol.ID_BLE, NinebotProtocol.BLE_CODE_PROTECTION, 2))
        enqueue(NinebotProtocol.read(NinebotProtocol.ID_BLE, NinebotProtocol.BLE_CODE, 2))
        enqueue(NinebotProtocol.read(NinebotProtocol.ID_CTRL, NinebotProtocol.CTRL_KERS, 2))
        enqueue(NinebotProtocol.read(NinebotProtocol.ID_BATT, NinebotProtocol.BATT_CHARGE_LIMIT, 2))
        requestStatus()
    }

    private fun requestStatus() {
        enqueue(NinebotProtocol.read(NinebotProtocol.ID_BLE, NinebotProtocol.BLE_LOCK_STATE, 2))
        if (!unlocked) return
        enqueue(NinebotProtocol.read(NinebotProtocol.ID_CTRL, NinebotProtocol.CTRL_BATTERY, 2))
        enqueue(NinebotProtocol.read(NinebotProtocol.ID_CTRL, NinebotProtocol.CTRL_RANGE, 2))
        enqueue(NinebotProtocol.read(NinebotProtocol.ID_CTRL, NinebotProtocol.CTRL_ODOMETER, 4))
        enqueue(NinebotProtocol.read(NinebotProtocol.ID_CTRL, NinebotProtocol.CTRL_TEMPERATURE, 2))
        enqueue(NinebotProtocol.read(NinebotProtocol.ID_CTRL, NinebotProtocol.CTRL_MODE, 2))
    }

    private fun enqueue(frame: ByteArray) {
        queue.addLast(frame)
        if (!requestPending) {
            sendNext()
        }
    }

    private fun sendNext() {
        if (step != Step.READY) return
        val frame = queue.pollFirst() ?: return
        sendUnqueued(frame)
        if ((frame[5].toInt() and 0xff) == NinebotProtocol.CMD_WRITE_NOREPLY) {
            sendNext()
            return
        }
        requestPending = true
        handler.postDelayed(requestTimeout, REQUEST_TIMEOUT_MS)
    }

    /** Encrypts and sends a frame to the scooter in chunks of 20 bytes. */
    private fun sendUnqueued(frame: ByteArray) {
        val builder = createTransactionBuilder("ninebot_send")
        for (chunk in chunks(crypto.encrypt(frame))) {
            builder.write(UUID_CHARACTERISTIC_TX, *chunk)
        }
        builder.queue()
    }

    private fun chunks(frame: ByteArray): List<ByteArray> = frame.toList().chunked(CHUNK_SIZE).map { it.toByteArray() }

    private fun stopTimers() {
        handler.removeCallbacks(requestTimeout)
        handler.removeCallbacks(pairTimeout)
        handler.removeCallbacks(poll)
        handler.removeCallbacks(fastPoll)
    }

    // =======================================================================
    // Registers
    // =======================================================================

    private fun onRegister(message: NinebotProtocol.Message) {
        val event = GBDeviceEventUpdatePreferences()
        var hasPreferences = true
        when (message.src) {
            NinebotProtocol.ID_CTRL -> when (message.index) {
                NinebotProtocol.CTRL_FIRMWARE -> {
                    val v = message.uint(2)
                    val versionEvent = GBDeviceEventVersionInfo()
                    versionEvent.fwVersion = "%d.%d.%d".format((v shr 8) and 0xf, (v shr 4) and 0xf, v and 0xf)
                    evaluateGBDeviceEvent(versionEvent)
                    hasPreferences = false
                }

                NinebotProtocol.CTRL_BATTERY -> {
                    val batteryEvent = GBDeviceEventBatteryInfo()
                    batteryEvent.level = message.uint(2)
                    evaluateGBDeviceEvent(batteryEvent)
                    hasPreferences = false
                }

                NinebotProtocol.CTRL_RANGE ->
                    event.withPreference(PREF_RANGE, "%.1f km".format(message.uint(2) / 100.0))

                NinebotProtocol.CTRL_ODOMETER ->
                    event.withPreference(PREF_ODOMETER, "%.1f km".format(message.uint(4) / 1000.0))

                NinebotProtocol.CTRL_TEMPERATURE ->
                    event.withPreference(PREF_TEMPERATURE, "%.1f °C".format(message.uint(2) / 10.0))

                NinebotProtocol.CTRL_MODE -> {
                    LOG.debug("Riding mode register: {}", message.uint(2))
                    val mode = NinebotRideMode.fromCode(message.uint(2))
                    if (mode == null) hasPreferences = false
                    else event.withPreference(PREF_MODE, mode.name.lowercase())
                }

                NinebotProtocol.CTRL_KERS -> {
                    val kers = NinebotKers.fromCode(message.uint(2))
                    if (kers == null) hasPreferences = false
                    else event.withPreference(PREF_KERS, kers.name.lowercase())
                }

                else -> hasPreferences = false
            }

            NinebotProtocol.ID_BLE -> when (message.index) {
                NinebotProtocol.BLE_LOCK_STATE -> {
                    val wasUnlocked = unlocked
                    unlocked = message.uint(2) == 1
                    if (unlocked && !wasUnlocked) requestStatus()
                    autoUnlockDone = autoUnlockDone || unlocked
                    if (unlocked) {
                        powerOnRetries = 0
                    } else if (powerOnRetries > 0) {
                        powerOnRetries--
                        LOG.info("Still locked after switching on, trying again")
                        handler.postDelayed({ synchronized(lock) { if (step == Step.READY) unlock() } }, POWER_ON_RETRY_DELAY_MS)
                    }
                    event.withPreference(
                        PREF_LOCK_STATE,
                        context.getString(if (unlocked) R.string.ninebot_lock_state_unlocked else R.string.ninebot_lock_state_locked)
                    )
                    // Only once per connection, otherwise switching the scooter off would switch it on again
                    if (!unlocked && !autoUnlockDone && (sharedPrefs().getBoolean(PREF_AUTO_UNLOCK, false) || searchEnabled()) && !switchedOffRecently()) {
                        autoUnlockDone = true
                        LOG.info("Unlocking automatically")
                        // Right after the connection the scooter ignores the first unlock, so try again like on a manual switch on
                        powerOnRetries = POWER_ON_RETRIES
                        unlock()
                    }
                }

                NinebotProtocol.BLE_CODE_PROTECTION ->
                    event.withPreference(PREF_CODE_PROTECTION, message.uint(2) == 1)

                NinebotProtocol.BLE_CODE ->
                    event.withPreference(PREF_CODE, "%04d".format(message.uint(2)))

                else -> hasPreferences = false
            }

            NinebotProtocol.ID_BATT -> when (message.index) {
                NinebotProtocol.BATT_CHARGE_LIMIT -> {
                    val limit = NinebotChargeLimit.fromPercent(message.uint(2))
                    if (limit == null) hasPreferences = false
                    else event.withPreference(PREF_CHARGE_LIMIT, limit.name.lowercase())
                }

                else -> hasPreferences = false
            }

            else -> hasPreferences = false
        }
        if (hasPreferences) {
            evaluateGBDeviceEvent(event)
        }
    }

    private fun searchEnabled() = sharedPrefs().getBoolean(PREF_SEARCH, false)

    private fun restartSearch() {
        handler.removeCallbacks(search)
        if (searchEnabled()) {
            handler.postDelayed(search, SEARCH_INTERVAL_MS)
        }
    }

    override fun onSendConfiguration(config: String) {
        if (config == PREF_SEARCH) {
            synchronized(lock) { restartSearch() }
            return
        }
        synchronized(lock) {
            if (step != Step.READY) {
                super.onSendConfiguration(config)
                return
            }
            val prefs = Prefs(sharedPrefs())
            when (config) {
                PREF_CODE_PROTECTION ->
                    enqueue(
                        NinebotProtocol.write(
                            NinebotProtocol.ID_BLE, NinebotProtocol.BLE_CODE_PROTECTION,
                            if (prefs.getBoolean(config, false)) 1 else 0
                        )
                    )

                PREF_CODE -> {
                    val code = prefs.getString(config, "").toIntOrNull()
                    if (code != null && code in 0..9999) {
                        enqueue(NinebotProtocol.write(NinebotProtocol.ID_BLE, NinebotProtocol.BLE_CODE, code))
                    } else {
                        LOG.warn("Ignoring invalid security code")
                    }
                }

                PREF_MODE -> NinebotRideMode.entries.find { it.name.lowercase() == prefs.getString(config, "") }?.let {
                    LOG.info("Setting the riding mode to {} (register value {})", it, it.code)
                    enqueue(NinebotProtocol.write(NinebotProtocol.ID_CTRL, NinebotProtocol.CTRL_MODE, it.code, reply = true))
                    enqueue(NinebotProtocol.read(NinebotProtocol.ID_CTRL, NinebotProtocol.CTRL_MODE, 2))
                }

                PREF_KERS -> NinebotKers.entries.find { it.name.lowercase() == prefs.getString(config, "") }?.let {
                    enqueue(NinebotProtocol.write(NinebotProtocol.ID_CTRL, NinebotProtocol.CTRL_KERS, it.code))
                }

                PREF_CHARGE_LIMIT ->
                    NinebotChargeLimit.entries.find { it.name.lowercase() == prefs.getString(config, "") }?.let {
                        enqueue(NinebotProtocol.write(NinebotProtocol.ID_BATT, NinebotProtocol.BATT_CHARGE_LIMIT, it.percent))
                    }

                PREF_UNLOCK, PREF_POWER_ON -> unlock()

                PREF_POWER_OFF -> powerOff()

                else -> super.onSendConfiguration(config)
            }
        }
    }

    override fun onPowerOff() {
        synchronized(lock) {
            if (step == Step.READY) powerOff()
        }
    }

    private fun powerOff() {
        // The scooter drops the connection when switching off, the reconnect must not switch it on again
        autoUnlockDone = true
        sharedPrefs().edit().putLong(PREF_POWER_OFF_AT, System.currentTimeMillis()).apply()
        enqueue(NinebotProtocol.write(NinebotProtocol.ID_CTRL, NinebotProtocol.CTRL_POWER_OFF, 1))
    }

    private fun switchedOffRecently(): Boolean =
        System.currentTimeMillis() - sharedPrefs().getLong(PREF_POWER_OFF_AT, 0) < POWER_OFF_SUPPRESS_AUTO_UNLOCK_MS

    private fun unlock() {
        enqueue(NinebotProtocol.write(NinebotProtocol.ID_BLE, NinebotProtocol.BLE_UNLOCK, 1))
        enqueue(NinebotProtocol.read(NinebotProtocol.ID_BLE, NinebotProtocol.BLE_LOCK_STATE, 2))
    }

    private enum class Step { IDLE, INIT, PING, PAIR, READY }

    companion object {
        private val LOG = LoggerFactory.getLogger(NinebotScooterSupport::class.java)

        val UUID_SERVICE_UART: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val UUID_CHARACTERISTIC_TX: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val UUID_CHARACTERISTIC_RX: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

        private const val CHUNK_SIZE = 20
        private const val REQUEST_TIMEOUT_MS = 3000L
        private const val PAIR_TIMEOUT_MS = 10_000L
        private const val RETRY_TIMEOUT_MS = 5000L
        private const val MAX_ATTEMPTS = 6
        private const val BUTTON_TIMEOUT_MS = 60_000L
        private const val POLL_INTERVAL_MS = 30_000L
        private const val FAST_POLL_INTERVAL_MS = 5000L
        private const val SEARCH_INTERVAL_MS = 3000L

        const val PREF_AUTH_KEY = DeviceSettingsPreferenceConst.PREF_AUTH_KEY
        const val PREF_POWER_ON_PENDING = "ninebot_power_on_pending"
        private const val POWER_ON_REQUEST_MAX_AGE_MS = 120_000L
        private const val POWER_ON_RETRIES = 4
        private const val POWER_ON_RETRY_DELAY_MS = 3000L
        private const val PREF_POWER_OFF_AT = "ninebot_power_off_at"
        private const val POWER_OFF_SUPPRESS_AUTO_UNLOCK_MS = 300_000L

        const val PREF_CODE_PROTECTION = DeviceSettingsPreferenceConst.PREF_PASSWORD_ENABLED
        const val PREF_CODE = DeviceSettingsPreferenceConst.PREF_PASSWORD
        const val PREF_UNLOCK = DeviceSettingsPreferenceConst.PREF_NINEBOT_UNLOCK
        const val PREF_AUTO_UNLOCK = DeviceSettingsPreferenceConst.PREF_NINEBOT_AUTO_UNLOCK
        const val PREF_LOCK_STATE = DeviceSettingsPreferenceConst.PREF_NINEBOT_LOCK_STATE
        const val PREF_KERS = DeviceSettingsPreferenceConst.PREF_NINEBOT_KERS
        const val PREF_CHARGE_LIMIT = DeviceSettingsPreferenceConst.PREF_NINEBOT_CHARGE_LIMIT
        const val PREF_POWER_ON = DeviceSettingsPreferenceConst.PREF_NINEBOT_POWER_ON
        const val PREF_POWER_OFF = DeviceSettingsPreferenceConst.PREF_NINEBOT_POWER_OFF
        const val PREF_RANGE = DeviceSettingsPreferenceConst.PREF_NINEBOT_RANGE
        const val PREF_ODOMETER = DeviceSettingsPreferenceConst.PREF_NINEBOT_ODOMETER
        const val PREF_TEMPERATURE = DeviceSettingsPreferenceConst.PREF_NINEBOT_TEMPERATURE
        const val PREF_MODE = DeviceSettingsPreferenceConst.PREF_NINEBOT_MODE
        const val PREF_SEARCH = DeviceSettingsPreferenceConst.PREF_NINEBOT_SEARCH
    }
}
