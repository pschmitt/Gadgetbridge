package nodomain.freeyourgadget.gadgetbridge.activities.dashboard

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import androidx.core.widget.NestedScrollView
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.widgets.GridRect
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetGrid
import kotlin.math.roundToInt

/**
 * The widget goes to the column under its left edge.
 * When it covers at least half of another widget, it goes before the first widget that it overlaps and whose top is not
 * above the top of the covered widget. The covered widget goes to the first free column after it.
 * Otherwise, it goes before the first other widget whose top is below its top.
 * The other widgets stay in their columns.
 */
class GridDragController(
    private val grid: WidgetGridRecyclerView,
    private val scrollView: NestedScrollView,
    private val binder: DashboardGridBinder,
    private val onDrop: (arrangement: List<Pair<String, Int>>) -> Unit,
) {
    private var dragged: View? = null
    private var grabX = 0f
    private var grabY = 0f
    private var fingerX = 0f
    private var fingerY = 0f
    private val density = grid.resources.displayMetrics.density
    private val spacing = grid.resources.getDimensionPixelSize(R.dimen.dashboard_grid_spacing)
    private val columnWidth: Float
        get() = (grid.width - grid.paddingLeft - grid.paddingRight).toFloat() / binder.columns

    private val followFinger = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
        view.translationX = fingerX - grabX - view.left
        view.translationY = fingerY - grabY - view.top
    }

    /**
     * Starts to move [child], at the position of the last touch down on the grid.
     * Does nothing if no touch is in progress.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun start(child: View) {
        if (!grid.isTouchDown) return
        val instanceId = binder.instanceIdOf(child) ?: return
        dragged = child
        binder.draggedId = instanceId
        fingerX = grid.lastDownX
        fingerY = grid.lastDownY
        grabX = fingerX - child.left
        grabY = fingerY - child.top
        child.translationZ = DRAG_ELEVATION_DP * density
        child.addOnLayoutChangeListener(followFinger)

        grid.interceptTouches = true
        grid.parent.requestDisallowInterceptTouchEvent(true)
        grid.setOnTouchListener { _, event -> onTouch(event) }
    }

    private fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> move(event.x, event.y)
            MotionEvent.ACTION_UP -> drop()
            MotionEvent.ACTION_CANCEL -> drop()
        }
        return true
    }

    private fun move(x: Float, y: Float) {
        val instanceId = binder.draggedId ?: return
        val child = dragged ?: return
        fingerX = x
        fingerY = y
        autoScroll(y)

        val width = binder.widthOf(instanceId)
        val maxColumn = (binder.columns - width).coerceAtLeast(0)
        val column = ((x - grabX - grid.paddingLeft) / columnWidth).roundToInt().coerceIn(0, maxColumn)

        val others = binder.ids().filter { it != instanceId }
        val stacked = WidgetGrid.stack(binder.columns, others.map(::rectOf))
        val left = x - grabX - grid.paddingLeft
        val top = y - grabY - grid.paddingTop
        val right = left + width * columnWidth
        val bottom = top + binder.heightPxOf(instanceId) + spacing * 2
        val covered = stacked.map { rect ->
            val rectLeft = rect.x * columnWidth
            val rectRight = (rect.x + rect.width) * columnWidth
            val rectBottom = (rect.y + rect.height).toFloat()
            val overlapX = (minOf(right, rectRight) - maxOf(left, rectLeft)).coerceAtLeast(0f)
            val overlapY = (minOf(bottom, rectBottom) - maxOf(top, rect.y.toFloat())).coerceAtLeast(0f)
            overlapX * overlapY / ((rectRight - rectLeft) * rect.height)
        }
        val under = covered.indices.maxByOrNull { covered[it] }?.takeIf { covered[it] >= MIN_COVERED } ?: -1
        val index = if (under >= 0) {
            stacked.indices.first { covered[it] > 0f && stacked[it].y >= stacked[under].y }
        } else {
            stacked.indexOfFirst { it.y > top }.takeIf { it >= 0 } ?: others.size
        }

        binder.setColumn(instanceId, column)
        binder.moveBefore(instanceId, others.getOrNull(index))
        if (under >= 0) {
            binder.placeAtFirstFree(others[under])
        }
        followFinger.onLayoutChange(child, 0, 0, 0, 0, 0, 0, 0, 0)
        grid.invalidate()
    }

    /**
     * The rectangle of the widget [instanceId], with its height in pixels and its margins.
     */
    private fun rectOf(instanceId: String): GridRect {
        return GridRect(
            binder.columnOf(instanceId),
            0,
            binder.widthOf(instanceId),
            binder.heightPxOf(instanceId) + spacing * 2,
        )
    }

    /**
     * Scrolls when the finger is near the top or the bottom edge of [scrollView].
     */
    private fun autoScroll(y: Float) {
        val edge = AUTO_SCROLL_EDGE_DP * density
        val visibleY = grid.top + y - scrollView.scrollY
        val step = (AUTO_SCROLL_STEP_DP * density).roundToInt()
        when {
            visibleY < edge -> scrollView.scrollBy(0, -step)
            visibleY > scrollView.height - edge -> scrollView.scrollBy(0, step)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun drop() {
        val child = dragged ?: return
        dragged = null
        binder.draggedId = null
        child.removeOnLayoutChangeListener(followFinger)
        child.animate().translationX(0f).translationY(0f).translationZ(0f)

        grid.interceptTouches = false
        grid.setOnTouchListener(null)

        onDrop(binder.arrangement())
    }

    private companion object {
        const val DRAG_ELEVATION_DP = 8f
        const val MIN_COVERED = 0.5f
        const val AUTO_SCROLL_EDGE_DP = 48f
        const val AUTO_SCROLL_STEP_DP = 12f
    }
}
