package nodomain.freeyourgadget.gadgetbridge.activities.dashboard

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetRegistry
import nodomain.freeyourgadget.gadgetbridge.widgets.launcher.LauncherContent
import nodomain.freeyourgadget.gadgetbridge.widgets.launcher.LauncherWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.launcher.LauncherWidgetProvider
import nodomain.freeyourgadget.gadgetbridge.widgets.launcher.LauncherWidgetRenderer
import nodomain.freeyourgadget.gadgetbridge.widgets.launcher.LauncherWidgetStore
import nodomain.freeyourgadget.gadgetbridge.widgets.launcher.LauncherWidgets
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * The configuration activity of a launcher widget. A new launcher widget gets a widget from [WidgetPickerActivity].
 * Then the settings of the launcher widget open.
 */
class LauncherWidgetConfigureActivity : AbstractGBActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private val pickWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val typeId = result.data?.getStringExtra(WidgetPickerActivity.EXTRA_TYPE_ID)
        if (result.resultCode != RESULT_OK || typeId == null) {
            finish()
            return@registerForActivityResult
        }
        val variantId = result.data?.getStringExtra(WidgetPickerActivity.EXTRA_VARIANT_ID)
        val launcherWidget = LauncherWidgetStore.add(appWidgetId, typeId, variantId)
        showLoading(launcherWidget)
        LauncherWidgets.update(this, intArrayOf(appWidgetId))
        openSettings(launcherWidget)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        theme.applyStyle(R.style.ThemeOverlay_App_Translucent, true)

        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED, resultIntent())
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider
        if (provider != ComponentName(this, LauncherWidgetProvider::class.java)) {
            LOG.warn("App widget ID {} is not a launcher widget from Gadgetbridge", appWidgetId)
            finish()
            return
        }
        if (savedInstanceState != null) {
            return
        }

        val launcherWidget = LauncherWidgetStore.get(appWidgetId)
        if (launcherWidget != null) {
            openSettings(launcherWidget)
            return
        }

        val intent = Intent(this, WidgetPickerActivity::class.java)
        intent.putExtra(WidgetPickerActivity.EXTRA_LAUNCHER, true)
        pickWidget.launch(intent)
    }

    private fun showLoading(launcherWidget: LauncherWidget) {
        val widget = WidgetRegistry.byId(launcherWidget.instance.typeId) ?: return
        val variant = LauncherWidgetRenderer.variantFor(this, appWidgetId, widget, launcherWidget)
        val config = LauncherWidgets.configOf(launcherWidget, variant)
        val views = LauncherWidgetRenderer.render(this, appWidgetId, widget, variant, config, LauncherContent.Loading)
        AppWidgetManager.getInstance(this).updateAppWidget(appWidgetId, views)
    }

    private fun openSettings(launcherWidget: LauncherWidget) {
        setResult(RESULT_OK, resultIntent())
        WidgetInstanceSettingsActivity.startForLauncherWidget(this, appWidgetId, launcherWidget)
        finish()
    }

    private fun resultIntent(): Intent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(LauncherWidgetConfigureActivity::class.java)
    }
}
