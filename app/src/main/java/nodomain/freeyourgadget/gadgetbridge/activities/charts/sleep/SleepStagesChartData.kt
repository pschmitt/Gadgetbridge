package nodomain.freeyourgadget.gadgetbridge.activities.charts.sleep

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
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.SleepSession

object SleepStagesChartData {
    private const val MAX_INTENSITY = 1.0

    data class SessionSamples(val seconds: LongArray, val stages: IntArray)

    /** Stage boundaries from the shared sessions, including gaps between separate sessions. */
    @JvmStatic
    fun sessionSamples(
        sessions: List<SleepSession>,
        stageIndex: (ActivityKind) -> Int,
        gapIndex: Int,
    ): SessionSamples {
        val points = sortedMapOf<Long, Int>()
        for (stage in sessions.flatMap { it.stages }.sortedBy { it.startTime }) {
            if (stage.endTime <= stage.startTime) continue
            points[stage.startTime / 1000L] = stageIndex(stage.kind)
            points[stage.endTime / 1000L] = gapIndex
        }
        return SessionSamples(points.keys.toLongArray(), points.values.toIntArray())
    }

    /**
     * A line on the end axis over a sleep chart, e.g. heart rate or SpO2.
     */
    data class Overlay(
        val label: String,
        val color: Int,
        val points: List<ChartPoint>,
        val maxGap: Double?,
        val minimum: Double,
        val maximum: Double,
        val average: Double?,
        val averageColor: Int,
        val format: ChartValueFormat,
        val valueText: (Double) -> String,
    )

    /**
     * One row per stage index below [colors] size, from the first stage at the bottom up. Other stages are gaps.
     */
    @JvmStatic
    fun hypnogramSpec(seconds: LongArray, stages: IntArray, colors: IntArray): ChartSpec {
        if (seconds.isEmpty()) {
            return ChartSpec.EMPTY
        }
        val points = seconds.indices.map {
            ChartPoint(seconds[it].toDouble(), if (stages[it] in colors.indices) stages[it].toDouble() else -1.0)
        }
        return ChartSpec(
            series = listOf(ChartSeries("stages", "", points, SeriesStyle.Stages(colors.toList()))),
            xAxis = AxisSpec(format = ChartValueFormat.TIME_OF_DAY, minimum = points.first().x, maximum = points.last().x),
            yAxis = AxisSpec(format = ChartValueFormat.DECIMAL, minimum = -0.5, maximum = colors.size - 0.5, showLabels = false),
        )
    }

    /**
     * [spec] with [overlay] as a line on the end axis, and its average as a limit line.
     */
    @JvmStatic
    fun withOverlay(spec: ChartSpec, overlay: Overlay?): ChartSpec {
        if (overlay == null || overlay.points.isEmpty() || spec.isEmpty) {
            return spec
        }
        val line = ChartSeries(
            "overlay", overlay.label, overlay.points,
            SeriesStyle.Line(overlay.color, curve = LineCurve.FLAT_AT_POINTS, maxGap = overlay.maxGap), AxisSide.END,
        )
        return spec.copy(
            series = spec.series + line,
            endYAxis = AxisSpec(format = overlay.format, minimum = overlay.minimum, maximum = overlay.maximum),
            limitLines = spec.limitLines + listOfNotNull(
                overlay.average?.let { LimitLineSpec(value = it, color = overlay.averageColor, axis = AxisSide.END) }
            ),
        )
    }

    /**
     * Tooltip for a time: the stage then, with its stretch for [showStretch], and the [overlay] value.
     */
    @JvmStatic
    fun selection(
        seconds: LongArray,
        stages: IntArray,
        labels: Array<String>,
        colors: IntArray,
        showStretch: Boolean,
        overlay: Overlay?,
        timeText: (Long) -> String,
        durationText: (Long) -> String,
    ): (Double) -> ChartSelection = { x ->
        val time = Math.round(x)
        val title = timeText(time)
        val rows = mutableListOf<ChartSelection.Row>()
        val found = seconds.asList().binarySearch(time)
        val i = if (found >= 0) found else -found - 2
        if (i >= 0) {
            val stage = stages[i]
            val stretch = if (showStretch) stretchAt(seconds, stages, i) else null
            val text = if (stretch == null) {
                labels[stage]
            } else {
                "${labels[stage]} ${timeText(stretch.first)} – ${timeText(stretch.second)} (${durationText(stretch.second - stretch.first)})"
            }
            rows += ChartSelection.Row(colors[stage], text)
        }
        overlay?.let { overlayValueAt(it, x) }?.let { rows += ChartSelection.Row(overlay.color, overlay.valueText(it)) }
        ChartSelection(title, rows, (listOf(title) + rows.map { it.text }).joinToString(". ", postfix = "."))
    }

    /**
     * Start and end of the run of equal stages around sample [index].
     */
    @JvmStatic
    fun stretchAt(seconds: LongArray, stages: IntArray, index: Int): Pair<Long, Long> {
        var first = index
        while (first > 0 && stages[first - 1] == stages[index]) first--
        var next = index + 1
        while (next < stages.size && stages[next] == stages[index]) next++
        return seconds[first] to seconds[minOf(next, seconds.size - 1)]
    }

    /**
     * The latest overlay value at or before [x], within its max gap.
     */
    private fun overlayValueAt(overlay: Overlay, x: Double): Double? {
        val point = overlay.points.lastOrNull { it.x <= x } ?: return null
        if (overlay.maxGap != null && x - point.x > overlay.maxGap) return null
        return point.y
    }

    /**
     * Movement per stage as solid areas over the night, with heart rate on an end axis. [stages] index into
     * [labels] and [colors]; stage [notWornStage] gets no ramp to or from its neighbours.
     */
    @JvmStatic
    fun daySpec(
        seconds: LongArray,
        stages: IntArray,
        values: DoubleArray,
        notWornStage: Int,
        labels: Array<String>,
        colors: IntArray,
        markerColor: Int,
        hrSeconds: LongArray,
        hrBpm: IntArray,
        hrMaxGapSeconds: Double,
        hrAverage: Int,
        showHrAverage: Boolean,
        hrLabel: String,
        hrColor: Int,
        hrAverageColor: Int,
        hrMinimum: Double,
        hrMaximum: Double,
    ): ChartSpec {
        val hrPoints = hrSeconds.indices
            .filter { hrBpm[it] > 0 }
            .map { ChartPoint(hrSeconds[it].toDouble(), hrBpm[it].toDouble()) }
        if (seconds.isEmpty() && hrPoints.isEmpty()) {
            return ChartSpec.EMPTY
        }
        // One area per stretch of a stage, so separate stretches aren't joined along the bottom
        val areas = mutableListOf<Pair<Int, MutableList<ChartPoint>>>()
        for (i in seconds.indices) {
            val x = seconds[i].toDouble()
            val stage = stages[i]
            if (i == 0) {
                areas += stage to mutableListOf(ChartPoint(x, 0.0))
            } else if (stages[i - 1] != stage) {
                val previous = stages[i - 1]
                val ramp = previous != notWornStage && stage != notWornStage
                areas.last().second += ChartPoint(x, if (ramp) values[i] else values[i - 1])
                areas.last().second += ChartPoint(x, 0.0)
                areas += stage to mutableListOf(ChartPoint(x, 0.0))
            }
            areas.last().second += ChartPoint(x, values[i])
        }
        val series = areas.mapIndexed { index, (stage, points) ->
            ChartSeries("stage_${stage}_$index", labels[stage], points, SeriesStyle.Line(colors[stage], filled = true, solidFill = true), selectable = false)
        } + listOfNotNull(
            ChartSeries("samples", "", seconds.indices.map { ChartPoint(seconds[it].toDouble(), values[it]) }, SeriesStyle.Line(markerColor, showLine = false)),
            ChartSeries("hr", hrLabel, hrPoints, SeriesStyle.Line(hrColor, curve = LineCurve.FLAT_AT_POINTS, maxGap = hrMaxGapSeconds), AxisSide.END)
                .takeIf { hrPoints.isNotEmpty() },
        )
        return ChartSpec(
            series = series,
            xAxis = AxisSpec(
                format = ChartValueFormat.TIME_OF_DAY,
                minimum = minOf(seconds.firstOrNull()?.toDouble() ?: Double.MAX_VALUE, hrPoints.firstOrNull()?.x ?: Double.MAX_VALUE),
                maximum = maxOf(seconds.lastOrNull()?.toDouble() ?: -Double.MAX_VALUE, hrPoints.lastOrNull()?.x ?: -Double.MAX_VALUE),
            ),
            yAxis = AxisSpec(format = ChartValueFormat.DECIMAL, minimum = 0.0, maximum = MAX_INTENSITY, showLabels = false),
            endYAxis = if (hrPoints.isEmpty()) null else AxisSpec(format = ChartValueFormat.INTEGER, minimum = hrMinimum, maximum = hrMaximum),
            limitLines = if (hrPoints.isNotEmpty() && showHrAverage && hrAverage > 0) {
                listOf(LimitLineSpec(value = hrAverage.toDouble(), color = hrAverageColor, axis = AxisSide.END))
            } else {
                emptyList()
            },
        )
    }
}
