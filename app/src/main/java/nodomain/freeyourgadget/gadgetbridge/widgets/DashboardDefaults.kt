package nodomain.freeyourgadget.gadgetbridge.widgets

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.DeviceHelper
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.BloodPressureWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.BodyEnergyWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.DistanceWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.GoalsWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.HeartRateWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.HrvWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.LastActivityWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.PaiWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.SleepScoreWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.SleepWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.StepsWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.StressWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.TodayWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.TrainingLoadWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.TrainingReadinessWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.Vo2MaxWidget
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * The widgets of a new dashboard, and the new widgets of a paired device.
 */
object DashboardDefaults {
    private val LOG: Logger = LoggerFactory.getLogger(DashboardDefaults::class.java)

    /**
     * A row of the default dashboard: the common variant to all its widgets, and the widgets themselves, in order.
     */
    private class Row(val variant: WidgetVariant, vararg val candidates: GBWidget<*>)

    /**
     * The default rows, in order.
     */
    private val ROWS: List<Row> = listOf(
        Row(WidgetVariant.LARGE, TodayWidget),
        Row(WidgetVariant.SMALL, GoalsWidget),
        Row(WidgetVariant.NUMBER, StepsWidget, SleepWidget, DistanceWidget, HeartRateWidget),
        Row(WidgetVariant.TALL, StressWidget, HrvWidget, BodyEnergyWidget, SleepScoreWidget),
        Row(WidgetVariant.NUMBER, TrainingLoadWidget, TrainingReadinessWidget, Vo2MaxWidget, PaiWidget),
        Row(WidgetVariant.WIDE, LastActivityWidget),
        Row(WidgetVariant.WIDE, BloodPressureWidget),
    )

    /**
     * Populates the default dashboard, one row at a time, with supported widgets, until it's full.
     */
    fun placements(): List<WidgetPlacement> {
        val devices = DeviceHelper.getInstance().availableDevices
        val placed = mutableSetOf<GBWidget<*>>()
        val placements = mutableListOf<WidgetPlacement>()
        for (row in ROWS) {
            val perRow = WidgetGrid.BASE_COLUMNS / row.variant.width
            val widgets = row.candidates
                .filter { widget -> widget !in placed && devices.any { widget.isSupportedBy(it) } }
                .take(perRow)
            if (widgets.isEmpty()) continue

            if (widgets.size == 1 && perRow > 1) {
                val widget = widgets.single()
                val fullWidth = widget.variants.find {
                    it.width == WidgetGrid.BASE_COLUMNS && it.heightDp == row.variant.heightDp
                } ?: continue
                placed += widget
                placements += WidgetPlacement(WidgetInstance(widget.id, widget.id), fullWidth.id, 0)
                continue
            }

            widgets.forEachIndexed { index, widget ->
                placed += widget
                placements += WidgetPlacement(
                    WidgetInstance(widget.id, widget.id),
                    row.variant.id,
                    index * row.variant.width
                )
            }
        }
        return placements
    }

    /**
     * The widgets that [device] supports, that no other device supports, and that no layout has.
     */
    fun newWidgets(device: GBDevice): List<GBWidget<*>> {
        val others = GBApplication.app().deviceManager.devices.filter { it.address != device.address }
        val placedTypes = WidgetLayoutStore.instances().map { it.typeId }.toSet()
        return WidgetRegistry.all().filter { widget ->
            widget.id !in placedTypes && widget.isSupportedBy(device) && others.none { widget.isSupportedBy(it) }
        }
    }

    /**
     * Fills an empty dashboard with the default widgets when [device] is the first device, and shows a notice for
     * the new widgets of [device].
     */
    fun onDeviceAdded(device: GBDevice) {
        val others = GBApplication.app().deviceManager.devices.filter { it.address != device.address }
        if (others.isEmpty() && WidgetLayoutStore.instances().isEmpty()) {
            val placements = placements()
            LOG.info("Adding default widgets {} to the empty dashboard", placements.map { it.instance.typeId })
            WidgetLayoutStore.replaceLayouts(mapOf(WidgetGrid.BASE_COLUMNS to placements))
        }

        val widgets = newWidgets(device)
        if (widgets.isNotEmpty()) {
            LOG.info("Device {} supports new widgets {}", device.address, widgets.map { it.id })
            DashboardNotices.set(DashboardNotices.state().copy(deviceAddress = device.address))
        }
    }
}
