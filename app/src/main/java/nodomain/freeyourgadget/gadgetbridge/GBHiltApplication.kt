package nodomain.freeyourgadget.gadgetbridge

import dagger.hilt.android.HiltAndroidApp
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceManager
import javax.inject.Inject

/**
 * The application entry point for Hilt.
 *
 * [GBApplication] remains in Java, since it is a large class with a lot of legacy initialization
 * logic. This subclass exists so that Hilt can be enabled from Kotlin, and so that the
 * [DeviceManager] singleton can be injected before [GBApplication.onCreate] runs.
 *
 * [DeviceManager] is provided by [nodomain.freeyourgadget.gadgetbridge.di.AppModule] as a
 * singleton. Its constructor does not touch any state that is only available after
 * [GBApplication.onCreate], so it can be safely constructed while the Hilt component is created.
 * [DeviceManager.initialize] is called from [GBApplication.onCreate] once prefs and the database
 * are ready.
 */
@HiltAndroidApp
open class GBHiltApplication : GBApplication() {
    @Inject
    lateinit var deviceManagerInstance: DeviceManager

    @Deprecated("Deprecated in Java")
    override fun getDeviceManager(): DeviceManager = deviceManagerInstance
}
