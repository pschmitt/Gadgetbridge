package nodomain.freeyourgadget.gadgetbridge.activities.charts.bloodpressure

import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSide
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartValueFormat
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.LimitLineSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.SeriesStyle

object BloodPressureChartData {
    private const val SECONDS_PER_DAY = 24 * 60 * 60
    private const val PRESSURE_MINIMUM = 40.0
    private const val PRESSURE_MAXIMUM = 200.0
    private const val DAY_RANGE_WIDTH = 0.7f

    /**
     * Systolic and diastolic readings over the day from [startTs] (epoch seconds), with pulse on an end axis.
     * [labels] and [colors] are systolic, diastolic, pulse.
     */
    @JvmStatic
    fun daySpec(
        startTs: Long,
        seconds: LongArray,
        systolic: IntArray,
        diastolic: IntArray,
        pulse: IntArray,
        systolicAverage: Int,
        showAverage: Boolean,
        averageColor: Int,
        labels: Array<String>,
        colors: IntArray,
        pressureUnit: String,
        pulseUnit: String,
        pulseMinimum: Double,
        pulseMaximum: Double,
    ): ChartSpec {
        fun points(values: IntArray) = seconds.indices
            .filter { values[it] > 0 }
            .map { ChartPoint(seconds[it].toDouble(), values[it].toDouble()) }

        val series = listOf(
            ChartSeries("systolic", labels[0], points(systolic), SeriesStyle.Line(colors[0], showPoints = true)),
            ChartSeries("diastolic", labels[1], points(diastolic), SeriesStyle.Line(colors[1], showPoints = true)),
            ChartSeries("pulse", labels[2], points(pulse), SeriesStyle.Line(colors[2], showPoints = true), AxisSide.END),
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
            yAxis = pressureAxis(pressureUnit),
            endYAxis = pulseAxis(series, pulseUnit, pulseMinimum, pulseMaximum),
            limitLines = if (showAverage && systolicAverage > 0) {
                listOf(LimitLineSpec(value = systolicAverage.toDouble(), color = averageColor))
            } else {
                emptyList()
            },
        )
    }

    /**
     * The systolic and diastolic range of each day, with the average pulse on an end axis.
     */
    @JvmStatic
    fun periodSpec(
        epochDays: LongArray,
        systolicMinimum: IntArray,
        systolicMaximum: IntArray,
        diastolicMinimum: IntArray,
        diastolicMaximum: IntArray,
        pulseAverage: IntArray,
        labels: Array<String>,
        colors: IntArray,
        pressureUnit: String,
        pulseUnit: String,
        pulseMinimum: Double,
        pulseMaximum: Double,
    ): ChartSpec {
        fun ranges(low: IntArray, high: IntArray) = epochDays.indices
            .filter { low[it] > 0 && high[it] > 0 }
            .map { ChartPoint(epochDays[it].toDouble(), high[it].toDouble(), low = low[it].toDouble()) }

        val series = listOf(
            ChartSeries("systolic", labels[0], ranges(systolicMinimum, systolicMaximum), SeriesStyle.Range(colors[0], DAY_RANGE_WIDTH)),
            ChartSeries("diastolic", labels[1], ranges(diastolicMinimum, diastolicMaximum), SeriesStyle.Range(colors[1], DAY_RANGE_WIDTH)),
            ChartSeries(
                "pulse", labels[2],
                epochDays.indices.filter { pulseAverage[it] > 0 }.map { ChartPoint(epochDays[it].toDouble(), pulseAverage[it].toDouble()) },
                SeriesStyle.Line(colors[2], showPoints = true),
                AxisSide.END,
            ),
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
            yAxis = pressureAxis(pressureUnit),
            endYAxis = pulseAxis(series, pulseUnit, pulseMinimum, pulseMaximum),
        )
    }

    private fun pressureAxis(unit: String) =
        AxisSpec(format = ChartValueFormat.INTEGER, minimum = PRESSURE_MINIMUM, maximum = PRESSURE_MAXIMUM, unit = unit)

    private fun pulseAxis(series: List<ChartSeries>, unit: String, minimum: Double, maximum: Double) =
        if (series.any { it.axis == AxisSide.END }) {
            AxisSpec(format = ChartValueFormat.INTEGER, minimum = minimum, maximum = maximum, unit = unit)
        } else {
            null
        }
}
