package nodomain.freeyourgadget.gadgetbridge.widgets.launcher

import android.content.BroadcastReceiver
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.hilt.android.qualifiers.ApplicationContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Updates the launcher widgets when new data is stored, and when the language, the theme, the night mode or the
 * dashboard devices change.
 */
@Singleton
open class LauncherWidgetUpdateTriggers @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: GBPrefs,
) {
    private var registered = false

    private var nightMode = Configuration.UI_MODE_NIGHT_UNDEFINED

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            LOG.debug("Received {}, updating the launcher widgets", intent.action)
            requestUpdate(NEW_DATA_DELAY_SECONDS)
        }
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in PREF_KEYS) {
            LOG.debug("Preference {} changed, updating the launcher widgets", key)
            requestUpdate(0)
        }
    }

    private val configurationCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            val newNightMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
            if (newNightMode == nightMode) return
            nightMode = newNightMode
            LOG.debug("Night mode changed to {}, updating the launcher widgets", newNightMode)
            requestUpdate(0)
        }

        @Deprecated("Deprecated in Java")
        override fun onLowMemory() {
        }
    }

    /**
     * Starts listening for changes that require a launcher widget update. Does nothing if already registered.
     */
    @MainThread
    fun register() {
        if (registered) return
        registered = true
        val filter = IntentFilter()
        filter.addAction(GBApplication.ACTION_NEW_DATA)
        filter.addAction(GBApplication.ACTION_LANGUAGE_CHANGE)
        LocalBroadcastManager.getInstance(context).registerReceiver(receiver, filter)
        prefs.preferences.registerOnSharedPreferenceChangeListener(prefsListener)
        nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        context.registerComponentCallbacks(configurationCallbacks)
    }

    /**
     * Stops listening for changes. Does nothing if not registered.
     */
    @MainThread
    fun unregister() {
        if (!registered) return
        registered = false
        LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
        prefs.preferences.unregisterOnSharedPreferenceChangeListener(prefsListener)
        context.unregisterComponentCallbacks(configurationCallbacks)
    }

    @VisibleForTesting
    protected open fun requestUpdate(delaySeconds: Long) {
        LauncherWidgets.updateAll(context, delaySeconds)
    }

    private companion object {
        private val LOG: Logger = LoggerFactory.getLogger(LauncherWidgetUpdateTriggers::class.java)

        private const val NEW_DATA_DELAY_SECONDS = 3L

        private val PREF_KEYS = setOf(
            "dashboard_devices_all",
            "dashboard_devices_multiselect",
            "pref_key_theme",
            "pref_key_theme_amoled_black",
            GBApplication.PREF_ACCENT_COLOR,
            GBApplication.PREF_CONTRASTING_SURFACES,
        )
    }
}
