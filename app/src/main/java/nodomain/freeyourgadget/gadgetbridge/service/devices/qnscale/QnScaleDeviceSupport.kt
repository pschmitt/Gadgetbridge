/*  Copyright (C) 2026 brigon

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.qnscale

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.widget.Toast
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.SettingsActivity
import nodomain.freeyourgadget.gadgetbridge.devices.GenericWeightSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.qnscale.QnScaleProtocol
import nodomain.freeyourgadget.gadgetbridge.entities.GenericWeightSample
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.model.WeightUnit
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLESingleDeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder
import nodomain.freeyourgadget.gadgetbridge.util.GB
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * BLE support for the "QN-Scale" family of Chipsea CS20-based body-composition scales (tested
 * against an Arboleaf CS20N and, with the VA protocol, a GE Fit Plus). See [QnScaleProtocol] for the
 * frame format.
 *
 * Unlike most Gadgetbridge devices, this handshake is scale-initiated rather than phone-initiated:
 * after notifications are enabled the scale itself sends a ScaleInfo frame unprompted, and every
 * later step (config -> ack -> time sync -> history trigger -> history query -> stored measurement)
 * happens as a reaction to whatever the scale just sent, not as a fixed up-front sequence. This
 * mirrors the actual order captured from the manufacturer's own app.
 *
 * VA scales insert a user exchange after the history trigger (0xA0 hello -> 0xA1 -> 0xA0 profile ->
 * 0xA1 -> 0x22 query + 0xA2 last weight); without it they report no stored data and stream no live
 * weights.
 */
class QnScaleDeviceSupport : AbstractBTLESingleDeviceSupport(LOG) {
    private var weightScaleFactor: Float = 100.0f
    private var protocolType: Byte? = null
    private var liveSaved = false
    private var configSent = false

    init {
        addSupportedService(UUID_SERVICE_QN_SCALE)
    }

    override fun useAutoConnect(): Boolean {
        return false
    }

    override fun initializeDevice(builder: TransactionBuilder): TransactionBuilder {
        weightScaleFactor = 100.0f
        protocolType = null
        liveSaved = false
        configSent = false
        builder.setDeviceState(GBDevice.State.INITIALIZING)
        builder.notify(UUID_CHARACTERISTIC_NOTIFY, true)
        // Everything past this point (config, time sync, history sync) is driven reactively by
        // whatever the scale sends next - see onCharacteristicChanged.
        builder.setDeviceState(GBDevice.State.INITIALIZED)
        return builder
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ): Boolean {
        if (super.onCharacteristicChanged(gatt, characteristic, value)) {
            return true
        }
        if (characteristic.uuid != UUID_CHARACTERISTIC_NOTIFY) {
            return false
        }

        when (val frame = QnScaleProtocol.parse(value, weightScaleFactor, protocolType)) {
            is QnScaleProtocol.Frame.ScaleInfo -> {
                weightScaleFactor = frame.scaleFactor
                protocolType = frame.protocolType
                // The scale repeats this frame after the config; configure once per connection.
                if (!configSent) {
                    configSent = true
                    sendConfig()
                }
            }

            is QnScaleProtocol.Frame.ConfigAck -> sendTimeSync()

            is QnScaleProtocol.Frame.HistoryTrigger ->
                if (isVa()) send("VA hello", QnScaleProtocol.buildVaHello()) else sendHistoryQuery()

            is QnScaleProtocol.Frame.VaUserReply -> when (frame.step) {
                QnScaleProtocol.VA_STEP_HELLO -> {
                    val user = ActivityUser()
                    send("VA profile", QnScaleProtocol.buildVaProfile(user.heightCm, user.age, user.gender == ActivityUser.GENDER_MALE))
                }
                QnScaleProtocol.VA_STEP_PROFILE -> {
                    val type = protocolType ?: return true
                    send("VA history query", QnScaleProtocol.buildVaStoredDataQuery(type))
                    send("VA last weight", QnScaleProtocol.buildVaLastWeight(lastWeightKg()))
                }
                else -> LOG.debug("Unhandled QN-Scale VA reply: {}", GB.hexdump(value))
            }

            is QnScaleProtocol.Frame.NoStoredMeasurements -> LOG.debug("QN-Scale has no stored measurements")

            is QnScaleProtocol.Frame.StoredMeasurement -> {
                val timestampMillis = QnScaleProtocol.qnEpochSecondsToMillis(frame.deviceEpochSeconds, protocolType)
                if (frame.deviceEpochSeconds > lastImportedHistoryEpochSeconds()) {
                    saveMeasurement(frame.weightKg, frame.resistance1, timestampMillis)
                    saveLastImportedHistoryEpochSeconds(frame.deviceEpochSeconds)
                } else {
                    LOG.debug("Skipping already-imported stored measurement at {}", timestampMillis)
                }
            }

            is QnScaleProtocol.Frame.LiveWeight -> {
                if (frame.stable) {
                    // The scale repeats its final frame; a weigh-in is saved once, and the next
                    // one begins with unstable frames.
                    if (!liveSaved) {
                        saveMeasurement(frame.weightKg, frame.resistance1, System.currentTimeMillis())
                        liveSaved = true
                        sendStableAck()
                        if (isVa()) {
                            // The scale also stores this weigh-in and returns it on the next sync;
                            // its stored timestamp is a few seconds either side of now.
                            val skipUntil = QnScaleProtocol.nowAsQnEpochSeconds(protocolType) + LIVE_STORED_MARGIN_SECONDS
                            if (skipUntil > lastImportedHistoryEpochSeconds()) saveLastImportedHistoryEpochSeconds(skipUntil)
                        }
                    }
                } else {
                    liveSaved = false
                }
            }

            is QnScaleProtocol.Frame.Unknown -> LOG.debug("Unhandled QN-Scale frame: {}", GB.hexdump(value))
        }

        return true
    }

    override fun onSendConfiguration(config: String) {
        if (config == SettingsActivity.PREF_UNIT_WEIGHT && protocolType != null) {
            sendConfig()
        }
    }

    private fun sendConfig() {
        val type = protocolType ?: return
        val user = ActivityUser()
        val unitByte = when (GBApplication.getPrefs().weightUnit) {
            WeightUnit.POUND -> UNIT_BYTE_POUND
            else -> UNIT_BYTE_KILOGRAM
        }
        val frame = QnScaleProtocol.buildConfigFrame(
            protocolType = type,
            unitByte = unitByte,
            heightCm = user.heightCm,
            ageYears = user.age,
            isMale = user.gender == ActivityUser.GENDER_MALE,
        )
        createTransactionBuilder("send config").apply {
            write(UUID_CHARACTERISTIC_WRITE, *frame)
            queue()
        }
    }

    private fun sendTimeSync() {
        val type = protocolType ?: return
        val frame = QnScaleProtocol.buildTimeSyncFrame(type, QnScaleProtocol.nowAsQnEpochSeconds(type))
        createTransactionBuilder("time sync").apply {
            write(UUID_CHARACTERISTIC_WRITE, *frame)
            queue()
        }
    }

    private fun sendHistoryQuery() {
        val type = protocolType ?: return
        val frame = QnScaleProtocol.buildStoredDataQuery(type)
        createTransactionBuilder("history query").apply {
            write(UUID_CHARACTERISTIC_WRITE, *frame)
            queue()
        }
    }

    private fun sendStableAck() {
        val type = protocolType ?: return
        val frame = QnScaleProtocol.buildStableAck(type)
        createTransactionBuilder("stable ack").apply {
            write(UUID_CHARACTERISTIC_WRITE, *frame)
            queue()
        }
    }

    private fun send(task: String, frame: ByteArray) {
        createTransactionBuilder(task).apply {
            write(UUID_CHARACTERISTIC_WRITE, *frame)
            queue()
        }
    }

    private fun isVa(): Boolean = protocolType == QnScaleProtocol.PROTOCOL_TYPE_VA

    private fun prefs() = GBApplication.getDeviceSpecificSharedPrefs(device.address)

    /** The last weight this scale recorded, else the weight in the user's profile. */
    private fun lastWeightKg(): Float {
        try {
            GBApplication.acquireDB().use { handler ->
                GenericWeightSampleProvider(device, handler.daoSession).latestSample?.let { return it.weightKg }
            }
        } catch (e: Exception) {
            LOG.error("Error reading the last QN-Scale weight", e)
        }
        return ActivityUser().weightKg.toFloat()
    }

    private fun saveMeasurement(weightKg: Float, resistanceOhm: Int, timestampMillis: Long) {
        // A stable 0.0 kg frame is the scale's step-off/tare event, not a real reading.
        // Persisting it shows a 0 kg point in the weight chart and a 0 kg record in
        // Health Connect that consumers reject ("must be a positive number").
        if (weightKg <= 0.0f) {
            LOG.debug("Skipping zero-weight QN-Scale sample (tare/step-off)")
            return
        }
        try {
            GBApplication.acquireDB().use { handler ->
                val session = handler.daoSession
                val provider = GenericWeightSampleProvider(device, session)
                val sample = GenericWeightSample().apply {
                    timestamp = timestampMillis
                    this.weightKg = weightKg
                    // A raw resistance of 0 means the scale couldn't get a bioimpedance reading
                    // (typically poor foot contact) rather than a real measured impedance of 0Ω.
                    impedanceOhm = if (resistanceOhm > 0) resistanceOhm else null
                }
                provider.persistSamples(sample, context)
            }
            GB.signalActivityDataFinish(device)
        } catch (e: Exception) {
            LOG.error("Error saving QN-Scale measurement", e)
        }
    }

    private fun lastImportedHistoryEpochSeconds(): Long {
        return prefs().getLong(PREF_LAST_HISTORY_EPOCH_SECONDS, 0L)
    }

    private fun saveLastImportedHistoryEpochSeconds(epochSeconds: Long) {
        prefs().edit().putLong(PREF_LAST_HISTORY_EPOCH_SECONDS, epochSeconds).apply()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(QnScaleDeviceSupport::class.java)

        private val UUID_SERVICE_QN_SCALE: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        private val UUID_CHARACTERISTIC_NOTIFY: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        private val UUID_CHARACTERISTIC_WRITE: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")

        private const val PREF_LAST_HISTORY_EPOCH_SECONDS = "qnscale_last_history_epoch_seconds"
        private const val LIVE_STORED_MARGIN_SECONDS = 60L

        private const val UNIT_BYTE_KILOGRAM: Byte = 0x01
        private const val UNIT_BYTE_POUND: Byte = 0x02
    }
}
