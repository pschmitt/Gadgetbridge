package nodomain.freeyourgadget.gadgetbridge

import android.content.Intent
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.RuntimeEnvironment

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
