package nodomain.freeyourgadget.gadgetbridge.activities.charts.heartrate

import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartValueFormat
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.LimitLineSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.LineCurve
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.SeriesStyle

object HeartRateChartData {
    private const val SECONDS_PER_DAY = 24 * 60 * 60
    private const val Y_PADDING = 30.0

    /**
     * Heart rate over the day starting at [startTs] (epoch seconds), broken where samples are more than
     * [maxGapSeconds] apart, with an optional dashed [average] line.
     */
    @JvmStatic
    fun daySpec(
        startTs: Long,
        sampleSeconds: LongArray,
        bpm: IntArray,
        maxGapSeconds: Int,
        average: Int,
        showAverage: Boolean,
        label: String,
        color: Int,
        averageColor: Int,
    ): ChartSpec {
        val points = sampleSeconds.indices
            .filter { bpm[it] > 0 }
            .map { ChartPoint(sampleSeconds[it].toDouble(), bpm[it].toDouble()) }
        if (points.isEmpty()) {
            return ChartSpec.EMPTY
        }
        return ChartSpec(
            series = listOf(
                ChartSeries("hr", label, points, SeriesStyle.Line(color, filled = true, curve = LineCurve.FLAT_AT_POINTS, maxGap = maxGapSeconds.toDouble())),
            ),
            xAxis = AxisSpec(
                format = ChartValueFormat.TIME_OF_DAY,
                minimum = startTs.toDouble(),
                maximum = (startTs + SECONDS_PER_DAY).toDouble(),
            ),
            yAxis = yAxis(points.map { it.y }),
            limitLines = if (showAverage && average > 0) {
                listOf(LimitLineSpec(value = average.toDouble(), color = averageColor))
            } else {
                emptyList()
            },
        )
    }

    /**
     * Minimum, resting, average and maximum per day, as lines in that order. [labels] and [colors] follow the same
     * order.
     */
    @JvmStatic
    fun periodSpec(
        epochDays: LongArray,
        minimum: IntArray,
        resting: IntArray,
        average: IntArray,
        maximum: IntArray,
        showResting: Boolean,
        showAverage: Boolean,
        labels: Array<String>,
        colors: IntArray,
    ): ChartSpec {
        fun line(key: String, index: Int, values: IntArray) = ChartSeries(
            key,
            labels[index],
            epochDays.indices.filter { values[it] > 0 }.map { ChartPoint(epochDays[it].toDouble(), values[it].toDouble()) },
            SeriesStyle.Line(colors[index]),
        )

        val series = listOfNotNull(
            line("min", 0, minimum),
            line("resting", 1, resting).takeIf { showResting },
            line("avg", 2, average).takeIf { showAverage },
            line("max", 3, maximum),
        ).filter { it.points.isNotEmpty() }
        if (epochDays.isEmpty() || series.isEmpty()) {
            return ChartSpec.EMPTY
        }
        return ChartSpec(
            series = series,
            xAxis = AxisSpec(
                format = ChartValueFormat.EPOCH_DAY,
                minimum = epochDays.first().toDouble(),
                maximum = epochDays.last().toDouble(),
            ),
            yAxis = yAxis(series.flatMap { s -> s.points.map { it.y } }),
        )
    }

    private fun yAxis(values: List<Double>) = AxisSpec(
        format = ChartValueFormat.INTEGER,
        minimum = maxOf(0.0, values.min() - Y_PADDING),
        maximum = values.max() + Y_PADDING,
    )
}
