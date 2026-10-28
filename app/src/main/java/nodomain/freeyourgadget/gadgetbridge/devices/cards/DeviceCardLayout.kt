package nodomain.freeyourgadget.gadgetbridge.devices.cards

import androidx.core.content.edit
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import org.slf4j.LoggerFactory

/**
 * The user order and the removed entries of a device card section, persisted per device.
 * Entries that are not in the persisted order are new, and display at their default position.
 * Optional entries are displayed only when they are explicitly added by the user.
 * This avoids the need for preference migrations when devices get new entries, or new capabilities.
 */
enum class DeviceCardLayout(private val prefOrder: String, private val prefRemoved: String) {
    ITEMS("device_card_items_order", "device_card_items_removed"),
    QUICK_CONTROLS("device_card_quick_controls_order", "device_card_quick_controls_removed"),
    ;

    /**
     * Returns the entries of [defaults] that the user did not remove, in the user order.
     */
    fun <T : DeviceCardEntry> apply(device: GBDevice, defaults: List<T>): List<T> {
        val prefs = GBApplication.getDeviceSpecificSharedPrefs(device.address)
        val order = split(prefs.getString(prefOrder, null))
        val removed = split(prefs.getString(prefRemoved, null)).toSet()

        val byId = defaults.filter { it.id !in removed }.associateBy { it.id }
        val ordered = order.mapNotNull { byId[it] }.toMutableList()
        val orderedIds = order.toSet()

        for ((index, item) in defaults.withIndex()) {
            if (item.id in removed || item.id in orderedIds || item.optional) {
                continue
            }
            val previous = defaults.subList(0, index).lastOrNull { prev -> ordered.any { it.id == prev.id } }
            val position = if (previous == null) 0 else ordered.indexOfFirst { it.id == previous.id } + 1
            ordered.add(position, item)
        }

        return ordered
    }

    /**
     * Returns the entries of [defaults] that the user removed.
     */
    fun <T : DeviceCardEntry> removed(device: GBDevice, defaults: List<T>): List<T> {
        val prefs = GBApplication.getDeviceSpecificSharedPrefs(device.address)
        val order = split(prefs.getString(prefOrder, null)).toSet()
        val removed = split(prefs.getString(prefRemoved, null)).toSet()
        return defaults.filter { it.id in removed || (it.optional && it.id !in order) }
    }

    fun save(device: GBDevice, ordered: List<DeviceCardEntry>, removed: List<DeviceCardEntry>) {
        val order = ordered.joinToString(",") { it.id }
        val removedIds = removed.filterNot { it.optional }.joinToString(",") { it.id }
        LOG.debug("Saving device card layout {} for {}: order={}, removed={}", name, device.address, order, removedIds)
        GBApplication.getDeviceSpecificSharedPrefs(device.address).edit {
            putString(prefOrder, order)
            putString(prefRemoved, removedIds)
        }
    }

    fun reset(device: GBDevice) {
        LOG.debug("Resetting device card layout {} for {}", name, device.address)
        GBApplication.getDeviceSpecificSharedPrefs(device.address).edit {
            remove(prefOrder)
            remove(prefRemoved)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(DeviceCardLayout::class.java)

        private const val SHOW_NEVER = "never"
        private const val SHOW_CONNECTED = "connected"

        /**
         * Whether the device card icon row is shown for the current state of the device.
         */
        @JvmStatic
        fun isShown(device: GBDevice): Boolean {
            val mode = GBApplication.getDevicePrefs(device)
                .getString(DeviceSettingsPreferenceConst.PREF_DEVICE_CARD_SHOW_ITEMS, "always")
            return when (mode) {
                SHOW_NEVER -> false
                SHOW_CONNECTED -> device.isInitialized
                else -> true
            }
        }

        private fun split(value: String?): List<String> = value?.split(",")
            ?.filter { it.isNotEmpty() }
            ?: emptyList()
    }
}
