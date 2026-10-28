package nodomain.freeyourgadget.gadgetbridge.vendorimport

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.vendorimport.VendorDataImportActivity
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.GB
import org.slf4j.LoggerFactory

/**
 * Imports the selected data of a vendor export in the background.
 */
class VendorDataImportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uri = inputData.getString(KEY_URI)?.toUri()
        val device = inputData.getString(KEY_DEVICE_ADDRESS)?.let {
            GBApplication.app().deviceManager.getDeviceByAddress(it)
        }
        val importer = device?.deviceCoordinator?.getVendorDataImporter(device)
        val deviceId = inputData.getString(KEY_EXPORT_DEVICE)
        if (uri == null || device == null || importer == null || deviceId == null) {
            LOG.error("Missing input for the vendor data import: {}", inputData)
            return Result.failure()
        }

        val selection = VendorImportSelection(
            deviceId = deviceId,
            categories = inputData.getStringArray(KEY_CATEGORIES).toCategories(),
            appCategories = inputData.getStringArray(KEY_APP_CATEGORIES).toCategories(),
        )
        val total = inputData.getInt(KEY_TOTAL, 0)

        LOG.info("Starting the vendor data import into {}: {}", device, selection)

        val counts = mutableMapOf<VendorDataCategory, IntArray>()
        var done = 0
        var lastUpdate = 0L
        setForeground(createForegroundInfo(device, 0, total))

        try {
            val input = applicationContext.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Failed to open $uri")
            input.use {
                importer.importData(applicationContext, device, it, selection) { category, result ->
                    counts.getOrPut(category) { IntArray(VendorImportItemResult.entries.size) }[result.ordinal]++
                    done++
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastUpdate >= PROGRESS_INTERVAL_MS) {
                        lastUpdate = now
                        setProgressAsync(workDataOf(KEY_DONE to done, KEY_TOTAL to total))
                        setForegroundAsync(createForegroundInfo(device, done, total))
                    }
                }
            }
        } catch (e: CancellationException) {
            LOG.info("Vendor data import cancelled after {} items", done)
            throw e
        } catch (e: Exception) {
            LOG.error("Vendor data import failed", e)
            notifyFinished(device, R.string.vendor_data_import_failed)
            return Result.failure(counts.toData())
        } finally {
            releaseUriPermission(applicationContext, uri)
            device.sendDeviceUpdateIntent(applicationContext)
        }

        LOG.info("Vendor data import finished: {} items", done)
        notifyFinished(device, R.string.vendor_data_import_finished)
        return Result.success(counts.toData())
    }

    private fun createForegroundInfo(device: GBDevice, done: Int, total: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, GB.NOTIFICATION_CHANNEL_ID_TRANSFER)
            .setContentTitle(applicationContext.getString(R.string.vendor_data_import_notification_title))
            .setContentText(device.aliasOrName)
            .setSmallIcon(R.drawable.ic_notification)
            .setProgress(total, done, total == 0)
            .setContentIntent(contentIntent(device))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                0,
                applicationContext.getString(R.string.cancel),
                WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
            )
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                GB.NOTIFICATION_ID_VENDOR_IMPORT,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(GB.NOTIFICATION_ID_VENDOR_IMPORT, notification)
        }
    }

    private fun notifyFinished(device: GBDevice, title: Int) {
        val notification = NotificationCompat.Builder(applicationContext, GB.NOTIFICATION_CHANNEL_ID_TRANSFER)
            .setContentTitle(applicationContext.getString(title))
            .setContentText(device.aliasOrName)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent(device))
            .setAutoCancel(true)
            .build()
        GB.notify(GB.NOTIFICATION_ID_VENDOR_IMPORT_RESULT, notification, applicationContext)
    }

    private fun contentIntent(device: GBDevice): PendingIntent {
        return PendingIntent.getActivity(
            applicationContext,
            0,
            VendorDataImportActivity.createImportIntent(applicationContext, device),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun Array<String>?.toCategories(): Set<VendorDataCategory> {
        return this?.mapNotNull { name -> VendorDataCategory.entries.find { it.name == name } }?.toSet().orEmpty()
    }

    private fun Map<VendorDataCategory, IntArray>.toData(): Data {
        val categories = keys.toList()
        return workDataOf(
            KEY_CATEGORIES to categories.map { it.name }.toTypedArray(),
            KEY_IMPORTED to categories.map { getValue(it)[VendorImportItemResult.IMPORTED.ordinal] }.toIntArray(),
            KEY_SKIPPED to categories.map { getValue(it)[VendorImportItemResult.SKIPPED.ordinal] }.toIntArray(),
            KEY_FAILED to categories.map { getValue(it)[VendorImportItemResult.FAILED.ordinal] }.toIntArray(),
        )
    }

    data class Counts(val imported: Int, val skipped: Int, val failed: Int)

    sealed class ImportState {
        data object None : ImportState()
        data class OtherDevice(val address: String) : ImportState()
        data class Running(val done: Int, val total: Int) : ImportState()
        data class Finished(val state: WorkInfo.State, val counts: Map<VendorDataCategory, Counts>) : ImportState()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(VendorDataImportWorker::class.java)

        private const val UNIQUE_WORK_NAME = "vendor_data_import"
        private const val TAG_DEVICE_PREFIX = "vendor_data_import_device:"
        private const val PROGRESS_INTERVAL_MS = 500L

        private const val KEY_URI = "uri"
        private const val KEY_DEVICE_ADDRESS = "device_address"
        private const val KEY_EXPORT_DEVICE = "export_device"
        private const val KEY_CATEGORIES = "categories"
        private const val KEY_APP_CATEGORIES = "app_categories"
        private const val KEY_TOTAL = "total"
        private const val KEY_DONE = "done"
        private const val KEY_IMPORTED = "imported"
        private const val KEY_SKIPPED = "skipped"
        private const val KEY_FAILED = "failed"

        fun enqueue(context: Context, device: GBDevice, uri: Uri, selection: VendorImportSelection, total: Int) {
            val request = OneTimeWorkRequestBuilder<VendorDataImportWorker>()
                .setInputData(
                    workDataOf(
                        KEY_URI to uri.toString(),
                        KEY_DEVICE_ADDRESS to device.address,
                        KEY_EXPORT_DEVICE to selection.deviceId,
                        KEY_CATEGORIES to selection.categories.map { it.name }.toTypedArray(),
                        KEY_APP_CATEGORIES to selection.appCategories.map { it.name }.toTypedArray(),
                        KEY_TOTAL to total,
                    )
                )
                .addTag(TAG_DEVICE_PREFIX + device.address)
                .build()

            LOG.info("Enqueuing the vendor data import into {}", device)
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }

        fun observe(context: Context, device: GBDevice): Flow<ImportState> {
            return WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME).map { infos ->
                val info = infos.lastOrNull() ?: return@map ImportState.None
                val address =
                    info.tags.firstOrNull { it.startsWith(TAG_DEVICE_PREFIX) }?.removePrefix(TAG_DEVICE_PREFIX)
                if (address != device.address) {
                    return@map if (!info.state.isFinished && address != null) ImportState.OtherDevice(address) else ImportState.None
                }
                if (!info.state.isFinished) {
                    return@map ImportState.Running(
                        info.progress.getInt(KEY_DONE, 0),
                        info.progress.getInt(KEY_TOTAL, 0),
                    )
                }
                ImportState.Finished(info.state, info.outputData.toCounts())
            }
        }

        /**
         * Keeps the read permission for the uri, so that the worker can read it after the activity closes.
         */
        fun takeUriPermission(context: Context, uri: Uri) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                LOG.warn("Failed to take the permission for {}", uri, e)
            }
        }

        private fun releaseUriPermission(context: Context, uri: Uri) {
            try {
                context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                LOG.warn("Failed to release the permission for {}", uri, e)
            }
        }

        private fun Data.toCounts(): Map<VendorDataCategory, Counts> {
            val categories = getStringArray(KEY_CATEGORIES) ?: return emptyMap()
            val imported = getIntArray(KEY_IMPORTED) ?: return emptyMap()
            val skipped = getIntArray(KEY_SKIPPED) ?: return emptyMap()
            val failed = getIntArray(KEY_FAILED) ?: return emptyMap()
            return categories.withIndex().mapNotNull { (i, name) ->
                val category = VendorDataCategory.entries.find { it.name == name } ?: return@mapNotNull null
                category to Counts(imported[i], skipped[i], failed[i])
            }.toMap()
        }
    }
}
