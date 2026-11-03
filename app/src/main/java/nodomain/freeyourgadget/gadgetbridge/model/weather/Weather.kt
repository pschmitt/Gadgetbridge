package nodomain.freeyourgadget.gadgetbridge.model.weather

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec

/**
 * Static access to the [WeatherRepository] of the current application, kept so that existing
 * callers keep working.
 *
 * @deprecated Use Hilt DI to inject [WeatherRepository] instead.
 */
object Weather {
    private fun repository(): WeatherRepository = GBApplication.app().weatherRepository

    @JvmStatic
    fun setWeatherSpec(newSpecs: Collection<WeatherSpec>) {
        repository().setWeatherSpec(newSpecs)
    }

    @JvmStatic
    fun getWeatherSpec(): WeatherSpec? = repository().getWeatherSpec()

    @JvmStatic
    fun getWeatherSpecs(): List<WeatherSpec> = repository().getWeatherSpecs()

    @JvmStatic
    fun initializeCache(cacheManager: WeatherCacheManager) {
        repository().initializeCache(cacheManager)
    }
}
