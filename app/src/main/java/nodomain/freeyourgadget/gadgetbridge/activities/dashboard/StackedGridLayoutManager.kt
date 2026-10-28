package nodomain.freeyourgadget.gadgetbridge.activities.dashboard

import android.content.Context
import android.view.View.MeasureSpec
import androidx.recyclerview.widget.RecyclerView
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.widgets.GridRect
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetGrid
import kotlin.math.roundToInt

/**
 * Lays out the children in the columns of their item specs, stacked in the adapter order. A column is the width divided
 * by [columns] wide. Each child goes directly under the lowest child in its columns.
 */
class StackedGridLayoutManager(context: Context) : RecyclerView.LayoutManager() {
    /**
     * Provides the placement and the height of every item. Implemented by the grid adapter.
     */
    interface ItemSpecs {
        fun itemCount(): Int

        /**
         * The first column of the item at [position].
         */
        fun itemX(position: Int): Int

        /**
         * The width of the item at [position], in columns.
         */
        fun itemWidth(position: Int): Int

        /**
         * The height of the item at [position], in pixels.
         */
        fun itemHeightPx(position: Int): Int
    }

    var columns: Int = WidgetGrid.BASE_COLUMNS
        set(value) {
            if (field == value || value <= 0) return
            field = value
            requestLayout()
        }

    var itemSpecs: ItemSpecs? = null

    /**
     * The height of the laid out items, including their margins, from the last layout.
     */
    var contentHeight: Int = 0
        private set

    private val spacing = context.resources.getDimensionPixelSize(R.dimen.dashboard_grid_spacing)
    private var cellSize = 0f

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams =
        RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT)

    override fun isAutoMeasureEnabled(): Boolean = true

    override fun canScrollVertically(): Boolean = false

    override fun scrollVerticallyBy(dy: Int, recycler: RecyclerView.Recycler, state: RecyclerView.State): Int = 0

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        val specs = itemSpecs
        if (specs == null || specs.itemCount() == 0) {
            contentHeight = 0
            removeAndRecycleAllViews(recycler)
            return
        }

        cellSize = (width - paddingLeft - paddingRight).toFloat() / columns
        val rects = WidgetGrid.stack(
            columns,
            (0 until specs.itemCount()).map { position ->
                GridRect(
                    specs.itemX(position),
                    0,
                    specs.itemWidth(position),
                    specs.itemHeightPx(position) + spacing * 2,
                )
            },
        )
        contentHeight = rects.maxOfOrNull { it.y + it.height } ?: 0

        detachAndScrapAttachedViews(recycler)
        for (position in rects.indices) {
            val child = recycler.getViewForPosition(position)
            addView(child)
            val rect = rects[position]
            val span = edge(rect.x + rect.width) - edge(rect.x)
            child.measure(
                exact(span - spacing * 2),
                exact(specs.itemHeightPx(position)),
            )
            val left = paddingLeft + edge(rect.x) + spacing
            val top = paddingTop + rect.y + spacing
            child.layout(left, top, left + child.measuredWidth, top + child.measuredHeight)
        }
    }

    private fun exact(size: Int): Int =
        MeasureSpec.makeMeasureSpec(size.coerceAtLeast(0), MeasureSpec.EXACTLY)

    /**
     * The pixel offset of the edge before cell [cells]. Rounding each edge, not each cell size, keeps the gaps the same.
     */
    private fun edge(cells: Int): Int = (cells * cellSize).roundToInt()
}
