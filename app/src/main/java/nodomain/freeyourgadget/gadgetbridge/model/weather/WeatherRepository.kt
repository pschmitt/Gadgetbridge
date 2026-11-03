package nodomain.freeyourgadget.gadgetbridge.model.weather

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs
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

    /**
     * Sets up the cache according to the "cache_weather" preference, and loads the cached weather.
     */
    fun initialize() {
        initializeCache(WeatherCacheManager(context.cacheDir, prefs.getBoolean("cache_weather", true)))
    }

    fun initializeCache(cacheManager: WeatherCacheManager) {
        this.cacheManager = cacheManager

        cacheManager.load { loadedSpecs ->
            weatherSpecs = snapshot(loadedSpecs)
        }
    }

    fun setWeatherSpec(newSpecs: Collection<WeatherSpec>) {
        val specs = snapshot(newSpecs)
        weatherSpecs = specs
        cacheManager?.save(specs)
    }

    fun getWeatherSpec(): WeatherSpec? = weatherSpecs.firstOrNull()

    fun getWeatherSpecs(): List<WeatherSpec> = weatherSpecs

    private fun snapshot(specs: Collection<WeatherSpec>): List<WeatherSpec> =
        Collections.unmodifiableList(ArrayList(specs))
}
