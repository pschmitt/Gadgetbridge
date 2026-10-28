package nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart

import android.os.Bundle
import android.widget.Button
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper

/**
 * One virtual button per slot of a period axis.
 */
internal class ChartSlotAccessibilityHelper(private val chart: GbChartView) : ExploreByTouchHelper(chart) {
    override fun getVirtualViewAt(x: Float, y: Float): Int = chart.slotAt(x, y) ?: INVALID_ID

    override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
        if (chart.hasSlotBounds()) {
            chart.slots()?.forEach { virtualViewIds.add(it) }
        }
    }

    @Suppress("DEPRECATION")
    override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
        node.contentDescription = chart.selectionAt(virtualViewId.toDouble())?.description.orEmpty()
        node.className = Button::class.java.name
        node.isSelected = chart.selectedX == virtualViewId.toDouble()
        node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
        node.setBoundsInParent(chart.slotBounds(virtualViewId))
    }

    override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
        if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false
        chart.toggle(virtualViewId.toDouble())
        return true
    }
}
