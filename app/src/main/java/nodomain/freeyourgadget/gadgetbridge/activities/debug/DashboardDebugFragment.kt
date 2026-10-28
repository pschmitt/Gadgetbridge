package nodomain.freeyourgadget.gadgetbridge.activities.debug

import android.os.Bundle
import androidx.preference.PreferenceCategory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetGrid
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetLayoutStore

class DashboardDebugFragment : AbstractDebugFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.debug_preferences_dashboard, rootKey)

        onClick(PREF_DEBUG_DASHBOARD_RESET) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Reset dashboard")
                .setMessage(R.string.are_you_sure)
                .setPositiveButton(R.string.reset) { _, _ ->
                    WidgetLayoutStore.reset()
                    reloadLayouts()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
        onClick(PREF_DEBUG_DASHBOARD_ADD_ALL_VARIANTS) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Add every widget variant")
                .setMessage(R.string.are_you_sure)
                .setPositiveButton(R.string.yes) { _, _ ->
                    WidgetLayoutStore.addAllVariants()
                    reloadLayouts()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        reloadLayouts()
    }

    private fun reloadLayouts() {
        val header = findPreference<PreferenceCategory>(PREF_DEBUG_DASHBOARD_HEADER_LAYOUTS)!!
        removeDynamicPrefs(header)

        for ((columns, placements) in WidgetLayoutStore.storedLayouts().toSortedMap()) {
            val base = if (columns == WidgetGrid.BASE_COLUMNS) " (base)" else ""
            addDynamicPref(header, "$columns columns$base", "${placements.size} widgets", R.drawable.ic_dashboard) {
                goTo(DashboardLayoutDebugFragment.newInstance(columns))
            }
        }
    }

    companion object {
        private const val PREF_DEBUG_DASHBOARD_RESET = "pref_debug_dashboard_reset"
        private const val PREF_DEBUG_DASHBOARD_ADD_ALL_VARIANTS = "pref_debug_dashboard_add_all_variants"
        private const val PREF_DEBUG_DASHBOARD_HEADER_LAYOUTS = "pref_debug_dashboard_header_layouts"
    }
}
