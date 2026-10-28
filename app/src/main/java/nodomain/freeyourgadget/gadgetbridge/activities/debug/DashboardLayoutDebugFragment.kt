package nodomain.freeyourgadget.gadgetbridge.activities.debug

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.WidgetInstanceSettingsActivity
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetGrid
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetLayoutStore
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetPlacement
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetRegistry

class DashboardLayoutDebugFragment : AbstractDebugFragment() {
    private var columns = WidgetGrid.BASE_COLUMNS

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.debug_preferences_dashboard_layout, rootKey)

        columns = requireArguments().getInt(ARG_COLUMNS)
        preferenceScreen?.title = "$columns columns"

        val isBase = columns == WidgetGrid.BASE_COLUMNS
        val title = if (isBase) "Reset layout" else "Delete layout"
        findPreference<Preference>(PREF_DEBUG_DASHBOARD_LAYOUT_RESET)!!.apply {
            this.title = title
            summary = if (isBase) {
                "Migrate the legacy config again for this layout"
            } else {
                "Derive this layout from the base layout again"
            }
            setIcon(if (isBase) R.drawable.ic_settings_backup_restore else R.drawable.ic_delete)
        }
        onClick(PREF_DEBUG_DASHBOARD_LAYOUT_RESET) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setMessage(R.string.are_you_sure)
                .setPositiveButton(if (isBase) R.string.reset else R.string.delete) { _, _ ->
                    WidgetLayoutStore.resetLayout(columns)
                    if (isBase) {
                        reloadWidgets()
                    } else {
                        parentFragmentManager.popBackStack()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        reloadWidgets()
    }

    private fun reloadWidgets() {
        val header = findPreference<PreferenceCategory>(PREF_DEBUG_DASHBOARD_LAYOUT_HEADER_WIDGETS)!!
        removeDynamicPrefs(header)

        val placements = WidgetLayoutStore.storedLayouts()[columns].orEmpty()
        header.title = "Widgets (${placements.size})"
        for (placement in placements) {
            val widget = WidgetRegistry.byId(placement.instance.typeId)
            val title = widget?.displayName(requireContext()) ?: "${placement.instance.typeId} (unknown)"
            val summary = "${placement.instance.instanceId} · ${placement.variantId} · x=${placement.x}"
            addDynamicPref(header, title, summary, widget?.icon ?: R.drawable.ic_widgets) {
                showInstance(placement)
            }
        }
    }

    private fun showInstance(placement: WidgetPlacement) {
        val instanceId = placement.instance.instanceId
        val settings = GBApplication.getWidgetSharedPrefs(instanceId)?.all.orEmpty().toSortedMap()
        val message = settings.entries.joinToString("\n") { "${it.key} = ${it.value}" }.ifEmpty { "No settings" }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(instanceId)
            .setMessage("type = ${placement.instance.typeId}\nvariant = ${placement.variantId}\nx = ${placement.x}\n\n$message")
            .setPositiveButton(R.string.ok, null)
            .setNeutralButton(R.string.menuitem_settings) { _, _ ->
                val instance = WidgetLayoutStore.instanceForEdit(columns, instanceId) ?: return@setNeutralButton
                startActivity(WidgetInstanceSettingsActivity.createIntent(requireContext(), columns, instance))
            }
            .show()
    }

    companion object {
        private const val ARG_COLUMNS = "columns"

        private const val PREF_DEBUG_DASHBOARD_LAYOUT_RESET = "pref_debug_dashboard_layout_reset"
        private const val PREF_DEBUG_DASHBOARD_LAYOUT_HEADER_WIDGETS = "pref_debug_dashboard_layout_header_widgets"

        fun newInstance(columns: Int) = DashboardLayoutDebugFragment().apply {
            arguments = Bundle().apply {
                putInt(ARG_COLUMNS, columns)
            }
        }
    }
}
