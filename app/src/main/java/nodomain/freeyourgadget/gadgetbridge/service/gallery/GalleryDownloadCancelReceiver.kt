package nodomain.freeyourgadget.gadgetbridge.service.gallery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Cancels the [GalleryDownloadQueue] of a device, from the action of its notification.
 */
class GalleryDownloadCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS) ?: return
        LOG.info("Cancel requested for the downloads of {}", address)
        GalleryDownloadQueues.cancel(context, address)
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(GalleryDownloadCancelReceiver::class.java)

        const val EXTRA_DEVICE_ADDRESS = "device_address"
    }
}
