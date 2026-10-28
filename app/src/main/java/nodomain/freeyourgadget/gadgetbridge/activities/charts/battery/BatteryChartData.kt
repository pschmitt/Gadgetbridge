package nodomain.freeyourgadget.gadgetbridge.activities.charts.battery

import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSide
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartValueFormat
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.SeriesStyle
import kotlin.math.abs

object BatteryChartData {
    private const val SECONDS_PER_DAY = 24 * 60 * 60L
    private const val SECONDS_PER_HOUR = 60 * 60L
    private const val MAX_POINTS_WITH_DOTS = 60
    private const val AXIS_PADDING = 0.1
    private const val NORMALIZED_MIN = -5.0
    private const val NORMALIZED_MAX = 105.0
    private const val SELECTION_WINDOW_FRACTION = 0.01

    /**
     * One battery metric's samples. [fixedMinimum] and [fixedMaximum] pin its axis, otherwise the axis spans at
     * least [minAxisSpan].
     */
    class Metric(
        val key: String,
        val label: String,
        val color: Int,
        val seconds: LongArray,
        val values: DoubleArray,
        val minAxisSpan: Double,
        val fixedMinimum: Double?,
        val fixedMaximum: Double?,
        val axisLabel: (Double) -> String,
    ) {
        val isEmpty: Boolean get() = seconds.isEmpty()
    }

    /**
     * Battery metrics between [startTs] and [endTs] (epoch seconds). One metric gets its own axis, two get one
     * axis each, and three or more are scaled to 0-100 on one hidden axis.
     */
    @JvmStatic
    fun spec(startTs: Long, endTs: Long, metrics: List<Metric>): ChartSpec {
        val plotted = metrics.filterNot { it.isEmpty }
        if (plotted.isEmpty()) {
            return ChartSpec.EMPTY
        }
        val normalized = plotted.size >= 3
        val series = plotted.mapIndexed { index, metric ->
            val min = metric.values.min()
            val max = metric.values.max()
            val points = metric.seconds.indices.map { i ->
                val value = metric.values[i]
                ChartPoint(metric.seconds[i].toDouble(), if (normalized) normalize(value, min, max) else value)
            }
            ChartSeries(
                metric.key,
                metric.label,
                points,
                SeriesStyle.Line(metric.color, showPoints = points.size <= MAX_POINTS_WITH_DOTS),
                axis = if (plotted.size == 2 && index == 1) AxisSide.END else AxisSide.START,
            )
        }
        return ChartSpec(
            series = series,
            xAxis = AxisSpec(
                format = if (endTs - startTs <= SECONDS_PER_DAY + SECONDS_PER_HOUR) ChartValueFormat.TIME_OF_DAY else ChartValueFormat.DATE,
                minimum = startTs.toDouble(),
                maximum = endTs.toDouble(),
            ),
            yAxis = if (normalized) {
                AxisSpec(minimum = NORMALIZED_MIN, maximum = NORMALIZED_MAX, showLabels = false)
            } else {
                axisFor(plotted[0])
            },
            endYAxis = if (plotted.size == 2) axisFor(plotted[1]) else null,
        )
    }

    /**
     * The index of the sample in [seconds] nearest to [x], if it is within a small part of the [startTs]
     * to [endTs] range, or -1.
     */
    @JvmStatic
    fun nearestSample(seconds: LongArray, x: Double, startTs: Long, endTs: Long): Int {
        val window = (endTs - startTs) * SELECTION_WINDOW_FRACTION
        var nearest = -1
        var nearestDistance = Double.MAX_VALUE
        for (i in seconds.indices) {
            val distance = abs(seconds[i] - x)
            if (distance <= window && distance < nearestDistance) {
                nearest = i
                nearestDistance = distance
            }
        }
        return nearest
    }

    private fun axisFor(metric: Metric): AxisSpec {
        if (metric.fixedMinimum != null && metric.fixedMaximum != null) {
            return AxisSpec(minimum = metric.fixedMinimum, maximum = metric.fixedMaximum, labeler = metric.axisLabel)
        }
        var min = metric.values.min()
        var max = metric.values.max()
        if (max - min < metric.minAxisSpan) {
            val center = (min + max) / 2
            min = center - metric.minAxisSpan / 2
            max = center + metric.minAxisSpan / 2
        }
        if (max <= min) {
            min -= 1
            max += 1
        }
        val padding = (max - min) * AXIS_PADDING
        return AxisSpec(minimum = min - padding, maximum = max + padding, labeler = metric.axisLabel)
    }

    private fun normalize(value: Double, min: Double, max: Double): Double =
        if (max <= min) 50.0 else (value - min) / (max - min) * 100.0
}
