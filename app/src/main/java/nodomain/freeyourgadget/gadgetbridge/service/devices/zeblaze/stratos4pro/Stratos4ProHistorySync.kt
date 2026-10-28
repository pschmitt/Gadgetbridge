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

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.devices.BatteryLevelProvider
import nodomain.freeyourgadget.gadgetbridge.devices.BatteryVoltageSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.GenericHeartRateSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.GenericStressSampleProvider
import nodomain.freeyourgadget.gadgetbridge.entities.BatteryLevel
import nodomain.freeyourgadget.gadgetbridge.entities.BatteryVoltageSample
import nodomain.freeyourgadget.gadgetbridge.entities.GenericHeartRateSample
import nodomain.freeyourgadget.gadgetbridge.entities.GenericHrvValueSample
import nodomain.freeyourgadget.gadgetbridge.entities.GenericSleepStageSample
import nodomain.freeyourgadget.gadgetbridge.entities.GenericSpo2Sample
import nodomain.freeyourgadget.gadgetbridge.entities.GenericStressSample
import nodomain.freeyourgadget.gadgetbridge.entities.Stratos4ProStepsSample
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder
import nodomain.freeyourgadget.gadgetbridge.util.GB
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * History sync: per-minute frames (`c5` / `c6`), sleep and stress files, then the workouts. One
 * bulk transfer at a time: a second range request aborts the one in flight (no `fd`), so stages
 * run strictly in sequence. Stage changes are synchronized (frames arrive on the BLE thread, fetches
 * come from the main thread too); DB writes run outside the lock, and [isBusy] takes none.
 */
internal class Stratos4ProHistorySync(support: Stratos4ProSupport) : Stratos4ProFeature(support) {
    private enum class SyncStage { FRAMES, SLEEP, STRESS, WORKOUTS, DONE }

    @Volatile
    private var syncStage = SyncStage.DONE

    @Volatile
    private var syncStageStartedAt = 0L
    private var syncStart = 0L
    private var syncEnd = 0L
    private var syncFrames = 0
    private var syncNext = 0
    private var syncWorkouts = false

    /**
     * Also pull sleep/stress? A `c5 acac` push comes about once a minute; re-pulling both files
     * each time is waste, so a push refreshes the frames only.
     */
    private var syncFull = false

    /** A sync cut by a disconnect is gone on the watch too: start clean. */
    @Synchronized
    fun onConnect() {
        syncStage = SyncStage.DONE
    }

    /**
     * Sync running. A stage older than [SYNC_STAGE_TIMEOUT_S] is stalled and must not keep blocking
     * the keepalive.
     */
    fun isBusy(): Boolean =
        syncStage != SyncStage.DONE && (
                // A GPS file may go quiet for minutes (own watchdog); a restart would abort it.
                workouts.gpsPending ||
                        System.currentTimeMillis() / 1000L - syncStageStartedAt < SYNC_STAGE_TIMEOUT_S)

    /** Keep a long transfer (workout details, GPS file) from counting as stalled. */
    fun touch() {
        syncStageStartedAt = System.currentTimeMillis() / 1000L
    }

    /** The workout stage has fetched everything. */
    fun onWorkoutsDone() = advanceFrom(SyncStage.WORKOUTS)

    /** A bulk transfer of [stage] is complete; move on unless the sync has moved already. */
    @Synchronized
    private fun advanceFrom(stage: SyncStage) {
        if (syncStage == stage) nextSyncStage()
    }

    /** Everything since the last sync, max 7 days: frames, sleep, stress, workouts. */
    @Synchronized
    fun fetch(b: TransactionBuilder? = null, workouts: Boolean = false, full: Boolean = true) {
        val now = System.currentTimeMillis() / 1000L
        if (isBusy()) {
            LOG.debug("sync already running ({}), ignoring", syncStage)
            return
        }
        if (syncStage != SyncStage.DONE) {
            LOG.warn("sync stage {} stalled, restarting", syncStage)
        }
        syncStageStartedAt = now
        val last = devicePrefs.getLong(PREF_LAST_SYNC, 0L)
        syncStart = maxOf(last, now - 7 * 86400L)
        syncEnd = now
        syncFrames = 0
        syncNext = 0
        syncWorkouts = workouts
        syncFull = full
        syncStage = SyncStage.FRAMES
        val builder = b ?: createTransactionBuilder("fetch history")
        builder.writeData(rangeCmd(CMD_FITNESS_INDEX, MODE_GET, FIELD_FRAME_MINUTE, syncStart, syncEnd))
        if (b == null) builder.queue()
        LOG.info("fetch history {}..{}", syncStart, syncEnd)
    }

    /** Advance to the next bulk transfer once the current one has fully arrived. Locked. */
    private fun nextSyncStage() {
        val now = System.currentTimeMillis() / 1000L
        syncStage = when (syncStage) {
            SyncStage.FRAMES -> if (syncFull) SyncStage.SLEEP else if (syncWorkouts) SyncStage.WORKOUTS else SyncStage.DONE
            SyncStage.SLEEP -> SyncStage.STRESS
            SyncStage.STRESS -> if (syncWorkouts) SyncStage.WORKOUTS else SyncStage.DONE
            SyncStage.WORKOUTS -> SyncStage.DONE
            SyncStage.DONE -> SyncStage.DONE
        }
        syncStageStartedAt = now
        LOG.debug("sync stage -> {}", syncStage)
        val request = when (syncStage) {
            SyncStage.SLEEP -> rangeCmd(CMD_HEALTH_STREAM, MODE_GET, FIELD_SLEEP_FILE, now - 7 * 86400L, now)
            SyncStage.STRESS -> rangeCmd(CMD_HEALTH_MONITOR, MODE_GET, FIELD_STRESS_HISTORY, syncStart, now)
            SyncStage.WORKOUTS -> rangeCmd(CMD_WORKOUT, MODE_GET, FIELD_WORKOUT_LIST, now - 30 * 86400L, now)
            SyncStage.DONE -> {
                LOG.info("history sync done")
                GB.signalActivityDataFinish(device)
                settings.readDeferredLists()
                fetchBatteryLogIfDue()
                return
            }
            SyncStage.FRAMES -> return
        }
        queueFrames("fetch ${syncStage.name.lowercase()}", request)
    }

    /** `c5` replies: `aa 01` = number of fitness frames in the requested range; `ac ac` = new data. */
    @Synchronized
    fun handleFitnessIndex(value: ByteArray) {
        if (value.size < 5) return
        if (value[2] == MODE_REPORT) {
            LOG.debug("watch reports new fitness data, syncing frames")
            fetch(full = false)
            return
        }
        if (value[2] != MODE_GET || value[3] != FIELD_FRAME_MINUTE) return
        val count = tlvInt(value) ?: return
        syncFrames = count
        syncNext = 0
        LOG.debug("{} fitness frames to fetch", count)
        if (count == 0) {
            finishFitnessSync()
        } else {
            requestNextFitnessFrame()
        }
    }

    /** Locked. */
    private fun requestNextFitnessFrame() {
        if (syncNext >= syncFrames) {
            if (syncFrames > 0) finishFitnessSync()
            return
        }
        val idx = syncNext++
        queueFrames(
            "fetch fitness frame $idx",
            byteArrayOf(PKT_HEADER, CMD_HEALTH_STREAM, MODE_GET, FIELD_FRAME_MINUTE, 0x0a) +
                    be32Bytes(syncStart.toInt()) + be32Bytes(syncEnd.toInt()) + byteArrayOf(hi(idx), lo(idx))
        )
    }

    private fun finishFitnessSync() {
        // Overlap the next window by an hour to refetch a frame still being written; rows are keyed
        // by time.
        devicePrefs.preferences.edit().putLong(PREF_LAST_SYNC, syncEnd - 3600L).apply()
        syncFrames = 0
        LOG.debug("fitness frames done")
        nextSyncStage()
    }

    /**
     * `c6`: `aa 01` history frame (after `c5 aa 01`), `aa 04` sleep file, `ac 01` live minute, `ac
     * 02` one stress reading.
     */
    fun handleHealthStream(value: ByteArray) {
        if (value.size < 5) return
        when (value[2]) {
            MODE_GET -> when (value[3]) {
                FIELD_FRAME_MINUTE -> {
                    val payload = chunks.collect(value, 5) ?: return
                    val frame = Stratos4ProHistoryParser.parseHistoryFrame(payload)
                    if (frame == null) {
                        LOG.warn("fitness frame too short: {}", payload.toHex())
                    } else {
                        storeMotionMinutes(frame.minutes)
                        LOG.debug("fitness frame {} start={} items={}", frame.index, frame.start, frame.minutes.size)
                    }
                    synchronized(this) { requestNextFitnessFrame() }
                }

                FIELD_SLEEP_FILE -> {
                    val payload = chunks.collectFile(value) ?: return
                    handleSleepFile(payload)
                }

                else -> LOG.debug("c6 get field {}: {}", hex(value[3]), value.toHex())
            }

            MODE_REPORT -> when (value[3]) {
                FIELD_FRAME_MINUTE -> {
                    val payload = chunks.collect(value, 5) ?: return
                    val (ts, item) = Stratos4ProHistoryParser.parseLiveMinute(payload) ?: run {
                        LOG.warn("live minute unparsed: {}", payload.toHex())
                        return
                    }
                    LOG.debug("live minute at unix {}: {}", ts, item)
                    storeMotionMinutes(listOf(ts to item))
                }

                FIELD_STREAM_STRESS -> {
                    val (ts, stress) = Stratos4ProHistoryParser.parseStressReading(value) ?: run {
                        // Also 0 = no reading yet, so not a warning.
                        LOG.debug("stress push skipped: {}", value.toHex())
                        return
                    }
                    storeStress(stress, ts)
                }

                else -> LOG.debug("c6 push field {}: {}", hex(value[3]), value.toHex())
            }

            else -> {}
        }
    }

    /**
     * `e1 aa 02` stress history. Auto stress off: only the terminator `e1 aa 02 fd 00` comes. That
     * is an empty file, else the sync sticks in STRESS.
     */
    fun handleStressHistory(value: ByteArray) {
        val payload = chunks.collect(value, 7, bareEndIsEmpty = true) ?: return
        val samples = Stratos4ProHistoryParser.parseStressFile(payload)
        if (samples.isNotEmpty()) {
            try {
                GBApplication.acquireDB().use { db ->
                    val session = db.daoSession
                    val deviceId = DBHelper.getDevice(device, session).id!!
                    val userId = DBHelper.getUser(session).id!!
                    session.genericStressSampleDao.insertOrReplaceInTx(samples.map { (ts, v) ->
                        GenericStressSample().apply {
                            timestamp = ts * 1000L
                            this.deviceId = deviceId
                            this.userId = userId
                            stress = v
                        }
                    })
                }
            } catch (e: Exception) {
                LOG.error("Failed to store stress history", e)
            }
        }
        LOG.debug("stress history: {} bytes, {} samples stored", payload.size, samples.size)
        advanceFrom(SyncStage.STRESS)
    }

    /** `e1 ac 03`: live HR during a measurement. */
    fun handleLiveHeartRate(value: ByteArray) {
        for ((hr, ts) in Stratos4ProHistoryParser.parseLiveHeartRates(value)) storeHeartRate(hr, ts)
    }

    /** Event log (~200 KB, ~15 s) after the lists, at most every [BATTERY_LOG_INTERVAL_S]. */
    private fun fetchBatteryLogIfDue() {
        val last = devicePrefs.getLong(PREF_LAST_BATTERY_LOG, 0L)
        if (System.currentTimeMillis() / 1000L - last < BATTERY_LOG_INTERVAL_S) return
        handler.postDelayed({
            if (!isConnected() || syncStage != SyncStage.DONE) return@postDelayed
            // Stamped on send, so a watch that never answers is not asked every sync.
            val now = System.currentTimeMillis() / 1000L
            devicePrefs.preferences.edit().putLong(PREF_LAST_BATTERY_LOG, now).apply()
            queueGetField(CMD_EVENT_LOG, FIELD_EVENT_LOG)
        }, BATTERY_LOG_DELAY_MS)
    }

    /** `d1 aa 04` event log -> battery level and voltage history. Other `d1` frames: log only. */
    fun handleEventLog(value: ByteArray) {
        if (value.size < 6 || value[2] != MODE_GET || value[3] != FIELD_EVENT_LOG) {
            LOG.debug("d1 frame: {}", value.toHex())
            return
        }
        val payload = chunks.collectFile(value) ?: return
        val log = Stratos4ProBatteryLog.parse(payload)
        LOG.info(
            "event log: {} bytes, {} records, {} levels, {} voltages",
            payload.size, log.records, log.levels.size, log.millivolts.size
        )
        if (log.levels.isEmpty() && log.millivolts.isEmpty()) return
        try {
            GBApplication.acquireDB().use { db ->
                val session = db.daoSession
                BatteryLevelProvider(device, session).persistSamples(log.levels.map { (ts, level) ->
                    BatteryLevel().apply {
                        timestamp = ts.toInt()
                        batteryIndex = 0
                        this.level = level
                    }
                }, context)
                BatteryVoltageSampleProvider(device, session).persistSamples(log.millivolts.map { (ts, mv) ->
                    BatteryVoltageSample().apply {
                        timestamp = ts * 1000L
                        batteryIndex = 0
                        voltage = mv / 1000f
                    }
                }, context)
            }
        } catch (e: Exception) {
            LOG.error("Failed to store battery history", e)
        }
    }

    private fun handleSleepFile(payload: ByteArray) {
        val records = Stratos4ProHistoryParser.parseSleepFile(payload)
        if (records.isNotEmpty()) {
            try {
                GBApplication.acquireDB().use { db ->
                    val session = db.daoSession
                    val deviceId = DBHelper.getDevice(device, session).id!!
                    val userId = DBHelper.getUser(session).id!!
                    session.genericSleepStageSampleDao.insertOrReplaceInTx(records.map { r ->
                        GenericSleepStageSample().apply {
                            timestamp = r.start * 1000L
                            this.deviceId = deviceId
                            this.userId = userId
                            duration = r.duration
                            stage = r.stage
                        }
                    })
                }
            } catch (e: Exception) {
                LOG.error("Failed to store sleep", e)
            }
        }
        LOG.debug("sleep file: {} bytes, {} stages stored", payload.size, records.size)
        advanceFrom(SyncStage.SLEEP)
    }

    /** Steps / heart rate / SpO2 / HRV per minute. One DB session per batch: a 7-day sync is ~840 minutes. */
    private fun storeMotionMinutes(minutes: List<Pair<Long, MotionValues>>) {
        if (minutes.isEmpty()) return
        try {
            GBApplication.acquireDB().use { db ->
                val session = db.daoSession
                val deviceId = DBHelper.getDevice(device, session).id!!
                val userId = DBHelper.getUser(session).id!!
                val steps = ArrayList<Stratos4ProStepsSample>()
                val hrs = ArrayList<GenericHeartRateSample>()
                val spo2s = ArrayList<GenericSpo2Sample>()
                val hrvs = ArrayList<GenericHrvValueSample>()
                for ((ts, v) in minutes) {
                    val millis = ts * 1000L
                    if (v.steps > 0 || v.calories > 0 || v.distance > 0) {
                        steps.add(Stratos4ProStepsSample().apply {
                            timestamp = millis
                            this.deviceId = deviceId
                            this.userId = userId
                            this.steps = v.steps
                            distanceCm = v.distanceCm
                            activeCalories = v.activeCalories
                        })
                    }
                    val hr = v.heartRate
                    if (hr in MIN_PLAUSIBLE_BPM..MAX_PLAUSIBLE_BPM) {
                        hrs.add(GenericHeartRateSample().apply {
                            timestamp = millis
                            this.deviceId = deviceId
                            this.userId = userId
                            heartRate = hr
                        })
                    }
                    if (v.spo2 in 50..100) {
                        spo2s.add(GenericSpo2Sample().apply {
                            timestamp = millis
                            this.deviceId = deviceId
                            this.userId = userId
                            spo2 = v.spo2
                        })
                    }
                    if (v.hrv > 0) {
                        hrvs.add(GenericHrvValueSample().apply {
                            timestamp = millis
                            this.deviceId = deviceId
                            this.userId = userId
                            value = v.hrv
                        })
                    }
                }
                if (steps.isNotEmpty()) session.stratos4ProStepsSampleDao.insertOrReplaceInTx(steps)
                if (hrs.isNotEmpty()) session.genericHeartRateSampleDao.insertOrReplaceInTx(hrs)
                if (spo2s.isNotEmpty()) session.genericSpo2SampleDao.insertOrReplaceInTx(spo2s)
                if (hrvs.isNotEmpty()) session.genericHrvValueSampleDao.insertOrReplaceInTx(hrvs)
            }
        } catch (e: Exception) {
            LOG.error("Failed to store fitness minutes", e)
        }
    }

    /**
     * `c3` day summary: 02 last HR, 03 its time, 0c / 09 today's everyday / workout metrics. Steps,
     * kcal and distance come from the per-minute frames; the totals are only logged.
     */
    fun handleDaySummary(value: ByteArray) {
        if (value.size < 5 || value[2] != MODE_GET) return
        val fields = parseTlv(value, 5)

        val rate = fields[FIELD_LAST_HEART_RATE]?.takeIf { it.isNotEmpty() }?.let { u8(it, 0) }
        val measuredAt = fields[FIELD_LAST_HEART_RATE_TIME]?.takeIf { it.size >= 4 }?.let { be32(it, 0) }
        if (rate != null && rate in MIN_PLAUSIBLE_BPM..MAX_PLAUSIBLE_BPM && measuredAt != null && measuredAt > 0) {
            storeHeartRate(rate, measuredAt)
        }

        val parts = listOfNotNull(fields[FIELD_DAY_METRICS], fields[FIELD_DAY_WORKOUT_METRICS])
            .map { parseSubTlv(it) }
        if (parts.isNotEmpty()) {
            fun total(key: Int) = parts.sumOf { it[key]?.beInt() ?: 0 }
            LOG.info(
                "watch day total: {} steps, {} kcal, {} m",
                total(SUBFIELD_STEPS), total(SUBFIELD_KCAL), total(SUBFIELD_DISTANCE)
            )
        }
    }

    /** Store one HR reading in the generic table. [measuredAt] in unix seconds. */
    private fun storeHeartRate(rate: Int, measuredAt: Long) {
        try {
            GBApplication.acquireDB().use { db ->
                val session = db.daoSession
                val sample = GenericHeartRateSample()
                sample.timestamp = measuredAt * 1000L
                sample.heartRate = rate
                sample.deviceId = DBHelper.getDevice(device, session).id!!
                sample.userId = DBHelper.getUser(session).id!!
                GenericHeartRateSampleProvider(device, session).addSample(sample)
                LOG.debug("stored heart rate {} bpm at unix {}", rate, measuredAt)
            }
        } catch (e: Exception) {
            LOG.error("Failed to store heart rate", e)
        }
    }

    /** Store one stress reading. */
    private fun storeStress(stress: Int, measuredAt: Long) {
        try {
            GBApplication.acquireDB().use { db ->
                val session = db.daoSession
                val sample = GenericStressSample()
                sample.timestamp = measuredAt * 1000L
                sample.stress = stress
                sample.deviceId = DBHelper.getDevice(device, session).id!!
                sample.userId = DBHelper.getUser(session).id!!
                GenericStressSampleProvider(device, session).addSample(sample)
                LOG.debug("stored stress {} at unix {}", stress, measuredAt)
            }
        } catch (e: Exception) {
            LOG.error("Failed to store stress", e)
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(Stratos4ProHistorySync::class.java)

        /** Unix seconds of the last completed fitness sync window end. */
        const val PREF_LAST_SYNC = "stratos4pro_last_sync"
        /** A bulk stage that has not completed within this long is considered lost. */
        const val SYNC_STAGE_TIMEOUT_S = 90L
        /** Unix seconds of the last battery log request. */
        private const val PREF_LAST_BATTERY_LOG = "stratos4pro_last_battery_log"
        private const val BATTERY_LOG_INTERVAL_S = 12 * 3600L
        private const val BATTERY_LOG_DELAY_MS = 8_000L
    }
}
