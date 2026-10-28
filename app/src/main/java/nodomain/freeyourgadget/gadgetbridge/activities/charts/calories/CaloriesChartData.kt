package nodomain.freeyourgadget.gadgetbridge.activities.charts.calories

import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartValueFormat
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.LimitLineSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.SeriesStyle

object CaloriesChartData {
    private const val SECONDS_PER_DAY = 24 * 60 * 60
    private const val HEADROOM = 1.1

    /**
     * Cumulative active calories over the day starting at [startTs] (epoch seconds), with the goal line.
     */
    @JvmStatic
    fun dailySpec(startTs: Long, points: List<ChartPoint>, goal: Int, color: Int, label: String): ChartSpec {
        if (points.size <= 1) {
            return ChartSpec.EMPTY
        }
        val highest = maxOf(points.maxOf { it.y }, goal.toDouble(), 1.0)
        return ChartSpec(
            series = listOf(ChartSeries("active", label, points, SeriesStyle.Line(color = color, filled = true))),
            xAxis = AxisSpec(
                format = ChartValueFormat.TIME_OF_DAY,
                minimum = startTs.toDouble(),
                maximum = (startTs + SECONDS_PER_DAY).toDouble(),
            ),
            yAxis = AxisSpec(format = ChartValueFormat.INTEGER, minimum = 0.0, maximum = highest * HEADROOM),
            limitLines = listOf(LimitLineSpec(value = goal.toDouble(), color = color)),
        )
    }

    @JvmStatic
    fun periodSpec(epochDays: LongArray, active: LongArray, goal: Int, color: Int, label: String): ChartSpec {
        if (epochDays.isEmpty() || active.all { it == 0L }) {
            return ChartSpec.EMPTY
        }
        return ChartSpec(
            series = listOf(
                ChartSeries(
                    key = "active",
                    label = label,
                    points = epochDays.indices.map { ChartPoint(epochDays[it].toDouble(), active[it].toDouble()) },
                    style = SeriesStyle.Column(color = color),
                ),
            ),
            xAxis = AxisSpec(
                format = ChartValueFormat.EPOCH_DAY,
                minimum = epochDays.first().toDouble(),
                maximum = epochDays.last().toDouble(),
            ),
            yAxis = AxisSpec(
                format = ChartValueFormat.INTEGER,
                minimum = 0.0,
                maximum = maxOf(active.max() * HEADROOM, goal.toDouble()),
            ),
            limitLines = listOf(LimitLineSpec(value = goal.toDouble(), color = color)),
        )
    }
}
