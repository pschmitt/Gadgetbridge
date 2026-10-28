package nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart

import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.XMarkerLabelLayout.Label
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.XMarkerLabelLayout.Placement
import org.junit.Assert.assertEquals
import org.junit.Test

class XMarkerLabelLayoutTest {

    private fun place(vararg labels: Label) = XMarkerLabelLayout.place(labels.toList(), 0f, 600f)

    @Test
    fun spreadLabels_keepTheirSides() {
        assertEquals(
            listOf(Placement(false, false), Placement(true, false), Placement(false, false)),
            place(Label(0f, 40f, false, false), Label(100f, 40f, true, false), Label(200f, 40f, false, false)),
        )
    }

    @Test
    fun aLabelOverlappingOneOnItsSide_movesToTheOtherSide() {
        assertEquals(
            listOf(Placement(true, false), Placement(false, false)),
            place(Label(100f, 40f, true, false), Label(120f, 40f, true, false)),
        )
    }

    @Test
    fun aLabelOverlappingOnBothSides_isLeftOut() {
        assertEquals(
            listOf(Placement(true, false), Placement(false, false), null, Placement(true, false)),
            place(
                Label(100f, 40f, true, false),
                Label(110f, 40f, false, false),
                Label(120f, 40f, true, false),
                Label(150f, 40f, true, false),
            ),
        )
    }

    @Test
    fun aLabelBeforeItsLine_takesTheRoomLeftOfIt() {
        // a final label left of its line reaches back over the bottom label of a short interval before it
        assertEquals(
            listOf(Placement(false, false), Placement(true, false), Placement(false, true)),
            place(Label(400f, 30f, false, false), Label(500f, 40f, true, false), Label(595f, 70f, true, true)),
        )
    }

    @Test
    fun aLabelCrossingTheRightEdge_goesBeforeItsLine() {
        assertEquals(listOf(Placement(false, true)), place(Label(590f, 40f, false, false)))
    }

    @Test
    fun linesOutsideThePlot_getNoLabel() {
        assertEquals(
            listOf(null, Placement(false, false)),
            place(Label(-10f, 40f, false, false), Label(5f, 40f, false, false)),
        )
    }
}
