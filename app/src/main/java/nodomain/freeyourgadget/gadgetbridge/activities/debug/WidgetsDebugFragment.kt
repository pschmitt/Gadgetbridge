package nodomain.freeyourgadget.gadgetbridge.activities.debug

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import androidx.preference.PreferenceCategory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.Widget
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.WidgetInstanceSettingsActivity
import nodomain.freeyourgadget.gadgetbridge.adapter.SimpleIconListAdapter
import nodomain.freeyourgadget.gadgetbridge.model.RunnableListIconItem
import nodomain.freeyourgadget.gadgetbridge.util.WidgetPreferenceStorage
import nodomain.freeyourgadget.gadgetbridge.widgets.launcher.LauncherWidgetStore
import nodomain.freeyourgadget.gadgetbridge.widgets.launcher.LauncherWidgets

class WidgetsDebugFragment : AbstractDebugFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.debug_preferences_widgets, rootKey)

        onClick(PREF_DEBUG_WIDGETS_PREFS_SHOW) { showAppWidgetsPrefs() }
        onClick(PREF_DEBUG_WIDGETS_PREFS_DELETE) { deleteWidgetsPrefs() }

        reloadLegacyWidgets()
        reloadDashboardWidgets()
    }

    private fun reloadLegacyWidgets() {
        val widgetsHeader: PreferenceCategory = findPreference(PREF_HEADER_LAUNCHER_WIDGETS_LEGACY)!!
        removeDynamicPrefs(widgetsHeader)

        // https://stackoverflow.com/questions/17387191/check-if-a-widget-is-exists-on-homescreen-using-appwidgetid
        val appWidgetManager = AppWidgetManager.getInstance(requireContext())
        val appWidgetHost = AppWidgetHost(requireContext(), 1) // for removing phantoms
        val appWidgetIDs = appWidgetManager.getAppWidgetIds(ComponentName(requireContext(), Widget::class.java))
        if (appWidgetIDs.isEmpty()) {
            addDynamicPref(widgetsHeader, "", "No widgets found")
            return
        }

        for ((i, appWidgetID) in appWidgetIDs.withIndex()) {
            addDynamicPref(widgetsHeader, "Widget $i", "id = $appWidgetID", R.drawable.ic_widgets) {
                val items: MutableList<RunnableListIconItem?> = ArrayList(4)

                items.add(
                    RunnableListIconItem(getString(R.string.delete), R.drawable.ic_delete_forever) {
                        appWidgetHost.deleteAppWidgetId(appWidgetID)
                        reloadLegacyWidgets()
                    }
                )

                val adapter = SimpleIconListAdapter(context, items)

                MaterialAlertDialogBuilder(requireContext())
                    .setAdapter(adapter) { _: DialogInterface?, i1: Int -> items[i1]!!.action.run() }
                    .setTitle("Widget $i / id=$appWidgetID")
                    .setNegativeButton(android.R.string.cancel) { _: DialogInterface?, _: Int -> }
                    .create()
                    .show()
            }
        }
    }

    private fun reloadDashboardWidgets() {
        val header: PreferenceCategory = findPreference(PREF_HEADER_LAUNCHER_WIDGETS_DASHBOARD)!!
        removeDynamicPrefs(header)

        val appWidgetHost = AppWidgetHost(requireContext(), 1) // for removing phantoms
        val appWidgetIds = LauncherWidgets.appWidgetIds(requireContext())
        if (appWidgetIds.isEmpty()) {
            addDynamicPref(header, "", "No widgets found")
            return
        }

        for (appWidgetId in appWidgetIds) {
            val launcherWidget = LauncherWidgetStore.get(appWidgetId)
            val title = launcherWidget?.instance?.typeId ?: "Not configured"
            val summary = "id = $appWidgetId, variant = ${launcherWidget?.variantId ?: "automatic"}"
            addDynamicPref(header, title, summary, R.drawable.ic_widgets) {
                val items: MutableList<RunnableListIconItem?> = ArrayList(3)

                if (launcherWidget != null) {
                    items.add(
                        RunnableListIconItem(getString(R.string.pref_dashboard_widget_settings), R.drawable.ic_settings) {
                            WidgetInstanceSettingsActivity.startForLauncherWidget(requireContext(), appWidgetId, launcherWidget)
                        }
                    )
                }
                items.add(
                    RunnableListIconItem("Update", R.drawable.ic_refresh) {
                        LauncherWidgets.update(requireContext(), intArrayOf(appWidgetId))
                    }
                )
                items.add(
                    RunnableListIconItem(getString(R.string.delete), R.drawable.ic_delete_forever) {
                        appWidgetHost.deleteAppWidgetId(appWidgetId)
                        LauncherWidgetStore.delete(intArrayOf(appWidgetId))
                        reloadDashboardWidgets()
                    }
                )

                val adapter = SimpleIconListAdapter(context, items)

                MaterialAlertDialogBuilder(requireContext())
                    .setAdapter(adapter) { _: DialogInterface?, i: Int -> items[i]!!.action.run() }
                    .setTitle("$title / id=$appWidgetId")
                    .setNegativeButton(android.R.string.cancel) { _: DialogInterface?, _: Int -> }
                    .create()
                    .show()
            }
        }
    }

    private fun deleteWidgetsPrefs() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete widgets preferences")
            .setMessage(R.string.are_you_sure)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val widgetPreferenceStorage = WidgetPreferenceStorage()
                widgetPreferenceStorage.deleteWidgetsPrefs(requireContext())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAppWidgetsPrefs() {
        val widgetPreferenceStorage = WidgetPreferenceStorage()
        val appWidgetsPrefs = widgetPreferenceStorage.getAppWidgetsPrefs(requireContext())

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Saved widget preferences")
            .setMessage(appWidgetsPrefs)
            .setNeutralButton(android.R.string.copy) { _, _ ->
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Saved widget preferences", appWidgetsPrefs)
                clipboard.setPrimaryClip(clip)
            }
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    companion object {
        private const val PREF_DEBUG_WIDGETS_PREFS_SHOW = "pref_debug_widgets_prefs_show"
        private const val PREF_DEBUG_WIDGETS_PREFS_DELETE = "pref_debug_widgets_prefs_delete"
        private const val PREF_HEADER_LAUNCHER_WIDGETS_LEGACY = "pref_header_launcher_widgets_legacy"
        private const val PREF_HEADER_LAUNCHER_WIDGETS_DASHBOARD = "pref_header_launcher_widgets_dashboard"
    }
}
