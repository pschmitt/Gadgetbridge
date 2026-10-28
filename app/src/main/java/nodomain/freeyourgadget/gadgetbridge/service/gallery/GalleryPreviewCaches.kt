package nodomain.freeyourgadget.gadgetbridge.service.gallery

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

/**
 * The video preview cache of each device with a [GallerySource], by device address.
 * Each device has its cache in its own cache directory.
 */
@OptIn(UnstableApi::class)
object GalleryPreviewCaches {
    private val LOG: Logger = LoggerFactory.getLogger(GalleryPreviewCaches::class.java)

    private const val PREVIEW_DIR = "previews"
    private const val CACHE_SIZE = 500L * 1024 * 1024

    // A SimpleCache locks its directory. Therefore, all access to an instance and its directory is under this lock.
    private val caches = HashMap<String, SimpleCache>()

    private val databaseProvider: StandaloneDatabaseProvider by lazy {
        StandaloneDatabaseProvider(GBApplication.getContext())
    }

    /**
     * Returns a [DataSource.Factory] that reads the Preview of [file] from [source] through the
     * cache of the device. When [source] is not registered, the factory does not use the cache.
     */
    fun dataSourceFactory(device: GBDevice, source: GallerySource, file: MediaFile): DataSource.Factory {
        val upstream = GalleryDataSource.Factory(source, file)
        val cache = get(device, source) ?: return upstream
        val key = GalleryImage(file, Variant.PREVIEW).cacheKey
        return CacheDataSource.Factory()
            .setCache(cache)
            .setCacheKeyFactory { key }
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .setEventListener(object : CacheDataSource.EventListener {
                override fun onCachedBytesRead(cacheSizeBytes: Long, cachedBytesRead: Long) {
                    LOG.debug("Read {} bytes of {} from the preview cache", cachedBytesRead, key)
                }

                override fun onCacheIgnored(reason: Int) {
                    LOG.warn("Preview cache ignored for {}, reason {}", key, reason)
                }
            })
    }

    /**
     * Removes all the previews of the device from its cache. Blocks while it deletes the files.
     */
    fun clearCache(device: GBDevice) {
        synchronized(caches) {
            val cache = caches[device.address]
            if (cache != null) {
                // A player can use the cache at the same time. A reader keeps its open file. An in-progress write
                // locks its range, so that the cache keeps the entry of its key until the commit.
                val keys = cache.keys
                LOG.debug("Removing {} previews of {} from the preview cache", keys.size, device.address)
                keys.forEach { cache.removeResource(it) }
            } else {
                val directory = directory(device)
                LOG.debug("Deleting the preview cache of {} in {}", device.address, directory)
                SimpleCache.delete(directory, databaseProvider)
            }
        }
    }

    /**
     * Removes the previews of the files from the cache of the device. Blocks while it deletes the files.
     */
    fun remove(device: GBDevice, source: GallerySource, files: List<MediaFile>) {
        synchronized(caches) {
            val cache = get(device, source) ?: return
            LOG.debug("Removing the previews of {} files of {} from the preview cache", files.size, device.address)
            files.forEach { cache.removeResource(GalleryImage(it, Variant.PREVIEW).cacheKey) }
        }
    }

    /**
     * Releases the cache of the device.
     */
    fun release(address: String) {
        synchronized(caches) {
            val cache = caches.remove(address) ?: return
            LOG.debug("Releasing the preview cache of {}", address)
            cache.release()
        }
    }

    private fun get(device: GBDevice, source: GallerySource): Cache? {
        synchronized(caches) {
            // GallerySourceRegistry.unregister removes the source before it releases the cache
            if (GallerySourceRegistry.get(device.address) !== source) {
                LOG.warn("The gallery source of {} is not registered", device.address)
                return null
            }
            return caches.getOrPut(device.address) {
                val directory = directory(device)
                LOG.debug("Creating the preview cache for {} in {}", device.address, directory)
                SimpleCache(directory, LeastRecentlyUsedCacheEvictor(CACHE_SIZE), databaseProvider)
            }
        }
    }

    private fun directory(device: GBDevice) = File(device.deviceCoordinator.getDeviceCacheDir(device), PREVIEW_DIR)
}
