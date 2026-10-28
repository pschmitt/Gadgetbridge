package nodomain.freeyourgadget.gadgetbridge.activities.charts.temperature

import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartValueFormat
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.LimitLineSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.LineCurve
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.SeriesStyle
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

object TemperatureChartData {
    private const val SECONDS_PER_DAY = 24 * 60 * 60
    private const val DAY_RANGE_WIDTH = 0.7f

    /**
     * Temperatures over the day from [startTs] (epoch seconds), with the y axis [axisGap] beyond the extremes.
     */
    @JvmStatic
    fun daySpec(
        startTs: Long,
        seconds: LongArray,
        values: DoubleArray,
        average: Double,
        showAverage: Boolean,
        axisGap: Double,
        label: String,
        color: Int,
        averageColor: Int,
    ): ChartSpec {
        val points = seconds.indices
            .filter { !values[it].isNaN() }
            .map { ChartPoint(seconds[it].toDouble(), values[it]) }
        if (points.isEmpty()) {
            return ChartSpec.EMPTY
        }
        return ChartSpec(
            series = listOf(ChartSeries("temperature", label, points, SeriesStyle.Line(color, curve = LineCurve.FLAT_AT_POINTS))),
            xAxis = AxisSpec(
                format = ChartValueFormat.TIME_OF_DAY,
                minimum = startTs.toDouble(),
                maximum = (startTs + SECONDS_PER_DAY).toDouble(),
            ),
            yAxis = AxisSpec(
                format = ChartValueFormat.DECIMAL,
                minimum = maxOf(0.0, points.minOf { it.y }.roundToLong() - axisGap),
                maximum = points.maxOf { it.y }.roundToLong() + axisGap,
            ),
            limitLines = if (showAverage && average > 0) {
                listOf(LimitLineSpec(value = average, color = averageColor))
            } else {
                emptyList()
            },
        )
    }

    /**
     * The min-max range of each day with its average as a line; NaN marks a day without data and breaks the line.
     */
    @JvmStatic
    fun periodSpec(
        epochDays: LongArray,
        minimum: DoubleArray,
        maximum: DoubleArray,
        average: DoubleArray,
        axisGap: Double,
        rangeLabel: String,
        rangeColor: Int,
        averageLabel: String,
        averageColor: Int,
    ): ChartSpec {
        val ranges = epochDays.indices
            .filter { !minimum[it].isNaN() && !maximum[it].isNaN() }
            .map { ChartPoint(epochDays[it].toDouble(), maximum[it], low = minimum[it]) }
        val averages = epochDays.indices
            .filter { !average[it].isNaN() }
            .map { ChartPoint(epochDays[it].toDouble(), average[it]) }
        if (ranges.isEmpty() && averages.isEmpty()) {
            return ChartSpec.EMPTY
        }
        val values = ranges.flatMap { listOf(it.y, it.low!!) } + averages.map { it.y }
        return ChartSpec(
            series = listOf(
                ChartSeries("range", rangeLabel, ranges, SeriesStyle.Range(rangeColor, DAY_RANGE_WIDTH)),
                ChartSeries("average", averageLabel, averages, SeriesStyle.Line(averageColor, maxGap = 1.0)),
            ).filter { it.points.isNotEmpty() },
            xAxis = AxisSpec(
                format = ChartValueFormat.EPOCH_DAY,
                minimum = epochDays.first().toDouble(),
                maximum = epochDays.last().toDouble(),
            ),
            yAxis = AxisSpec(
                format = ChartValueFormat.DECIMAL,
                minimum = floor(values.min()) - axisGap,
                maximum = ceil(values.max()) + axisGap,
            ),
        )
    }
}
