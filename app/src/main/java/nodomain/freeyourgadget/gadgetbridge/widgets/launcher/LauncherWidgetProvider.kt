package nodomain.freeyourgadget.gadgetbridge.widgets.launcher

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import androidx.work.ExistingWorkPolicy
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * A dashboard widget on the launcher.
 */
class LauncherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        LOG.debug("onUpdate {}", appWidgetIds.contentToString())
        LauncherWidgets.update(context, appWidgetIds)
        LauncherWidgets.scheduleMidnight(context, ExistingWorkPolicy.KEEP)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        LOG.debug("onAppWidgetOptionsChanged {}", appWidgetId)
        LauncherWidgets.update(context, intArrayOf(appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        LauncherWidgetStore.delete(appWidgetIds)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        LauncherWidgetStore.remap(oldWidgetIds, newWidgetIds)
        LauncherWidgets.update(context, newWidgetIds)
    }

    override fun onEnabled(context: Context) {
        LauncherWidgets.scheduleMidnight(context, ExistingWorkPolicy.KEEP)
    }

    override fun onDisabled(context: Context) {
        LauncherWidgets.cancelMidnight(context)
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(LauncherWidgetProvider::class.java)
    }
}
