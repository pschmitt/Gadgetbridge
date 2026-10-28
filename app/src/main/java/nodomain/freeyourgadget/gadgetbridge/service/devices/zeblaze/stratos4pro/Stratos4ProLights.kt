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
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceManager
import nodomain.freeyourgadget.gadgetbridge.devices.zeblaze.stratos4pro.Stratos4ProCoordinator
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLEDeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLESingleDeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder
import org.slf4j.LoggerFactory
import java.util.EnumMap
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Predicate

/**
 * White and red LED control for boards with measured LED pins (Stratos 4 Pro).
 *
 * The protocol has no flashlight command, and the watch's own flashlight key is useless during a
 * workout (the workout view takes every key). The debug log service (`e49a3001`) runs shell
 * commands, and the pins are plain register writes:
 *
 * - white: WIO3 control `0x4006830c`, `0x10040` on (output enable + level), `0` off
 * - red: GPIO58 via set / clear `0x40068214` / `0x40068224`, bit 26
 *
 * Shell text is only these four writes ([Command]) and fixed lines from other helpers ([shell]),
 * never user input. Pins are board specific, so [Stratos4ProCoordinator.supportsLights] gates on
 * the model.
 *
 * Wire (write `e49a3002`, notify `e49a3003`): `1D 10 81 <paramLen LE16>`, then TLVs `<type:1> <len
 * LE16> <value>`, 00 console code, 01 shell text. Writes are ignored until the CCC of `e49a3003` is
 * written. Each accepted write gets [ACK].
 *
 * The open stream holds a PARTIAL wake lock on the watch until the BLE link drops (disabling the
 * notification does not close it). So after [IDLE_RELEASE_MS] with both off the link is dropped
 * once and auto-reconnect restores it.
 *
 * With keepalive on, a light turned off on the watch comes back; use the card.
 */
class Stratos4ProLights(
    private val support: AbstractBTLESingleDeviceSupport,
    /** A bulk transfer is running: do not drop the link now. */
    private val isBusy: () -> Boolean,
) {
    enum class Light(val on: Command, val off: Command, val strobePref: String) {
        WHITE(Command.WHITE_ON, Command.WHITE_OFF, PREF_WHITE_STROBE_MS),
        RED(Command.RED_ON, Command.RED_OFF, PREF_RED_STROBE_MS),
    }

    enum class Mode { OFF, ON, STROBE }

    /** The only commands this class can send. */
    enum class Command(val text: String) {
        WHITE_ON("dbg mww 4006830c 10040"),
        WHITE_OFF("dbg mww 4006830c 0"),
        RED_ON("dbg mww 40068214 4000000"),
        RED_OFF("dbg mww 40068224 4000000"),
    }

    /** What GB thinks is lit, per watch; read by the card. Cleared on dispose. */
    class State {
        @Volatile
        var serviceFound = false

        private val modes = ConcurrentHashMap<Light, Mode>()

        fun mode(light: Light): Mode = modes[light] ?: Mode.OFF

        fun set(light: Light, mode: Mode) {
            modes[light] = mode
        }

        fun anyOn(): Boolean = Light.entries.any { mode(it) != Mode.OFF }
    }

    // Everything below is touched on the main thread only, via [handler].
    private val handler = Handler(Looper.getMainLooper())
    private val strobes = EnumMap<Light, Strobe>(Light::class.java)

    /** Stream open on this link: CCC written, prepare sent. */
    private var channelOpen = false

    /** Uptime of the last write still waiting for its ACK, 0 if none. */
    private var ackPendingSince = 0L
    private var writes = 0
    private var acks = 0
    private var deferredTicks = 0

    /** Realtime of the last write on this instance, 0 if none. */
    private var lastWriteAt = 0L

    private val idleRelease = Runnable { releaseIdleLink() }
    private val keepalive = Runnable { keepaliveTick() }

    private val address: String
        get() = support.device.address

    private val state: State
        get() = state(address)

    /** The strobe on time for [light] in ms, or 0 for a steady light. */
    private fun strobeMs(light: Light): Int =
        parseStrobeMs(support.devicePrefs.getString(light.strobePref, null))

    /** End of `initializeDevice`, on the init builder: [restore] runs after MTU and setup. */
    fun onConnect(builder: TransactionBuilder) {
        val write = support.getCharacteristic(UUID_CHARACTERISTIC_LOG_WRITE)
        val found = write != null && support.getCharacteristic(UUID_CHARACTERISTIC_LOG_NOTIFY) != null
        state.serviceFound = found
        handler.post {
            // New link: the stream is closed and the strobes died with the old one.
            channelOpen = false
            ackPendingSince = 0L
            stopAllStrobes()
            handler.removeCallbacks(keepalive)
            handler.removeCallbacks(idleRelease)
        }
        if (!found) return
        // Write with response, so a watch that stops taking writes is noticed.
        write.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        builder.run(Runnable { handler.post { restore() } })
    }

    fun toggle(light: Light) {
        handler.post {
            if (!allowed()) {
                LOG.warn("lights: {} toggle ignored, not supported on this watch", light)
                return@post
            }
            val next = when (state.mode(light)) {
                Mode.OFF -> if (strobeMs(light) > 0) Mode.STROBE else Mode.ON
                Mode.ON, Mode.STROBE -> Mode.OFF
            }
            apply(light, next)
        }
    }

    fun onKeepaliveSettingChanged() {
        handler.post { scheduleKeepalive() }
    }

    /** Strobe setting changed; a lit light follows at once. */
    fun onStrobeSettingChanged(light: Light) {
        handler.post {
            if (allowed()) followStrobeSetting(light)
        }
    }

    /** Packets from `e49a3003`. Called on the BLE thread. */
    fun onNotify(value: ByteArray) {
        if (value.contentEquals(ACK)) {
            // The ACK sometimes repeats ~200 ms later, hence a flag, not a counter.
            handler.post {
                acks++
                ackPendingSince = 0L
            }
            return
        }
        LOG.debug("lights: log service sent {}", value.joinToString("") { "%02x".format(it) })
    }

    fun dispose() {
        if (writes > 0) {
            LOG.info("lights: {} writes, {} acks, {} ticks deferred", writes, acks, deferredTicks)
        }
        handler.removeCallbacksAndMessages(null)
        strobes.clear()
        for (light in Light.entries) state.set(light, Mode.OFF)
        refreshCard()
    }

    private fun allowed(): Boolean {
        val coordinator = support.device.deviceCoordinator as? Stratos4ProCoordinator ?: return false
        return state.serviceFound && coordinator.supportsLights(support.device)
    }

    /** Short drop: re-light what was on. Long drop: clear it. */
    private fun restore() {
        val recent = lastWriteAt != 0L && SystemClock.elapsedRealtime() - lastWriteAt < RESTORE_WINDOW_MS
        for (light in Light.entries) {
            val mode = state.mode(light)
            if (mode == Mode.OFF) continue
            if (!recent || !allowed()) {
                LOG.info("lights: {} was {}, not restoring", light, mode)
                state.set(light, Mode.OFF)
                continue
            }
            LOG.info("lights: {} was {} before the reconnect, restoring", light, mode)
            apply(light, if (strobeMs(light) > 0) Mode.STROBE else Mode.ON)
        }
        refreshCard()
    }

    /** Bring a lit light in line with its strobe setting. Steady that stays steady: no write. */
    private fun followStrobeSetting(light: Light) {
        val current = state.mode(light)
        if (current == Mode.OFF) return
        val next = if (strobeMs(light) > 0) Mode.STROBE else Mode.ON
        if (next == Mode.ON && current == Mode.ON) return
        // Also STROBE -> STROBE with a new period: apply() restarts the timer.
        apply(light, next)
    }

    private fun apply(light: Light, mode: Mode) {
        if (mode != Mode.OFF && !canSend()) {
            // Keep the state: the next connect's restore should still see it.
            return
        }
        stopStrobe(light)
        state.set(light, mode)
        LOG.info("lights: {} -> {}", light, mode)
        when (mode) {
            Mode.OFF -> send(light.off)
            Mode.ON -> send(light.on)
            Mode.STROBE -> {
                val strobe = Strobe(light, strobeMs(light).toLong())
                strobes[light] = strobe
                handler.post(strobe)
            }
        }
        scheduleKeepalive()
        scheduleIdleRelease()
        refreshCard()
    }

    private fun canSend(): Boolean {
        if (!support.isConnected) {
            LOG.debug("lights: not connected")
            return false
        }
        // Longest command is 32 bytes; at the default MTU it would be cut and the rest run.
        val max = AbstractBTLEDeviceSupport.calcMaxWriteChunk(support.mtu)
        if (max < MAX_FRAME_SIZE) {
            LOG.warn("lights: MTU {} too small for a {} byte command", support.mtu, MAX_FRAME_SIZE)
            return false
        }
        return true
    }

    /** Raw shell lines; false = not sent. */
    internal fun shell(lines: List<String>): Boolean {
        if (!allowed() || !canSend()) return false
        handler.post {
            if (!allowed()) return@post
            for (line in lines) send(line)
            scheduleIdleRelease()
        }
        return true
    }

    private fun send(command: Command) = send(command.text)

    private fun send(text: String) {
        if (!canSend()) return
        val builder = support.createTransactionBuilder("light $text")
        if (!channelOpen) {
            LOG.info("lights: opening the log service stream")
            builder.notify(UUID_CHARACTERISTIC_LOG_NOTIFY, true)
            builder.write(UUID_CHARACTERISTIC_LOG_WRITE, *PREPARE_FRAME)
            channelOpen = true
        }
        builder.write(UUID_CHARACTERISTIC_LOG_WRITE, *frame(TLV_SHELL_TEXT, text.toByteArray(Charsets.US_ASCII)))
        builder.queue()
        ackPendingSince = SystemClock.uptimeMillis()
        lastWriteAt = SystemClock.elapsedRealtime()
        writes++
    }

    /** One timer per light; one write per tick, on time = off time. */
    private inner class Strobe(val light: Light, val periodMs: Long) : Runnable {
        private var lit = false

        override fun run() {
            if (strobes[light] !== this || state.mode(light) != Mode.STROBE) return
            if (!support.isConnected) return // the reconnect restores it
            val waiting = SystemClock.uptimeMillis() - ackPendingSince
            if (ackPendingSince != 0L && waiting < ACK_TIMEOUT_MS) {
                // Last write not acked yet: retry shortly instead of piling up writes.
                deferredTicks++
                handler.postDelayed(this, minOf(periodMs, ACK_RETRY_MS))
                return
            }
            lit = !lit
            send(if (lit) light.on else light.off)
            handler.postDelayed(this, periodMs)
        }
    }

    private fun stopStrobe(light: Light) {
        strobes.remove(light)?.let { handler.removeCallbacks(it) }
    }

    private fun stopAllStrobes() {
        for (strobe in strobes.values) handler.removeCallbacks(strobe)
        strobes.clear()
    }

    private fun scheduleIdleRelease() {
        handler.removeCallbacks(idleRelease)
        if (channelOpen && !state.anyOn()) {
            handler.postDelayed(idleRelease, IDLE_RELEASE_MS)
        }
    }

    private fun keepaliveMs(): Long =
        parseKeepaliveS(support.devicePrefs.getString(PREF_KEEPALIVE_S, null)) * 1000L

    private fun keepaliveTick() {
        if (!support.isConnected) return
        val ms = keepaliveMs()
        if (ms <= 0) return
        val waiting = SystemClock.uptimeMillis() - ackPendingSince
        if (ackPendingSince != 0L && waiting < ACK_TIMEOUT_MS) {
            deferredTicks++
            handler.postDelayed(keepalive, ACK_RETRY_MS)
            return
        }
        var anySteady = false
        for (light in Light.entries) {
            if (state.mode(light) == Mode.ON) {
                send(light.on)
                anySteady = true
            }
        }
        if (anySteady) {
            handler.postDelayed(keepalive, ms)
        }
    }

    private fun scheduleKeepalive() {
        handler.removeCallbacks(keepalive)
        val ms = keepaliveMs()
        if (ms > 0 && Light.entries.any { state.mode(it) == Mode.ON }) {
            handler.postDelayed(keepalive, ms)
        }
    }

    @SuppressLint("MissingPermission")
    private fun releaseIdleLink() {
        if (!channelOpen || state.anyOn() || !support.isConnected) return
        if (isBusy()) {
            LOG.info("lights: idle, but a transfer is running; retrying the link drop later")
            handler.postDelayed(idleRelease, IDLE_RETRY_MS)
            return
        }
        if (!support.autoReconnect) {
            LOG.info("lights: idle, but auto-reconnect is off, so the link stays up and the watch stays out of deep sleep until it drops")
            return
        }
        LOG.info("lights: off for {} s, dropping the link once to release the watch's wake lock", IDLE_RELEASE_MS / 1000)
        channelOpen = false
        val builder = support.createTransactionBuilder("lights idle release")
        // A plain GATT disconnect looks like a remote drop to BtLEQueue, which reconnects.
        builder.run(Predicate<BluetoothGatt> { gatt ->
            gatt.disconnect()
            true
        })
        builder.queue()
    }

    private fun refreshCard() {
        LocalBroadcastManager.getInstance(support.context)
            .sendBroadcast(Intent(DeviceManager.ACTION_DEVICES_CHANGED))
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(Stratos4ProLights::class.java)

        val UUID_SERVICE_LOG: UUID = UUID.fromString("e49a3001-f69a-11e8-8eb2-f2801f1b9fd1")
        val UUID_CHARACTERISTIC_LOG_WRITE: UUID = UUID.fromString("e49a3002-f69a-11e8-8eb2-f2801f1b9fd1")
        val UUID_CHARACTERISTIC_LOG_NOTIFY: UUID = UUID.fromString("e49a3003-f69a-11e8-8eb2-f2801f1b9fd1")

        /** Virtual keys sent by the card buttons. */
        const val PREF_WHITE_TOGGLE = "stratos4pro_light_white_toggle"
        const val PREF_RED_TOGGLE = "stratos4pro_light_red_toggle"

        /** On time in ms (off time equal). Empty or 0 = steady. */
        const val PREF_WHITE_STROBE_MS = "stratos4pro_light_white_strobe_ms"
        const val PREF_RED_STROBE_MS = "stratos4pro_light_red_strobe_ms"

        const val PREF_KEEPALIVE_S = "stratos4pro_light_keepalive_s"
        const val KEEPALIVE_DEFAULT_S = 3
        const val KEEPALIVE_MAX_S = 60

        const val STROBE_MIN_MS = 100
        const val STROBE_MAX_MS = 10000

        private const val ACK_TIMEOUT_MS = 2000L
        private const val ACK_RETRY_MS = 25L
        private const val IDLE_RELEASE_MS = 2 * 60 * 1000L
        private const val IDLE_RETRY_MS = 30 * 1000L
        private const val RESTORE_WINDOW_MS = 60 * 1000L

        private const val TLV_CONSOLE_CODE = 0x00
        private const val TLV_SHELL_TEXT = 0x01

        /** Reply to every accepted write: `1D 10 81 07 00 | 7F 04 00 A0 86 01 00` (100000). */
        private val ACK = byteArrayOf(
            0x1d, 0x10, 0x81.toByte(), 0x07, 0x00,
            0x7f, 0x04, 0x00, 0xa0.toByte(), 0x86.toByte(), 0x01, 0x00,
        )

        /** Console code 0x0001, "prepare". */
        val PREPARE_FRAME: ByteArray = frame(TLV_CONSOLE_CODE, byteArrayOf(0x01, 0x00))

        private val MAX_FRAME_SIZE = Command.entries.maxOf { frame(it).size }

        private val states = ConcurrentHashMap<String, State>()

        fun state(address: String): State =
            states.getOrPut(address.uppercase(Locale.ROOT)) { State() }

        /** Empty, invalid or 0 = steady; else clamped to range. */
        fun parseStrobeMs(value: String?): Int {
            val ms = value?.trim()?.toIntOrNull() ?: return 0
            if (ms <= 0) return 0
            return ms.coerceIn(STROBE_MIN_MS, STROBE_MAX_MS)
        }

        /** Unset = default; empty, invalid or 0 = off; else clamped. */
        fun parseKeepaliveS(value: String?): Int {
            if (value == null) return KEEPALIVE_DEFAULT_S
            val s = value.trim().toIntOrNull() ?: return 0
            return s.coerceIn(0, KEEPALIVE_MAX_S)
        }

        fun frame(command: Command): ByteArray =
            frame(TLV_SHELL_TEXT, command.text.toByteArray(Charsets.US_ASCII))

        /** `1D 10 81 <paramLen LE16> <type> <len LE16> <value>`, a single TLV. */
        private fun frame(type: Int, value: ByteArray): ByteArray {
            val paramLen = value.size + 3
            val out = ByteArray(5 + paramLen)
            out[0] = 0x1d
            out[1] = 0x10
            out[2] = 0x81.toByte()
            out[3] = paramLen.toByte()
            out[4] = (paramLen ushr 8).toByte()
            out[5] = type.toByte()
            out[6] = value.size.toByte()
            out[7] = (value.size ushr 8).toByte()
            System.arraycopy(value, 0, out, 8, value.size)
            return out
        }
    }
}
