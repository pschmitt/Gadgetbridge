package nodomain.freeyourgadget.gadgetbridge.service.gallery

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.util.GB
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * The notification for the [GalleryDownloadQueue] of one device.
 */
object GalleryDownloadNotification {
    private val LOG: Logger = LoggerFactory.getLogger(GalleryDownloadNotification::class.java)

    /**
     * Shows the download of file [index] of [count], with the progress, in bytes, of all the files.
     * A [length] of 0 or less displays an indeterminate progress.
     */
    fun progress(context: Context, address: String, index: Int, count: Int, name: String, bytes: Long, length: Long) {
        val cancelIntent = Intent(context, GalleryDownloadCancelReceiver::class.java)
            .putExtra(GalleryDownloadCancelReceiver.EXTRA_DEVICE_ADDRESS, address)
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            address.hashCode(),
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, GB.NOTIFICATION_CHANNEL_ID_TRANSFER)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(context.getString(R.string.gallery_download_progress, index, count))
            .setContentText(name)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_cancel, context.getString(R.string.cancel), cancelPendingIntent)
        if (length > 0) {
            builder.setProgress(PROGRESS_MAX, (bytes * PROGRESS_MAX / length).toInt().coerceIn(0, PROGRESS_MAX), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        notify(context, address, builder.build())
    }

    /**
     * Shows the result of the queue. A tap opens [lastUri], when it is not null.
     */
    fun summary(
        context: Context,
        address: String,
        title: String,
        downloaded: Int,
        skipped: Int,
        failed: Int,
        lastUri: Uri?,
    ) {
        val builder = NotificationCompat.Builder(context, GB.NOTIFICATION_CHANNEL_ID_TRANSFER)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.gallery_download_summary, downloaded, skipped, failed))
            .setAutoCancel(true)
        if (lastUri != null) {
            val viewIntent = Intent(Intent.ACTION_VIEW)
                .setData(lastUri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            builder.setContentIntent(
                PendingIntent.getActivity(
                    context,
                    address.hashCode(),
                    viewIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        }
        notify(context, address, builder.build())
    }

    fun cancel(context: Context, address: String) {
        NotificationManagerCompat.from(context).cancel(address, GB.NOTIFICATION_ID_GALLERY_DOWNLOAD)
    }

    private fun notify(context: Context, address: String, notification: Notification) {
        GB.createNotificationChannels(context)
        try {
            NotificationManagerCompat.from(context).notify(address, GB.NOTIFICATION_ID_GALLERY_DOWNLOAD, notification)
        } catch (e: SecurityException) {
            LOG.warn("No permission to show the notification", e)
        }
    }

    private const val PROGRESS_MAX = 1000
}
