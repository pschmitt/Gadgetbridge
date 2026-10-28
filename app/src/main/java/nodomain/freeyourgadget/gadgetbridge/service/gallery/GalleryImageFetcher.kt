package nodomain.freeyourgadget.gadgetbridge.service.gallery

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible
import okio.Buffer
import okio.source
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.EOFException
import java.io.IOException

class GalleryImageFetcher(
    private val source: GallerySource,
    private val image: GalleryImage,
    private val options: Options,
    private val diskCache: DiskCache?,
    private val dispatcher: CoroutineDispatcher,
) : Fetcher {
    class Factory(
        private val source: GallerySource,
        private val dispatcher: CoroutineDispatcher,
    ) : Fetcher.Factory<GalleryImage> {
        override fun create(data: GalleryImage, options: Options, imageLoader: ImageLoader): Fetcher {
            return GalleryImageFetcher(source, data, options, imageLoader.diskCache, dispatcher)
        }
    }

    /**
     * The memory cache key of a [GalleryImage]: [prefix], then [GalleryImage.cacheKey].
     */
    class CacheKeyer(private val prefix: String) : Keyer<GalleryImage> {
        override fun key(data: GalleryImage, options: Options): String = prefix + data.cacheKey
    }

    override suspend fun fetch(): FetchResult {
        val key = image.cacheKey
        LOG.debug("Fetching {}", key)
        val cache = diskCache
        if (cache != null && options.diskCachePolicy.readEnabled) {
            cache.openSnapshot(key)?.let { snapshot ->
                LOG.debug("Read {} from the disk cache", key)
                return fileResult(cache, snapshot, key, DataSource.DISK)
            }
        }

        if (cache != null && options.diskCachePolicy.writeEnabled) {
            val editor = cache.openEditor(key)
            if (editor != null) {
                val snapshot = try {
                    val size = runInterruptible(dispatcher) {
                        source.open(image.file, image.variant, 0, -1).use { input ->
                            val written = cache.fileSystem.write(editor.data) {
                                writeAll(input.source())
                            }
                            checkLength(written, input.length, key)
                            written
                        }
                    }
                    LOG.debug("Stored {} bytes of {} in the disk cache", size, key)
                    editor.commitAndOpenSnapshot()
                } catch (e: Exception) {
                    try {
                        editor.abort()
                    } catch (abortError: Exception) {
                        LOG.warn("Failed to abort the disk cache edit of {}", key, abortError)
                    }
                    throw e
                }
                if (snapshot != null) {
                    return fileResult(cache, snapshot, key, DataSource.NETWORK)
                }
            }
        }

        val bytes = runInterruptible(dispatcher) {
            source.open(image.file, image.variant, 0, -1).use { input ->
                input.readBytes().also { checkLength(it.size.toLong(), input.length, key) }
            }
        }
        LOG.debug("Received {} bytes of {}", bytes.size, key)
        return SourceFetchResult(
            ImageSource(Buffer().write(bytes), options.fileSystem),
            null,
            DataSource.NETWORK
        )
    }

    @Throws(IOException::class)
    private fun checkLength(received: Long, expected: Long, key: String) {
        if (expected >= 0 && received != expected) {
            throw EOFException("Received $received of $expected bytes of $key")
        }
    }

    private fun fileResult(
        cache: DiskCache,
        snapshot: DiskCache.Snapshot,
        key: String,
        dataSource: DataSource,
    ): FetchResult {
        return SourceFetchResult(
            ImageSource(snapshot.data, cache.fileSystem, key, snapshot),
            null,
            dataSource
        )
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(GalleryImageFetcher::class.java)
    }
}
