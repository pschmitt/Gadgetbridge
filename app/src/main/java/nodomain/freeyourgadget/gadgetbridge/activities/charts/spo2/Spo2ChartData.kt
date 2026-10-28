package nodomain.freeyourgadget.gadgetbridge.activities.charts.spo2

import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartValueFormat
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.LimitLineSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.SeriesStyle

object Spo2ChartData {
    private const val SECONDS_PER_DAY = 24 * 60 * 60
    private const val MAX_GAP_SECONDS = 300.0
    private const val DAY_Y_MINIMUM = 65.0
    private const val DAY_Y_MAXIMUM = 100.5
    private const val PERIOD_Y_MAXIMUM = 100.0
    private const val PERIOD_Y_STEP = 5
    private const val DAY_RANGE_WIDTH = 0.7f

    /**
     * Automatic readings as a line broken at gaps, manual ones as dots, over the day from [startTs] (epoch seconds).
     */
    @JvmStatic
    fun daySpec(
        startTs: Long,
        autoSeconds: LongArray,
        autoValues: IntArray,
        manualSeconds: LongArray,
        manualValues: IntArray,
        average: Int,
        showAverage: Boolean,
        label: String,
        color: Int,
        averageColor: Int,
    ): ChartSpec {
        fun points(seconds: LongArray, values: IntArray) = seconds.indices
            .filter { values[it] > 0 }
            .map { ChartPoint(seconds[it].toDouble(), values[it].toDouble()) }

        val series = listOf(
            ChartSeries("auto", label, points(autoSeconds, autoValues), SeriesStyle.Line(color, maxGap = MAX_GAP_SECONDS)),
            ChartSeries(
                "manual", label, points(manualSeconds, manualValues),
                SeriesStyle.Line(color, showPoints = true, showLine = false),
            ),
        ).filter { it.points.isNotEmpty() }
        if (series.isEmpty()) {
            return ChartSpec.EMPTY
        }
        return ChartSpec(
            series = series,
            xAxis = AxisSpec(
                format = ChartValueFormat.TIME_OF_DAY,
                minimum = startTs.toDouble(),
                maximum = (startTs + SECONDS_PER_DAY).toDouble(),
            ),
            yAxis = AxisSpec(format = ChartValueFormat.INTEGER, minimum = DAY_Y_MINIMUM, maximum = DAY_Y_MAXIMUM),
            limitLines = if (showAverage && average > 0) {
                listOf(LimitLineSpec(value = average.toDouble(), color = averageColor))
            } else {
                emptyList()
            },
        )
    }

    /**
     * The min-max range of each day with its average as a line. A day without data breaks the line.
     */
    @JvmStatic
    fun periodSpec(
        epochDays: LongArray,
        minimum: IntArray,
        maximum: IntArray,
        average: IntArray,
        rangeLabel: String,
        rangeColor: Int,
        averageLabel: String,
        averageColor: Int,
    ): ChartSpec {
        val days = epochDays.indices.filter { minimum[it] > 0 && maximum[it] > 0 }
        if (days.isEmpty()) {
            return ChartSpec.EMPTY
        }
        val ranges = days.map { ChartPoint(epochDays[it].toDouble(), maximum[it].toDouble(), low = minimum[it].toDouble()) }
        val averages = days.filter { average[it] > 0 }.map { ChartPoint(epochDays[it].toDouble(), average[it].toDouble()) }
        val lowest = days.minOf { minimum[it] }
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
                format = ChartValueFormat.INTEGER,
                minimum = maxOf(0, PERIOD_Y_STEP * ((lowest - PERIOD_Y_STEP) / PERIOD_Y_STEP)).toDouble(),
                maximum = PERIOD_Y_MAXIMUM,
            ),
        )
    }
}
