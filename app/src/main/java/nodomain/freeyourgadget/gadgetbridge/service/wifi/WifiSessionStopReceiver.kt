package nodomain.freeyourgadget.gadgetbridge.service.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Closes the [WifiSession] of a device, from the action of its notification.
 */
class WifiSessionStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val address = intent.getStringExtra(EXTRA_DEVICE_ADDRESS) ?: return
        val session = WifiSessionRegistry.get(address)
        if (session == null) {
            LOG.warn("No session for {}", address)
            WifiSessionNotification.cancel(context, address)
            return
        }
        LOG.info("Closing session for {}", address)
        session.close()
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(WifiSessionStopReceiver::class.java)

        const val EXTRA_DEVICE_ADDRESS = "device_address"
    }
}
