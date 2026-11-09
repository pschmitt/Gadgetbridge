package nodomain.freeyourgadget.gadgetbridge.util.permissions

import androidx.annotation.StringRes

/**
 * A permission that a device needs, with its section and the rationale for the specific device.
 */
data class DevicePermission @JvmOverloads constructor(
    val permission: GBPermission,
    val section: Section,
    @StringRes val rationale: Int = permission.rationale,
) {
    enum class Section {
        RECOMMENDED,
        OPTIONAL,
    }
}
