package nodomain.freeyourgadget.gadgetbridge.activities.charts.stress

import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSide
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartValueFormat
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.LimitLineSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.SeriesStyle

object StressChartData {
    private const val SECONDS_PER_DAY = 24 * 60 * 60
    private const val MINUTES_PER_DAY = 24.0 * 60
    private const val MAX_STRESS = 100.0
    private const val DAY_STACK = "day"

    /**
     * One solid area per stress level over the day from [startTs] (epoch seconds). The samples are what a tap or
     * drag selects, coloured by their index in [sampleLevels].
     */
    @JvmStatic
    fun daySpec(
        startTs: Long,
        levels: List<List<ChartPoint>>,
        labels: Array<String>,
        colors: IntArray,
        sampleSeconds: LongArray,
        sampleValues: IntArray,
        average: Int,
        showAverage: Boolean,
        averageColor: Int,
        sampleLevels: IntArray,
    ): ChartSpec {
        val samples = sampleSeconds.indices
            .filter { sampleValues[it] > 0 }
            .groupBy { sampleLevels[it] }
            .toSortedMap()
            .map { (level, indices) ->
                ChartSeries(
                    "stress_$level", "",
                    indices.map { ChartPoint(sampleSeconds[it].toDouble(), sampleValues[it].toDouble()) },
                    SeriesStyle.Line(colors[level], showLine = false),
                )
            }
        if (samples.isEmpty()) {
            return ChartSpec.EMPTY
        }
        val areas = levels.indices.map { i ->
            ChartSeries(
                "level_$i", labels[i], levels[i],
                SeriesStyle.Line(colors[i], filled = true, solidFill = true),
                selectable = false,
            )
        }
        return ChartSpec(
            series = areas.filter { it.points.isNotEmpty() } + samples,
            xAxis = AxisSpec(
                format = ChartValueFormat.TIME_OF_DAY,
                minimum = startTs.toDouble(),
                maximum = (startTs + SECONDS_PER_DAY).toDouble(),
            ),
            yAxis = AxisSpec(format = ChartValueFormat.INTEGER, minimum = 0.0, maximum = MAX_STRESS),
            limitLines = if (showAverage && average > 0) {
                listOf(LimitLineSpec(value = average.toDouble(), color = averageColor))
            } else {
                emptyList()
            },
        )
    }

    /**
     * Minutes per stress level each day, stacked bottom to top in the order of [minutes].
     * The average stress of each day is drawn as a line.
     */
    @JvmStatic
    fun periodSpec(
        epochDays: LongArray,
        minutes: Array<DoubleArray>,
        labels: Array<String>,
        colors: IntArray,
        averages: IntArray,
        averageLabel: String,
        averageColor: Int,
    ): ChartSpec {
        if (epochDays.isEmpty() || minutes.all { level -> level.all { it == 0.0 } }) {
            return ChartSpec.EMPTY
        }
        val averagePoints = epochDays.indices
            .filter { averages[it] > 0 }
            .map { ChartPoint(epochDays[it].toDouble(), averages[it].toDouble()) }
        return ChartSpec(
            series = minutes.indices.map { i ->
                ChartSeries(
                    "level_$i", labels[i],
                    epochDays.indices.map { ChartPoint(epochDays[it].toDouble(), minutes[i][it]) },
                    SeriesStyle.Column(colors[i], stackKey = DAY_STACK),
                )
            } + ChartSeries(
                "average", averageLabel, averagePoints,
                SeriesStyle.Line(averageColor, maxGap = 1.0),
                AxisSide.END,
            ),
            xAxis = AxisSpec(
                format = ChartValueFormat.EPOCH_DAY,
                minimum = epochDays.first().toDouble(),
                maximum = epochDays.last().toDouble(),
            ),
            yAxis = AxisSpec(format = ChartValueFormat.DURATION_MINUTES, minimum = 0.0, maximum = MINUTES_PER_DAY),
            endYAxis = AxisSpec(format = ChartValueFormat.INTEGER, minimum = 0.0, maximum = MAX_STRESS),
        )
    }
}
