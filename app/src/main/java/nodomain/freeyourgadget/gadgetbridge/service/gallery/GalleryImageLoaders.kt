package nodomain.freeyourgadget.gadgetbridge.service.gallery

import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import kotlinx.coroutines.Dispatchers
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import okio.Path.Companion.toOkioPath
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

/**
 * The [ImageLoader] of each device with a [GallerySource], by device address.
 * All loaders share one memory cache. Each device has its disk cache in its own cache directory.
 */
object GalleryImageLoaders {
    private val LOG: Logger = LoggerFactory.getLogger(GalleryImageLoaders::class.java)

    private const val THUMBNAIL_DIR = "thumbnails"
    private const val DISK_CACHE_SIZE = 300L * 1024 * 1024
    private const val MAX_PARALLEL_FETCHES = 3

    private val loaders = HashMap<String, ImageLoader>()

    private val memoryCache: MemoryCache by lazy {
        MemoryCache.Builder().maxSizePercent(GBApplication.getContext()).build()
    }

    /**
     * Returns the loader of the device. When [source] is not registered, the loader is not kept and has no
     * disk cache.
     */
    fun get(device: GBDevice, source: GallerySource): ImageLoader {
        synchronized(loaders) {
            // GallerySourceRegistry.unregister removes the source before it shuts down the loader
            if (GallerySourceRegistry.get(device.address) !== source) {
                LOG.warn("The gallery source of {} is not registered", device.address)
                return build(device, source, null)
            }
            return loaders.getOrPut(device.address) {
                build(device, source, File(device.deviceCoordinator.getDeviceCacheDir(device), THUMBNAIL_DIR))
            }
        }
    }

    private fun build(device: GBDevice, source: GallerySource, directory: File?): ImageLoader {
        LOG.debug("Creating image loader for {} in {}", device.address, directory)
        return ImageLoader.Builder(GBApplication.getContext())
            .components {
                add(GalleryImageFetcher.CacheKeyer(memoryKeyPrefix(device.address)))
                add(
                    GalleryImageFetcher.Factory(
                        source,
                        Dispatchers.IO.limitedParallelism(MAX_PARALLEL_FETCHES)
                    )
                )
            }
            .memoryCache(memoryCache)
            .diskCache {
                directory?.let {
                    DiskCache.Builder()
                        .directory(it.toOkioPath())
                        .maxSizeBytes(DISK_CACHE_SIZE)
                        .build()
                }
            }
            .build()
    }

    /**
     * Clears the in-memory and disk caches for the device.
     * Blocks while it deletes the files.
     */
    fun clearCache(address: String) {
        val prefix = memoryKeyPrefix(address)
        val keys = memoryCache.keys.filter { it.key.startsWith(prefix) }
        LOG.debug("Removing {} images of {} from the memory cache", keys.size, address)
        keys.forEach { memoryCache.remove(it) }
        synchronized(loaders) { loaders[address] }?.diskCache?.clear()
    }

    /**
     * Removes the images of the files from the memory cache and from the disk cache of the device.
     * Blocks while it deletes the files.
     */
    fun remove(address: String, files: List<MediaFile>) {
        val diskKeys = files.flatMap { file -> Variant.entries.map { GalleryImage(file, it).cacheKey } }
        val memoryKeys = diskKeys.mapTo(HashSet()) { memoryKeyPrefix(address) + it }
        val keys = memoryCache.keys.filter { it.key in memoryKeys }
        LOG.debug("Removing {} images of {} files of {} from the caches", keys.size, files.size, address)
        keys.forEach { memoryCache.remove(it) }
        val diskCache = synchronized(loaders) { loaders[address] }?.diskCache ?: return
        diskKeys.forEach { diskCache.remove(it) }
    }

    /**
     * Stops the image loader for the device and closes its disk cache.
     */
    fun shutdown(address: String) {
        val loader = synchronized(loaders) { loaders.remove(address) } ?: return
        LOG.debug("Shutting down image loader for {}", address)
        loader.shutdown()
        loader.diskCache?.shutdown()
    }

    private fun memoryKeyPrefix(address: String) = "$address/"
}
