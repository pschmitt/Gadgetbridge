package nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.components

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsScope
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ListEntry
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ListEntryGroup
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice

fun DeviceSettingsScope.keepActivityDataOnDevice() {
    switchSetting(
        key = DeviceSettingsPreferenceConst.PREF_KEEP_ACTIVITY_DATA_ON_DEVICE,
        title = R.string.pref_title_do_not_archive_activity_data,
        summary = R.string.pref_summary_do_not_archive_activity_data,
        icon = R.drawable.ic_keep,
        defaultValue = false,
        connectedOnly = false,
    )
}

fun DeviceSettingsScope.fetchUnknownFiles() {
    switchSetting(
        key = DeviceSettingsPreferenceConst.PREF_FETCH_UNKNOWN_FILES,
        title = R.string.pref_fetch_unknown_files_title,
        summary = R.string.pref_fetch_unknown_files_summary,
        icon = R.drawable.ic_downloading,
        defaultValue = false,
        connectedOnly = false,
    )
}

fun DeviceSettingsScope.installUnsupportedFiles() {
    switchSetting(
        key = DeviceSettingsPreferenceConst.PREF_INSTALL_UNSUPPORTED_FILES,
        title = R.string.pref_install_unsupported_files_title,
        summary = R.string.pref_install_unsupported_files_summary,
        icon = R.drawable.ic_arrow_upload_progress,
        defaultValue = false,
        connectedOnly = false,
    )
}

fun DeviceSettingsScope.newSyncProtocol() {
    switchSetting(
        key = DeviceSettingsPreferenceConst.PREF_NEW_SYNC_PROTOCOL,
        title = R.string.pref_new_sync_protocol_title,
        summary = R.string.pref_new_sync_protocol_summary,
        icon = R.drawable.ic_refresh,
        defaultValue = false,
        connectedOnly = false,
    )
}

/**
 * Settings to replace the default features of the device with a list of features of [E].
 */
inline fun <reified E : Enum<E>> DeviceSettingsScope.overrideFeatures(device: GBDevice, defaultFeatures: Set<E>) {
    val (defaults, others) = enumValues<E>().partition { it in defaultFeatures }

    switchSetting(
        key = DeviceSettingsPreferenceConst.PREF_OVERRIDE_FEATURES_ENABLED,
        title = R.string.pref_override_features_title,
        summary = R.string.pref_override_features_summary,
        icon = R.drawable.ic_developer_mode,
        defaultValue = false,
        connectedOnly = false,
        onSharedPreferenceChanged = { device.sendDeviceUpdateIntent(GBApplication.getContext()) },
    )
    multiSelect(
        key = DeviceSettingsPreferenceConst.PREF_OVERRIDE_FEATURES_LIST,
        title = R.string.pref_test_features_title,
        summary = R.string.pref_test_features_summary,
        icon = R.drawable.ic_developer_mode,
        entryGroups = listOf(
            ListEntryGroup(
                R.string.pref_override_features_default,
                defaults.map { ListEntry.Text(it.name, it.name) }
            ),
            ListEntryGroup(
                R.string.pref_override_features_unsupported,
                others.map { ListEntry.Text(it.name, it.name) }),
        ),
        defaultValue = defaultFeatures.mapTo(mutableSetOf()) { it.name },
        dependency = DeviceSettingsPreferenceConst.PREF_OVERRIDE_FEATURES_ENABLED,
        connectedOnly = false,
        onSharedPreferenceChanged = { device.sendDeviceUpdateIntent(GBApplication.getContext()) },
    )
}

/**
 * The features for a device: the features from [overrideFeatures] when the override is enabled,
 * else [defaultFeatures].
 */
inline fun <reified E : Enum<E>> getOverriddenFeatures(device: GBDevice, defaultFeatures: Set<E>): Set<E> {
    val devicePrefs = GBApplication.getDevicePrefs(device)
    if (!devicePrefs.getBoolean(DeviceSettingsPreferenceConst.PREF_OVERRIDE_FEATURES_ENABLED, false)) {
        return defaultFeatures
    }
    val names = devicePrefs.getStringSet(
        DeviceSettingsPreferenceConst.PREF_OVERRIDE_FEATURES_LIST,
        defaultFeatures.mapTo(mutableSetOf()) { it.name },
    )
    return enumValues<E>().filterTo(mutableSetOf()) { it.name in names }
}
