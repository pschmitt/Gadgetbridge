package nodomain.freeyourgadget.gadgetbridge.service.gallery

import android.content.Context
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * The [GalleryDownloadQueue] of each device with a [GallerySource], by device address.
 */
object GalleryDownloadQueues {
    private val LOG: Logger = LoggerFactory.getLogger(GalleryDownloadQueues::class.java)

    private val queues = HashMap<String, GalleryDownloadQueue>()

    /**
     * Returns the queue of the device, or null when [source] is not registered.
     */
    fun get(device: GBDevice, source: GallerySource): GalleryDownloadQueue? {
        synchronized(queues) {
            // GallerySourceRegistry.unregister removes the source before it shuts down the queue
            if (GallerySourceRegistry.get(device.address) !== source) {
                LOG.warn("The gallery source of {} is not registered", device.address)
                return null
            }
            return queues.getOrPut(device.address) {
                LOG.debug("Creating the download queue for {}", device.address)
                GalleryDownloadQueue(GBApplication.getContext(), device.address, device.aliasOrName, source)
            }
        }
    }

    fun cancel(context: Context, address: String) {
        val queue = synchronized(queues) { queues[address] }
        if (queue == null) {
            LOG.warn("No download queue for {}", address)
            GalleryDownloadNotification.cancel(context, address)
            return
        }
        queue.cancel()
    }

    /**
     * Removes the files from the queue of the device, if the device has one.
     */
    fun remove(address: String, ids: Set<String>) {
        synchronized(queues) { queues[address] }?.remove(ids)
    }

    fun shutdown(address: String) {
        val queue = synchronized(queues) { queues.remove(address) } ?: return
        LOG.debug("Shutting down the download queue of {}", address)
        queue.shutdown()
    }
}
