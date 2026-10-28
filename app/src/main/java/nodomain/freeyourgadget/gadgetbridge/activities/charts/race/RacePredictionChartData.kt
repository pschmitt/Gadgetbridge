package nodomain.freeyourgadget.gadgetbridge.activities.charts.race

import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartValueFormat
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.SeriesStyle

object RacePredictionChartData {
    private const val MIN_Y_PADDING_SECONDS = 60.0
    private const val Y_PADDING_SHARE = 0.1

    /**
     * Predicted race time in seconds per day; days with 0 have none.
     */
    @JvmStatic
    fun spec(epochDays: LongArray, seconds: DoubleArray, label: String, color: Int): ChartSpec {
        val points = epochDays.indices
            .filter { seconds[it] > 0.0 }
            .map { ChartPoint(epochDays[it].toDouble(), seconds[it]) }
        if (points.isEmpty()) {
            return ChartSpec.EMPTY
        }
        val lowest = points.minOf { it.y }
        val highest = points.maxOf { it.y }
        val padding = maxOf(MIN_Y_PADDING_SECONDS, (highest - lowest) * Y_PADDING_SHARE)
        return ChartSpec(
            series = listOf(ChartSeries("race", label, points, SeriesStyle.Line(color = color, showPoints = true))),
            xAxis = AxisSpec(
                format = ChartValueFormat.EPOCH_DAY,
                minimum = epochDays.first().toDouble(),
                maximum = epochDays.last().toDouble(),
            ),
            yAxis = AxisSpec(
                format = ChartValueFormat.DURATION_SECONDS,
                minimum = maxOf(0.0, lowest - padding),
                maximum = highest + padding,
            ),
        )
    }
}
