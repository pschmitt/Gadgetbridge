package nodomain.freeyourgadget.gadgetbridge.service.gallery

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * The [GallerySource] of each connected device, by device address.
 */
object GallerySourceRegistry {
    private val LOG: Logger = LoggerFactory.getLogger(GallerySourceRegistry::class.java)

    private val sources: MutableMap<String, GallerySource> = ConcurrentHashMap()

    @JvmStatic
    fun register(address: String, source: GallerySource) {
        LOG.debug("Registering gallery source for {}", address)
        sources[address] = source
    }

    @JvmStatic
    fun unregister(address: String, source: GallerySource) {
        LOG.debug("Unregistering gallery source for {}", address)
        if (sources.remove(address, source)) {
            try {
                GalleryDownloadQueues.shutdown(address)
            } finally {
                try {
                    GalleryImageLoaders.shutdown(address)
                } finally {
                    GalleryPreviewCaches.release(address)
                }
            }
        }
    }

    @JvmStatic
    fun get(address: String): GallerySource? = sources[address]
}
