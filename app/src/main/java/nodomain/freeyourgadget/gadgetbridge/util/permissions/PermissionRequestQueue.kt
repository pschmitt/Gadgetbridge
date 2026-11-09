package nodomain.freeyourgadget.gadgetbridge.util.permissions

import android.app.Activity

/**
 * Requests several permissions in a row. Permissions that must be requested alone open their own dialog or settings
 * page, and [resume] continues with the next one. All remaining permissions are requested in a single request at the
 * end.
 */
class PermissionRequestQueue {
    private val pending = ArrayDeque<GBPermission>()

    fun start(activity: Activity, permissions: Collection<GBPermission>) {
        pending.clear()
        pending.addAll(permissions)
        resume(activity)
    }

    /**
     * Requests the next pending permissions.
     */
    fun resume(activity: Activity) {
        pending.removeAll { it.isGranted(activity) }
        if (pending.isEmpty()) {
            return
        }
        val alone = pending.firstOrNull { it.isRequestedAlone }
        if (alone != null) {
            pending.remove(alone)
            alone.request(activity)
            return
        }
        val together = pending.toList()
        pending.clear()
        GBPermission.requestTogether(activity, together)
    }
}
