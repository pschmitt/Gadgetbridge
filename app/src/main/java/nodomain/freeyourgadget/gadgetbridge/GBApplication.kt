/*  Copyright (C) 2015-2025 Andreas Shimokawa, Arjan Schrijver, Carsten
    Pfeiffer, Damien Gaignon, Daniel Dakhno, Daniele Gobbetti, Davis Mosenkovs,
    Dmitriy Bogdanov, Joel Beckmeyer, José Rebelo, Kornél Schmidt, Ludovic
    Jozeau, Martin, Martin.JM, mvn23, Normano64, odavo32nof, Pauli Salmenrinne,
    Pavel Elagin, Petr Vaněk, Saul Nunez, Taavi Eomäe, x29a, Thomas Kuehne

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.app.UiModeManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.util.TypedValue
import android.view.WindowManager
import androidx.annotation.StyleRes
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.hilt.android.HiltAndroidApp
import nodomain.freeyourgadget.gadgetbridge.activities.ControlCenterv2
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.database.PeriodicDbExporter
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceManager
import nodomain.freeyourgadget.gadgetbridge.externalevents.BluetoothStateChangeReceiver
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppRepository
import nodomain.freeyourgadget.gadgetbridge.externalevents.opentracks.OpenTracksContentObserver
import nodomain.freeyourgadget.gadgetbridge.externalservices.ExternalServiceRegistry
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService
import nodomain.freeyourgadget.gadgetbridge.model.weather.WeatherRepository
import nodomain.freeyourgadget.gadgetbridge.prefs.GBPrefsMigrator
import nodomain.freeyourgadget.gadgetbridge.service.NotificationCollectorMonitorService
import nodomain.freeyourgadget.gadgetbridge.util.AndroidUtils
import nodomain.freeyourgadget.gadgetbridge.util.CompanionDeviceUtil
import nodomain.freeyourgadget.gadgetbridge.util.DoNotDisturbPolicy
import nodomain.freeyourgadget.gadgetbridge.util.GB
import nodomain.freeyourgadget.gadgetbridge.util.GB.NOTIFICATION_CHANNEL_HIGH_PRIORITY_ID
import nodomain.freeyourgadget.gadgetbridge.util.GB.NOTIFICATION_ID_ERROR
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs
import nodomain.freeyourgadget.gadgetbridge.util.InternetHelperSingleton
import nodomain.freeyourgadget.gadgetbridge.util.LimitedQueue
import nodomain.freeyourgadget.gadgetbridge.util.PermissionsUtils
import nodomain.freeyourgadget.gadgetbridge.util.backup.PeriodicZipExporter
import nodomain.freeyourgadget.gadgetbridge.util.healthconnect.HealthConnectPermissionManager
import nodomain.freeyourgadget.gadgetbridge.util.preferences.DevicePrefs
import nodomain.freeyourgadget.gadgetbridge.util.preferences.ScopedPrefsProvider
import nodomain.freeyourgadget.gadgetbridge.widgets.launcher.LauncherWidgetUpdateTriggers
import org.slf4j.LoggerFactory
import java.util.Locale
import javax.inject.Inject
import kotlin.system.exitProcess

/**
 * Main Application class that initializes and provides access to certain things like
 * logging and DB access.
 *
 * Static members are kept in the companion object and annotated with [JvmStatic] so that the
 * existing Java call sites keep working unchanged.
 */
@HiltAndroidApp
open class GBApplication : Application() {
    @Inject
    lateinit var deviceManagerInstance: DeviceManager

    @Inject
    lateinit var launcherWidgetUpdateTriggers: LauncherWidgetUpdateTriggers

    /**
     * The app's default shared preferences, which back [gbPrefs].
     */
    @Inject
    lateinit var defaultSharedPreferences: SharedPreferences

    @Inject
    lateinit var gbPrefs: GBPrefs

    @Inject
    lateinit var scopedPrefs: ScopedPrefsProvider

    @Inject
    lateinit var doNotDisturbPolicy: DoNotDisturbPolicy

    @Inject
    lateinit var deviceServiceInstance: DeviceService

    @Inject
    lateinit var databaseManager: DatabaseManager

    @Inject
    lateinit var weatherRepository: WeatherRepository

    private val themePreferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if ("pref_key_theme" == key) {
            updateApplicationNightMode()
        }
    }

    private val lifecycleObserver = AppLifecycleObserver()

    private var exceptionHandler: GBExceptionHandler? = null

    private var bluetoothStateChangeReceiver: BluetoothStateChangeReceiver? = null

    var openTracksObserver: OpenTracksContentObserver? = null

    init {
        appContext = this
        context = this
        // don't do anything here, add it to onCreate instead

        if (BuildConfig.DEBUG) {
            val thread = StrictMode.ThreadPolicy.Builder()
            thread.detectCustomSlowCalls()
            thread.permitDiskReads() // log requires disk access
            thread.permitDiskWrites() // log requires disk access
            thread.detectNetwork()
            thread.detectResourceMismatches()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                thread.detectUnbufferedIo()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                thread.detectExplicitGc()
            }
            StrictMode.setThreadPolicy(thread.penaltyLog().build())

            val vm = StrictMode.VmPolicy.Builder()
            vm.detectLeakedSqlLiteObjects()
            vm.detectLeakedClosableObjects()
            vm.detectLeakedRegistrationObjects()
            vm.detectFileUriExposure()
            vm.detectCleartextNetwork()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vm.detectContentUriWithoutPermission()
                vm.detectUntaggedSockets()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vm.detectImplicitDirectBoot()
                vm.detectCredentialProtectedWhileLocked()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                vm.detectIncorrectContextUse()
                vm.detectUnsafeIntentLaunch()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                vm.detectBlockedBackgroundActivityLaunch()
            }
            StrictMode.setVmPolicy(vm.penaltyLog().build())
        }
    }

    /**
     * Every step must be safe to repeat: Robolectric creates a new application instance and calls
     * onCreate() for each test, while static and process-wide state survives across tests in the same JVM.
     * Registrations with process-wide singletons are undone in [onTerminate].
     */
    override fun onCreate() {
        appInstance = this
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)

        val environmentSetup = GBEnvironment.isEnvironmentSetup()

        // We need to set up the environment before initializing logging, so that the logging
        // path is properly set, and we should set up logging before the database setup, so that
        // the database upgrade also gets logged as expected.
        if (!environmentSetup) {
            GBEnvironment.setupEnvironment(GBEnvironment.createDeviceEnvironment())
        }

        Logging.getInstance().initialize(
            gbPrefs.getBoolean("log_to_file", false),
            gbPrefs.getBoolean("log_level_trace", false),
        )

        if (!environmentSetup) {
            // setup db after the environment is set up, but don't do it in test mode
            // in test mode, it's done individually, see TestBase
            databaseManager.setupDatabase(this)
        }

        migratePrefsIfNeeded()
        updateApplicationNightMode()
        defaultSharedPreferences.registerOnSharedPreferenceChangeListener(themePreferenceListener)

        setupExceptionHandler(gbPrefs.getBoolean("crash_notification", isDebug()))

        registerActivityLifecycleCallbacks(GBActivityLifecycleCallbacks())

        weatherRepository.initialize()

        // Prefs and the database are ready at this point.
        deviceManagerInstance.initialize()
        val languageCode = gbPrefs.getString("language", "default") ?: "default"
        setLanguage(languageCode)

        if (!GBEnvironment.env().isTest) {
            PeriodicDbExporter.scheduleNextExecution(appContext)
            PeriodicZipExporter.scheduleNextExecution(appContext)
            NotificationAppRepository.loadInBackground()
        }

        if (isRunningOreoOrLater()) {
            bluetoothStateChangeReceiver = BluetoothStateChangeReceiver()
            val bif = IntentFilter()
            bif.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            if (isRunningPieOrLater()) {
                bif.addAction(BluetoothStateChangeReceiver.ANDROID_BLUETOOTH_DEVICE_ACTION_BATTERY_LEVEL_CHANGED)
            }
            registerReceiver(bluetoothStateChangeReceiver, bif)
        }
        startNotificationCollectorMonitorService()

        CompanionDeviceUtil.StartObservingAll(baseContext)

        if (gbPrefs.getBoolean(GBPrefs.HEALTH_CONNECT_ENABLED, false)) {
            HealthConnectPermissionManager.checkAndRectifyPermissions(this)
        }

        // Ensure the InternetHelper is bound, so that it works on first usage
        InternetHelperSingleton.ensureInternetHelperBound()

        launcherWidgetUpdateTriggers.register()

        // Let every external service schedule its own upkeep, such as renewing a token
        if (!GBEnvironment.env().isTest) {
            ExternalServiceRegistry.scheduleBackgroundWork(appContext)
        }
    }

    private fun startNotificationCollectorMonitorService() {
        if (!gbPrefs.getBoolean("prefs_key_enable_deprecated_notificationcollectormonitor", false)) {
            return
        }
        LOG.info("Starting the deprecated NotificationCollectorMonitorService foreground service.")
        try {
            //the following will ensure the notification manager is kept alive
            val serviceIntent = Intent(appContext, NotificationCollectorMonitorService::class.java)

            //ContextCompat starts a background service on android < O automatically despite the method name
            ContextCompat.startForegroundService(appContext, serviceIntent)
        } catch (e: IllegalStateException) {
            val message = e.toString()
            val instructionsIntent = Intent(
                Intent.ACTION_VIEW,
                "https://gadgetbridge.org/basics/topics/background-service/".toUri(),
            )
            val pi = PendingIntent.getActivity(
                appContext,
                0,
                instructionsIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE,
            )
            GB.notify(
                NOTIFICATION_ID_ERROR,
                NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_HIGH_PRIORITY_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(getString(R.string.error_background_service))
                    .setContentText(getString(R.string.error_background_service_reason_truncated))
                    .setContentIntent(pi)
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText(getString(R.string.error_background_service_reason) + " \"" + message + "\"")
                    )
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build(),
                appContext,
            )
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) {
            if (!hasBusyDevice()) {
                DBHelper.clearSession()
            }
        }
    }

    /**
     * Returns true if at least a single device is busy, e.g. synchronizing activity data
     * or something similar.
     * Note: busy is not the same as connected or initialized!
     */
    private fun hasBusyDevice(): Boolean = deviceManagerInstance.devices.any { it.isBusy }

    /**
     * Only called on emulators and by Robolectric when it tears down a test's application, never on
     * real devices. Undoes the registrations with process-wide singletons made in [onCreate], so that
     * the next test's application starts clean. Do not put cleanup here that must run on devices.
     */
    override fun onTerminate() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        defaultSharedPreferences.unregisterOnSharedPreferenceChangeListener(themePreferenceListener)
        launcherWidgetUpdateTriggers.unregister()
        deviceManagerInstance.shutdown()
        databaseManager.closeDatabase()
        exceptionHandler?.unregister()
        exceptionHandler = null
        super.onTerminate()
    }

    private fun setupExceptionHandler(notifyOnCrash: Boolean) {
        exceptionHandler?.unregister()
        exceptionHandler = GBExceptionHandler(notifyOnCrash).also { it.register() }
    }

    @VisibleForTesting
    protected open fun migratePrefsIfNeeded() {
        GBPrefsMigrator.migratePrefsIfNeeded(defaultSharedPreferences)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateLanguage(getLanguage())
    }

    /**
     * @deprecated Use Hilt DI to access the [DeviceManager] instead.
     */
    @Deprecated("Use Hilt DI to access the DeviceManager instead.")
    val deviceManager: DeviceManager
        get() = deviceManagerInstance

    fun getVersion(): String? {
        return try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            LOG.warn("Unable to determine Gadgetbridge's version", e)
            "0.0.0"
        }
    }

    fun getNameAndVersion(): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA)
            String.format("%s %s", appInfo.name, packageInfo.versionName)
        } catch (e: PackageManager.NameNotFoundException) {
            LOG.warn("Unable to determine Gadgetbridge's name/version", e)
            "Gadgetbridge"
        }
    }

    private class GBActivityLifecycleCallbacks : ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            val preventScreenshots = getPrefs().getBoolean(GBPrefs.BLOCK_SCREENSHOTS, false)
            if (preventScreenshots) {
                LOG.debug("set FLAG_SECURE for {}", activity.localClassName)
                activity.window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE,
                )
            }
        }

        override fun onActivityStarted(activity: Activity) {
        }

        override fun onActivityResumed(activity: Activity) {
        }

        override fun onActivityPaused(activity: Activity) {
        }

        override fun onActivityStopped(activity: Activity) {
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        }

        override fun onActivityDestroyed(activity: Activity) {
        }

        companion object {
            private val LOG = LoggerFactory.getLogger(GBActivityLifecycleCallbacks::class.java)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(GBApplication::class.java)

        private lateinit var appContext: GBApplication
        private var context: GBApplication? = null
        private var appInstance: GBApplication? = null
        private lateinit var language: Locale
        private val idSenderLookup = LimitedQueue<Int, String>(16)

        const val ACTION_QUIT = "nodomain.freeyourgadget.gadgetbridge.gbapplication.action.quit"
        const val ACTION_LANGUAGE_CHANGE = "nodomain.freeyourgadget.gadgetbridge.gbapplication.action.language_change"
        const val ACTION_THEME_CHANGE = "nodomain.freeyourgadget.gadgetbridge.gbapplication.action.theme_change"
        const val ACTION_NEW_DATA = "nodomain.freeyourgadget.gadgetbridge.action.new_data"
        const val ACTION_APP_IS_IN_FOREGROUND = "nodomain.freeyourgadget.gadgetbridge.action.app_foreground"
        const val ACTION_APP_IS_IN_BACKGROUND = "nodomain.freeyourgadget.gadgetbridge.action.app_background"

        const val PREF_ACCENT_COLOR = "pref_key_accent_color"
        const val PREF_ACCENT_COLOR_DEFAULT = "red"
        const val PREF_CONTRASTING_SURFACES = "pref_key_contrasting_surfaces"

        @JvmStatic
        fun quit() {
            LOG.info("Quitting Gadgetbridge...")
            CompanionDeviceUtil.StopObservingAll(getContext())
            val quitIntent = Intent(ACTION_QUIT)
            LocalBroadcastManager.getInstance(appContext).sendBroadcast(quitIntent)
            app().deviceServiceInstance.quit()
            app().databaseManager.closeDatabase()
            exitProcess(0)
        }

        @JvmStatic
        fun restart() {
            LOG.info("Restarting Gadgetbridge...")
            CompanionDeviceUtil.StopObservingAll(getContext())
            val quitIntent = Intent(ACTION_QUIT)
            LocalBroadcastManager.getInstance(appContext).sendBroadcast(quitIntent)
            app().deviceServiceInstance.quit()

            app().databaseManager.closeDatabase()

            val startActivity = Intent(appContext, ControlCenterv2::class.java)
            val pendingIntent = PendingIntent.getActivity(
                appContext,
                1337,
                startActivity,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val alarmManager = appContext.getSystemService(ALARM_SERVICE) as AlarmManager
            alarmManager.set(AlarmManager.RTC, System.currentTimeMillis() + 1500, pendingIntent)

            Runtime.getRuntime().exit(0)
        }

        @JvmStatic
        fun minimizeNotification(): Boolean {
            return getPrefs().getBoolean("minimize_priority", false)
        }

        @JvmStatic
        fun getContext(): Context {
            return context as Context
        }

        /**
         * Returns the application context if it has been created, or null during early startup
         * (e.g. while broadcast receivers are being registered) and in unit tests.
         */
        @JvmStatic
        fun getContextOrNull(): Context? {
            return context
        }

        /**
         * Returns the facade for talking to devices. Devices are managed by
         * an Android Service and this facade provides access to its functionality.
         *
         * @return the facade for talking to the service/devices.
         * @deprecated Use Hilt DI to inject [DeviceService] instead.
         */
        @JvmStatic
        fun deviceService(): DeviceService {
            return app().deviceServiceInstance
        }

        /**
         * Returns the facade for talking to a specific device. Devices are managed by
         * an Android Service and this facade provides access to its functionality.
         *
         * @return the facade for talking to the service/device.
         * @deprecated Use Hilt DI to inject [DeviceService] and call [DeviceService.forDevice] instead.
         */
        @JvmStatic
        fun deviceService(device: GBDevice?): DeviceService {
            return app().deviceServiceInstance.forDevice(device)
        }

        /**
         * @see DatabaseManager.acquireWrite
         * @deprecated Use Hilt DI to inject [DatabaseManager] instead.
         */
        @JvmStatic
        @Throws(GBException::class)
        fun acquireDB(): DBHandler {
            return app().databaseManager.acquireWrite()
        }

        /**
         * @see DatabaseManager.acquireReadOnly
         * @deprecated Use Hilt DI to inject [DatabaseManager] instead.
         */
        @JvmStatic
        @Throws(GBException::class)
        fun acquireDbReadOnly(): DBHandler {
            return app().databaseManager.acquireReadOnly()
        }

        @JvmStatic
        fun isRunningOreoOrLater(): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        }

        @JvmStatic
        fun isRunningTenOrLater(): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        }

        @JvmStatic
        fun isRedVelvetCakeOrLater(): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        }

        @JvmStatic
        fun isRunningTwelveOrLater(): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        }

        @JvmStatic
        fun isRunningTiramisuOrLater(): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        }

        @JvmStatic
        fun isRunningPieOrLater(): Boolean {
            return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        }

        /**
         * @deprecated Use Hilt DI to inject [DoNotDisturbPolicy] instead.
         */
        @JvmStatic
        fun isPriorityNumber(priorityType: Int, number: String?): Boolean {
            return app().doNotDisturbPolicy.isPriorityNumber(priorityType, number)
        }

        /**
         * @deprecated Use Hilt DI to inject [DoNotDisturbPolicy] instead.
         */
        @JvmStatic
        fun getGrantedInterruptionFilter(): Int {
            return app().doNotDisturbPolicy.getGrantedInterruptionFilter()
        }

        /**
         * @deprecated Use Hilt DI to inject [ScopedPrefsProvider] instead.
         */
        @JvmStatic
        fun getDeviceSpecificSharedPrefs(deviceIdentifier: CharSequence?): SharedPreferences {
            return app().scopedPrefs.getDeviceSpecificSharedPrefs(deviceIdentifier)
        }

        /**
         * @deprecated Use Hilt DI to inject [ScopedPrefsProvider] instead.
         */
        @JvmStatic
        fun getDevicePrefs(gbDevice: GBDevice): DevicePrefs {
            return app().scopedPrefs.getDevicePrefs(gbDevice)
        }

        /**
         * @deprecated Use Hilt DI to inject [ScopedPrefsProvider] instead.
         */
        @JvmStatic
        fun deleteDeviceSpecificSharedPrefs(deviceIdentifier: CharSequence?) {
            app().scopedPrefs.deleteDeviceSpecificSharedPrefs(deviceIdentifier)
        }

        /**
         * The per-instance settings file for a dashboard widget instance, keyed by its instance id.
         *
         * @deprecated Use Hilt DI to inject [ScopedPrefsProvider] instead.
         */
        @JvmStatic
        fun getWidgetSharedPrefs(instanceId: CharSequence?): SharedPreferences? {
            return app().scopedPrefs.getWidgetSharedPrefs(instanceId)
        }

        /**
         * @deprecated Use Hilt DI to inject [ScopedPrefsProvider] instead.
         */
        @JvmStatic
        fun deleteWidgetSharedPrefs(instanceId: CharSequence?) {
            app().scopedPrefs.deleteWidgetSharedPrefs(instanceId)
        }

        @JvmStatic
        fun setLanguage(lang: String) {
            language = if (lang == "default") {
                Resources.getSystem().configuration.locale
            } else if (lang.length == 2) {
                Locale(lang)
            } else {
                val split = lang.split("_")
                if (split.size == 2) {
                    Locale(split[0], split[1])
                } else {
                    // Unexpected format, fallback to system default
                    Resources.getSystem().configuration.locale
                }
            }
            updateLanguage(language)
        }

        @JvmStatic
        fun updateLanguage(locale: Locale) {
            AndroidUtils.setLanguage(appContext, locale)

            val intent = Intent()
            intent.action = ACTION_LANGUAGE_CHANGE
            LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent)
        }

        @JvmStatic
        fun getIDSenderLookup(): LimitedQueue<Int, String> {
            return idSenderLookup
        }

        @JvmStatic
        fun isDarkThemeEnabled(): Boolean {
            val selectedTheme = getPrefs().getString(
                "pref_key_theme",
                appContext.getString(R.string.pref_theme_value_system),
            )
            val resources = appContext.resources

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                (selectedTheme == appContext.getString(R.string.pref_theme_value_system) ||
                    selectedTheme == appContext.getString(R.string.pref_theme_value_dynamic))
            ) {
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            } else {
                selectedTheme == appContext.getString(R.string.pref_theme_value_dark)
            }
        }

        /**
         * Ensures the splash screen follows the theme preference, if manually set to light or dark.
         */
        private fun updateApplicationNightMode() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return
            }
            val selectedTheme = getPrefs().getString(
                "pref_key_theme",
                appContext.getString(R.string.pref_theme_value_system),
            )
            val nightMode = when (selectedTheme) {
                appContext.getString(R.string.pref_theme_value_light) -> UiModeManager.MODE_NIGHT_NO
                appContext.getString(R.string.pref_theme_value_dark) -> UiModeManager.MODE_NIGHT_YES
                else -> UiModeManager.MODE_NIGHT_AUTO
            }
            appContext.getSystemService(UiModeManager::class.java)?.setApplicationNightMode(nightMode)
        }

        @JvmStatic
        fun isAmoledBlackEnabled(): Boolean {
            return getPrefs().getBoolean("pref_key_theme_amoled_black", false)
        }

        @JvmStatic
        fun areDynamicColorsEnabled(): Boolean {
            val selectedTheme = getPrefs().getString(
                "pref_key_theme",
                appContext.getString(R.string.pref_theme_value_system),
            )
            return selectedTheme == appContext.getString(R.string.pref_theme_value_dynamic)
        }

        /**
         * The ThemeOverlay for the user's chosen accent color preset (Settings > User interface).
         * Not meaningful when [areDynamicColorsEnabled] is true - callers should skip
         * applying it in that case, since Dynamic Color already ties these colors to the wallpaper.
         */
        @JvmStatic
        @StyleRes
        fun getAccentColorOverlay(dark: Boolean): Int {
            val accent = getPrefs().getString(PREF_ACCENT_COLOR, PREF_ACCENT_COLOR_DEFAULT)
            return when (accent) {
                "coral" -> if (dark) R.style.ThemeOverlay_App_Accent_Coral_Dark else R.style.ThemeOverlay_App_Accent_Coral_Light
                "orange" -> if (dark) R.style.ThemeOverlay_App_Accent_Orange_Dark else R.style.ThemeOverlay_App_Accent_Orange_Light
                "amber" -> if (dark) R.style.ThemeOverlay_App_Accent_Amber_Dark else R.style.ThemeOverlay_App_Accent_Amber_Light
                "lime" -> if (dark) R.style.ThemeOverlay_App_Accent_Lime_Dark else R.style.ThemeOverlay_App_Accent_Lime_Light
                "green" -> if (dark) R.style.ThemeOverlay_App_Accent_Green_Dark else R.style.ThemeOverlay_App_Accent_Green_Light
                "teal" -> if (dark) R.style.ThemeOverlay_App_Accent_Teal_Dark else R.style.ThemeOverlay_App_Accent_Teal_Light
                "cyan" -> if (dark) R.style.ThemeOverlay_App_Accent_Cyan_Dark else R.style.ThemeOverlay_App_Accent_Cyan_Light
                "blue" -> if (dark) R.style.ThemeOverlay_App_Accent_Blue_Dark else R.style.ThemeOverlay_App_Accent_Blue_Light
                "indigo" -> if (dark) R.style.ThemeOverlay_App_Accent_Indigo_Dark else R.style.ThemeOverlay_App_Accent_Indigo_Light
                "violet" -> if (dark) R.style.ThemeOverlay_App_Accent_Violet_Dark else R.style.ThemeOverlay_App_Accent_Violet_Light
                "pink" -> if (dark) R.style.ThemeOverlay_App_Accent_Pink_Dark else R.style.ThemeOverlay_App_Accent_Pink_Light
                "white" -> if (dark) R.style.ThemeOverlay_App_Accent_White_Dark else R.style.ThemeOverlay_App_Accent_White_Light
                "red", null -> if (dark) R.style.ThemeOverlay_App_Accent_Red_Dark else R.style.ThemeOverlay_App_Accent_Red_Light
                else -> if (dark) R.style.ThemeOverlay_App_Accent_Red_Dark else R.style.ThemeOverlay_App_Accent_Red_Light
            }
        }

        @JvmStatic
        fun areContrastingSurfacesEnabled(dark: Boolean): Boolean {
            return getPrefs().getBoolean(PREF_CONTRASTING_SURFACES, dark)
        }

        @JvmStatic
        @StyleRes
        fun getContrastingSurfacesOverlay(dark: Boolean): Int {
            if (areDynamicColorsEnabled()) {
                if (dark && isAmoledBlackEnabled()) {
                    return R.style.ThemeOverlay_App_ContrastingSurfaces_Dynamic_Black
                }
                return R.style.ThemeOverlay_App_ContrastingSurfaces_Dynamic
            }
            if (!dark) {
                return R.style.ThemeOverlay_App_ContrastingSurfaces_Light
            }
            return if (isAmoledBlackEnabled()) {
                R.style.ThemeOverlay_App_ContrastingSurfaces_Black
            } else {
                R.style.ThemeOverlay_App_ContrastingSurfaces_Dark
            }
        }

        @JvmStatic
        fun getTextColor(context: Context): Int {
            return if (AndroidUtils.isDynamicColorActive()) {
                AndroidUtils.getDynamicColor(context, com.google.android.material.R.attr.colorOnSurface)
            } else if (isDarkThemeEnabled()) {
                context.resources.getColor(R.color.primarytext_dark)
            } else {
                context.resources.getColor(R.color.primarytext_light)
            }
        }

        @JvmStatic
        fun getSecondaryTextColor(context: Context): Int {
            return if (AndroidUtils.isDynamicColorActive()) {
                AndroidUtils.getDynamicColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant)
            } else if (isDarkThemeEnabled()) {
                context.resources.getColor(R.color.on_surface_variant_dark)
            } else {
                context.resources.getColor(R.color.on_surface_variant_light)
            }
        }

        @JvmStatic
        fun getBackgroundColor(context: Context): Int {
            val typedValue = TypedValue()
            val theme = context.theme
            theme.resolveAttribute(android.R.attr.background, typedValue, true)
            return typedValue.data
        }

        @JvmStatic
        fun getWindowBackgroundColor(context: Context): Int {
            val typedValue = TypedValue()
            val theme = context.theme
            theme.resolveAttribute(android.R.attr.windowBackground, typedValue, true)
            return typedValue.data
        }

        @JvmStatic
        fun hasDirectInternetAccess(): Boolean {
            return PermissionsUtils.checkPermission(getContext(), Manifest.permission.INTERNET)
        }

        @JvmStatic
        fun hasInternetAccess(): Boolean {
            return hasDirectInternetAccess() || InternetHelperSingleton.ensureInternetHelperBound()
        }

        /**
         * @deprecated Use Hilt DI to inject [GBPrefs] instead.
         */
        @JvmStatic
        fun getPrefs(): GBPrefs {
            return app().gbPrefs
        }

        @JvmStatic
        fun app(): GBApplication {
            return checkNotNull(appInstance) { "GBApplication is not initialized" }
        }

        /**
         * Returns the application instance if [onCreate] has run, or null otherwise (e.g. in plain unit tests).
         */
        @JvmStatic
        fun appOrNull(): GBApplication? {
            return appInstance
        }

        @JvmStatic
        fun getLanguage(): Locale {
            return language
        }

        @JvmStatic
        fun isNightly(): Boolean {
            //noinspection ConstantValue - false positive
            return BuildConfig.APPLICATION_ID.contains("nightly")
        }

        @JvmStatic
        fun isDebug(): Boolean {
            return BuildConfig.DEBUG
        }
    }
}
