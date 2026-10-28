package nodomain.freeyourgadget.gadgetbridge.widgets

/**
 * A rectangle in the dashboard grid: the first column and the width in columns, and the top and the height in a unit
 * of length.
 */
data class GridRect(val x: Int, val y: Int, val width: Int, val height: Int)

/**
 * The cell grid of the dashboard.
 */
object WidgetGrid {
    /**
     * The column count of the base layout, from which other layouts are derived.
     */
    const val BASE_COLUMNS = 6

    @JvmStatic
    fun columnsForWidth(widthDp: Int): Int = when {
        widthDp >= 840 -> 12
        widthDp >= 600 -> 9
        else -> BASE_COLUMNS
    }

    /**
     * Stacks the rectangles in their order. Each rectangle goes directly under the lowest rectangle in its columns.
     * Returns the new rectangles, in the order of [rects].
     */
    fun stack(columns: Int, rects: List<GridRect>): List<GridRect> {
        val bottoms = IntArray(columns)
        return rects.map { rect ->
            val width = rect.width.coerceAtMost(columns)
            val x = rect.x.coerceIn(0, columns - width)
            val y = (x until x + width).maxOf { bottoms[it] }
            for (column in x until x + width) {
                bottoms[column] = y + rect.height
            }
            GridRect(x, y, width, rect.height)
        }
    }

    /**
     * The first column at which a rectangle of [width] goes highest, under the stacked rectangles in [stacked].
     */
    fun firstFreeColumn(columns: Int, stacked: List<GridRect>, width: Int): Int {
        val bottoms = IntArray(columns)
        for (rect in stacked) {
            for (column in rect.x until rect.x + rect.width) {
                bottoms[column] = maxOf(bottoms[column], rect.y + rect.height)
            }
        }
        val clampedWidth = width.coerceAtMost(columns)
        return (0..columns - clampedWidth).minBy { x -> (x until x + clampedWidth).maxOf { bottoms[it] } }
    }

    /**
     * [placement] at the first free column of a layout with [columns], under the placements in [occupied].
     */
    fun placeAtFirstFree(columns: Int, occupied: List<WidgetPlacement>, placement: WidgetPlacement): WidgetPlacement {
        val width = (sizeOf(placement) ?: WidgetVariant.GAUGE).width
        val stacked = stack(columns, occupied.mapNotNull { rectOf(it) })
        return placement.copy(x = firstFreeColumn(columns, stacked, width))
    }

    private fun sizeOf(placement: WidgetPlacement): WidgetVariant? =
        WidgetRegistry.byId(placement.instance.typeId)?.variant(placement.variantId)

    private fun rectOf(placement: WidgetPlacement): GridRect? {
        val size = sizeOf(placement) ?: return null
        return GridRect(placement.x, 0, size.width, size.heightDp)
    }
}
