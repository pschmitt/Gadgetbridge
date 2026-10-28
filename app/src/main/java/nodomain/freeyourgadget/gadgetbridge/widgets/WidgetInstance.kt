package nodomain.freeyourgadget.gadgetbridge.widgets

import android.content.Context
import androidx.annotation.StringRes
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.Prefs
import nodomain.freeyourgadget.gadgetbridge.util.gson.GsonSerialized
import java.io.Serializable

/**
 * One copy of a widget that the user added. [typeId] is the [GBWidget.id].
 */
@GsonSerialized
data class WidgetInstance(
    val instanceId: String,
    val typeId: String,
) : Serializable

/**
 * A widget instance in one layout, at the column of its left edge. The order of the placements in the layout is the
 * order in which they are stacked.
 */
@GsonSerialized
data class WidgetPlacement(
    val instance: WidgetInstance,
    val variantId: String,
    val x: Int,
) : Serializable

/**
 * A widget instance as the dashboard grid displays it.
 */
data class PlacedWidget(
    val instance: WidgetInstance,
    val widget: GBWidget<*>,
    val variant: WidgetVariant,
    val x: Int,
)

/**
 * Per-instance configuration, wrapping [instance]'s own SharedPreferences plus the dashboard-wide
 * device filter it inherits from by default. [variant] is the instance's variant in the displayed layout.
 */
class WidgetConfig(
    val instance: WidgetInstance,
    val prefs: Prefs,
    private val dashboardShowAllDevices: Boolean,
    private val dashboardDeviceList: Set<String>,
    val variant: WidgetVariant = WidgetVariant.GAUGE,
) {
    val isTall: Boolean get() = variant.id == WidgetVariant.TALL.id

    val isWide: Boolean get() = variant.id == WidgetVariant.WIDE.id

    val isLargeStyle: Boolean get() = variant.id == WidgetVariant.METRIC_LARGE.id

    /**
     * Whether the tile spans the base layout's width.
     */
    val isFullWidth: Boolean get() = variant.width >= WidgetGrid.BASE_COLUMNS

    /**
     * Whether the tile shows more than a gauge, and so needs history.
     */
    val needsHistory: Boolean
        get() = variant.id == WidgetVariant.NUMBER.id || isTall || isWide || isLargeStyle

    /**
     * Whether the tile shows an intraday chart.
     */
    val isLarge: Boolean get() = isTall || isFullWidth

    /**
     * Resolves the devices this instance should show data for, filtered to those the widget supports.
     */
    fun resolveDevices(widget: GBWidget<*>): List<GBDevice> {
        val allDevices = GBApplication.app().deviceManager.devices
        val candidates = when (prefs.getString(KEY_DEVICES_MODE, DEVICES_MODE_INHERIT)) {
            DEVICES_MODE_ALL -> allDevices
            DEVICES_MODE_SELECTED -> {
                val selected = prefs.getStringSet(KEY_DEVICES, emptySet())
                allDevices.filter { selected.contains(it.address) }
            }

            else -> if (dashboardShowAllDevices) {
                allDevices
            } else {
                allDevices.filter { dashboardDeviceList.contains(it.address) }
            }
        }
        return candidates.filter { widget.isSupportedBy(it) }
    }

    /**
     * The instance's display title: a user override if set, otherwise [label].
     */
    fun title(context: Context, @StringRes label: Int): String {
        val custom = prefs.getString(KEY_TITLE, "")
        return custom.ifBlank { context.getString(label) }
    }

    companion object {
        const val KEY_DEVICES_MODE = "widget_devices_mode"
        const val KEY_DEVICES = "widget_devices"
        const val KEY_TITLE = "widget_title"

        const val DEVICES_MODE_INHERIT = "inherit"
        const val DEVICES_MODE_ALL = "all"
        const val DEVICES_MODE_SELECTED = "selected"
    }
}
