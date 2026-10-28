package nodomain.freeyourgadget.gadgetbridge.activities.charts.weight

import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSide
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

object WeightChartData {
    private const val Y_MARGIN = 1.0

    /**
     * A body composition percentage per reading, drawn on the end axis. [values] is parallel to the readings, NaN
     * where a reading has no estimate (no impedance).
     */
    class PercentLine(
        val key: String,
        val label: String,
        val color: Int,
        val values: DoubleArray,
    )

    /**
     * Weight readings between [startTs] and [endTs] (epoch seconds), with the target as a dashed line, and any
     * [percentLines] against a percent axis on the end side.
     */
    @JvmStatic
    @JvmOverloads
    fun spec(
        startTs: Long,
        endTs: Long,
        seconds: LongArray,
        weights: DoubleArray,
        target: Double,
        label: String,
        color: Int,
        targetColor: Int,
        percentLines: List<PercentLine> = emptyList(),
        percentUnit: String = "%",
    ): ChartSpec {
        val points = seconds.indices
            .filter { weights[it] > 0 }
            .map { ChartPoint(seconds[it].toDouble(), weights[it]) }
        if (points.isEmpty()) {
            return ChartSpec.EMPTY
        }
        val values = points.map { it.y } + if (target > 0) listOf(target) else emptyList()
        val percentSeries = percentLines.map { line ->
            val linePoints = seconds.indices
                .filter { weights[it] > 0 && !line.values[it].isNaN() }
                .map { ChartPoint(seconds[it].toDouble(), line.values[it]) }
            ChartSeries(line.key, line.label, linePoints, SeriesStyle.Line(line.color, showPoints = true), AxisSide.END)
        }.filter { it.points.isNotEmpty() }
        return ChartSpec(
            series = listOf(ChartSeries("weight", label, points, SeriesStyle.Line(color, curve = LineCurve.FLAT_AT_POINTS, showPoints = true))) +
                percentSeries,
            xAxis = AxisSpec(format = ChartValueFormat.DATE, minimum = startTs.toDouble(), maximum = endTs.toDouble()),
            yAxis = AxisSpec(
                format = ChartValueFormat.DECIMAL,
                minimum = maxOf(0.0, floor(values.min() - Y_MARGIN)),
                maximum = ceil(values.max() + Y_MARGIN),
            ),
            endYAxis = percentAxis(percentSeries, percentUnit),
            limitLines = if (target > 0) listOf(LimitLineSpec(value = target, color = targetColor)) else emptyList(),
        )
    }

    private fun percentAxis(series: List<ChartSeries>, unit: String): AxisSpec? {
        if (series.isEmpty()) {
            return null
        }
        val values = series.flatMap { line -> line.points.map { it.y } }
        return AxisSpec(
            format = ChartValueFormat.DECIMAL,
            minimum = maxOf(0.0, floor(values.min() - Y_MARGIN)),
            maximum = minOf(100.0, ceil(values.max() + Y_MARGIN)),
            unit = unit,
        )
    }
}
