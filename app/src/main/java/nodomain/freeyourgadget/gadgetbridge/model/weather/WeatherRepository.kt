package nodomain.freeyourgadget.gadgetbridge.model.weather

import android.content.Context
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs
import org.slf4j.LoggerFactory
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the latest weather received from the weather provider app, and persists it to the cache
 * when the "cache_weather" preference is enabled.
 */
@Singleton
class WeatherRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: GBPrefs,
) {
    /**
     * An unmodifiable snapshot, replaced as a whole on every update, so that readers on other
     * threads never see a partially updated list.
     */
    @Volatile
    private var weatherSpecs: List<WeatherSpec> = emptyList()

    @Volatile
    private var cacheManager: WeatherCacheManager? = null

    /** Guards [version] and every write to [weatherSpecs]. */
    private val lock = Any()

    /** Incremented on every [setWeatherSpec], so a cache load can tell if newer weather arrived. */
    private var version = 0

    /**
     * Sets up the cache according to the "cache_weather" preference, and loads the cached weather.
     */
    fun initialize() {
        initializeCache(WeatherCacheManager(context.cacheDir, prefs.getBoolean("cache_weather", true)))
    }

    /**
     * Uses [cacheManager] from now on, and loads the cached weather in the background. The cached
     * weather is discarded if newer weather was set while it was loading.
     */
    fun initializeCache(cacheManager: WeatherCacheManager) {
        this.cacheManager = cacheManager

        val startVersion = currentVersion()
        cacheManager.load { loadedSpecs ->
            onCacheLoaded(startVersion, loadedSpecs)
        }
    }

    fun setWeatherSpec(newSpecs: Collection<WeatherSpec>) {
        val specs = snapshot(newSpecs)
        synchronized(lock) {
            weatherSpecs = specs
            version++
        }
        cacheManager?.save(specs)
    }

    @VisibleForTesting
    internal fun currentVersion(): Int = synchronized(lock) { version }

    /**
     * Applies weather loaded from the cache, unless [setWeatherSpec] was called since the load
     * started at [startVersion].
     */
    @VisibleForTesting
    internal fun onCacheLoaded(startVersion: Int, loadedSpecs: List<WeatherSpec>) {
        synchronized(lock) {
            if (version != startVersion) {
                LOG.debug("Discarding {} cached weather specs, newer weather was set while loading", loadedSpecs.size)
                return
            }
            weatherSpecs = snapshot(loadedSpecs)
        }
    }

    fun getWeatherSpec(): WeatherSpec? = weatherSpecs.firstOrNull()

    fun getWeatherSpecs(): List<WeatherSpec> = weatherSpecs

    private fun snapshot(specs: Collection<WeatherSpec>): List<WeatherSpec> =
        Collections.unmodifiableList(ArrayList(specs))

    private companion object {
        private val LOG = LoggerFactory.getLogger(WeatherRepository::class.java)
    }
}
