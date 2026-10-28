package nodomain.freeyourgadget.gadgetbridge.activities.charts.sleep

import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartValueFormat
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.LimitLineSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.SeriesStyle

object SleepPeriodChartData {
    private const val DAY_STACK = "day"
    private const val Y_HEADROOM_MINUTES = 60.0

    /**
     * Minutes per sleep stage each day, stacked bottom to top in the order of [minutes], with the target and
     * the average as dashed lines.
     */
    @JvmStatic
    fun periodSpec(
        epochDays: LongArray,
        minutes: Array<DoubleArray>,
        labels: Array<String>,
        colors: IntArray,
        target: Double,
        targetColor: Int,
        average: Double,
        showAverage: Boolean,
        averageColor: Int,
    ): ChartSpec {
        if (epochDays.isEmpty() || minutes.all { stage -> stage.all { it <= 0.0 } }) {
            return ChartSpec.EMPTY
        }
        val highest = epochDays.indices.maxOf { day -> minutes.sumOf { it[day] } }
        return ChartSpec(
            series = minutes.indices.map { i ->
                ChartSeries(
                    "stage_$i", labels[i],
                    epochDays.indices.map { ChartPoint(epochDays[it].toDouble(), minutes[i][it]) },
                    SeriesStyle.Column(colors[i], stackKey = DAY_STACK),
                )
            },
            xAxis = AxisSpec(
                format = ChartValueFormat.EPOCH_DAY,
                minimum = epochDays.first().toDouble(),
                maximum = epochDays.last().toDouble(),
            ),
            yAxis = AxisSpec(
                format = ChartValueFormat.DURATION_MINUTES,
                minimum = 0.0,
                maximum = maxOf(highest, target) + Y_HEADROOM_MINUTES,
            ),
            limitLines = listOfNotNull(
                LimitLineSpec(value = target, color = targetColor).takeIf { target > 0 },
                LimitLineSpec(value = average, color = averageColor).takeIf { showAverage && average > 0 },
            ),
        )
    }
}
