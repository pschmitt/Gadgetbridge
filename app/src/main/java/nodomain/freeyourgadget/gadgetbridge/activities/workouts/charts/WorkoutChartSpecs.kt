package nodomain.freeyourgadget.gadgetbridge.activities.workouts.charts

import android.content.Context
import nodomain.freeyourgadget.gadgetbridge.activities.HeartRateUtils
import nodomain.freeyourgadget.gadgetbridge.activities.charts.IntervalChartUtils
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartSelection
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSide
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartValueFormat
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.LimitLineSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.LineCurve
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.SeriesStyle
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.durationLabel
import nodomain.freeyourgadget.gadgetbridge.model.heartratezones.HeartRateZonesResolver
import nodomain.freeyourgadget.gadgetbridge.model.workout.WorkoutChart
import java.util.Locale
import kotlin.math.abs

/**
 * Turns [WorkoutChart]s into a [ChartSpec] over the seconds since the workout started.
 * - The first chart uses the start axis
 * - A second chart uses the end axis
 * - Further charts are scaled into the range of the first chart, without axis labels
 */
object WorkoutChartSpecs {
    @JvmStatic
    @JvmOverloads
    fun spec(
        context: Context,
        charts: List<WorkoutChart>,
        showZones: Boolean,
        intervals: List<IntervalChartUtils.IntervalRow>? = null,
    ): ChartSpec {
        val scales = charts.mapIndexed { index, chart -> if (index < 2) IDENTITY else scale(chart, charts[0]) }
        val metricSeries = charts.mapIndexed { index, chart -> series(chart, sideOf(index), scales[index]) }
        val points = metricSeries.flatMap { it.points }
        if (points.isEmpty()) {
            return ChartSpec.EMPTY
        }
        val zoneSeries = mutableListOf<ChartSeries>()
        val zoneLines = mutableListOf<LimitLineSpec>()
        if (showZones) {
            charts.forEachIndexed { index, chart ->
                zoneSeries += zoneBands(context, chart, sideOf(index), scales[index])
                zoneLines += zoneLines(context, chart, sideOf(index), scales[index])
            }
        }
        return ChartSpec(
            series = zoneSeries + metricSeries,
            xAxis = AxisSpec(
                format = ChartValueFormat.DURATION_SECONDS,
                minimum = points.minOf { it.x },
                maximum = points.maxOf { it.x },
            ),
            yAxis = yAxis(charts[0]),
            endYAxis = charts.getOrNull(1)?.let { yAxis(it) },
            limitLines = zoneLines,
            xMarkers = if (IntervalChartUtils.hasIntervalPhases(intervals)) {
                IntervalChartUtils.boundaryLines(context, intervals)
            } else {
                emptyList()
            },
        )
    }

    /**
     * Tooltip content for the charts: the elapsed time and the type of the interval it falls in, then each chart's
     * nearest value.
     */
    @JvmStatic
    fun selection(
        context: Context,
        charts: List<WorkoutChart>,
        intervals: List<IntervalChartUtils.IntervalRow>?,
    ): (Double) -> ChartSelection {
        val points = charts.map { chart -> chart.series.points.sortedBy { it.x } }
        val xs = points.map { list -> DoubleArray(list.size) { list[it].x } }
        val phases = intervals?.takeIf { IntervalChartUtils.hasIntervalPhases(it) }
        return { x ->
            val interval = phases?.lastOrNull { it.startSeconds <= x }
            val title = listOfNotNull(durationLabel(x), interval?.let { IntervalChartUtils.labelForType(context, it.type) })
                .joinToString(" · ")
            val rows = charts.indices.mapNotNull { index ->
                val chart = charts[index]
                val nearest = nearest(points[index], xs[index], x) ?: return@mapNotNull null
                val value = chart.labeler?.invoke(nearest.y) ?: String.format(Locale.getDefault(), "%.1f", nearest.y)
                val text = listOfNotNull(value, chart.unitString?.takeIf { it.isNotEmpty() }).joinToString(" ")
                Triple(chart.title, colorOf(chart), text)
            }
            ChartSelection(
                title = title,
                rows = rows.map { ChartSelection.Row(it.second, it.third) },
                description = (listOf(title) + rows.map { "${it.first} ${it.third}" }).joinToString(". ", postfix = "."),
            )
        }
    }

    private fun nearest(points: List<ChartPoint>, xs: DoubleArray, x: Double): ChartPoint? {
        if (points.isEmpty()) return null
        val found = xs.binarySearch(x)
        if (found >= 0) return points[found]
        val after = -found - 1
        return when {
            after >= points.size -> points.last()
            after == 0 -> points.first()
            abs(xs[after] - x) < abs(x - xs[after - 1]) -> points[after]
            else -> points[after - 1]
        }
    }

    @JvmStatic
    fun colorOf(chart: WorkoutChart): Int = chart.series.color

    private val IDENTITY: (Double) -> Double = { it }

    private fun sideOf(index: Int) = if (index == 1) AxisSide.END else AxisSide.START

    private fun rangeOf(chart: WorkoutChart): Pair<Double, Double>? {
        val low = chart.minimum ?: chart.series.points.minOfOrNull { it.y } ?: return null
        val high = chart.maximum ?: chart.series.points.maxOfOrNull { it.y } ?: return null
        return low to high
    }

    /**
     * Maps the values of [chart] linearly from its own range into the range of [target].
     */
    private fun scale(chart: WorkoutChart, target: WorkoutChart): (Double) -> Double {
        val (low, high) = rangeOf(chart) ?: return IDENTITY
        val (targetLow, targetHigh) = rangeOf(target) ?: return IDENTITY
        if (high <= low) {
            return { (targetLow + targetHigh) / 2 }
        }
        val factor = (targetHigh - targetLow) / (high - low)
        return { targetLow + (it - low) * factor }
    }

    private fun series(chart: WorkoutChart, side: AxisSide, scale: (Double) -> Double): ChartSeries {
        val style = if (chart.series.dots) {
            SeriesStyle.Line(chart.series.color, showLine = false, showPoints = true)
        } else {
            SeriesStyle.Line(chart.series.color, curve = LineCurve.FLAT_AT_POINTS, maxGap = chart.series.maxGap)
        }
        val points = if (scale === IDENTITY) chart.series.points else chart.series.points.map { it.copy(y = scale(it.y)) }
        return ChartSeries(chart.id, chart.title, points, style, side)
    }

    private fun zoneBands(context: Context, chart: WorkoutChart, side: AxisSide, scale: (Double) -> Double): List<ChartSeries> {
        val zones = chart.zoneThresholds ?: return emptyList()
        val points = chart.series.points.sortedBy { it.x }
        if (points.isEmpty()) {
            return emptyList()
        }
        val chartMax = maxOf(HeartRateUtils.getInstance().maxHeartRate, zones.zone5 + 1)
        val peak = points.maxOf { it.y }
        return (1..5).mapNotNull { zone ->
            val top = HeartRateZonesResolver.upperBoundOf(zone, zones, chartMax).toDouble()
            val bottom = HeartRateZonesResolver.lowerBoundOf(zone, zones).toDouble()
            if (top <= bottom || peak <= bottom) {
                return@mapNotNull null
            }
            ChartSeries(
                "zone_$zone", "",
                points.map { ChartPoint(it.x, scale(it.y.coerceIn(bottom, top))) },
                SeriesStyle.Line(HeartRateZonesResolver.colorForZone(context, zone), filled = true, showLine = false, fillBase = scale(bottom)),
                side,
                selectable = false,
            )
        }
    }

    private fun zoneLines(context: Context, chart: WorkoutChart, side: AxisSide, scale: (Double) -> Double): List<LimitLineSpec> {
        val zones = chart.zoneThresholds ?: return emptyList()
        return listOf(2 to zones.zone2, 3 to zones.zone3, 4 to zones.zone4, 5 to zones.zone5)
            .filter { it.second > 0 }
            .map { (zone, bpm) -> LimitLineSpec(scale(bpm.toDouble()), HeartRateZonesResolver.colorForZone(context, zone), axis = side) }
    }

    private fun yAxis(chart: WorkoutChart) = AxisSpec(
        format = ChartValueFormat.DECIMAL,
        minimum = chart.minimum,
        maximum = chart.maximum,
        labeler = chart.labeler,
    )
}
