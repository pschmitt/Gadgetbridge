package nodomain.freeyourgadget.gadgetbridge.activities.dashboard

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractPreferenceFragment
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.SettingsRenderHost
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSetting
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingRenderer
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsRefreshHandle
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ListEntry
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.Prefs
import nodomain.freeyourgadget.gadgetbridge.widgets.GBWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetGrid
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetLayoutStore
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetRegistry
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetVariant
import nodomain.freeyourgadget.gadgetbridge.widgets.launcher.LauncherWidgetRenderer
import nodomain.freeyourgadget.gadgetbridge.widgets.launcher.LauncherWidgetStore
import nodomain.freeyourgadget.gadgetbridge.widgets.variant
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Renders the settings of one widget instance, on the dashboard or on the launcher: the common ones (device
 * selection, variant, title override), followed by the widget's own [GBWidget.settings].
 */
class WidgetInstanceSettingsFragment : AbstractPreferenceFragment(), SettingsRenderHost {
    private var refreshHandle: DeviceSettingsRefreshHandle? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val instanceId = requireArguments().getString(ARG_INSTANCE_ID) ?: return
        preferenceManager.sharedPreferencesName = "widgetsettings_$instanceId"
        preferenceScreen = preferenceManager.createPreferenceScreen(requireContext())
        renderSettings()
    }

    private fun renderSettings() {
        val instanceId = requireArguments().getString(ARG_INSTANCE_ID) ?: return
        val typeId = requireArguments().getString(ARG_TYPE_ID) ?: return
        val columns = requireArguments().getInt(ARG_COLUMNS, WidgetGrid.BASE_COLUMNS)
        val appWidgetId = requireArguments().getInt(ARG_APP_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val widget = WidgetRegistry.byId(typeId)
        if (widget == null) {
            LOG.error("Widget type {} is not available", typeId)
            requireActivity().finish()
            return
        }
        val launcherWidget = LauncherWidgetStore.get(appWidgetId)
        val placement = WidgetLayoutStore.layout(columns).find { it.instance.instanceId == instanceId }
        val variant = if (launcherWidget != null) {
            LauncherWidgetRenderer.variantFor(requireContext(), appWidgetId, widget, launcherWidget)
        } else {
            widget.variant(placement?.variantId)
        }

        refreshHandle?.cleanup()
        preferenceScreen.removeAll()

        val prefs = Prefs(GBApplication.getWidgetSharedPrefs(instanceId))
        val items = mutableListOf<DeviceSetting>()
        items += commonSettings(widget, launcherWidget != null)
        widget.settings(requireContext(), variant)?.let {
            items += it.items
        }

        refreshHandle = DeviceSettingRenderer.render(items, preferenceScreen, prefs, this)

        val variantPref = findPreference<Preference>(KEY_VARIANT) ?: return
        if (launcherWidget != null) {
            variantPref.summary = launcherWidget.variantId?.let { getString(widget.variant(it).name) }
                ?: getString(R.string.automatic)
            variantPref.setOnPreferenceClickListener {
                showVariantSheet(widget, launcherWidget.variantId, true) { selected ->
                    LauncherWidgetStore.setVariant(appWidgetId, selected?.id)
                    renderSettings()
                }
                true
            }
            return
        }
        if (placement == null) {
            variantPref.isVisible = false
            return
        }
        variantPref.summary = getString(widget.variant(placement.variantId).name)
        variantPref.setOnPreferenceClickListener {
            showVariantSheet(widget, placement.variantId, false) { selected ->
                selected ?: return@showVariantSheet
                WidgetLayoutStore.setVariant(columns, instanceId, selected.id)
                renderSettings()
            }
            true
        }
    }

    /**
     * Shows a preview of each variant of [widget] in a bottom sheet, as wide as the screen so that the previews size
     * match the dashboard. With [showAutomatic], a card for the automatic variant is above the previews. It is selected
     * when [selectedVariantId] is null, and a tap on it selects a null variant.
     */
    @SuppressLint("InflateParams")  // The bottom sheet sets the layout params of its content view
    private fun showVariantSheet(
        widget: GBWidget<*>,
        selectedVariantId: String?,
        showAutomatic: Boolean,
        onSelect: (WidgetVariant?) -> Unit,
    ) {
        val sheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dashboard_widget_previews, null)
        showVariantPreviews(
            view.findViewById(R.id.dashboard_widget_previews),
            widget,
            selectedVariantId,
            onClick = { variant ->
                onSelect(variant)
                sheet.dismiss()
            },
            onAutomatic = if (showAutomatic) {
                {
                    onSelect(null)
                    sheet.dismiss()
                }
            } else {
                null
            },
            automaticSelected = selectedVariantId == null,
        )
        sheet.setContentView(view)
        sheet.behavior.maxWidth = resources.displayMetrics.widthPixels
        sheet.behavior.skipCollapsed = true
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.show()
    }

    override fun onDestroyView() {
        refreshHandle?.cleanup()
        refreshHandle = null
        super.onDestroyView()
    }

    private fun commonSettings(widget: GBWidget<*>, launcher: Boolean): List<DeviceSetting> = deviceSettings {
        list(
            key = WidgetConfig.KEY_DEVICES_MODE,
            title = R.string.bottom_nav_devices,
            icon = R.drawable.ic_devices_wearables,
            entries = listOf(
                ListEntry.Res(WidgetConfig.DEVICES_MODE_INHERIT, R.string.pref_widget_devices_mode_inherit),
                ListEntry.Res(WidgetConfig.DEVICES_MODE_ALL, R.string.pref_dashboard_all_devices_title),
                ListEntry.Res(WidgetConfig.DEVICES_MODE_SELECTED, R.string.pref_auto_export_gpx_selected_devices),
            ),
            defaultValue = WidgetConfig.DEVICES_MODE_INHERIT,
        )
        multiSelect(
            key = WidgetConfig.KEY_DEVICES,
            title = R.string.pref_dashboard_select_devices_title,
            icon = R.drawable.ic_devices_other,
            entriesProvider = { prefs ->
                val selected = prefs.getStringSet(WidgetConfig.KEY_DEVICES, emptySet())
                GBApplication.app().deviceManager.devices
                    .filter { widget.isSupportedBy(it) }
                    .sortedWith(
                        compareBy<GBDevice> { it.address !in selected }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.aliasOrName }
                    )
                    .map { ListEntry.Text(it.address, it.aliasOrName) }
            },
            visibleWhen = {
                it.getString(
                    WidgetConfig.KEY_DEVICES_MODE,
                    WidgetConfig.DEVICES_MODE_INHERIT
                ) == WidgetConfig.DEVICES_MODE_SELECTED
            },
        )
        if (widget.variants.size > 1) {
            action(
                key = KEY_VARIANT,
                title = R.string.pref_widget_variant_title,
                icon = R.drawable.ic_font_size,
                connectedOnly = false,
            )
        }
        text(
            key = WidgetConfig.KEY_TITLE,
            title = R.string.pref_widget_title_title,
            summary = R.string.pref_widget_title_summary,
            icon = R.drawable.ic_label_24px,
            defaultValue = "",
            inputType = InputType.TYPE_CLASS_TEXT,
            connectedOnly = false,
        )
        if (launcher) {
            switchSetting(
                key = LauncherWidgetRenderer.KEY_CARD,
                title = R.string.pref_launcher_widget_card_title,
                icon = R.drawable.ic_widgets,
                defaultValue = true,
                connectedOnly = false,
            )
            list(
                key = LauncherWidgetRenderer.KEY_THEME,
                title = R.string.pref_title_theme,
                icon = R.drawable.ic_paint,
                entries = listOf(
                    ListEntry.Res(LauncherWidgetRenderer.THEME_APP, R.string.pref_default),
                    ListEntry.Res(LauncherWidgetRenderer.THEME_SYSTEM, R.string.pref_theme_system),
                    ListEntry.Res(LauncherWidgetRenderer.THEME_LIGHT, R.string.pref_theme_light),
                    ListEntry.Res(LauncherWidgetRenderer.THEME_DARK, R.string.pref_theme_dark),
                ),
                defaultValue = LauncherWidgetRenderer.THEME_APP,
                connectedOnly = false,
            )
        }
    }.items

    //
    // SettingsRenderHost
    //
    // findPreference(CharSequence) and getContext() are already provided by
    // PreferenceFragmentCompat/Fragment with compatible signatures.
    //

    override fun addPreferenceHandlerFor(preferenceKey: String) {
        // No device to send configuration to.
    }

    override fun addPreferenceHandlerFor(preferenceKey: String, extraListener: Preference.OnPreferenceChangeListener) {
        findPreference<Preference>(preferenceKey)?.onPreferenceChangeListener = extraListener
    }

    override fun notifyPreferenceChanged(preferenceKey: String) {
        // No device to notify - the renderer already re-evaluates visibleWhen/entriesProvider
        // itself after every change.
    }

    override fun setInputTypeFor(preferenceKey: String, editTypeFlags: Int) {
        (findPreference<Preference>(preferenceKey) as? EditTextPreference)?.setOnBindEditTextListener { editText: EditText ->
            editText.inputType = editTypeFlags
        }
    }

    override fun navigateToScreen(screen: PreferenceScreen) {
        onNavigateToScreen(screen)
    }

    override fun addXmlPreferences(resId: Int) {
        addPreferencesFromResource(resId)
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(WidgetInstanceSettingsFragment::class.java)

        const val ARG_INSTANCE_ID = "widget_instance_id"
        const val ARG_TYPE_ID = "widget_type_id"
        const val ARG_COLUMNS = "widget_columns"
        const val ARG_APP_WIDGET_ID = "widget_app_widget_id"
        private const val KEY_VARIANT = "widget_variant"

        fun newInstance(
            instanceId: String,
            typeId: String,
            columns: Int,
            appWidgetId: Int
        ): WidgetInstanceSettingsFragment {
            val fragment = WidgetInstanceSettingsFragment()
            val args = Bundle()
            args.putString(ARG_INSTANCE_ID, instanceId)
            args.putString(ARG_TYPE_ID, typeId)
            args.putInt(ARG_COLUMNS, columns)
            args.putInt(ARG_APP_WIDGET_ID, appWidgetId)
            fragment.arguments = args
            return fragment
        }
    }
}
