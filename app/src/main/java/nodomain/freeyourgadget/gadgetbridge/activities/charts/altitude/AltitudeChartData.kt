/*  Copyright (C) 2026 Davide Gessa

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
package nodomain.freeyourgadget.gadgetbridge.activities.charts.altitude

import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartValueFormat
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.SeriesStyle
import kotlin.math.ceil
import kotlin.math.floor

object AltitudeChartData {
    private const val Y_MARGIN = 5.0

    /**
     * Altitude readings between [startTs] and [endTs]
     */
    @JvmStatic
    fun spec(
        startTs: Long,
        endTs: Long,
        seconds: LongArray,
        altitudes: DoubleArray,
        label: String,
        color: Int,
    ): ChartSpec {
        val points = seconds.indices
            .map { ChartPoint(seconds[it].toDouble(), altitudes[it]) }
        if (points.isEmpty()) {
            return ChartSpec.EMPTY
        }
        val values = points.map { it.y }
        return ChartSpec(
            series = listOf(ChartSeries("altitude", label, points, SeriesStyle.Line(color, showPoints = true, showLine = false))),
            xAxis = AxisSpec(
                format = ChartValueFormat.DATE,
                minimum = startTs.toDouble(),
                maximum = endTs.toDouble(),
            ),
            yAxis = AxisSpec(
                format = ChartValueFormat.DECIMAL,
                minimum = floor(values.min() - Y_MARGIN),
                maximum = ceil(values.max() + Y_MARGIN),
            ),
        )
    }
}
