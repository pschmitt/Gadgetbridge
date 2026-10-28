package nodomain.freeyourgadget.gadgetbridge.devices.cards

import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice

/**
 * An entry of the device card that the user can sort, remove and add back.
 */
sealed interface DeviceCardEntry {
    /**
     * A stable identifier, used to persist the order and the removed entries.
     */
    val id: String

    /**
     * Whether the entry is displayed for the current device state.
     */
    fun isVisible(device: GBDevice): Boolean

    /**
     * Whether the entry is not present by default - the user must add it.
     */
    val optional: Boolean
        get() = false
}
