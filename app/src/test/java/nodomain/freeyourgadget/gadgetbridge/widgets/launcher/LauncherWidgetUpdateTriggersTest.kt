package nodomain.freeyourgadget.gadgetbridge.widgets.launcher

import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherWidgetUpdateTriggersTest : TestBase() {
    private class CountingTriggers(context: Context) : LauncherWidgetUpdateTriggers(context) {
        var count = 0
        override fun requestUpdate(delaySeconds: Long) {
            count++
        }
    }

    private fun sendNewData() {
        LocalBroadcastManager.getInstance(app).sendBroadcastSync(Intent(GBApplication.ACTION_NEW_DATA))
    }

    private fun toggleDashboardDevicesAll() {
        val prefs = GBApplication.getPrefs().preferences
        prefs.edit(commit = true) {
            putBoolean("dashboard_devices_all", !prefs.getBoolean("dashboard_devices_all", true))
        }
    }

    @Test
    fun registerTwiceTriggersOnce() {
        val triggers = CountingTriggers(app)
        triggers.register()
        triggers.register()

        sendNewData()
        assertEquals(1, triggers.count)

        toggleDashboardDevicesAll()
        assertEquals(2, triggers.count)

        triggers.unregister()
    }

    @Test
    fun unregisterStopsTriggering() {
        val triggers = CountingTriggers(app)
        triggers.register()
        triggers.unregister()

        sendNewData()
        toggleDashboardDevicesAll()
        assertEquals(0, triggers.count)
    }

    @Test
    fun unregisterWithoutRegisterDoesNothing() {
        CountingTriggers(app).unregister()
    }
}
