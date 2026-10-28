/*  Copyright (C) 2026 Dany Mestas

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart

/**
 * Places the labels of the x markers so that none overlaps another, in pixels at the current zoom.
 */
internal object XMarkerLabelLayout {
    /**
     * A marker label to place: its line at pixel [x], [width] pixels wide including the gap to the line, and the side
     * of the plot ([atBottom]) and of the line ([beforeLine]) it asks for.
     */
    data class Label(val x: Float, val width: Float, val atBottom: Boolean, val beforeLine: Boolean)

    data class Placement(val atBottom: Boolean, val beforeLine: Boolean)

    /**
     * Takes the labels in order and gives each the side of the plot it asks for when that leaves it clear of the
     * labels placed before it, else the other side, else no place at all (null): the line is still drawn. A label goes
     * left of its line when it asks to or when it would cross [right], the right edge of the plot. Labels of lines
     * outside [left]..[right] get no place and take no room.
     */
    fun place(labels: List<Label>, left: Float, right: Float): List<Placement?> {
        val taken = mapOf(true to mutableListOf<ClosedFloatingPointRange<Float>>(), false to mutableListOf())
        return labels.map { label ->
            if (label.x < left || label.x > right) return@map null
            val beforeLine = label.beforeLine || label.x + label.width > right
            val span = if (beforeLine) label.x - label.width..label.x else label.x..label.x + label.width
            val side = listOf(label.atBottom, !label.atBottom).firstOrNull { side ->
                taken.getValue(side).none { it.start < span.endInclusive && span.start < it.endInclusive }
            } ?: return@map null
            taken.getValue(side) += span
            Placement(side, beforeLine)
        }
    }
}
