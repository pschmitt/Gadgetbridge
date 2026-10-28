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

import android.os.SystemClock
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.devices.BaseActivitySummaryProvider
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.entities.GenericHeartRateSample
import nodomain.freeyourgadget.gadgetbridge.export.AutoFitExporter
import nodomain.freeyourgadget.gadgetbridge.export.AutoGpxExporter
import nodomain.freeyourgadget.gadgetbridge.export.GPXExporter
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryParser
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrack
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Date

/**
 * Workouts (`e8`): `aa 01` list, `aa 02` summary -> BaseActivitySummary, `aa 03` detail pages
 * (per-sample HR/pace/elevation, also stored as HR samples), then for outdoor types the `aa 09`
 * GPS file -> GPX. Detail + GPS are fetched once per workout.
 *
 * Frames arrive on the BLE thread, the GPS watchdog fires on the main thread: the workout state
 * is guarded by this object, and a workout is finished by whoever claims it first. Lock order:
 * this, then [history]; history never takes this lock ([gpsPending] is volatile).
 */
internal class Stratos4ProWorkoutSync(support: Stratos4ProSupport) : Stratos4ProFeature(support) {
    private class WorkoutInProgress(val id: Int, val pages: Int, val start: Long, val end: Long, val type: Int) {
        var page = 0
        val raw = ByteArrayOutputStream()
        val series = WorkoutSeries()
    }

    /** Still to fetch, from the `aa 01` list. */
    private val pendingWorkouts = ArrayDeque<WorkoutListItem>()

    /** The list item whose summary was requested last. */
    private var requested: WorkoutListItem? = null
    private var workout: WorkoutInProgress? = null

    @Volatile
    private var gpsWatchdog: Runnable? = null

    /** Uptime of the last GPS chunk, 0 until chunk 0 of the current transfer has arrived. */
    private var gpsLastChunkAt = 0L

    /** A GPS file is being waited for; it may go quiet for minutes, see [armGpsWatchdog]. */
    val gpsPending: Boolean get() = gpsWatchdog != null

    @Synchronized
    fun onConnect() {
        pendingWorkouts.clear()
        requested = null
        workout = null
        stopGpsWatchdog()
    }

    /** Frames plus workouts; waits for a running sync so the saved workout is not skipped. */
    private val pushSync = object : Runnable {
        override fun run() {
            if (!isConnected()) return
            if (history.isBusy()) {
                handler.postDelayed(this, PUSH_SYNC_DELAY_MS)
                return
            }
            LOG.info("workout saved on watch, syncing")
            history.fetch(workouts = true, full = false)
        }
    }

    /** All chunked with a 2-byte index and `fdfd`. */
    fun handleWorkout(value: ByteArray) {
        // `ac` = a workout was saved on the watch. Pushes come in bursts, sync once it is quiet.
        if (value[2] == MODE_REPORT) {
            LOG.debug("workout push: {}", value.toHex())
            handler.removeCallbacks(pushSync)
            handler.postDelayed(pushSync, PUSH_SYNC_DELAY_MS)
            return
        }
        if (value.size < 6 || value[2] != MODE_GET) return
        when (value[3]) {
            FIELD_WORKOUT_LIST -> {
                val p = chunks.collectFile(value) ?: return
                if (p.size < 2) {
                    // Bare `fdfd` = no list. Still advance, or the sync sticks in WORKOUTS.
                    LOG.debug("workout list empty")
                    history.onWorkoutsDone()
                    return
                }
                val items = Stratos4ProWorkoutParser.parseList(p)
                // Workouts stored with details are skipped: a re-store costs transfers and resets
                // the activity kind set in Gadgetbridge. Keys of workouts gone from the list drop.
                val stored = storedWorkouts() intersect items.map { storedKey(it.id, it.detailPages) }.toSet()
                devicePrefs.preferences.edit().putStringSet(PREF_STORED_WORKOUTS, stored).apply()
                val todo = items.filter { storedKey(it.id, it.detailPages) !in stored }
                synchronized(this) {
                    pendingWorkouts.clear()
                    pendingWorkouts.addAll(todo)
                }
                LOG.info("{} workouts on watch, {} to fetch: {}", u16(p, 0), todo.size, items)
                requestNextSummary()
            }

            FIELD_WORKOUT_SUMMARY -> {
                val p = chunks.collectFile(value) ?: return
                val w = storeSummary(p)
                if (w != null && (w.pages > 0 || Stratos4ProWorkoutParser.isGpsSport(w.type))) {
                    synchronized(this) { workout = w }
                    requestSplit(w)
                } else {
                    requestNextSummary()
                }
            }

            // One km split per request, until km 0.
            FIELD_WORKOUT_SPLIT -> {
                val p = chunks.collectFile(value) ?: return
                val w = synchronized(this) { workout } ?: return
                history.touch()
                val split = Stratos4ProWorkoutParser.parseKmSplit(p)
                if (split != null && w.series.splits.size < MAX_KM_SPLITS) {
                    w.series.splits.add(split)
                    requestSplit(w)
                } else if (w.pages > 0) {
                    requestDetail(w)
                } else {
                    requestGps(w)
                }
            }

            FIELD_WORKOUT_DETAIL -> {
                val p = chunks.collectFile(value) ?: return
                val w = synchronized(this) { workout } ?: return
                history.touch()
                w.raw.write(p)
                handleDetailPage(w, p)
                w.page++
                if (w.page < w.pages) {
                    requestDetail(w)
                } else if (Stratos4ProWorkoutParser.isGpsSport(w.type)) {
                    requestGps(w)
                } else {
                    finish(w, null)
                }
            }

            FIELD_WORKOUT_GPS -> {
                val w = synchronized(this) {
                    val w = workout ?: return
                    // Rearm on every chunk: the watchdog measures silence. After a workout the
                    // watch pauses mid-file for minutes, see armGpsWatchdog.
                    val now = SystemClock.elapsedRealtime()
                    if (gpsLastChunkAt > 0 && now - gpsLastChunkAt > GPS_TIMEOUT_MS) {
                        LOG.debug("GPS transfer for workout {} resumed after {} s", w.id, (now - gpsLastChunkAt) / 1000)
                    }
                    gpsLastChunkAt = now
                    armGpsWatchdog(w)
                    w
                }
                history.touch()
                val p = chunks.collectFile(value) ?: return
                w.raw.write(p)
                finish(w, p)
            }

            else -> LOG.debug("e8 field {}: {}", hex(value[3]), value.toHex())
        }
    }

    /** Next summary, or tell the history sync the workouts are done. */
    private fun requestNextSummary() {
        val item = synchronized(this) {
            workout = null
            pendingWorkouts.removeFirstOrNull().also { requested = it }
        }
        if (item == null) {
            history.onWorkoutsDone()
            return
        }
        queueFrames(
            "fetch workout ${item.id}",
            byteArrayOf(PKT_HEADER, CMD_WORKOUT, MODE_GET, FIELD_WORKOUT_SUMMARY, 0x02, hi(item.id), lo(item.id))
        )
    }

    /** `01 e8 aa 04 03 <id:2><n>`: km split n. */
    private fun requestSplit(w: WorkoutInProgress) {
        val n = w.series.splits.size
        queueFrames(
            "fetch workout ${w.id} split $n",
            byteArrayOf(PKT_HEADER, CMD_WORKOUT, MODE_GET, FIELD_WORKOUT_SPLIT, 0x03, hi(w.id), lo(w.id), n.toByte())
        )
    }

    /** `01 e8 aa 03 05 <id:2><page:2> 00` (last byte: "new algorithm", 0). */
    private fun requestDetail(w: WorkoutInProgress) {
        queueFrames(
            "fetch workout ${w.id} detail ${w.page}",
            byteArrayOf(PKT_HEADER, CMD_WORKOUT, MODE_GET, FIELD_WORKOUT_DETAIL, 0x05, hi(w.id), lo(w.id), hi(w.page), lo(w.page), 0x00)
        )
    }

    /**
     * `01 e8 aa 09 00 02 <id:2>`. A workout without a track may get no answer; a watchdog moves on.
     */
    private fun requestGps(w: WorkoutInProgress) {
        queueFrames(
            "fetch workout ${w.id} gps",
            byteArrayOf(PKT_HEADER, CMD_WORKOUT, MODE_GET, FIELD_WORKOUT_GPS, 0x00, 0x02, hi(w.id), lo(w.id))
        )
        synchronized(this) {
            gpsLastChunkAt = 0L
            armGpsWatchdog(w)
        }
    }

    /**
     * (Re)arm the silence timer of a GPS transfer. Before chunk 0: [GPS_TIMEOUT_MS] (no track may
     * mean no answer). Once flowing: [GPS_PAUSE_TIMEOUT_MS], since right after a workout the watch
     * pauses mid-file and resumes by itself (137 s, 25 s, 95 s on one 1287-chunk track). Locked.
     */
    private fun armGpsWatchdog(w: WorkoutInProgress) {
        gpsWatchdog?.let { handler.removeCallbacks(it) }
        val flowing = gpsLastChunkAt > 0
        val watchdog = object : Runnable {
            override fun run() {
                synchronized(this@Stratos4ProWorkoutSync) {
                    // A newer watchdog replaced this one after it was already due.
                    if (gpsWatchdog !== this) return
                    // Clear it on every path: history.isBusy() trusts a non-null one.
                    gpsWatchdog = null
                    if (workout !== w) return
                }
                LOG.warn("GPS transfer for workout {} stalled, giving up", w.id)
                chunks.cancelFile(CMD_WORKOUT, MODE_GET, FIELD_WORKOUT_GPS)
                finish(w, null, clean = false)
            }
        }
        gpsWatchdog = watchdog
        handler.postDelayed(watchdog, if (flowing) GPS_PAUSE_TIMEOUT_MS else GPS_TIMEOUT_MS)
    }

    /** Locked. */
    private fun stopGpsWatchdog() {
        gpsWatchdog?.let { handler.removeCallbacks(it) }
        gpsWatchdog = null
        gpsLastChunkAt = 0L
    }

    /** Take [w] out of the transfer; false if the watchdog or the last chunk got there first. */
    private fun claim(w: WorkoutInProgress): Boolean = synchronized(this) {
        if (workout !== w) return false
        workout = null
        stopGpsWatchdog()
        true
    }

    /** [clean] = the watch answered everything we asked for (an empty GPS file counts). */
    private fun finish(w: WorkoutInProgress, gps: ByteArray?, clean: Boolean = true) {
        if (!claim(w)) return
        history.touch()
        if (clean && w.start > devicePrefs.getLong(PREF_LAST_WORKOUT_DETAIL, 0L)) {
            devicePrefs.preferences.edit().putLong(PREF_LAST_WORKOUT_DETAIL, w.start).apply()
        }
        if (clean) markStored(w.id, w.pages)
        try {
            val name = Stratos4ProWorkoutParser.sportTypeToActivityKind(w.type).getLabel(context)
            val track = gps?.let { Stratos4ProWorkoutParser.buildTrack(w.series, w.start, name, it) }
            track?.let { LOG.debug("workout {} GPS: {} points", w.id, it.allPoints.size) }
            var summary: BaseActivitySummary? = null
            GBApplication.acquireDB().use { db ->
                val session = db.daoSession
                val s = ActivitySummaryParser.findOrCreateBaseActivitySummary(session, device, w.start)
                val dir = device.deviceCoordinator.getWritableExportDirectory(device, true)
                val raw = File(dir, "stratos4pro_${w.start}.bin")
                raw.writeBytes(w.raw.toByteArray())
                s.rawDetailsPath = raw.absolutePath
                val d = s.summaryData?.let { ActivitySummaryData.fromJson(it) } ?: ActivitySummaryData()
                if (Stratos4ProWorkoutParser.addDetailDerivedStats(d, w.series)) s.summaryData = d.toJson()
                if (track != null) {
                    writeGpx(track, File(dir, "stratos4pro_${w.start}.gpx"))?.let { s.gpxTrack = it }
                }
                BaseActivitySummaryProvider(device, session).persistSamples(listOf(s), context)
                summary = s
            }
            // Auto-export per the user's settings, after persisting. FIT keeps cadence, stride,
            // distance and sport; GPX needs a track, FIT does not.
            summary?.let { s ->
                if (track != null) AutoGpxExporter.doExport(context, device, s, track)
                AutoFitExporter.doExport(context, device, s, track)
            }
        } catch (e: Exception) {
            LOG.error("failed to finish workout {}", w.id, e)
        }
        LOG.info("workout {} done: {} detail pages, {} HR samples, gps={} bytes", w.id, w.pages, w.series.hr.size, gps?.size ?: 0)
        requestNextSummary()
    }

    private fun handleDetailPage(w: WorkoutInProgress, p: ByteArray) {
        val page = Stratos4ProWorkoutParser.parseDetailPage(w.series, p) ?: return
        if (page.hr.isNotEmpty()) {
            try {
                GBApplication.acquireDB().use { db ->
                    val session = db.daoSession
                    val deviceId = DBHelper.getDevice(device, session).id!!
                    val userId = DBHelper.getUser(session).id!!
                    session.genericHeartRateSampleDao.insertOrReplaceInTx(page.hr.map { (ts, bpm) ->
                        GenericHeartRateSample().apply {
                            timestamp = ts * 1000L; this.deviceId = deviceId; this.userId = userId; heartRate = bpm
                        }
                    })
                }
            } catch (e: Exception) {
                LOG.error("failed to store workout HR", e)
            }
        }
        val s = w.series
        LOG.debug("workout {} detail page {}: {} samples every {} s from {} (hr={} cad={} spd={} ele={} dist={})",
            w.id, w.page, page.samples, page.interval, page.start, s.hr.size, s.cadence.size, s.speed.size, s.elevation.size, s.distance.size)
    }

    /** Writes [track] to [file] for the in-app workout chart; returns the path, or null. */
    private fun writeGpx(track: ActivityTrack, file: File): String? {
        try {
            GBApplication.acquireDB().use { db ->
                track.device = DBHelper.getDevice(device, db.daoSession)
                track.user = DBHelper.getUser(db.daoSession)
            }
            GPXExporter().performExport(track, file, null)
        } catch (e: Exception) {
            LOG.error("GPX export failed", e)
            return null
        }
        return file.absolutePath
    }

    /** Stores the summary; returns the workout for the follow-up detail/GPS fetch, or null. */
    private fun storeSummary(p: ByteArray): WorkoutInProgress? {
        val summary = Stratos4ProWorkoutParser.parseSummary(p) ?: return null
        val id = summary.id!!
        val start = summary.start!!
        val end = summary.end!!
        val kind = Stratos4ProWorkoutParser.sportTypeToActivityKind(summary.type)
        // Detail + GPS are expensive (3 h = 40 pages + ~126 kB), so fetched once. The mark only
        // advances on a clean finish, so a stalled transfer is retried.
        val detailsDone = start <= devicePrefs.getLong(PREF_LAST_WORKOUT_DETAIL, 0L)
        try {
            GBApplication.acquireDB().use { db ->
                val session = db.daoSession
                val s = ActivitySummaryParser.findOrCreateBaseActivitySummary(session, device, start)
                s.endTime = Date(end * 1000L)
                s.activityKind = kind.code
                s.rawSummaryData = p
                // Keep what the detail fetch added (peaks, km splits); it runs once per workout.
                val d = summary.toSummaryData()
                val old = ActivitySummaryData.fromJson(s.summaryData)
                for (k in old.keys) if (!d.has(k)) d.add(k, old.get(k))
                s.summaryData = d.toJson()
                BaseActivitySummaryProvider(device, session).persistSamples(listOf(s), context)
            }
            LOG.info("stored workout {} {}..{} type={} detailsDone={}", kind, start, end, summary.type, detailsDone)
        } catch (e: Exception) {
            LOG.error("Failed to store workout", e)
            return null
        }
        // The page count comes from the list item this summary was requested for.
        val item = synchronized(this) { requested }
        if (detailsDone) {
            item?.let { markStored(it.id, it.detailPages) }
            return null
        }
        if (item != null && item.id != id) LOG.warn("workout summary id {} for list item {}", id, item.id)
        return WorkoutInProgress(id, item?.detailPages ?: 0, start, end, summary.type)
    }

    private fun storedWorkouts(): Set<String> =
        devicePrefs.preferences.getStringSet(PREF_STORED_WORKOUTS, null).orEmpty()

    private fun markStored(id: Int, pages: Int) {
        devicePrefs.preferences.edit().putStringSet(PREF_STORED_WORKOUTS, storedWorkouts() + storedKey(id, pages)).apply()
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(Stratos4ProWorkoutSync::class.java)

        /** How long to wait for a workout GPS file before deciding there is none. */
        const val GPS_TIMEOUT_MS = 8_000L
        /** Silence allowed once a GPS file is flowing; the watch pauses mid-file, see armGpsWatchdog. */
        const val GPS_PAUSE_TIMEOUT_MS = 300_000L
        /** Upper bound for the km split loop. */
        const val MAX_KM_SPLITS = 100
        /** Quiet time after an `e8 ac` push before the sync, also the busy retry. */
        const val PUSH_SYNC_DELAY_MS = 5_000L
        /** Start time of the newest workout whose detail pages + GPS file were fully fetched. */
        // Bumped when the detail parser extracts more, so synced workouts are pulled once again.
        // v2: cadence/speed/elevation/distance; v3: speed from distance.
        const val PREF_LAST_WORKOUT_DETAIL = "stratos4pro_last_workout_detail_v3"

        /**
         * List items stored with details, as [storedKey]; versioned with the mark above. Each list
         * fetch keeps only the items still listed, so it never outgrows the watch's own list.
         */
        private const val PREF_STORED_WORKOUTS = PREF_LAST_WORKOUT_DETAIL + "_stored"

        /** A workout rewritten with another page count is fetched again. */
        private fun storedKey(id: Int, pages: Int) = "$id:$pages"
    }
}
