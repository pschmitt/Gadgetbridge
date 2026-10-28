package nodomain.freeyourgadget.gadgetbridge.activities.dashboard

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import nodomain.freeyourgadget.gadgetbridge.widgets.GridRect
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetGrid
import kotlin.math.roundToInt
import androidx.core.view.isGone

/**
 * Places each child in the columns of its [LayoutParams], stacked in the order of the children. A column is the width
 * divided by [columns] wide. Each child goes directly under the lowest child in its columns.
 */
class WidgetGridLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(context, attrs) {
    var columns: Int = WidgetGrid.BASE_COLUMNS
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    class LayoutParams(
        var cellX: Int,
        var cellWidth: Int,
        /**
         * The height of the child in pixels, without the margins.
         */
        var childHeight: Int,
    ) : MarginLayoutParams(MATCH_PARENT, MATCH_PARENT)

    /**
     * Whether this layout intercepts all touch events from its children (e.g. during a drag).
     */
    var interceptTouches: Boolean = false

    /**
     * The position of the last touch down, in the coordinates of this layout.
     */
    var lastDownX = 0f
        private set
    var lastDownY = 0f
        private set

    private var cellSize = 0f

    val columnWidth: Float get() = cellSize


    /**
     * The visible children and their rectangles in pixels, margins included, from the last measure.
     */
    private var stacked: List<Pair<View, GridRect>> = emptyList()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        cellSize = (width - paddingLeft - paddingRight).toFloat() / columns

        val visible = children.filterNot { it.isGone }.toList()
        val rects = WidgetGrid.stack(columns, visible.map { child ->
            val lp = child.layoutParams as LayoutParams
            GridRect(lp.cellX, 0, lp.cellWidth, lp.childHeight + lp.topMargin + lp.bottomMargin)
        })
        stacked = visible.zip(rects)

        for ((child, rect) in stacked) {
            val lp = child.layoutParams as LayoutParams
            val childWidth = edge(rect.x + rect.width) - edge(rect.x) - lp.leftMargin - lp.rightMargin
            child.measure(
                MeasureSpec.makeMeasureSpec(childWidth.coerceAtLeast(0), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(lp.childHeight.coerceAtLeast(0), MeasureSpec.EXACTLY),
            )
        }

        val height = rects.maxOfOrNull { it.y + it.height } ?: 0
        setMeasuredDimension(width, height + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for ((child, rect) in stacked) {
            val lp = child.layoutParams as LayoutParams
            val left = paddingLeft + edge(rect.x) + lp.leftMargin
            val top = paddingTop + rect.y + lp.topMargin
            child.layout(left, top, left + child.measuredWidth, top + child.measuredHeight)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            lastDownX = ev.x
            lastDownY = ev.y
        }
        return interceptTouches
    }

    /**
     * The pixel offset of the edge before cell [cells]. Rounding each edge, not each cell size, keeps the gaps the same.
     */
    private fun edge(cells: Int): Int = (cells * cellSize).roundToInt()

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean = p is LayoutParams

    override fun generateDefaultLayoutParams(): LayoutParams = LayoutParams(0, 1, 0)

    override fun generateLayoutParams(p: ViewGroup.LayoutParams?): LayoutParams {
        val lp = LayoutParams(0, 1, 0)
        if (p is MarginLayoutParams) {
            lp.setMargins(p.leftMargin, p.topMargin, p.rightMargin, p.bottomMargin)
        }
        return lp
    }

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams {
        val lp = LayoutParams(0, 1, 0)
        val margins = MarginLayoutParams(context, attrs)
        lp.setMargins(margins.leftMargin, margins.topMargin, margins.rightMargin, margins.bottomMargin)
        return lp
    }

    override fun shouldDelayChildPressedState(): Boolean = false
}
