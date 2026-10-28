package nodomain.freeyourgadget.gadgetbridge.activities.notifications.apps

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractPreferenceFragment
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractSettingsActivityV2
import nodomain.freeyourgadget.gadgetbridge.activities.app_specific_notifications.AppSpecificNotificationSettingsDetailActivity
import nodomain.freeyourgadget.gadgetbridge.adapter.AppSpecificNotificationSettingsAppListAdapter
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppConfig
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppFilterMatch
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppFilterMode
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppFilterSyntax
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppPrivacyOverride
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppRepository
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppRules
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppSendState
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppToggleOverride
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationDefaultAction
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationSettings
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType
import nodomain.freeyourgadget.gadgetbridge.util.DeviceIconUtils
import nodomain.freeyourgadget.gadgetbridge.util.NotificationUtils

class NotificationAppSettingsFragment : AbstractPreferenceFragment() {
    /**
     * The values that the screen displays next to the config.
     * [label] is null when the app is not installed.
     * [devices] indicates the devices that support app-specific notification settings.
     */
    private class Details(
        val defaultAction: NotificationDefaultAction,
        val global: NotificationSettings,
        val label: String?,
        val icon: Drawable?,
        val devices: List<GBDevice>,

        /**
         * True when a Pebble is paired, or when an app has a Pebble messages value that is not the default.
         */
        val pebbleMessages: Boolean,
    )

    private lateinit var packageName: String
    private var config: NotificationAppConfig? = null

    private lateinit var header: Preference
    private lateinit var sendState: ListPreference
    private lateinit var pebbleState: ListPreference
    private lateinit var filterMode: ListPreference
    private lateinit var filterMatch: ListPreference
    private lateinit var filterSyntax: ListPreference
    private lateinit var filterIgnoreCase: SwitchPreferenceCompat
    private lateinit var filterEntries: Preference
    private lateinit var replacements: Preference
    private lateinit var privacy: ListPreference
    private lateinit var whenScreenOn: ListPreference
    private lateinit var preferLongText: ListPreference
    private lateinit var blockIfOutOfNotificationTimes: ListPreference
    private lateinit var ignoreLowPriority: ListPreference
    private lateinit var forwardLocalOnly: SwitchPreferenceCompat
    private lateinit var devicesCategory: PreferenceCategory

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.notification_app_settings, rootKey)
        packageName = requireNotNull(
            requireActivity().intent.getStringExtra(NotificationAppSettingsActivity.EXTRA_PACKAGE_NAME)
        )

        header = findPreference(KEY_HEADER)!!
        sendState = findPreference(KEY_SEND_STATE)!!
        pebbleState = findPreference(KEY_PEBBLE_STATE)!!
        filterMode = findPreference(KEY_FILTER_MODE)!!
        filterMatch = findPreference(KEY_FILTER_MATCH)!!
        filterSyntax = findPreference(KEY_FILTER_SYNTAX)!!
        filterIgnoreCase = findPreference(KEY_FILTER_IGNORE_CASE)!!
        filterEntries = findPreference(KEY_FILTER_ENTRIES)!!
        replacements = findPreference(KEY_REPLACEMENTS)!!
        privacy = findPreference(KEY_PRIVACY)!!
        whenScreenOn = findPreference(KEY_WHEN_SCREEN_ON)!!
        preferLongText = findPreference(KEY_PREFER_LONG_TEXT)!!
        blockIfOutOfNotificationTimes = findPreference(KEY_BLOCK_IF_OUT_OF_NOTIFICATION_TIMES)!!
        ignoreLowPriority = findPreference(KEY_IGNORE_LOW_PRIORITY)!!
        forwardLocalOnly = findPreference(KEY_FORWARD_LOCAL_ONLY)!!
        devicesCategory = findPreference(KEY_DEVICES_CATEGORY)!!

        // Enabled when the config and the global values are loaded
        preferenceScreen.isEnabled = false
        devicesCategory.isVisible = false
        pebbleState.isVisible = false

        if (packageName == NotificationAppConfig.ALL_APPS) {
            preferenceScreen.title = getString(R.string.notification_apps_all_apps)
            for (key in listOf(KEY_HEADER, KEY_SEND_STATE, KEY_PEBBLE_STATE, KEY_OVERRIDES_CATEGORY)) {
                findPreference<Preference>(key)!!.isVisible = false
            }
        }

        setEntries(
            filterMode,
            NotificationAppFilterMode.entries,
            NotificationAppFilterMode.entries.map { getString(it.label) },
        )
        setEntries(
            filterMatch,
            NotificationAppFilterMatch.entries,
            NotificationAppFilterMatch.entries.map { getString(it.label) },
        )
        setEntries(
            filterSyntax,
            NotificationAppFilterSyntax.entries,
            NotificationAppFilterSyntax.entries.map { getString(it.label) },
        )

        onListChange(sendState) { config, value ->
            config.copy(sendState = NotificationAppSendState.valueOf(value))
        }
        onListChange(pebbleState) { config, value ->
            config.copy(pebbleState = NotificationAppSendState.valueOf(value))
        }
        onListChange(filterMode) { config, value ->
            config.copy(filter = config.filter.copy(mode = NotificationAppFilterMode.valueOf(value)))
        }
        onListChange(filterMatch) { config, value ->
            config.copy(filter = config.filter.copy(match = NotificationAppFilterMatch.valueOf(value)))
        }
        filterSyntax.setOnPreferenceChangeListener { _, newValue ->
            onFilterSyntaxChange(
                NotificationAppFilterSyntax.valueOf(
                    newValue as String
                )
            )
        }
        filterIgnoreCase.setOnPreferenceChangeListener { _, newValue ->
            NotificationAppEditor.update(
                packageName,
                ::rebindStored
            ) { it.copy(filter = it.filter.copy(ignoreCase = newValue as Boolean)) }
            true
        }
        filterEntries.setOnPreferenceClickListener {
            startActivity(
                Intent(requireContext(), NotificationAppFilterEntriesActivity::class.java)
                    .putExtra(NotificationAppFilterEntriesActivity.EXTRA_PACKAGE_NAME, packageName)
                    .putExtra(NotificationAppFilterEntriesActivity.EXTRA_TITLE, preferenceScreen.title)
            )
            true
        }
        replacements.setOnPreferenceClickListener {
            startActivity(
                Intent(requireContext(), NotificationAppReplacementsActivity::class.java)
                    .putExtra(NotificationAppReplacementsActivity.EXTRA_PACKAGE_NAME, packageName)
                    .putExtra(NotificationAppReplacementsActivity.EXTRA_TITLE, preferenceScreen.title)
            )
            true
        }
        onListChange(privacy) { config, value ->
            config.copy(overrides = config.overrides.copy(privacy = NotificationAppPrivacyOverride.valueOf(value)))
        }
        onListChange(whenScreenOn) { config, value ->
            config.copy(overrides = config.overrides.copy(whenScreenOn = NotificationAppToggleOverride.valueOf(value)))
        }
        onListChange(preferLongText) { config, value ->
            config.copy(overrides = config.overrides.copy(preferLongText = NotificationAppToggleOverride.valueOf(value)))
        }
        onListChange(blockIfOutOfNotificationTimes) { config, value ->
            config.copy(
                overrides = config.overrides.copy(
                    blockIfOutOfNotificationTimes = NotificationAppToggleOverride.valueOf(value)
                )
            )
        }
        onListChange(ignoreLowPriority) { config, value ->
            config.copy(
                overrides = config.overrides.copy(
                    ignoreLowPriority = NotificationAppToggleOverride.valueOf(value)
                )
            )
        }
        forwardLocalOnly.setOnPreferenceChangeListener { _, newValue ->
            NotificationAppEditor.update(packageName, ::rebindStored) {
                it.copy(forwardLocalOnly = newValue as Boolean)
            }
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_notification_app_settings, menu)
            }

            override fun onPrepareMenu(menu: Menu) {
                menu.findItem(R.id.notification_app_settings_reset).isVisible =
                    packageName != NotificationAppConfig.ALL_APPS && config?.isConfigured == true
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId != R.id.notification_app_settings_reset) {
                    return false
                }
                showResetDialog()
                return true
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            showDetails(withContext(Dispatchers.IO) { loadDetails(appContext) })
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                NotificationAppEditor.configs.collect { configs ->
                    bind(configs[packageName] ?: NotificationAppConfig(packageName))
                }
            }
        }
    }

    private fun loadDetails(context: Context): Details {
        val defaultAction = NotificationAppRepository.getDefaultAction()
        val global = NotificationSettings.fromPrefs(GBApplication.getPrefs())
        if (packageName == NotificationAppConfig.ALL_APPS) {
            return Details(defaultAction, global, null, null, emptyList(), false)
        }
        val label = NotificationUtils.getApplicationLabel(context, packageName)
        val icon = if (label != null) NotificationUtils.getAppIcon(context, packageName) else null
        val allDevices = GBApplication.app().deviceManager.devices
        val pebbleMessages = allDevices.any { it.type == DeviceType.PEBBLE } ||
            NotificationAppRepository.configs.value.values.any { it.pebbleState != NotificationAppSendState.DEFAULT }
        val devices = allDevices.filter { device ->
            val coordinator = device.deviceCoordinator
            coordinator.supportsNotificationVibrationPatterns(device) ||
                coordinator.supportsNotificationVibrationRepetitionPatterns(device) ||
                coordinator.supportsNotificationLedPatterns(device)
        }
        return Details(defaultAction, global, label, icon, devices, pebbleMessages)
    }

    private fun showDetails(details: Details) {
        val sendStates = listOf(
            getString(R.string.notification_apps_default_value, getString(details.defaultAction.label)),
            getString(R.string.always),
            getString(R.string.never),
        )
        setEntries(sendState, NotificationAppSendState.entries, sendStates)
        setEntries(pebbleState, NotificationAppSendState.entries, sendStates)
        setEntries(
            privacy,
            NotificationAppPrivacyOverride.entries,
            listOf(
                getString(R.string.notification_apps_default_value, getString(details.global.privacy.label)),
                getString(R.string.pref_message_privacy_mode_off),
                getString(R.string.pref_message_privacy_mode_complete),
                getString(R.string.pref_message_privacy_mode_bodyonly),
            ),
        )
        setToggleEntries(whenScreenOn, details.global.whenScreenOn)
        setToggleEntries(preferLongText, details.global.preferLongText)
        setToggleEntries(blockIfOutOfNotificationTimes, details.global.blockIfOutOfNotificationTimes)
        setToggleEntries(ignoreLowPriority, details.global.ignoreLowPriority)

        if (packageName != NotificationAppConfig.ALL_APPS) {
            val label = details.label ?: packageName
            preferenceScreen.title = label
            header.title = label
            header.summary = if (details.label == null) {
                packageName + "\n" + getString(R.string.notification_apps_not_installed)
            } else {
                packageName
            }
            header.icon = details.icon ?: requireContext().packageManager.defaultActivityIcon

            pebbleState.isVisible = details.pebbleMessages
            devicesCategory.isVisible = details.devices.isNotEmpty()
            for (device in details.devices) {
                devicesCategory.addPreference(
                    Preference(requireContext()).apply {
                        layoutResource = R.layout.preference_device
                        title = device.aliasOrName
                        icon = DeviceIconUtils.tile(
                            requireContext(),
                            device.deviceCoordinator.defaultIconResource,
                            device.isConnected
                        )
                        setOnPreferenceClickListener {
                            startDeviceSettings(device, label)
                            true
                        }
                    }
                )
            }
        }
        (requireActivity() as AbstractSettingsActivityV2).setActionBarTitle(preferenceScreen.title)
        preferenceScreen.isEnabled = true
    }

    private fun bind(config: NotificationAppConfig) {
        this.config = config
        sendState.value = config.sendState.name
        pebbleState.value = config.pebbleState.name

        val filter = config.filter
        filterMode.value = filter.mode.name
        filterMatch.value = filter.match.name
        filterSyntax.value = filter.syntax.name
        filterIgnoreCase.isChecked = filter.ignoreCase
        filterEntries.setTitle(filter.syntax.entriesTitle)
        filterEntries.summary = filter.entries.joinToString("\n").ifEmpty { getString(R.string.not_set) }
        val filterOn = filter.mode != NotificationAppFilterMode.OFF
        for (preference in listOf(filterMatch, filterSyntax, filterIgnoreCase, filterEntries)) {
            preference.isEnabled = filterOn
        }

        val count = config.replacements.size
        replacements.summary = if (count == 0) {
            getString(R.string.none)
        } else {
            resources.getQuantityString(R.plurals.notification_apps_replacement_count, count, count)
        }

        val overrides = config.overrides
        privacy.value = overrides.privacy.name
        whenScreenOn.value = overrides.whenScreenOn.name
        preferLongText.value = overrides.preferLongText.name
        blockIfOutOfNotificationTimes.value = overrides.blockIfOutOfNotificationTimes.name
        ignoreLowPriority.value = overrides.ignoreLowPriority.name
        forwardLocalOnly.isChecked = config.forwardLocalOnly

        requireActivity().invalidateMenu()
    }

    /**
     * Saves the result of [transform] with the selected value of [preference].
     */
    private fun onListChange(
        preference: ListPreference,
        transform: (NotificationAppConfig, String) -> NotificationAppConfig,
    ) {
        preference.setOnPreferenceChangeListener { _, newValue ->
            NotificationAppEditor.update(packageName, ::rebindStored) { transform(it, newValue as String) }
            true
        }
    }

    /**
     * Displays the stored config again, after a change that was not saved.
     */
    private fun rebindStored() {
        if (isAdded) {
            bind(NotificationAppRepository.configs.value[packageName] ?: NotificationAppConfig(packageName))
        }
    }

    private fun setEntries(preference: ListPreference, values: List<Enum<*>>, labels: List<String>) {
        preference.entries = labels.toTypedArray()
        preference.entryValues = values.map { it.name }.toTypedArray()
    }

    private fun setToggleEntries(preference: ListPreference, global: Boolean) {
        setEntries(
            preference,
            NotificationAppToggleOverride.entries,
            listOf(
                getString(
                    R.string.notification_apps_default_value,
                    getString(if (global) R.string.on else R.string.off)
                ),
                getString(R.string.on),
                getString(R.string.off),
            ),
        )
    }

    private fun onFilterSyntaxChange(syntax: NotificationAppFilterSyntax): Boolean {
        val error = if (syntax == NotificationAppFilterSyntax.REGEX) {
            config?.filter?.entries.orEmpty().firstNotNullOfOrNull { entry ->
                NotificationAppRules.regexError(entry)
                    ?.let { getString(R.string.notification_apps_invalid_regex_entry, entry, it) }
            }
        } else {
            null
        }
        if (error != null) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(syntax.entriesTitle)
                .setMessage(error)
                .setPositiveButton(R.string.ok, null)
                .show()
            return false
        }
        NotificationAppEditor.update(packageName, ::rebindStored) { it.copy(filter = it.filter.copy(syntax = syntax)) }
        return true
    }

    private fun showResetDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.soundcore_equalizer_reset_title)
            .setMessage(R.string.notification_apps_reset_confirm)
            .setPositiveButton(R.string.reset) { _, _ ->
                NotificationAppEditor.launch { NotificationAppRepository.delete(packageName) }
                requireActivity().finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun startDeviceSettings(device: GBDevice, label: String) {
        startActivity(
            Intent(requireContext(), AppSpecificNotificationSettingsDetailActivity::class.java)
                .putExtra(AppSpecificNotificationSettingsAppListAdapter.STRING_EXTRA_PACKAGE_NAME, packageName)
                .putExtra(AppSpecificNotificationSettingsAppListAdapter.STRING_EXTRA_PACKAGE_TITLE, label)
                .putExtra(GBDevice.EXTRA_DEVICE, device)
        )
    }

    companion object {
        private const val KEY_HEADER = "notif_app_header"
        private const val KEY_SEND_STATE = "notif_app_send_state"
        private const val KEY_PEBBLE_STATE = "notif_app_pebble_state"
        private const val KEY_FILTER_MODE = "notif_app_filter_mode"
        private const val KEY_FILTER_MATCH = "notif_app_filter_match"
        private const val KEY_FILTER_SYNTAX = "notif_app_filter_syntax"
        private const val KEY_FILTER_IGNORE_CASE = "notif_app_filter_ignore_case"
        private const val KEY_FILTER_ENTRIES = "notif_app_filter_entries"
        private const val KEY_REPLACEMENTS = "notif_app_replacements"
        private const val KEY_OVERRIDES_CATEGORY = "notif_app_overrides_category"
        private const val KEY_PRIVACY = "notif_app_privacy"
        private const val KEY_WHEN_SCREEN_ON = "notif_app_when_screen_on"
        private const val KEY_PREFER_LONG_TEXT = "notif_app_prefer_long_text"
        private const val KEY_BLOCK_IF_OUT_OF_NOTIFICATION_TIMES = "notif_app_block_if_out_of_notification_times"
        private const val KEY_IGNORE_LOW_PRIORITY = "notif_app_ignore_low_priority"
        private const val KEY_FORWARD_LOCAL_ONLY = "notif_app_forward_local_only"
        private const val KEY_DEVICES_CATEGORY = "notif_app_devices_category"
    }
}
