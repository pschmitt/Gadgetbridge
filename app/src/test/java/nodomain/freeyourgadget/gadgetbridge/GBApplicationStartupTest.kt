package nodomain.freeyourgadget.gadgetbridge

import android.app.NotificationManager
import android.content.Intent
import androidx.lifecycle.LifecycleRegistry
import androidx.core.content.edit
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType
import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec
import nodomain.freeyourgadget.gadgetbridge.model.weather.Weather
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Robolectric creates a new application for every test, while process-wide state survives across tests. These
 * tests check that every application is fully initialized, and that registrations from earlier tests' applications
 * were undone. Each test also runs after other tests, so the checks cover leftovers from earlier applications.
 */
class GBApplicationStartupTest : TestBase() {
    private fun processLifecycleObserverCount(): Int =
        (ProcessLifecycleOwner.get().lifecycle as LifecycleRegistry).observerCount

    @Test
    fun appIsTheCurrentApplication() {
        assertSame(RuntimeEnvironment.getApplication(), GBApplication.app())
        assertEquals(1, processLifecycleObserverCount())
    }

    @Test
    fun deviceManagerIsTheOneInjectedIntoTheCurrentApplication() {
        assertSame(app.deviceManagerInstance, GBApplication.app().deviceManagerInstance)
        assertEquals(1, processLifecycleObserverCount())
    }

    @Test
    fun prefsAreTheOnesInjectedIntoTheCurrentApplication() {
        assertSame(app.gbPrefs, GBApplication.getPrefs())
        assertSame(app.defaultSharedPreferences, GBApplication.getPrefs().preferences)
    }

    @Test
    fun scopedPrefsGoThroughTheProviderInjectedIntoTheCurrentApplication() {
        assertSame(
            app.scopedPrefs.getDeviceSpecificSharedPrefs(MAC),
            GBApplication.getDeviceSpecificSharedPrefs(MAC.lowercase()),
        )
        assertSame(app.scopedPrefs.getWidgetSharedPrefs("42"), GBApplication.getWidgetSharedPrefs("42"))
        assertNull(GBApplication.getWidgetSharedPrefs(""))

        GBApplication.getDeviceSpecificSharedPrefs(MAC).edit(commit = true) { putBoolean("key", true) }
        GBApplication.deleteDeviceSpecificSharedPrefs(MAC)
        assertFalse(app.scopedPrefs.getDeviceSpecificSharedPrefs(MAC).contains("key"))
    }

    @Test
    fun doNotDisturbChecksGoThroughThePolicyInjectedIntoTheCurrentApplication() {
        val notificationManager = app.getSystemService(NotificationManager::class.java)
        // Without notification policy access, everything is allowed through
        assertEquals(NotificationManager.INTERRUPTION_FILTER_ALL, GBApplication.getGrantedInterruptionFilter())

        shadowOf(notificationManager).setNotificationPolicyAccessGranted(true)
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        assertEquals(
            app.doNotDisturbPolicy.getGrantedInterruptionFilter(),
            GBApplication.getGrantedInterruptionFilter(),
        )
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, GBApplication.getGrantedInterruptionFilter())
    }

    @Test
    fun deviceServiceIsTheOneInjectedIntoTheCurrentApplication() {
        assertSame(GBApplication.deviceService(), app.deviceServiceInstance)

        val device = createDummyGDevice(MAC)
        val forDevice = GBApplication.deviceService(device)
        assertSame(device, forDevice.device)
    }

    @Test
    fun databaseIsTheOneManagedByTheCurrentApplication() {
        // TestBase.setUp() opened the database through the static accessors
        app.databaseManager.acquireReadOnly().use { assertSame(daoSession, it.daoSession) }
        GBApplication.acquireDbReadOnly().use { assertSame(daoSession, it.daoSession) }
        GBDatabaseManager.acquireReadOnly().use { assertSame(daoSession, it.daoSession) }
    }

    @Test
    fun databaseManagerReopensTheDatabaseWithItsOwnContext() {
        // Closes the database and sets it up again, without being given a context
        app.databaseManager.deleteActivityDatabase()

        app.databaseManager.acquireReadOnly().use {
            assertNotNull(it.daoSession)
            assertNotSame(daoSession, it.daoSession)
        }
    }

    @Test
    fun acquiringAClosedDatabaseThrowsAndReleasesTheLock() {
        val manager = app.databaseManager
        manager.closeDatabase()

        assertThrows(GBException::class.java) { manager.acquireWrite() }
        assertThrows(GBException::class.java) { manager.acquireReadOnly() }

        // Neither failed acquire kept its lock, so the database can be set up and used again
        manager.setupDatabase()
        manager.acquireWrite().use { assertNotNull(it.daoSession) }
        manager.acquireReadOnly().use { assertNotNull(it.daoSession) }
    }

    @Test
    fun weatherIsTheOneHeldByTheCurrentApplication() {
        val spec = WeatherSpec.createTestWeather()
        Weather.setWeatherSpec(listOf(spec))

        assertSame(spec, app.weatherRepository.getWeatherSpec())
        assertSame(spec, Weather.getWeatherSpec())
        assertEquals(listOf(spec), Weather.getWeatherSpecs())
    }

    @Test
    fun onTerminateLeavesNoPreferenceListenersBehind() {
        val prefs = app.defaultSharedPreferences
        // The theme listener and the launcher widget triggers
        assertEquals(2, prefsListenerCount(prefs))

        app.onTerminate()
        assertEquals(0, prefsListenerCount(prefs))
        // Changing the theme after termination must not crash or reach the old application
        prefs.edit(commit = true) { putString("pref_key_theme", "dark") }
    }

    @Test
    fun onCreateAgainAndOnTerminateRestoreTheExceptionHandler() {
        assertEquals(1, processLifecycleObserverCount())
        assertTrue(Thread.getDefaultUncaughtExceptionHandler() is GBExceptionHandler)

        app.onCreate()
        assertEquals(1, processLifecycleObserverCount())
        assertTrue(Thread.getDefaultUncaughtExceptionHandler() is GBExceptionHandler)

        app.onTerminate()
        assertEquals(0, processLifecycleObserverCount())
        // Neither the second onCreate() nor an earlier test's application left a GB handler behind
        assertFalse(Thread.getDefaultUncaughtExceptionHandler() is GBExceptionHandler)
    }

    @Test
    fun onTerminateStopsTheDeviceManager() {
        val device = createDummyGDevice(MAC)
        registerDeviceWithManager(device)

        sendDeviceChanged("Renamed")
        assertEquals("Renamed", device.name)

        app.onTerminate()
        sendDeviceChanged("Renamed again")
        assertEquals("Renamed", device.name)
    }

    private fun prefsListenerCount(prefs: android.content.SharedPreferences): Int {
        val field = prefs.javaClass.getDeclaredField("mListeners")
        field.isAccessible = true
        return (field.get(prefs) as Map<*, *>).size
    }

    private fun sendDeviceChanged(name: String) {
        val changed = GBDevice(MAC, name, null, null, DeviceType.TEST)
        LocalBroadcastManager.getInstance(app).sendBroadcastSync(
            Intent(GBDevice.ACTION_DEVICE_CHANGED).putExtra(GBDevice.EXTRA_DEVICE, changed)
        )
    }

    private companion object {
        const val MAC = "AA:BB:CC:DD:EE:FF"
    }
}
