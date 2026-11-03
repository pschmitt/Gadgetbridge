package nodomain.freeyourgadget.gadgetbridge.model.weather

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs
import java.util.concurrent.CopyOnWriteArrayList
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
    private val weatherSpecs = CopyOnWriteArrayList<WeatherSpec>()

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
            weatherSpecs.clear()
            weatherSpecs.addAll(loadedSpecs)
        }
    }

    fun setWeatherSpec(newSpecs: Collection<WeatherSpec>) {
        weatherSpecs.apply {
            clear()
            addAll(newSpecs)
        }
        cacheManager?.save(weatherSpecs)
    }

    fun getWeatherSpec(): WeatherSpec? = weatherSpecs.firstOrNull()

    fun getWeatherSpecs(): List<WeatherSpec> = weatherSpecs
}
