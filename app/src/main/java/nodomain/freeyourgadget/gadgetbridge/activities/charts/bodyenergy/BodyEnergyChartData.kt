package nodomain.freeyourgadget.gadgetbridge.activities.charts.bodyenergy

import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartValueFormat
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.LineCurve
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.SeriesStyle

object BodyEnergyChartData {
    private const val SECONDS_PER_DAY = 24 * 60 * 60
    private const val MAX_ENERGY = 100.0
    private const val DAY_RANGE_WIDTH = 0.4f
    private const val SMOOTHING_SECONDS = 15 * 60

    /**
     * Today's level over the average per [averages] bin, from [midnight] (epoch seconds) to the next one.
     * [averages] holds NaN for bins without data.
     */
    @JvmStatic
    fun daySpec(
        midnight: Long,
        sampleSeconds: LongArray,
        levels: IntArray,
        averages: DoubleArray,
        levelLabel: String,
        levelColor: Int,
        averageLabel: String,
        averageColor: Int,
    ): ChartSpec {
        val levelPoints =
            smoothed(sampleSeconds.indices.map { ChartPoint(sampleSeconds[it].toDouble(), levels[it].toDouble()) })
        val averagePoints = averagePoints(midnight, averages)
        if (levelPoints.isEmpty() && averagePoints.isEmpty()) {
            return ChartSpec.EMPTY
        }
        return ChartSpec(
            series = listOf(
                ChartSeries(
                    "average",
                    averageLabel,
                    averagePoints,
                    SeriesStyle.Line(averageColor, filled = true, curve = LineCurve.SMOOTH),
                    selectable = false,
                ),
                ChartSeries(
                    "level",
                    levelLabel,
                    levelPoints,
                    SeriesStyle.Line(levelColor, filled = true, curve = LineCurve.FLAT_AT_POINTS)
                ),
            ).filter { it.points.isNotEmpty() },
            xAxis = AxisSpec(
                format = ChartValueFormat.TIME_OF_DAY,
                minimum = midnight.toDouble(),
                maximum = (midnight + SECONDS_PER_DAY).toDouble(),
            ),
            yAxis = AxisSpec(format = ChartValueFormat.INTEGER, minimum = 0.0, maximum = MAX_ENERGY),
        )
    }

    private fun smoothed(points: List<ChartPoint>): List<ChartPoint> {
        var start = 0
        var end = 0
        var sum = 0.0
        return points.map { point ->
            while (end < points.size && points[end].x - point.x <= SMOOTHING_SECONDS) {
                sum += points[end].y
                end++
            }
            while (point.x - points[start].x > SMOOTHING_SECONDS) {
                sum -= points[start].y
                start++
            }
            point.copy(y = sum / (end - start))
        }
    }

    /**
     * One point per bin with data, plus the first bin again at the next midnight.
     */
    @JvmStatic
    fun averagePoints(midnight: Long, averages: DoubleArray): List<ChartPoint> {
        if (averages.isEmpty()) return emptyList()
        val binSeconds = SECONDS_PER_DAY / averages.size
        val points = averages.indices
            .filter { !averages[it].isNaN() }
            .map { ChartPoint((midnight + it.toLong() * binSeconds).toDouble(), averages[it]) }
        if (points.isEmpty() || averages[0].isNaN()) return points
        return points + ChartPoint((midnight + SECONDS_PER_DAY).toDouble(), averages[0])
    }

    @JvmStatic
    fun periodSpec(epochDays: LongArray, minimum: IntArray, maximum: IntArray, color: Int, label: String): ChartSpec {
        val points = epochDays.indices
            .filter { minimum[it] > 0 && maximum[it] > 0 }
            .map { ChartPoint(epochDays[it].toDouble(), maximum[it].toDouble(), low = minimum[it].toDouble()) }
        if (points.isEmpty()) {
            return ChartSpec.EMPTY
        }
        return ChartSpec(
            series = listOf(ChartSeries("range", label, points, SeriesStyle.Range(color, DAY_RANGE_WIDTH))),
            xAxis = AxisSpec(
                format = ChartValueFormat.EPOCH_DAY,
                minimum = epochDays.first().toDouble(),
                maximum = epochDays.last().toDouble(),
            ),
            yAxis = AxisSpec(format = ChartValueFormat.INTEGER, minimum = 0.0, maximum = MAX_ENERGY),
        )
    }
}
