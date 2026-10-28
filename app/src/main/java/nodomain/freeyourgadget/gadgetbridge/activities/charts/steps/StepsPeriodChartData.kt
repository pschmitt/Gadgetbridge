package nodomain.freeyourgadget.gadgetbridge.activities.charts.steps

import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartValueFormat
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.LimitLineSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.SeriesStyle

object StepsPeriodChartData {
    private const val Y_AXIS_HEADROOM_STEPS = 2000

    /**
     * One bar per day plus the goal line. [epochDays] must be consecutive.
     */
    @JvmStatic
    fun buildChartSpec(epochDays: LongArray, steps: LongArray, stepsColor: Int, goal: Int): ChartSpec {
        if (epochDays.isEmpty() || steps.all { it == 0L }) {
            return ChartSpec.EMPTY
        }
        val maxY = maxOf(steps.max(), goal.toLong()) + Y_AXIS_HEADROOM_STEPS
        return ChartSpec(
            series = listOf(
                ChartSeries(
                    key = "steps",
                    label = "",
                    points = epochDays.indices.map { ChartPoint(epochDays[it].toDouble(), steps[it].toDouble()) },
                    style = SeriesStyle.Column(color = stepsColor),
                ),
            ),
            xAxis = AxisSpec(
                format = ChartValueFormat.EPOCH_DAY,
                minimum = epochDays.first().toDouble(),
                maximum = epochDays.last().toDouble(),
            ),
            yAxis = AxisSpec(format = ChartValueFormat.INTEGER, minimum = 0.0, maximum = maxY.toDouble()),
            limitLines = listOf(LimitLineSpec(value = goal.toDouble(), color = stepsColor)),
        )
    }
}
