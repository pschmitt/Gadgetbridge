package nodomain.freeyourgadget.gadgetbridge.activities.devicesettings

import android.content.Context
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.TwoStatePreference
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_DEVICE_ACTION_FELL_SLEEP_SELECTIONS
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_DEVICE_ACTION_START_NON_WEAR_SELECTIONS
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_DEVICE_ACTION_WOKE_UP_SELECTIONS
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_DEVICE_INTENTS
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_PHONE_SILENT_MODE
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SYNC_BIRTHDAYS
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SYNC_CALENDAR
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_WORKOUT_SEND_GPS_TO_BAND
import nodomain.freeyourgadget.gadgetbridge.activities.permissions.DevicePermissionsActivity
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.CompanionDeviceUtil
import nodomain.freeyourgadget.gadgetbridge.util.permissions.GBPermission

/**
 * The hints above a device setting that needs a permission that is not granted.
 * The hint opens the device permission screen.
 */
class SettingPermissionHints(
    private val settingsFragment: PreferenceFragmentCompat,
    private val device: GBDevice,
) {
    private val hints = mutableMapOf<Requirement, Preference>()

    init {
        val context = settingsFragment.requireContext()
        for (requirement in REQUIREMENTS) {
            val setting = settingsFragment.findPreference<Preference>(requirement.key) ?: continue
            val hint = Preference(context).apply {
                key = "${requirement.key}_permission_hint"
                isPersistent = false
                setIcon(R.drawable.ic_warning)
                setOnPreferenceClickListener {
                    settingsFragment.startActivity(
                        DevicePermissionsActivity.newIntent(
                            context,
                            device,
                            setting.title?.toString(),
                            requirement.permissions
                        )
                    )
                    true
                }
            }
            insertAbove(setting, hint)
            hints[requirement] = hint
        }
        update()
    }

    /**
     * Shows the hints for the settings that need a missing permission, and disables the settings that cannot work
     * without it.
     */
    fun update() {
        val context = settingsFragment.context ?: return
        for ((requirement, hint) in hints) {
            val setting = settingsFragment.findPreference<Preference>(requirement.key) ?: continue
            val missing = requirement.permissions.filter { it.isAvailable(context) && !it.isGranted(context) }
            if (requirement.disableWhenMissing) {
                setting.isEnabled = missing.isEmpty()
            }
            hint.isVisible = missing.isNotEmpty() && requirement.isNeeded(context, setting)
            hint.title = context.resources.getQuantityString(
                R.plurals.permission_hint_missing,
                missing.size,
                missing.joinToString { context.getString(it.title) }
            )
        }
    }

    private fun insertAbove(setting: Preference, hint: Preference) {
        val parent = setting.parent ?: return
        val order = setting.order
        if (order != Preference.DEFAULT_ORDER) {
            for (i in 0 until parent.preferenceCount) {
                val preference = parent.getPreference(i)
                if (preference.order >= order && preference.order != Preference.DEFAULT_ORDER) {
                    preference.order += 1
                }
            }
            hint.order = order
        }
        parent.addPreference(hint)
    }

    private data class Requirement(
        val key: String,
        val permissions: List<GBPermission>,
        /**
         * True if the setting stays disabled while a permission is missing.
         */
        val disableWhenMissing: Boolean = false,
        /**
         * True if the current value of the setting needs the permissions.
         */
        val isNeeded: (Context, Preference) -> Boolean = { _, _ -> true },
    )

    companion object {
        private val DND_ACTIONS = listOf(
            R.string.pref_device_action_dnd_off_value,
            R.string.pref_device_action_dnd_priority_value,
            R.string.pref_device_action_dnd_alarms_value,
            R.string.pref_device_action_dnd_on_value,
        )

        private fun hasDndAction(context: Context, setting: Preference): Boolean {
            val actions = (setting as? MultiSelectListPreference)?.values.orEmpty()
            return DND_ACTIONS.any { context.getString(it) in actions }
        }

        private fun isChecked(setting: Preference): Boolean {
            return (setting as? TwoStatePreference)?.isChecked == true
        }

        private val REQUIREMENTS = listOf(
            Requirement(PREF_SYNC_CALENDAR, listOf(GBPermission.CALENDAR), disableWhenMissing = true),
            Requirement(PREF_SYNC_BIRTHDAYS, listOf(GBPermission.CONTACTS), disableWhenMissing = true),
            Requirement(
                PREF_DEVICE_ACTION_FELL_SLEEP_SELECTIONS,
                listOf(GBPermission.DND_ACCESS),
                isNeeded = ::hasDndAction
            ),
            Requirement(
                PREF_DEVICE_ACTION_WOKE_UP_SELECTIONS,
                listOf(GBPermission.DND_ACCESS),
                isNeeded = ::hasDndAction
            ),
            Requirement(
                PREF_DEVICE_ACTION_START_NON_WEAR_SELECTIONS,
                listOf(GBPermission.DND_ACCESS),
                isNeeded = ::hasDndAction
            ),
            // Only a change to or from silent mode needs DND access
            Requirement(PREF_PHONE_SILENT_MODE, listOf(GBPermission.DND_ACCESS)) { _, setting ->
                (setting as? ListPreference)?.value != "normal_vibrate"
            },
            // With a companion association, Android lets Gadgetbridge start activities from the background
            Requirement(PREF_DEVICE_INTENTS, listOf(GBPermission.DISPLAY_OVER)) { context, setting ->
                isChecked(setting) && CompanionDeviceUtil.getCompanionAssociations(context).isEmpty()
            },
            Requirement(
                PREF_WORKOUT_SEND_GPS_TO_BAND,
                listOf(GBPermission.LOCATION, GBPermission.BACKGROUND_LOCATION)
            ) { _, setting ->
                isChecked(setting)
            },
        )
    }
}
