package nodomain.freeyourgadget.gadgetbridge.service.wifi

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.GB
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * The ongoing notification of a live [WifiSession].
 */
object WifiSessionNotification {
    private val LOG: Logger = LoggerFactory.getLogger(WifiSessionNotification::class.java)

    fun update(context: Context, device: GBDevice, @StringRes stateText: Int, connected: Boolean) {
        val stopIntent = Intent(context, WifiSessionStopReceiver::class.java)
            .putExtra(WifiSessionStopReceiver.EXTRA_DEVICE_ADDRESS, device.address)
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            device.address.hashCode(),
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, GB.NOTIFICATION_CHANNEL_ID_TRANSFER)
            .setSmallIcon(R.drawable.ic_wifi_tethering)
            .setContentTitle(context.getString(R.string.wifi_ftp_notification_title, device.aliasOrName))
            .setContentText(context.getString(stateText))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, !connected)
            .addAction(
                R.drawable.ic_notification_disconnected,
                context.getString(R.string.controlcenter_disconnect),
                stopPendingIntent
            )
            .build()

        GB.createNotificationChannels(context)
        try {
            NotificationManagerCompat.from(context).notify(device.address, GB.NOTIFICATION_ID_WIFI_SESSION, notification)
        } catch (e: SecurityException) {
            LOG.warn("No permission to show the notification", e)
        }
    }

    fun cancel(context: Context, address: String) {
        NotificationManagerCompat.from(context).cancel(address, GB.NOTIFICATION_ID_WIFI_SESSION)
    }
}
