package nodomain.freeyourgadget.gadgetbridge.activities.charts.vo2max

import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartValueFormat
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.SeriesStyle

object VO2MaxChartData {
    private const val Y_PADDING = 2.0
    private const val Y_LIMIT = 100.0

    /**
     * One line per row of [values]; a day with 0 has no value.
     */
    @JvmStatic
    fun spec(epochDays: LongArray, values: Array<DoubleArray>, labels: Array<String>, colors: IntArray): ChartSpec {
        val series = values.indices.map { s ->
            ChartSeries(
                key = "vo2max_$s",
                label = labels[s],
                points = epochDays.indices
                    .filter { values[s][it] > 0.0 }
                    .map { ChartPoint(epochDays[it].toDouble(), values[s][it]) },
                style = SeriesStyle.Line(color = colors[s], showPoints = true),
            )
        }.filter { it.points.isNotEmpty() }
        if (epochDays.isEmpty() || series.isEmpty()) {
            return ChartSpec.EMPTY
        }
        val all = series.flatMap { s -> s.points.map { it.y } }
        return ChartSpec(
            series = series,
            xAxis = AxisSpec(
                format = ChartValueFormat.EPOCH_DAY,
                minimum = epochDays.first().toDouble(),
                maximum = epochDays.last().toDouble(),
            ),
            yAxis = AxisSpec(
                format = ChartValueFormat.INTEGER,
                minimum = maxOf(0.0, all.min() - Y_PADDING),
                maximum = minOf(Y_LIMIT, all.max() + Y_PADDING),
            ),
        )
    }
}
