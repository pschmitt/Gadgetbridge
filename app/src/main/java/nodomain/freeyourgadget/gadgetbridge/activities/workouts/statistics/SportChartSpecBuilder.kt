package nodomain.freeyourgadget.gadgetbridge.activities.workouts.statistics

import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSide
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartValueFormat
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.LegendItemSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.SeriesStyle
import kotlin.math.ceil
import kotlin.math.floor

/**
 * A metric to chart, with its label, color and display conversion.
 */
class MetricChart(
    val metric: SportMetric,
    val label: String,
    val color: Int,
    val type: SeriesType,
    val emptyMaximum: Double,
    val toDisplay: (Double) -> Double,
)

object SportChartSpecBuilder {
    /**
     * Chart spec for a period. [second] gets its own y axis. [barLabel] labels the bars of a custom period.
     */
    fun build(
        stats: PeriodStats,
        first: MetricChart,
        second: MetricChart? = null,
        barLabel: ((Int) -> String)? = null,
    ): ChartSpec {
        val firstPoints = points(stats, first)
        if (second != null && firstPoints.isEmpty()) {
            return build(stats, second, barLabel = barLabel)
        }
        val secondPoints = second?.let { points(stats, it) }.orEmpty()
        val shownSecond = second?.takeIf { secondPoints.any { it.y != 0.0 } }
        return ChartSpec(
            series = listOfNotNull(
                series(first, firstPoints, AxisSide.START),
                shownSecond?.let { series(it, secondPoints, AxisSide.END) },
            ),
            xAxis = AxisSpec(
                format = when (stats.period.kind) {
                    PeriodKind.WEEK -> ChartValueFormat.DAY_OF_WEEK
                    PeriodKind.MONTH -> ChartValueFormat.DAY_OF_MONTH
                    PeriodKind.YEAR -> ChartValueFormat.MONTH_OF_YEAR
                    PeriodKind.CUSTOM -> ChartValueFormat.BAR_INDEX
                },
                minimum = 1.0,
                maximum = stats.period.barCount.toDouble(),
                labeler = barLabel?.let { label -> { x -> label(x.toInt()) } },
            ),
            yAxis = yAxis(first, firstPoints),
            endYAxis = shownSecond?.let { yAxis(it, secondPoints) },
            legend = shownSecond?.let {
                listOf(LegendItemSpec(first.label, first.color), LegendItemSpec(it.label, it.color))
            }.orEmpty(),
        )
    }

    private fun series(chart: MetricChart, points: List<ChartPoint>, axis: AxisSide) = ChartSeries(
        key = chart.metric.name.lowercase(),
        label = chart.label,
        points = points,
        style = when (chart.type) {
            SeriesType.BAR -> SeriesStyle.Column(color = chart.color)
            SeriesType.LINE -> SeriesStyle.Line(color = chart.color, showPoints = true)
        },
        axis = axis,
    )

    private fun points(stats: PeriodStats, chart: MetricChart): List<ChartPoint> = stats.bars
        .filter { bar -> chart.type == SeriesType.BAR || chart.metric.valueOf(bar.totals) > 0.0 }
        .map { bar -> ChartPoint(bar.x, chart.toDisplay(chart.metric.valueOf(bar.totals))) }

    private fun yAxis(chart: MetricChart, points: List<ChartPoint>): AxisSpec = if (chart.metric.lineOnly) {
        rangeAxis(chart.metric, points)
    } else {
        AxisSpec(
            format = ChartValueFormat.DECIMAL,
            minimum = 0.0,
            maximum = chart.emptyMaximum.takeIf { points.all { it.y == 0.0 } },
        )
    }

    private fun rangeAxis(metric: SportMetric, points: List<ChartPoint>): AxisSpec {
        if (metric == SportMetric.AVG_AEROBIC_EFFECT || metric == SportMetric.AVG_ANAEROBIC_EFFECT) {
            return AxisSpec(format = ChartValueFormat.ONE_DECIMAL, minimum = 0.0, maximum = TRAINING_EFFECT_MAXIMUM)
        }
        val format = when (metric) {
            SportMetric.AVG_PACE, SportMetric.MAX_PACE -> ChartValueFormat.DURATION_SECONDS
            SportMetric.AVG_SPEED, SportMetric.MAX_SPEED -> ChartValueFormat.ONE_DECIMAL
            else -> ChartValueFormat.INTEGER
        }
        if (points.isEmpty()) {
            return AxisSpec(format = format)
        }
        val step = when (metric) {
            SportMetric.AVG_PACE, SportMetric.MAX_PACE -> PACE_AXIS_STEP
            SportMetric.AVG_SPEED, SportMetric.MAX_SPEED -> SPEED_AXIS_STEP
            SportMetric.AVG_HEART_RATE, SportMetric.MAX_HEART_RATE -> HEART_RATE_AXIS_STEP
            SportMetric.AVG_SWOLF -> SWOLF_AXIS_STEP
            SportMetric.AVG_CADENCE, SportMetric.MAX_CADENCE -> CADENCE_AXIS_STEP
            else -> POWER_AXIS_STEP
        }
        val lowest = points.minOf { it.y }
        val highest = points.maxOf { it.y }
        val padding = maxOf((highest - lowest) * AXIS_PADDING, step)
        return AxisSpec(
            format = format,
            minimum = maxOf(0.0, floor((lowest - padding) / step) * step),
            maximum = ceil((highest + padding) / step) * step,
        )
    }

    private const val AXIS_PADDING = 0.2
    private const val PACE_AXIS_STEP = 30.0
    private const val SPEED_AXIS_STEP = 1.0
    private const val HEART_RATE_AXIS_STEP = 5.0
    private const val POWER_AXIS_STEP = 10.0
    private const val SWOLF_AXIS_STEP = 5.0
    private const val CADENCE_AXIS_STEP = 5.0
    private const val TRAINING_EFFECT_MAXIMUM = 5.0
}
