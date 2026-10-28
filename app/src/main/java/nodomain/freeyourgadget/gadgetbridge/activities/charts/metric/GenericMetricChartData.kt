package nodomain.freeyourgadget.gadgetbridge.activities.charts.metric

import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartValueFormat
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.SeriesStyle
import kotlin.math.abs

object GenericMetricChartData {
    private const val MAX_POINTS_WITH_DOTS = 60
    private const val Y_PADDING = 0.1

    /**
     * Metric samples between [startTs] and [endTs] (epoch seconds), with a time of day axis for a single day.
     */
    @JvmStatic
    fun spec(
        startTs: Long,
        endTs: Long,
        singleDay: Boolean,
        seconds: LongArray,
        values: DoubleArray,
        label: String,
        color: Int,
    ): ChartSpec {
        if (seconds.isEmpty()) {
            return ChartSpec.EMPTY
        }
        val points = seconds.indices.map { ChartPoint(seconds[it].toDouble(), values[it]) }
        val min = values.min()
        val max = values.max()
        val range = max - min
        val padding = if (range == 0.0) maxOf(1.0, abs(max) * Y_PADDING) else range * Y_PADDING
        return ChartSpec(
            series = listOf(
                ChartSeries("metric", label, points, SeriesStyle.Line(color, showPoints = points.size <= MAX_POINTS_WITH_DOTS)),
            ),
            xAxis = AxisSpec(
                format = if (singleDay) ChartValueFormat.TIME_OF_DAY else ChartValueFormat.DATE,
                minimum = startTs.toDouble(),
                maximum = endTs.toDouble(),
            ),
            yAxis = AxisSpec(
                format = ChartValueFormat.DECIMAL,
                minimum = maxOf(0.0, min - padding),
                maximum = max + padding,
            ),
        )
    }
}
