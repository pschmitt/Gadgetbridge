package nodomain.freeyourgadget.gadgetbridge.model.weather

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec

/**
 * Static access to the [WeatherRepository] of the current application, kept so that existing
 * callers keep working.
 */
object Weather {
    private fun repository(): WeatherRepository = GBApplication.app().weatherRepository

    @Deprecated("Use Hilt DI to inject WeatherRepository instead.")
    @JvmStatic
    fun setWeatherSpec(newSpecs: Collection<WeatherSpec>) {
        repository().setWeatherSpec(newSpecs)
    }

    @Deprecated("Use Hilt DI to inject WeatherRepository instead.")
    @JvmStatic
    fun getWeatherSpec(): WeatherSpec? = repository().getWeatherSpec()

    @Deprecated("Use Hilt DI to inject WeatherRepository instead.")
    @JvmStatic
    fun getWeatherSpecs(): List<WeatherSpec> = repository().getWeatherSpecs()

    @Deprecated("Use Hilt DI to inject WeatherRepository instead.")
    @JvmStatic
    fun initializeCache(cacheManager: WeatherCacheManager) {
        repository().initializeCache(cacheManager)
    }
}
