package nodomain.freeyourgadget.gadgetbridge.util.permissions

import android.content.Context
import androidx.core.content.edit
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import org.slf4j.LoggerFactory

/**
 * Helper to track the permissions of a device that are not yet granted, and the set
 * of permissions that the user saw last.
 */
object MissingPermissions {
    private val LOG = LoggerFactory.getLogger(MissingPermissions::class.java)

    private const val PREF_SAVED = "permissions_saved_missing"

    @JvmStatic
    fun of(context: Context, device: GBDevice): List<DevicePermission> {
        return device.deviceCoordinator.getPermissions(device).filterNot { it.permission.isGranted(context) }
    }

    /**
     * The missing permissions that the user last saw when they closed the permission screen or dismissed the hint.
     * Null if they never did.
     */
    @JvmStatic
    fun getSaved(device: GBDevice): Set<GBPermission>? {
        val prefs = GBApplication.getDeviceSpecificSharedPrefs(device.address)
        if (!prefs.contains(PREF_SAVED)) {
            return null
        }
        val names = prefs.getStringSet(PREF_SAVED, emptySet()).orEmpty()
        return GBPermission.entries.filter { it.name in names }.toSet()
    }

    @JvmStatic
    fun save(context: Context, device: GBDevice) {
        val missing = of(context, device).map { it.permission.name }.toSet()
        LOG.debug("Saving missing permissions for {}: {}", device.address, missing)
        GBApplication.getDeviceSpecificSharedPrefs(device.address).edit {
            putStringSet(PREF_SAVED, missing)
        }
    }
}
