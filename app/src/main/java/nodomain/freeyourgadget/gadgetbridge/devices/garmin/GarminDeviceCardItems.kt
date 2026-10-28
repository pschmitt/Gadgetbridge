package nodomain.freeyourgadget.gadgetbridge.devices.garmin

import android.content.Context
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCardAction
import nodomain.freeyourgadget.gadgetbridge.devices.cards.ActionCardItem
import nodomain.freeyourgadget.gadgetbridge.devices.cards.DefaultDeviceCardItems
import nodomain.freeyourgadget.gadgetbridge.devices.cards.DeviceCardItem
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.actions.GarminSendWaypointActivity
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice

fun garminDeviceCardItems(device: GBDevice, coordinator: GarminCoordinator): List<DeviceCardItem> {
    val items = DefaultDeviceCardItems.build(coordinator, device).toMutableList()

    if (coordinator.supportsSendWaypoint(device)) {
        items.add(ActionCardItem(sendWaypointAction(), visible = { it.isInitialized }, optional = true))
    }

    return items
}

private fun sendWaypointAction(): DeviceCardAction = object : DeviceCardAction {
    override fun getId(): String = GarminPreferences.PREF_GARMIN_SEND_WAYPOINT
    override fun getIcon(device: GBDevice) = R.drawable.ic_add_location_alt_24px
    override fun getDescription(device: GBDevice, context: Context): String =
        context.getString(R.string.activity_send_waypoint_titel)

    override fun onClick(device: GBDevice, context: Context) {
        GarminSendWaypointActivity.handlePreferenceClick(context, device)
    }
}
