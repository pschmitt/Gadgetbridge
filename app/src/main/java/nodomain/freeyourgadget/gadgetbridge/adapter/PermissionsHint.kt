package nodomain.freeyourgadget.gadgetbridge.adapter

import android.content.Context
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.permissions.DevicePermission
import nodomain.freeyourgadget.gadgetbridge.util.permissions.MissingPermissions
import org.slf4j.LoggerFactory

object PermissionsHint {
    private val LOG = LoggerFactory.getLogger(PermissionsHint::class.java)

    /**
     * The cached result of [shouldShow] for each device address.
     */
    private val cache = mutableMapOf<String, Boolean>()

    /**
     * True if a recommended permission is missing, and it was not missing when the user last saw the permissions.
     */
    @JvmStatic
    fun shouldShow(context: Context, device: GBDevice): Boolean {
        return cache.getOrPut(device.address) {
            val missing = MissingPermissions.of(context, device)
                .filter { it.section == DevicePermission.Section.RECOMMENDED }
                .map { it.permission }
            val saved = MissingPermissions.getSaved(device)
            missing.isNotEmpty() && (saved == null || missing.any { it !in saved })
        }
    }

    @JvmStatic
    fun invalidate() {
        cache.clear()
    }

    @JvmStatic
    fun dismiss(context: Context, device: GBDevice) {
        LOG.debug("Dismissing permissions hint for {}", device.address)
        MissingPermissions.save(context, device)
        cache.remove(device.address)
    }
}
