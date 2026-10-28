package nodomain.freeyourgadget.gadgetbridge.activities.charts.hrv

import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartValueFormat
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.SeriesStyle

object HrvChartData {
    private const val Y_AXIS_PADDING = 15.0
    private const val DAY_RANGE_WIDTH = 0.3f
    private const val SINGLE_SAMPLE_PADDING_SECONDS = 60.0

    @JvmStatic
    fun periodSpec(
        epochDays: LongArray,
        dayAvg: IntArray,
        lastNight: IntArray,
        weeklyAvg: IntArray,
        dayMin: IntArray,
        dayMax: IntArray,
        baselineLow: IntArray,
        baselineHigh: IntArray,
        showDayAvg: Boolean,
        showLastNight: Boolean,
        showWeeklyAvg: Boolean,
        showDayRange: Boolean,
        labels: Array<String>,
        colors: IntArray,
    ): ChartSpec {
        fun linePoints(values: IntArray) = epochDays.indices
            .filter { values[it] > 0 }
            .map { ChartPoint(epochDays[it].toDouble(), values[it].toDouble()) }

        fun rangePoints(low: IntArray, high: IntArray) = epochDays.indices
            .filter { low[it] > 0 && high[it] > low[it] }
            .map { ChartPoint(epochDays[it].toDouble(), high[it].toDouble(), low = low[it].toDouble()) }

        fun line(key: String, index: Int, points: List<ChartPoint>) =
            ChartSeries(key, labels[index], points, SeriesStyle.Line(color = colors[index], showPoints = true, maxGap = 1.0))

        val series = listOfNotNull(
            line("day_avg", 0, linePoints(dayAvg)).takeIf { showDayAvg },
            line("last_night", 1, linePoints(lastNight)).takeIf { showLastNight },
            line("weekly_avg", 4, linePoints(weeklyAvg)).takeIf { showWeeklyAvg },
            ChartSeries("day_range", labels[2], rangePoints(dayMin, dayMax), SeriesStyle.Range(colors[2], DAY_RANGE_WIDTH))
                .takeIf { showDayRange },
            ChartSeries("baseline", labels[3], rangePoints(baselineLow, baselineHigh), SeriesStyle.Range(colors[3])),
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
            yAxis = yAxis(series),
        )
    }

    @JvmStatic
    fun lastNightSpec(epochSeconds: LongArray, values: IntArray, label: String, color: Int): ChartSpec {
        val points = epochSeconds.indices
            .filter { values[it] > 0 }
            .map { ChartPoint(epochSeconds[it].toDouble(), values[it].toDouble()) }
            .sortedBy { it.x }
        if (points.isEmpty()) {
            return ChartSpec.EMPTY
        }
        val series = listOf(ChartSeries("hrv", label, points, SeriesStyle.Line(color = color)))
        val single = points.first().x == points.last().x
        return ChartSpec(
            series = series,
            xAxis = AxisSpec(
                format = ChartValueFormat.TIME_OF_DAY,
                minimum = points.first().x - if (single) SINGLE_SAMPLE_PADDING_SECONDS else 0.0,
                maximum = points.last().x + if (single) SINGLE_SAMPLE_PADDING_SECONDS else 0.0,
            ),
            yAxis = yAxis(series),
        )
    }

    private fun yAxis(series: List<ChartSeries>): AxisSpec {
        val values = series.flatMap { s -> s.points.flatMap { listOfNotNull(it.y, it.low) } }
        return AxisSpec(
            format = ChartValueFormat.INTEGER,
            minimum = maxOf(0.0, values.min() - Y_AXIS_PADDING),
            maximum = values.max() + Y_AXIS_PADDING,
        )
    }
}
