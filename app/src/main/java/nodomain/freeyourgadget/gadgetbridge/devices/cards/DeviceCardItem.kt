package nodomain.freeyourgadget.gadgetbridge.devices.cards

import android.content.Context
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCardAction
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice

/**
 * A single item rendered in the device list card's icon row.
 */
sealed interface DeviceCardItem : DeviceCardEntry

/**
 * A clickable icon that triggers an action.
 *
 * @param busyIndicator show a progress bar under the icon while [GBDevice.isBusy] is true.
 */
data class ActionCardItem(
    val action: DeviceCardAction,
    val busyIndicator: Boolean = false,
    override val id: String = action.id,
    val visible: (GBDevice) -> Boolean = action::isVisible,
    override val optional: Boolean = false,
) : DeviceCardItem {
    override fun isVisible(device: GBDevice) = visible(device)
}

/**
 * One battery slot. Displays the live values from the corresponding battery in [GBDevice].
 */
data class BatteryCardItem(val batteryIndex: Int) : DeviceCardItem {
    override val id: String
        get() = "battery_$batteryIndex"

    override fun isVisible(device: GBDevice) = true
}

/**
 * A clickable color picker.
 */
data class ColorCardItem(
    override val id: String,
    val visible: (GBDevice) -> Boolean,
    val color: (GBDevice) -> Int,
    val description: (GBDevice, Context) -> String,
    val onClick: (GBDevice, Context) -> Unit,
) : DeviceCardItem {
    override fun isVisible(device: GBDevice) = visible(device)
}

/**
 * The heart rate measurement. The label displays the current heart rate sample.
 */
data class HeartRateCardItem(
    override val id: String = "heart_rate",
    val visible: (GBDevice) -> Boolean,
    val liveOnly: Boolean,
    val onClick: (GBDevice, Context) -> Unit,
) : DeviceCardItem {
    override fun isVisible(device: GBDevice) = visible(device)
}

/**
 * A button that opens a dialog with the items that are placed after it.
 */
data object MoreCardItem : DeviceCardItem {
    override val id = "more"
    override val optional = true

    override fun isVisible(device: GBDevice) = true
}
