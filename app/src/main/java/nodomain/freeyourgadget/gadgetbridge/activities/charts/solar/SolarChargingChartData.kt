package nodomain.freeyourgadget.gadgetbridge.activities.charts.solar

import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartValueFormat
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.SeriesStyle

object SolarChargingChartData {
    private const val SECONDS_PER_DAY = 24 * 60 * 60
    private const val MAX_PERCENT = 100.0
    private const val PERIOD_HEADROOM = 1.2

    /**
     * Solar intensity over the day from [startTs] (epoch seconds), one filled area per run of non-zero readings.
     */
    @JvmStatic
    fun daySpec(startTs: Long, seconds: LongArray, percent: FloatArray, label: String, color: Int): ChartSpec {
        val runs = mutableListOf<MutableList<ChartPoint>>()
        var run: MutableList<ChartPoint>? = null
        for (i in seconds.indices) {
            val x = seconds[i].toDouble()
            if (percent[i] > 0) {
                if (run == null) {
                    run = mutableListOf()
                    runs.add(run)
                    if (i > 0) {
                        run.add(ChartPoint(seconds[i - 1].toDouble(), 0.0))
                    }
                }
                run.add(ChartPoint(x, percent[i].toDouble()))
            } else {
                run?.add(ChartPoint(x, 0.0))
                run = null
            }
        }
        if (runs.isEmpty()) {
            return ChartSpec.EMPTY
        }
        return ChartSpec(
            series = runs.mapIndexed { i, points ->
                ChartSeries("run_$i", label, points, SeriesStyle.Line(color, filled = true, solidFill = true))
            },
            xAxis = AxisSpec(
                format = ChartValueFormat.TIME_OF_DAY,
                minimum = startTs.toDouble(),
                maximum = (startTs + SECONDS_PER_DAY).toDouble(),
            ),
            yAxis = AxisSpec(format = ChartValueFormat.INTEGER, minimum = 0.0, maximum = MAX_PERCENT),
        )
    }

    /**
     * One column per day, with the y axis reaching at least [minimumScale].
     */
    @JvmStatic
    fun periodSpec(epochDays: LongArray, values: DoubleArray, label: String, color: Int, minimumScale: Double): ChartSpec {
        if (epochDays.isEmpty() || values.all { it <= 0.0 }) {
            return ChartSpec.EMPTY
        }
        return ChartSpec(
            series = listOf(
                ChartSeries(
                    "value", label,
                    epochDays.indices.filter { values[it] > 0 }.map { ChartPoint(epochDays[it].toDouble(), values[it]) },
                    SeriesStyle.Column(color),
                ),
            ),
            xAxis = AxisSpec(
                format = ChartValueFormat.EPOCH_DAY,
                minimum = epochDays.first().toDouble(),
                maximum = epochDays.last().toDouble(),
            ),
            yAxis = AxisSpec(
                format = ChartValueFormat.DECIMAL,
                minimum = 0.0,
                maximum = maxOf(values.max() * PERIOD_HEADROOM, minimumScale),
            ),
        )
    }
}
