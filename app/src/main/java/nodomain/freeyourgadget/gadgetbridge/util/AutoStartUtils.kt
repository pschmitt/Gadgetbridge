package nodomain.freeyourgadget.gadgetbridge.util

import android.app.Activity
import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import androidx.core.content.edit
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import org.slf4j.LoggerFactory
import java.util.Locale

object AutoStartUtils {
    private val LOG = LoggerFactory.getLogger(AutoStartUtils::class.java)

    private const val PREF_SETTINGS_OPENED = "autostart_settings_opened"

    private const val MIUI_OP_AUTO_START = 10008

    private val MIUI_AUTO_START = ComponentName(
        "com.miui.securitycenter",
        "com.miui.permcenter.autostart.AutoStartManagementActivity"
    )

    @JvmStatic
    fun getSettingsIntent(context: Context): Intent? {
        val intent = Intent().setComponent(MIUI_AUTO_START)
        val exported = context.packageManager.queryIntentActivities(intent, 0).any { it.activityInfo.exported }
        return if (exported) intent else null
    }

    /**
     * True if autostart is allowed. If the state is not available, true after the user opened the settings page
     * at least once.
     */
    @JvmStatic
    fun isAllowed(context: Context): Boolean {
        if (getSettingsIntent(context) == null) {
            return true
        }
        checkMiuiAutoStart(context)?.let { return it }
        return GBApplication.getPrefs().getBoolean(PREF_SETTINGS_OPENED, false)
    }

    private fun checkMiuiAutoStart(context: Context): Boolean? = try {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val checkOpNoThrow = AppOpsManager::class.java.getMethod(
            "checkOpNoThrow",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java
        )
        val mode = checkOpNoThrow.invoke(appOps, MIUI_OP_AUTO_START, Process.myUid(), context.packageName) as Int
        LOG.debug("MIUI autostart mode: {}", mode)
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        LOG.warn("Failed to get the MIUI autostart state", e)
        null
    }

    @JvmStatic
    fun showRequestDialog(activity: Activity) {
        val intent = getSettingsIntent(activity) ?: return
        val appName = activity.getString(R.string.app_name)
        MaterialAlertDialogBuilder(activity)
            .setMessage(activity.getString(R.string.permission_autostart, appName, activity.getString(R.string.ok)))
            .setPositiveButton(R.string.ok) { _, _ ->
                try {
                    activity.startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    LOG.error("Autostart settings activity {} not found", intent.component, e)
                } catch (e: SecurityException) {
                    LOG.error("Not allowed to start autostart settings activity {}", intent.component, e)
                } finally {
                    GBApplication.getPrefs().preferences.edit {
                        putBoolean(PREF_SETTINGS_OPENED, true)
                    }
                }
            }
            .setNeutralButton(R.string.help) { _, _ ->
                AndroidUtils.openWebsite("https://dontkillmyapp.com/" + Build.MANUFACTURER.lowercase(Locale.ROOT))
            }
            .setNegativeButton(R.string.dismiss, null)
            .show()
    }
}
