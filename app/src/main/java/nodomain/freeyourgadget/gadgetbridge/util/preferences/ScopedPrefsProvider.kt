package nodomain.freeyourgadget.gadgetbridge.util.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides the [SharedPreferences] files that are scoped to a single device or to a single dashboard widget
 * instance, as opposed to the app's default preferences.
 */
@Singleton
class ScopedPrefsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun getDeviceSpecificSharedPrefs(deviceIdentifier: CharSequence?): SharedPreferences {
        require(!deviceIdentifier.isNullOrEmpty()) { "Invalid device identifier" }
        return context.getSharedPreferences(deviceFileName(deviceIdentifier), Context.MODE_PRIVATE)
    }

    fun getDevicePrefs(gbDevice: GBDevice): DevicePrefs {
        return DevicePrefs(getDeviceSpecificSharedPrefs(gbDevice.address), gbDevice)
    }

    fun deleteDeviceSpecificSharedPrefs(deviceIdentifier: CharSequence?) {
        if (deviceIdentifier.isNullOrEmpty()) {
            return
        }
        context.getSharedPreferences(deviceFileName(deviceIdentifier), Context.MODE_PRIVATE).edit { clear() }
    }

    /**
     * The per-instance settings file for a dashboard widget instance, keyed by its instance id.
     */
    fun getWidgetSharedPrefs(instanceId: CharSequence?): SharedPreferences? {
        if (instanceId.isNullOrEmpty()) {
            return null
        }
        return context.getSharedPreferences(widgetFileName(instanceId), Context.MODE_PRIVATE)
    }

    fun deleteWidgetSharedPrefs(instanceId: CharSequence?) {
        if (instanceId.isNullOrEmpty()) {
            return
        }
        context.getSharedPreferences(widgetFileName(instanceId), Context.MODE_PRIVATE).edit { clear() }
    }

    private fun deviceFileName(deviceIdentifier: CharSequence): String =
        "devicesettings_" + deviceIdentifier.toString().uppercase(Locale.ROOT)

    private fun widgetFileName(instanceId: CharSequence): String = "widgetsettings_$instanceId"
}
