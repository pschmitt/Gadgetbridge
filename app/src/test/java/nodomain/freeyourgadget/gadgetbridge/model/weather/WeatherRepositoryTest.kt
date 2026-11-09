package nodomain.freeyourgadget.gadgetbridge.model.weather

import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class WeatherRepositoryTest : TestBase() {
    private val repository: WeatherRepository
        get() = app.weatherRepository

    @Test
    fun startsEmpty() {
        assertNull(repository.getWeatherSpec())
        assertEquals(emptyList<WeatherSpec>(), repository.getWeatherSpecs())
    }

    @Test
    fun updatesReplaceTheWholeSnapshot() {
        val first = WeatherSpec.createTestWeather()
        repository.setWeatherSpec(listOf(first))
        val before = repository.getWeatherSpecs()

        val second = WeatherSpec.createTestWeather()
        repository.setWeatherSpec(listOf(second))

        // A reader holding the old snapshot keeps seeing it whole, it is never cleared in place
        assertEquals(listOf(first), before)
        assertSame(second, repository.getWeatherSpec())
    }

    @Test
    fun aCacheLoadThatFinishesAfterAnUpdateIsDiscarded() {
        val loadStartedAt = repository.currentVersion()
        val fresh = WeatherSpec.createTestWeather()
        repository.setWeatherSpec(listOf(fresh))

        repository.onCacheLoaded(loadStartedAt, listOf(WeatherSpec.createTestWeather()))

        assertEquals(listOf(fresh), repository.getWeatherSpecs())
    }

    @Test
    fun aCacheLoadWithNoUpdateInBetweenIsApplied() {
        val loadStartedAt = repository.currentVersion()
        val cached = WeatherSpec.createTestWeather()

        repository.onCacheLoaded(loadStartedAt, listOf(cached))

        assertEquals(listOf(cached), repository.getWeatherSpecs())
    }

    @Test
    fun snapshotsAreNotAffectedByTheSourceCollectionOrCallers() {
        val source = mutableListOf(WeatherSpec.createTestWeather())
        repository.setWeatherSpec(source)
        source.clear()

        assertEquals(1, repository.getWeatherSpecs().size)
        assertThrows(UnsupportedOperationException::class.java) {
            (repository.getWeatherSpecs() as MutableList<WeatherSpec>).clear()
        }
    }
}
