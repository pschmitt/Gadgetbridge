package nodomain.freeyourgadget.gadgetbridge.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetGridTest {
    @Test
    fun stackPutsRectUnderLowestRectInItsColumns() {
        val rects = WidgetGrid.stack(6, listOf(GridRect(0, 0, 3, 100), GridRect(3, 0, 3, 50), GridRect(0, 0, 6, 10)))
        assertEquals(listOf(GridRect(0, 0, 3, 100), GridRect(3, 0, 3, 50), GridRect(0, 100, 6, 10)), rects)
    }

    @Test
    fun stackKeepsStackNextToTallRect() {
        val rects = WidgetGrid.stack(6, listOf(GridRect(0, 0, 3, 300), GridRect(3, 0, 3, 127), GridRect(3, 0, 3, 127)))
        assertEquals(listOf(GridRect(0, 0, 3, 300), GridRect(3, 0, 3, 127), GridRect(3, 127, 3, 127)), rects)
    }

    @Test
    fun stackClampsToColumns() {
        val rects = WidgetGrid.stack(4, listOf(GridRect(3, 0, 2, 1)))
        assertEquals(listOf(GridRect(2, 0, 2, 1)), rects)
    }

    @Test
    fun firstFreeColumnGoesHighest() {
        val stacked = listOf(GridRect(0, 0, 3, 300), GridRect(3, 0, 3, 127))
        assertEquals(3, WidgetGrid.firstFreeColumn(6, stacked, 3))
    }

    @Test
    fun firstFreeColumnTakesLeftmostOnTie() {
        val stacked = listOf(GridRect(0, 0, 3, 100), GridRect(3, 0, 3, 100))
        assertEquals(0, WidgetGrid.firstFreeColumn(6, stacked, 3))
    }

    @Test
    fun columnsForWidth() {
        assertEquals(6, WidgetGrid.columnsForWidth(411))
        assertEquals(9, WidgetGrid.columnsForWidth(600))
        assertEquals(9, WidgetGrid.columnsForWidth(839))
        assertEquals(12, WidgetGrid.columnsForWidth(840))
    }
}
