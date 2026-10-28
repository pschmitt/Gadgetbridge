/*  Copyright (C) 2026 Dany Mestas

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
package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummaryDao
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.GB
import org.slf4j.LoggerFactory
import java.util.Date

/**
 * Uploads workouts to the online fitness trackers the user has enabled, and keeps already-uploaded
 * ones in step with later local edits. Enqueued (debounced) by
 * [nodomain.freeyourgadget.gadgetbridge.externalevents.NewDataReceiver] after a device fetch, and
 * directly by the workout detail screen after an edit.
 *
 * Two passes, with different bounds:
 *
 *  - new uploads, over workouts synced within [RECENT_WINDOW_MS], so enabling the feature does not
 *    ship a whole back catalogue at once;
 *  - re-sync, over every row in the upload table regardless of age, because a device reprocess
 *    rewrites arbitrarily old workouts.
 *
 * The re-sync pass compares a cheap fingerprint of the workout (see
 * [WorkoutUploadStore.sourceHashOf]) before doing any work, so the steady state costs one file
 * stat per uploaded workout and no network traffic.
 *
 * What each service accepts and can update is described by its [WorkoutUploadTarget], so neither
 * pass has per-service branches. On failure a notification is posted whose text explains the
 * reason (no internet / server unreachable / HTTP error).
 */
class WorkoutUploadWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    private val LOG = LoggerFactory.getLogger(WorkoutUploadWorker::class.java)

    override fun doWork(): Result {
        val context = applicationContext
        val targets = WorkoutUploadTargets.active(context, GBApplication.getPrefs())
        if (targets.isEmpty()) {
            return Result.success()
        }

        val address = inputData.getString(INPUT_DEVICE_ADDRESS)
        if (address.isNullOrBlank()) {
            LOG.warn("WorkoutUploadWorker started without a device address, skipping")
            return Result.success()
        }
        val gbDevice = GBApplication.app().deviceManager.getDeviceByAddress(address)
        if (gbDevice == null) {
            LOG.warn("WorkoutUploadWorker: device {} not found", address)
            return Result.success()
        }
        val deviceId = deviceEntityId(gbDevice) ?: return Result.success()

        val rowsByTarget = targets.associateWith { WorkoutUploadStore.allUploadedRows(it.service) }
        val provider = gbDevice.deviceCoordinator.getActivityTrackProvider(gbDevice, context)
        val failures = mutableMapOf<WorkoutUploadTarget, String>()

        for (summary in queryRecentSummaries(gbDevice)) {
            val id = summary.id ?: continue
            val sourceHash = WorkoutUploadStore.sourceHashOf(summary)
            for (target in targets) {
                if (rowsByTarget.getValue(target).containsKey(id)) continue
                WorkoutUploader.uploadNew(context, gbDevice, provider, target, summary, sourceHash)
                    .failureReason()?.let { failures[target] = it }
            }
        }

        val rowIds = rowsByTarget.values.flatMap { it.keys }.toSet()
        if (rowIds.isNotEmpty()) {
            val summariesById = summariesByIds(rowIds)
            var rebuildBudget = MAX_PAYLOAD_REBUILDS_PER_RUN
            for (id in rowIds) {
                val summary = summariesById[id]
                if (summary == null) {
                    // The workout is gone, so its upload rows have nothing left to point at. The
                    // remote activity is deliberately left alone.
                    targets.forEach { WorkoutUploadStore.delete(id, it.service) }
                    continue
                }
                if (summary.deviceId != deviceId) {
                    // Another device owns this workout and re-syncs it on its own runs, where the
                    // track provider and the export match that device.
                    continue
                }
                val sourceHash = WorkoutUploadStore.sourceHashOf(summary)
                for (target in targets) {
                    val row = rowsByTarget.getValue(target)[id] ?: continue
                    if (sourceHash == row.sourceHash) continue
                    if (rebuildBudget <= 0) {
                        LOG.info("Payload rebuild budget spent, deferring the rest of the re-sync")
                        return finish(context, failures, Result.retry())
                    }
                    rebuildBudget--
                    WorkoutUploader.resyncWorkout(
                        context, gbDevice, provider, target, summary, row, sourceHash
                    ).failureReason()?.let { failures[target] = it }
                }
            }
        }

        return finish(context, failures, Result.success())
    }

    /**
     * The reason to notify the user about, or null when there is nothing to tell them: a sync that
     * went through, one the workout was not eligible for, and a transient failure the next run
     * retries on its own.
     */
    private fun WorkoutUploader.SyncOutcome.failureReason(): String? = when (this) {
        is WorkoutUploader.SyncOutcome.Failed -> reason
        is WorkoutUploader.SyncOutcome.Resynced -> refusal
        else -> null
    }

    /** Posts a notification per failed service and returns [result]. */
    private fun finish(
        context: Context,
        failures: Map<WorkoutUploadTarget, String>,
        result: Result
    ): Result {
        for ((target, reason) in failures) {
            notifyFailure(
                context,
                target.failureNotificationId,
                target.failureMessage(context, reason)
            )
        }
        return result
    }

    private fun queryRecentSummaries(gbDevice: GBDevice): List<BaseActivitySummary> {
        return try {
            GBApplication.acquireDbReadOnly().use { db ->
                val device = DBHelper.findDevice(gbDevice, db.daoSession) ?: return emptyList()
                val since = Date(System.currentTimeMillis() - RECENT_WINDOW_MS)
                db.daoSession.baseActivitySummaryDao.queryBuilder()
                    .where(
                        BaseActivitySummaryDao.Properties.DeviceId.eq(device.id),
                        BaseActivitySummaryDao.Properties.StartTime.ge(since)
                    )
                    .orderAsc(BaseActivitySummaryDao.Properties.StartTime)
                    .build()
                    .list()
            }
        } catch (e: Exception) {
            LOG.error("Error querying summaries for auto-upload", e)
            emptyList()
        }
    }

    /** Row id of [gbDevice] in the device table, or null when it is not known to the database. */
    private fun deviceEntityId(gbDevice: GBDevice): Long? {
        return try {
            GBApplication.acquireDbReadOnly().use { db ->
                DBHelper.findDevice(gbDevice, db.daoSession)?.id
            }
        } catch (e: Exception) {
            LOG.error("Error resolving device {}", gbDevice.address, e)
            null
        }
    }

    /** Summaries for [ids], keyed by id. Missing ids are workouts that have been deleted. */
    private fun summariesByIds(ids: Collection<Long>): Map<Long, BaseActivitySummary> {
        return try {
            GBApplication.acquireDbReadOnly().use { db ->
                ids.chunked(SUMMARY_QUERY_CHUNK).flatMap { chunk ->
                    db.daoSession.baseActivitySummaryDao.queryBuilder()
                        .where(BaseActivitySummaryDao.Properties.Id.`in`(chunk))
                        .list()
                }.mapNotNull { summary -> summary.id?.let { it to summary } }.toMap()
            }
        } catch (e: Exception) {
            LOG.error("Error loading summaries for re-sync", e)
            emptyMap()
        }
    }

    private fun notifyFailure(context: Context, notificationId: Int, text: String) {
        val notification = NotificationCompat.Builder(context, GB.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.auto_upload_failed_notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .build()
        GB.notify(notificationId, notification, context)
    }

    companion object {
        const val INPUT_DEVICE_ADDRESS = "device_address"
        private const val WORK_NAME_PREFIX = "WorkoutUploadWorker_"
        private const val RECENT_WINDOW_MS = 14L * 24 * 60 * 60 * 1000

        /**
         * Payload rebuilds allowed in one run. An app update invalidates every fingerprint at
         * once, so without a cap a single run could re-export the whole upload history; the
         * remainder is picked up by the retry.
         */
        private const val MAX_PAYLOAD_REBUILDS_PER_RUN = 50

        /** Summary ids per IN clause, to stay well under SQLite's variable limit. */
        private const val SUMMARY_QUERY_CHUNK = 250

        /**
         * Enqueues an upload run for [deviceAddress] (unique per device, REPLACE). Used to re-sync
         * a workout edit made outside a device fetch, e.g. a header photo added on the detail
         * screen, which otherwise would not be picked up until the next data sync. Shares the
         * per-device unique work name with the fetch-triggered path, so the two coalesce.
         */
        @JvmStatic
        fun enqueue(context: Context, deviceAddress: String) {
            val request = OneTimeWorkRequest.Builder(WorkoutUploadWorker::class.java)
                .setInputData(Data.Builder().putString(INPUT_DEVICE_ADDRESS, deviceAddress).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_PREFIX + deviceAddress,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
