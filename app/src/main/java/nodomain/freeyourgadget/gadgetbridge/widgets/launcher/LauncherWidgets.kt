package nodomain.freeyourgadget.gadgetbridge.widgets.launcher

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.util.Prefs
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetVariant
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.concurrent.TimeUnit

/**
 * Stateless helpers for the launcher widgets. The update triggers live in [LauncherWidgetUpdateTriggers].
 */
object LauncherWidgets {
    private const val WORK_ALL = "launcher_widgets_update"
    private const val WORK_ONE = "launcher_widget_update_"
    private const val WORK_MIDNIGHT = "launcher_widgets_midnight"
    private const val MIDNIGHT_DELAY_SECONDS = 30

    /**
     * The ids of the launcher widgets. Empty if the system has no launcher widgets.
     */
    fun appWidgetIds(context: Context): IntArray = AppWidgetManager.getInstance(context)
        ?.getAppWidgetIds(ComponentName(context, LauncherWidgetProvider::class.java))
        ?: IntArray(0)

    /**
     * Loads and renders all launcher widgets again after [delaySeconds]. A new call before that replaces the
     * scheduled update.
     */
    fun updateAll(context: Context, delaySeconds: Long) {
        if (appWidgetIds(context).isEmpty()) {
            return
        }
        val request = OneTimeWorkRequestBuilder<LauncherWidgetWorker>()
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_ALL, ExistingWorkPolicy.REPLACE, request)
    }

    fun update(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val request = OneTimeWorkRequestBuilder<LauncherWidgetWorker>()
                .setInputData(workDataOf(LauncherWidgetWorker.KEY_APP_WIDGET_IDS to intArrayOf(appWidgetId)))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_ONE + appWidgetId, ExistingWorkPolicy.REPLACE, request)
        }
    }

    /**
     * Schedules an update of all launcher widgets shortly after the next midnight.
     */
    fun scheduleMidnight(context: Context, policy: ExistingWorkPolicy) {
        val midnight = GregorianCalendar.getInstance()
        midnight.add(Calendar.DAY_OF_MONTH, 1)
        midnight.set(Calendar.HOUR_OF_DAY, 0)
        midnight.set(Calendar.MINUTE, 0)
        midnight.set(Calendar.SECOND, MIDNIGHT_DELAY_SECONDS)
        val request = OneTimeWorkRequestBuilder<LauncherWidgetWorker>()
            .setInitialDelay(midnight.timeInMillis - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(LauncherWidgetWorker.KEY_MIDNIGHT to true))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_MIDNIGHT, policy, request)
    }

    fun cancelMidnight(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_MIDNIGHT)
    }

    /**
     * The configuration of [launcherWidget] for [variant]. The inherited devices are the devices of the dashboard.
     */
    fun configOf(launcherWidget: LauncherWidget, variant: WidgetVariant): WidgetConfig {
        val prefs = GBApplication.getPrefs()
        return WidgetConfig(
            launcherWidget.instance,
            Prefs(requireNotNull(GBApplication.getWidgetSharedPrefs(launcherWidget.instance.instanceId))),
            prefs.getBoolean("dashboard_devices_all", true),
            prefs.getStringSet("dashboard_devices_multiselect", emptySet()),
            variant,
        )
    }
}
