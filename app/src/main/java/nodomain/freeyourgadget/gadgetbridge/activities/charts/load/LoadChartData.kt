package nodomain.freeyourgadget.gadgetbridge.activities.charts.load

import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartValueFormat
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.SeriesStyle

object LoadChartData {
    const val OPTIMAL_LOAD_RATIO_LOWER = 0.8f
    const val OPTIMAL_LOAD_RATIO_UPPER = 1.5f
    private const val MIN_Y_MAXIMUM = 200
    private const val Y_AXIS_HEADROOM = 100

    @JvmStatic
    fun dailyLoadSpec(epochDays: LongArray, load: IntArray, color: Int): ChartSpec {
        if (epochDays.isEmpty() || load.all { it == 0 }) {
            return ChartSpec.EMPTY
        }
        return ChartSpec(
            series = listOf(
                ChartSeries(
                    key = "load",
                    label = "",
                    points = epochDays.indices.map { ChartPoint(epochDays[it].toDouble(), load[it].toDouble()) },
                    style = SeriesStyle.Column(color = color),
                ),
            ),
            xAxis = dayAxis(epochDays),
            yAxis = yAxis(load.max()),
        )
    }

    /**
     * Acute and chronic load lines, each only when shown, over the optimal range derived from chronic load.
     */
    @JvmStatic
    fun acuteChronicSpec(
        epochDays: LongArray,
        acute: IntArray,
        chronic: IntArray,
        showAcute: Boolean,
        showChronic: Boolean,
        acuteLabel: String,
        acuteColor: Int,
        chronicLabel: String,
        chronicColor: Int,
        optimalLabel: String,
        optimalColor: Int,
    ): ChartSpec {
        fun linePoints(values: IntArray) = epochDays.indices
            .filter { values[it] > 0 }
            .map { ChartPoint(epochDays[it].toDouble(), values[it].toDouble()) }

        val acutePoints = if (showAcute) linePoints(acute) else emptyList()
        val chronicPoints = if (showChronic) linePoints(chronic) else emptyList()
        val optimalPoints = chronicPoints.map {
            ChartPoint(it.x, it.y * OPTIMAL_LOAD_RATIO_UPPER, low = it.y * OPTIMAL_LOAD_RATIO_LOWER)
        }
        if (epochDays.isEmpty() || (acutePoints.isEmpty() && chronicPoints.isEmpty())) {
            return ChartSpec.EMPTY
        }

        val series = listOfNotNull(
            line("acute", acuteLabel, acutePoints, acuteColor).takeIf { showAcute },
            line("chronic", chronicLabel, chronicPoints, chronicColor).takeIf { showChronic },
            ChartSeries("optimal", optimalLabel, optimalPoints, SeriesStyle.Range(optimalColor))
                .takeIf { optimalPoints.isNotEmpty() },
        )
        val highest = (acutePoints + chronicPoints + optimalPoints).maxOf { it.y }
        return ChartSpec(series = series, xAxis = dayAxis(epochDays), yAxis = yAxis(highest.toInt()))
    }

    private fun line(key: String, label: String, points: List<ChartPoint>, color: Int) =
        ChartSeries(key, label, points, SeriesStyle.Line(color = color, showPoints = true))

    private fun dayAxis(epochDays: LongArray) = AxisSpec(
        format = ChartValueFormat.EPOCH_DAY,
        minimum = epochDays.first().toDouble(),
        maximum = epochDays.last().toDouble(),
    )

    private fun yAxis(highest: Int) = AxisSpec(
        format = ChartValueFormat.INTEGER,
        minimum = 0.0,
        maximum = (maxOf(highest, MIN_Y_MAXIMUM) + Y_AXIS_HEADROOM).toDouble(),
    )
}
