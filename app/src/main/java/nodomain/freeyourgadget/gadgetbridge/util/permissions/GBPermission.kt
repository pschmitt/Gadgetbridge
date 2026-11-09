package nodomain.freeyourgadget.gadgetbridge.util.permissions

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.util.AndroidUtils
import nodomain.freeyourgadget.gadgetbridge.util.AutoStartUtils
import nodomain.freeyourgadget.gadgetbridge.util.PermissionsUtils
import org.slf4j.LoggerFactory

/**
 * A permission that Gadgetbridge asks the user to grant, with its rationale. A permission can require one or more
 * Android permissions.
 */
enum class GBPermission(
    @StringRes val title: Int,
    @StringRes val rationale: Int,
    val androidPermissions: List<String>,
) {
    BLUETOOTH(
        R.string.permission_bluetooth_title,
        R.string.permission_bluetooth_summary,
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
    ),
    BATTERY_OPTIMIZATION(
        R.string.permission_disable_doze_title,
        R.string.permission_disable_doze_summary,
        listOf(PermissionsUtils.CUSTOM_PERM_IGNORE_BATT_OPTIM)
    ),
    AUTOSTART(
        R.string.permission_autostart_title,
        R.string.permission_autostart_summary,
        listOf(PermissionsUtils.CUSTOM_PERM_AUTOSTART)
    ) {
        override fun isAvailable(context: Context): Boolean {
            return AutoStartUtils.getSettingsIntent(context) != null
        }
    },
    GADGETBRIDGE_NOTIFICATIONS(
        R.string.permission_post_notification_title,
        R.string.permission_post_notification_summary,
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    ),
    NOTIFICATION_ACCESS(
        R.string.menuitem_notifications,
        R.string.permission_notifications_summary,
        listOf(PermissionsUtils.CUSTOM_PERM_NOTIFICATION_LISTENER)
    ),
    CALLS(
        R.string.phone_calls,
        R.string.permission_calls_summary,
        listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.PROCESS_OUTGOING_CALLS
        )
    ),
    CALL_CONTROL(
        R.string.permission_answer_phone_calls_title,
        R.string.permission_answer_phone_calls_summary,
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add(Manifest.permission.ANSWER_PHONE_CALLS)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
                add(Manifest.permission.CALL_PHONE)
            }
        }
    ),
    CONTACTS(
        R.string.permission_contacts_title,
        R.string.permission_contacts_summary,
        listOf(Manifest.permission.READ_CONTACTS)
    ),
    CALENDAR(
        R.string.permission_calendar_title,
        R.string.permission_calendar_summary,
        listOf(Manifest.permission.READ_CALENDAR)
    ),
    SMS_REPLY(
        R.string.permission_send_sms_title,
        R.string.permission_send_sms_summary,
        listOf(Manifest.permission.SEND_SMS)
    ),
    DND_ACCESS(
        R.string.permission_manage_dnd_title,
        R.string.permission_manage_dnd_summary,
        listOf(PermissionsUtils.CUSTOM_PERM_NOTIFICATION_SERVICE)
    ),
    LOCATION(
        R.string.pref_header_location,
        R.string.permission_gps_sharing_summary,
        listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
    ),
    BACKGROUND_LOCATION(
        R.string.permission_background_location_title,
        R.string.permission_background_location_summary,
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
    ) {
        override fun request(activity: Activity) {
            // Android ignores the background location permission request before the foreground location is granted
            if (!LOCATION.isGranted(activity)) {
                LOCATION.request(activity)
                return
            }
            super.request(activity)
        }
    },
    INTERNET_HELPER(
        R.string.internet_helper_permission_title,
        R.string.internet_helper_permission_summary,
        listOf(PermissionsUtils.CUSTOM_PERM_INTERNET_HELPER)
    ) {
        override fun isAvailable(context: Context): Boolean {
            return !GBApplication.hasDirectInternetAccess() &&
                AndroidUtils.isPackageInstalled(PermissionsUtils.PACKAGE_INTERNET_HELPER)
        }
    },
    DISPLAY_OVER(
        R.string.permission_displayover_title,
        R.string.permission_displayover_summary,
        listOf(PermissionsUtils.CUSTOM_PERM_DISPLAY_OVER)
    ),
    ;

    /**
     * True if the permission is available to be granted on this phone.
     */
    open fun isAvailable(context: Context): Boolean {
        return androidPermissions.isNotEmpty()
    }

    /**
     * True if Gadgetbridge must request this permission alone, and not in one request with other permissions
     * (for special permissions that need a separate activity).
     */
    val isRequestedAlone: Boolean
        get() = androidPermissions.any { it in PermissionsUtils.specialPermissions }

    fun isGranted(context: Context): Boolean {
        return androidPermissions.all { PermissionsUtils.checkPermission(context, it) }
    }

    open fun request(activity: Activity) {
        val missing = missingAndroidPermissions(activity)
        when {
            missing.isEmpty() -> return
            isPermanentlyDenied(activity, missing) -> openAppInfo(activity)
            // PermissionsUtils shows the dialogs and settings pages of the special accesses
            missing.size == 1 -> {
                saveRequested(missing)
                PermissionsUtils.requestPermission(activity, missing[0])
            }

            else -> requestAndroidPermissions(activity, missing)
        }
    }

    private fun missingAndroidPermissions(context: Context): List<String> {
        return androidPermissions.filterNot { PermissionsUtils.checkPermission(context, it) }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(GBPermission::class.java)

        /**
         * The local broadcast for permissions that the user has granted after the devices connected.
         * The names of the permissions are sent in [EXTRA_PERMISSIONS].
         */
        const val ACTION_PERMISSIONS_GRANTED = "nodomain.freeyourgadget.gadgetbridge.action.PERMISSIONS_GRANTED"
        const val EXTRA_PERMISSIONS = "permissions"

        private const val PREF_REQUESTED = "permissions_requested"

        private val SPECIAL_ACCESSES = setOf(
            PermissionsUtils.CUSTOM_PERM_IGNORE_BATT_OPTIM,
            PermissionsUtils.CUSTOM_PERM_AUTOSTART,
            PermissionsUtils.CUSTOM_PERM_NOTIFICATION_LISTENER,
            PermissionsUtils.CUSTOM_PERM_NOTIFICATION_SERVICE,
            PermissionsUtils.CUSTOM_PERM_DISPLAY_OVER,
        )

        /**
         * Asks for the missing Android permissions for all [permissions] in one request. None of the permissions can
         * be [isRequestedAlone].
         */
        @JvmStatic
        fun requestTogether(activity: Activity, permissions: Collection<GBPermission>) {
            val missing = permissions.flatMap { it.missingAndroidPermissions(activity) }
            if (missing.isNotEmpty()) {
                requestAndroidPermissions(activity, missing)
            }
        }

        private fun requestAndroidPermissions(activity: Activity, missing: List<String>) {
            saveRequested(missing)
            ActivityCompat.requestPermissions(activity, missing.toTypedArray(), 0)
        }

        private fun saveRequested(permissions: List<String>) {
            val prefs = GBApplication.getPrefs()
            val requested =
                prefs.getStringSet(PREF_REQUESTED, emptySet()) + permissions.filterNot { it in SPECIAL_ACCESSES }
            LOG.debug("Requesting {}", permissions)
            prefs.preferences.edit { putStringSet(PREF_REQUESTED, requested) }
        }

        /**
         * True if Android will not show the dialog for the [missing] permissions anymore, after the user denied
         * it twice, or selected "don't ask again".
         */
        private fun isPermanentlyDenied(activity: Activity, missing: List<String>): Boolean {
            val requested = GBApplication.getPrefs().getStringSet(PREF_REQUESTED, emptySet())
            return missing.all { it in requested && !ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        }

        private fun openAppInfo(activity: Activity) {
            try {
                activity.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${activity.packageName}".toUri())
                )
            } catch (e: ActivityNotFoundException) {
                LOG.error("App info activity not found", e)
            }
        }
    }
}
