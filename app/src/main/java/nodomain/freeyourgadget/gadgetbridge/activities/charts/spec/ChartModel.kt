package nodomain.freeyourgadget.gadgetbridge.activities.charts.spec

/**
 * A (x, y) data point. [x] is usually an epoch-second timestamp.
 */
data class ChartPoint(val x: Double, val y: Double, val low: Double? = null)

/**
 * How one [ChartSeries] should be drawn.
 */
sealed interface SeriesStyle {
    /**
     * A line, optionally filled underneath (an area chart), curved, or with a dot on every point. A filled line
     * with a [fillBase] is a translucent band from that value to the line.
     */
    data class Line(
        val color: Int,
        val filled: Boolean = false,
        val curve: LineCurve = LineCurve.STRAIGHT,
        val showPoints: Boolean = false,
        val maxGap: Double? = null,
        val showLine: Boolean = true,
        val solidFill: Boolean = false,
        val fillBase: Double? = null,
    ) : SeriesStyle

    /**
     * A column (bar). [stackKey] groups columns that stack together at the same x value.
     */
    data class Column(
        val color: Int,
        val stackKey: String? = null,
    ) : SeriesStyle

    /**
     * Each point's [ChartPoint.low] to its y. Without a [width] it's a full-width band, which can't share a chart
     * with [Column] series.
     */
    data class Range(val color: Int, val width: Float? = null) : SeriesStyle

    /**
     * A hypnogram: each point's row y lasts until the next point, coloured by [colors]. A y outside [colors] is a gap.
     */
    data class Stages(val colors: List<Int>) : SeriesStyle
}

/**
 * How a [SeriesStyle.Line] connects two neighbouring points.
 */
enum class LineCurve {
    /**
     * A straight segment.
     */
    STRAIGHT,

    /**
     * A curve that is flat at each point.
     */
    FLAT_AT_POINTS,

    /**
     * A curve that follows the slope through each point.
     */
    SMOOTH,
}

/**
 * Which y axis a [ChartSeries] is plotted against.
 */
enum class AxisSide { START, END }

/**
 * One drawable series: a label, its points, and the style to draw them.
 */
data class ChartSeries(
    val key: String,
    val label: String,
    val points: List<ChartPoint>,
    val style: SeriesStyle,
    val axis: AxisSide = AxisSide.START,
    val selectable: Boolean = true,
)

/**
 * How to format an axis' values.
 */
enum class ChartValueFormat {
    TIME_OF_DAY,
    DATE,
    DURATION_SECONDS,
    DECIMAL,
    ONE_DECIMAL,
    INTEGER,

    /**
     * An ISO day of the week, 1 (Monday) to 7 (Sunday), shown as its short name.
     */
    DAY_OF_WEEK,

    /**
     * A day of the month, 1 to 31. See [dayOfMonthValues] for the days that get a label.
     */
    DAY_OF_MONTH,

    /**
     * A month of the year, 1 to 12, shown as its narrow name (a single letter in most locales).
     */
    MONTH_OF_YEAR,

    /**
     * [java.time.LocalDate.toEpochDay]. Day names up to a week, day of month up to two months, months beyond.
     */
    EPOCH_DAY,

    /**
     * A duration in minutes, as `H:MM`.
     */
    DURATION_MINUTES,

    /**
     * A 1-based bar index, shown with [AxisSpec.labeler]. Up to six labels, evenly spaced.
     */
    BAR_INDEX,
}

data class AxisSpec(
    val format: ChartValueFormat = ChartValueFormat.DECIMAL,
    val minimum: Double? = null,
    val maximum: Double? = null,
    val unit: String? = null,
    val showLabels: Boolean = true,
    val labeler: ((Double) -> String)? = null,
)

/**
 * A horizontal limit line, e.g. goal / average.
 */
data class LimitLineSpec(
    val value: Double,
    val color: Int,
    val dashed: Boolean = true,
    val axis: AxisSide = AxisSide.START,
)

/**
 * A vertical line at x [value], e.g. where a workout interval starts. The [label] sits at the top or [labelAtBottom]
 * of the plot, right of the line or [labelBeforeLine].
 */
data class XMarkerSpec(
    val value: Double,
    val color: Int,
    val label: String? = null,
    val labelAtBottom: Boolean = false,
    val labelBeforeLine: Boolean = false,
)

/**
 * One legend. Usually mirrors a [ChartSeries] one-to-one, but might not (e.g. stress charts).
 */
data class LegendItemSpec(
    val label: String,
    val color: Int,
)

/**
 * A library-independent chart specification. [endYAxis] is the axis of the [AxisSide.END] series, if any.
 */
data class ChartSpec(
    val series: List<ChartSeries>,
    val xAxis: AxisSpec = AxisSpec(format = ChartValueFormat.TIME_OF_DAY),
    val yAxis: AxisSpec = AxisSpec(),
    val endYAxis: AxisSpec? = null,
    val limitLines: List<LimitLineSpec> = emptyList(),
    val xMarkers: List<XMarkerSpec> = emptyList(),
    val legend: List<LegendItemSpec> = emptyList(),
) {
    val isEmpty: Boolean get() = series.all { it.points.isEmpty() }

    companion object {
        /**
         * A [ChartSpec] with no series, rendered as the "no data" placeholder.
         */
        val EMPTY = ChartSpec(series = emptyList())
    }
}
