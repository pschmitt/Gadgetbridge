package nodomain.freeyourgadget.gadgetbridge.activities.charts.sleep

import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartValueFormat
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.SeriesStyle

object SleepScoreChartData {
    private const val MAX_SCORE = 100.0
    private const val MAX_GAP_DAYS = 1.0

    /**
     * Each day's sleep score as a line with dots, broken at days without a score.
     */
    @JvmStatic
    fun periodSpec(epochDays: LongArray, scores: IntArray, label: String, color: Int): ChartSpec {
        val points = epochDays.indices
            .filter { scores[it] > 0 }
            .map { ChartPoint(epochDays[it].toDouble(), scores[it].toDouble()) }
        if (epochDays.isEmpty() || points.isEmpty()) {
            return ChartSpec.EMPTY
        }
        return ChartSpec(
            series = listOf(
                ChartSeries("score", label, points, SeriesStyle.Line(color, showPoints = true, maxGap = MAX_GAP_DAYS)),
            ),
            xAxis = AxisSpec(
                format = ChartValueFormat.EPOCH_DAY,
                minimum = epochDays.first().toDouble(),
                maximum = epochDays.last().toDouble(),
            ),
            yAxis = AxisSpec(format = ChartValueFormat.INTEGER, minimum = 0.0, maximum = MAX_SCORE),
        )
    }
}
