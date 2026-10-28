package nodomain.freeyourgadget.gadgetbridge.activities.charts.respiratoryrate

import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartValueFormat
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.LineCurve
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.SeriesStyle

object RespiratoryRateChartData {
    private const val SECONDS_PER_DAY = 24 * 60 * 60
    private const val MAX_GAP_SECONDS = 300.0
    private const val MIN_Y_MAXIMUM = 20.0
    private const val Y_HEADROOM = 3.0

    /**
     * Breaths per minute over the day from [startTs] (epoch seconds), broken at gaps of more than five minutes.
     */
    @JvmStatic
    fun daySpec(startTs: Long, seconds: LongArray, values: DoubleArray, label: String, color: Int): ChartSpec {
        val points = seconds.indices
            .filter { values[it] > 0 }
            .map { ChartPoint(seconds[it].toDouble(), values[it]) }
        if (points.isEmpty()) {
            return ChartSpec.EMPTY
        }
        return ChartSpec(
            series = listOf(ChartSeries("rate", label, points, SeriesStyle.Line(color, curve = LineCurve.FLAT_AT_POINTS, maxGap = MAX_GAP_SECONDS))),
            xAxis = AxisSpec(
                format = ChartValueFormat.TIME_OF_DAY,
                minimum = startTs.toDouble(),
                maximum = (startTs + SECONDS_PER_DAY).toDouble(),
            ),
            yAxis = yAxis(points.maxOf { it.y }),
        )
    }

    /**
     * Awake and sleep averages per day, as lines with dots.
     */
    @JvmStatic
    fun periodSpec(
        epochDays: LongArray,
        awake: IntArray,
        sleep: IntArray,
        awakeLabel: String,
        awakeColor: Int,
        sleepLabel: String,
        sleepColor: Int,
    ): ChartSpec {
        fun line(key: String, label: String, values: IntArray, color: Int) = ChartSeries(
            key,
            label,
            epochDays.indices.filter { values[it] > 0 }.map { ChartPoint(epochDays[it].toDouble(), values[it].toDouble()) },
            SeriesStyle.Line(color, showPoints = true),
        )

        val series = listOf(
            line("awake", awakeLabel, awake, awakeColor),
            line("sleep", sleepLabel, sleep, sleepColor),
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
            yAxis = yAxis(series.maxOf { s -> s.points.maxOf { it.y } }),
        )
    }

    private fun yAxis(highest: Double) = AxisSpec(
        format = ChartValueFormat.INTEGER,
        minimum = 0.0,
        maximum = maxOf(highest + Y_HEADROOM, MIN_Y_MAXIMUM),
    )
}
