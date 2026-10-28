package nodomain.freeyourgadget.gadgetbridge.widgets.launcher

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.widgets.DashboardQuery
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetRegistry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate

/**
 * Loads the data of launcher widgets for the current day, and renders them.
 * If the load fails, the widget keeps its last bitmap.
 */
class LauncherWidgetWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val appWidgetIds = inputData.getIntArray(KEY_APP_WIDGET_IDS) ?: LauncherWidgets.appWidgetIds(applicationContext)
        LOG.debug("Updating launcher widgets {}", appWidgetIds.contentToString())

        val query = DashboardQuery.forDay(LocalDate.now())
        coroutineScope {
            appWidgetIds.map { async { update(it, query) } }.awaitAll()
        }

        if (inputData.getBoolean(KEY_MIDNIGHT, false)) {
            LauncherWidgets.scheduleMidnight(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
        return Result.success()
    }

    private suspend fun update(appWidgetId: Int, query: DashboardQuery) {
        val launcherWidget = LauncherWidgetStore.get(appWidgetId)
        if (launcherWidget == null) {
            LOG.warn("Launcher widget {} is not configured", appWidgetId)
            return
        }
        val widget = WidgetRegistry.byId(launcherWidget.instance.typeId)
        if (widget == null) {
            LOG.warn("Unknown widget type {} for launcher widget {}", launcherWidget.instance.typeId, appWidgetId)
            return
        }

        val variant = LauncherWidgetRenderer.variantFor(applicationContext, appWidgetId, widget, launcherWidget)
        val config = LauncherWidgets.configOf(launcherWidget, variant)
        val devices = config.resolveDevices(widget)
        val content = if (devices.isEmpty()) {
            LauncherContent.NoDevices
        } else {
            try {
                LauncherContent.Ready(widget.loadData(WidgetDataScope(query, devices), config))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.error("Failed to load data for launcher widget {}", appWidgetId, e)
                return
            }
        }
        LOG.debug(
            "Loaded launcher widget {} ({}, {}): {}",
            appWidgetId,
            widget.id,
            variant.id,
            content::class.simpleName
        )

        val views = withContext(Dispatchers.Main) {
            LauncherWidgetRenderer.render(applicationContext, appWidgetId, widget, variant, config, content)
        }
        AppWidgetManager.getInstance(applicationContext).updateAppWidget(appWidgetId, views)
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(LauncherWidgetWorker::class.java)

        const val KEY_APP_WIDGET_IDS = "app_widget_ids"
        const val KEY_MIDNIGHT = "midnight"
    }
}
