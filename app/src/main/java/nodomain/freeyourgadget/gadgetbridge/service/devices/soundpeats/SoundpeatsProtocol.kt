/*  Copyright (C) 2026 Pedro

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.soundpeats

import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SOUNDPEATS_ANC_MODE
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SOUNDPEATS_DUAL_CONNECT
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SOUNDPEATS_GAME_MODE
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SOUNDPEATS_MOVIE_MODE
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SOUNDPEATS_PROMPT_VOLUME
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SOUNDPEATS_TOUCH_LOCK
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SOUNDPEATS_WATER_DRAINAGE
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdatePreferences
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo
import nodomain.freeyourgadget.gadgetbridge.devices.soundpeats.SoundpeatsAncMode
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState
import nodomain.freeyourgadget.gadgetbridge.model.FindDeviceTarget
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Encodes requests and decodes device responses for Soundpeats headphones.
 *
 * Only the commands that are actually advertised by the Q3 Pro capability matrix are wired up, and
 * the payload shapes for the setters are confirmed by the captured traffic: a setter carries the raw
 * value as a single byte.
 *
 * Verified from the app: frame layout ([SoundpeatsPacket]), command ids ([SoundpeatsCommands]),
 * response mask, battery level in `payload[1]`, noise control mode in `payload[1]`.
 */
class SoundpeatsProtocol {
    companion object {
        private val LOG = LoggerFactory.getLogger(SoundpeatsProtocol::class.java)

        /** Battery indexes, matching the coordinator battery config. */
        const val BATTERY_INDEX_LEFT = 0
        const val BATTERY_INDEX_RIGHT = 1
        const val BATTERY_INDEX_CASE = 2

        fun encodeGetFirmwareVersion(): ByteArray =
            SoundpeatsPacket.encode(SoundpeatsCommands.GET_FIRMWARE_VERSION)

        fun encodeGetBattery(index: Int): ByteArray = SoundpeatsPacket.encode(
            when (index) {
                BATTERY_INDEX_LEFT -> SoundpeatsCommands.GET_LEFT_BATTERY
                BATTERY_INDEX_RIGHT -> SoundpeatsCommands.GET_RIGHT_BATTERY
                else -> SoundpeatsCommands.GET_CASE_BATTERY
            }
        )

        fun encodeGetAncMode(): ByteArray =
            SoundpeatsPacket.encode(SoundpeatsCommands.GET_NOISE_CANCELLATION_MODE)

        fun encodeSetAncMode(mode: SoundpeatsAncMode): ByteArray =
            SoundpeatsPacket.encode(
                SoundpeatsCommands.SET_NOISE_CANCELLATION_MODE,
                byteArrayOf((mode.code and 0xFF).toByte())
            )

        fun encodeGetGameMode(): ByteArray =
            SoundpeatsPacket.encode(SoundpeatsCommands.GET_GAME_MODE_STATUS)

        fun encodeSetGameMode(enabled: Boolean): ByteArray =
            SoundpeatsPacket.encode(SoundpeatsCommands.SET_GAME_MODE, booleanPayload(enabled))

        fun encodeGetMovieMode(): ByteArray =
            SoundpeatsPacket.encode(SoundpeatsCommands.GET_MOVIE_MODE_STATUS)

        fun encodeSetMovieMode(enabled: Boolean): ByteArray =
            SoundpeatsPacket.encode(SoundpeatsCommands.SET_MOVIE_MODE, booleanPayload(enabled))

        fun encodeGetDualConnection(): ByteArray =
            SoundpeatsPacket.encode(SoundpeatsCommands.GET_1TO2_STATUS)

        fun encodeSetDualConnection(enabled: Boolean): ByteArray =
            SoundpeatsPacket.encode(SoundpeatsCommands.SET_1TO2, booleanPayload(enabled))

        fun encodeGetTouchLock(): ByteArray =
            SoundpeatsPacket.encode(SoundpeatsCommands.GET_TOUCH_DISABLE_STATUS)

        fun encodeSetTouchLock(enabled: Boolean): ByteArray =
            SoundpeatsPacket.encode(SoundpeatsCommands.SET_TOUCH_DISABLE, booleanPayload(enabled))

        fun encodeGetPromptVolume(): ByteArray =
            SoundpeatsPacket.encode(SoundpeatsCommands.GET_PROMPT_VOLUME)

        fun encodeSetPromptVolume(volume: Int): ByteArray =
            SoundpeatsPacket.encode(
                SoundpeatsCommands.SET_PROMPT_VOLUME,
                byteArrayOf((volume.coerceIn(0, 100) and 0xFF).toByte())
            )

        fun encodeGetWaterDrainage(): ByteArray =
            SoundpeatsPacket.encode(SoundpeatsCommands.GET_WATER_DRAINAGE_STATUS)

        fun encodeSetWaterDrainage(enabled: Boolean): ByteArray =
            SoundpeatsPacket.encode(SoundpeatsCommands.SET_WATER_DRAINAGE, booleanPayload(enabled))

        /**
         * Starts or stops the ring. Left and right are separate commands, and the device wants them
         * as their own frames with a gap in between, so this returns one frame per earbud instead of
         * a single buffer. Confirmed on hardware: the ring starts with a non-zero payload and must be
         * stopped explicitly, otherwise it keeps ringing, so a start is always paired with a stop.
         */
        fun encodeFindDevice(start: Boolean, target: FindDeviceTarget): List<ByteArray> {
            val payload = booleanPayload(start)
            val left = SoundpeatsPacket.encode(SoundpeatsCommands.SET_LEFT_EAR_RING, payload)
            val right = SoundpeatsPacket.encode(SoundpeatsCommands.SET_RIGHT_EAR_RING, payload)

            return when (target) {
                FindDeviceTarget.LEFT -> listOf(left)
                FindDeviceTarget.RIGHT -> listOf(right)
                else -> listOf(left, right)
            }
        }

        /**
         * One EQ v2 band frame. Layout established from captured traffic of the vendor app applying
         * a preset to the device:
         *
         * ```
         * [0]    band count (16 on the Q3 Pro)
         * [1]    band index
         * [2..3] master gain * 60   int16 BE
         * [4..5] frequency * 3      int16 BE
         * [6..7] gain * 60          int16 BE (signed)
         * [8..9] Q * 4096           int16 BE
         * ```
         *
         * There is no separate curve/type byte: the last two bytes are a single 16-bit Q value
         * (`0x0333` = 819 = Q 0.2, `0x1000` = 4096 = Q 1.0).
         */
        fun encodeEqV2Band(bandCount: Int, masterGainDb: Double, band: EqBand): ByteArray {
            // The device truncates these products towards zero; rounding produces off-by-one Q/gain
            // bytes and the device then rejects the whole preset.
            val master = (masterGainDb * 60.0).toInt()
            val freq = band.freqHz * 3
            val gain = (band.gainDb * 60.0).toInt()
            val q = (band.q * 4096.0).toInt()

            val payload = byteArrayOf(
                (bandCount and 0xFF).toByte(),
                (band.index and 0xFF).toByte(),
                ((master shr 8) and 0xFF).toByte(),
                (master and 0xFF).toByte(),
                ((freq shr 8) and 0xFF).toByte(),
                (freq and 0xFF).toByte(),
                ((gain shr 8) and 0xFF).toByte(),
                (gain and 0xFF).toByte(),
                ((q shr 8) and 0xFF).toByte(),
                (q and 0xFF).toByte(),
            )
            return SoundpeatsPacket.encode(SoundpeatsCommands.MODIFY_EQ_V2, payload)
        }

        /** Encodes a whole profile as one frame per band, which is how the device expects it. */
        fun encodeEqV2(masterGainDb: Double, bands: List<EqBand>): List<ByteArray> =
            bands.map { encodeEqV2Band(bands.size, masterGainDb, it) }

        fun decodeEqV2Band(payload: ByteArray): EqBand? {
            if (payload.size < 10) return null
            val freq = ((payload[4].toInt() and 0xFF) shl 8) or (payload[5].toInt() and 0xFF)
            val gainRaw = ((payload[6].toInt() and 0xFF) shl 8) or (payload[7].toInt() and 0xFF)
            val gainSigned = if (gainRaw >= 0x8000) gainRaw - 0x10000 else gainRaw
            val qRaw = ((payload[8].toInt() and 0xFF) shl 8) or (payload[9].toInt() and 0xFF)
            return EqBand(
                index = payload[1].toInt() and 0xFF,
                freqHz = freq / 3,
                gainDb = gainSigned / 60.0,
                q = qRaw / 4096.0,
            )
        }

        private fun booleanPayload(enabled: Boolean): ByteArray =
            byteArrayOf(if (enabled) 1 else 0)
    }

    private val parser = SoundpeatsPacket.Parser()

    fun processIncomingBytes(bytes: ByteArray): List<GBDeviceEvent> {
        val events = mutableListOf<GBDeviceEvent>()

        for (frame in parser.feed(bytes)) {
            try {
                events.addAll(decodeFrame(frame))
            } catch (e: Exception) {
                LOG.error("Failed to decode frame command=0x%04x".format(frame.command), e)
            }
        }

        return events
    }

    private fun decodeFrame(frame: SoundpeatsPacket.Decoded): List<GBDeviceEvent> {
        val events = mutableListOf<GBDeviceEvent>()
        val payload = frame.payload

        LOG.debug("Received command=0x{} payload={}", "%04x".format(frame.command), payload.joinToString(" ") { "%02x".format(it) })

        when (frame.command) {
            SoundpeatsCommands.responseFor(SoundpeatsCommands.GET_FIRMWARE_VERSION) -> {
                val version = asciiVersion(payload)
                if (version != null) {
                    events.add(GBDeviceEventVersionInfo().apply {
                        fwVersion = version
                    })
                }
            }

            SoundpeatsCommands.responseFor(SoundpeatsCommands.GET_LEFT_BATTERY) ->
                batteryEvent(payload, BATTERY_INDEX_LEFT)?.let(events::add)

            SoundpeatsCommands.responseFor(SoundpeatsCommands.GET_RIGHT_BATTERY) ->
                batteryEvent(payload, BATTERY_INDEX_RIGHT)?.let(events::add)

            SoundpeatsCommands.responseFor(SoundpeatsCommands.GET_CASE_BATTERY) ->
                batteryEvent(payload, BATTERY_INDEX_CASE)?.let(events::add)

            SoundpeatsCommands.responseFor(SoundpeatsCommands.GET_NOISE_CANCELLATION_MODE) -> {
                val mode = paramByte(payload)?.let { SoundpeatsAncMode.fromCode(it) }
                if (mode != null) {
                    events.add(GBDeviceEventUpdatePreferences().apply {
                        withPreference(PREF_SOUNDPEATS_ANC_MODE, SoundpeatsAncMode.toPreference(mode))
                    })
                }
            }

            SoundpeatsCommands.responseFor(SoundpeatsCommands.GET_GAME_MODE_STATUS) ->
                booleanPreference(payload, PREF_SOUNDPEATS_GAME_MODE)?.let(events::add)

            SoundpeatsCommands.responseFor(SoundpeatsCommands.GET_MOVIE_MODE_STATUS) ->
                booleanPreference(payload, PREF_SOUNDPEATS_MOVIE_MODE)?.let(events::add)

            SoundpeatsCommands.responseFor(SoundpeatsCommands.GET_1TO2_STATUS) ->
                booleanPreference(payload, PREF_SOUNDPEATS_DUAL_CONNECT)?.let(events::add)

            SoundpeatsCommands.responseFor(SoundpeatsCommands.GET_TOUCH_DISABLE_STATUS) ->
                booleanPreference(payload, PREF_SOUNDPEATS_TOUCH_LOCK)?.let(events::add)

            SoundpeatsCommands.responseFor(SoundpeatsCommands.GET_PROMPT_VOLUME) -> {
                val volume = paramByte(payload)
                if (volume != null) {
                    events.add(GBDeviceEventUpdatePreferences().apply {
                        withPreference(PREF_SOUNDPEATS_PROMPT_VOLUME, volume)
                    })
                }
            }

            SoundpeatsCommands.responseFor(SoundpeatsCommands.GET_WATER_DRAINAGE_STATUS) ->
                booleanPreference(payload, PREF_SOUNDPEATS_WATER_DRAINAGE)?.let(events::add)

            else -> {
                LOG.debug("Unhandled response command=0x{}", "%04x".format(frame.command))
            }
        }

        return events
    }

    /**
     * The battery response carries a status byte first and the level in the second byte, which the
     * wire format; levels above 100 are treated as invalid and dropped.
     */
    private fun batteryEvent(payload: ByteArray, index: Int): GBDeviceEventBatteryInfo? {
        val level = paramByte(payload) ?: return null

        // 0xFF means "no reading": the earbud is in the case or charging. Reporting it as unknown
        // keeps the entry on the device card instead of leaving a stale value behind.
        if (level > 100) {
            return GBDeviceEventBatteryInfo().apply {
                batteryIndex = index
                this.level = GBDevice.BATTERY_UNKNOWN.toInt()
                state = BatteryState.BATTERY_NORMAL
            }
        }

        return GBDeviceEventBatteryInfo().apply {
            batteryIndex = index
            this.level = level
            state = BatteryState.BATTERY_NORMAL
        }
    }

    private fun booleanPreference(payload: ByteArray, key: String): GBDeviceEventUpdatePreferences? {
        val value = paramByte(payload) ?: return null
        return GBDeviceEventUpdatePreferences().apply {
            withPreference(key, value == 1)
        }
    }

    private fun paramByte(payload: ByteArray): Int? {
        if (payload.size < 2) return null
        return payload[1].toInt() and 0xFF
    }

    /** Firmware versions are plain ASCII in the payload; layout still to be confirmed on the wire. */
    private fun asciiVersion(payload: ByteArray): String? {
        if (payload.isEmpty()) return null

        val text = String(payload, StandardCharsets.UTF_8)
            .filter { it.code in 0x20..0x7E }
            .trim()

        return text.ifEmpty { null }
    }

    fun ancPreference(mode: String?): SoundpeatsAncMode =
        SoundpeatsAncMode.fromPreference(mode?.lowercase(Locale.ROOT))
}

/**
 * One EQ v2 band, as decoded/encoded by [SoundpeatsProtocol.encodeEqV2Band] and
 * [SoundpeatsProtocol.decodeEqV2Band].
 *
 * Values are the physical ones: frequency in Hz, gain in dB with 1/60 dB resolution and Q with
 * 1/4096 resolution, so `q = 0.2` becomes `0x0333` on the wire.
 */
data class EqBand(
    val index: Int,
    val freqHz: Int,
    val gainDb: Double,
    val q: Double,
)
