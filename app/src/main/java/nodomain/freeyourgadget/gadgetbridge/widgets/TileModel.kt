package nodomain.freeyourgadget.gadgetbridge.widgets

import android.graphics.PointF

/**
 * What a gauge widget's tile shows, independent of the tile style.
 */
data class TileModel(
    val value: CharSequence,
    val gauge: GaugeSpec,
    val status: StatusLine? = null,
    val chart: MiniChart? = null,
    val contentDescription: String? = null,
    val largeChart: MiniChart? = null,
    val axis: List<String> = emptyList(),
    val legend: List<LegendRow> = emptyList(),
    val trailing: CharSequence? = null,
    val valueSuffix: CharSequence? = null,
    val miniStats: List<Stat> = emptyList(),
    val stats: List<Stat> = emptyList(),
    val gaugeBesideLegend: Boolean = false,
    val valueDimmed: Boolean = false,
    val valueNote: CharSequence? = null,
    val notes: List<CharSequence> = emptyList(),
)

/**
 * A secondary figure: [value] above its [label].
 */
data class Stat(val value: CharSequence, val label: CharSequence)

/**
 * A legend line of the tall tile: a dot in [color], [name] and [value].
 */
data class LegendRow(val color: Int, val name: CharSequence, val value: CharSequence, val square: Boolean = false)

sealed interface GaugeSpec {
    /**
     * A single arc up to [fraction], or only the track when [fraction] is negative.
     */
    data class Progress(val color: Int, val fraction: Float) : GaugeSpec

    /**
     * Consecutive [segments] in [colors], with a marker at [marker] unless it is negative. The marker takes its
     * zone's colour unless [markerColor] is set.
     */
    class Zones(
        val colors: IntArray,
        val segments: FloatArray,
        val marker: Float = -1f,
        val gapBetweenSegments: Boolean = true,
        val markerColor: Int? = null,
    ) : GaugeSpec

    companion object {
        val EMPTY: GaugeSpec = Progress(0, -1f)
    }
}

sealed interface StatusLine {
    data class Text(val text: CharSequence) : StatusLine
    data class Chip(val color: Int, val text: CharSequence) : StatusLine
}

sealed interface MiniChart {
    data class Progress(val color: Int, val fraction: Float) : MiniChart

    class Composition(val colors: IntArray, val values: FloatArray) : MiniChart

    class ZoneBar(val colors: IntArray, val segments: FloatArray, val marker: Float) : MiniChart

    /**
     * [colors] holds one colour, or one per bar. NaN values and slots from [dataUntil] on draw a stub.
     */
    class Bars(
        val values: FloatArray,
        val colors: IntArray,
        val min: Float,
        val max: Float,
        val band: Band? = null,
        val highlightLast: Boolean = false,
        val dataUntil: Int = values.size,
    ) : MiniChart

    /**
     * Two stacked series per slot, [bottom] below [top], scaled to [max]. Slots from [dataUntil] on draw a stub.
     * [reference] draws a dashed line across the chart at that value.
     */
    class StackedBars(
        val bottom: FloatArray,
        val top: FloatArray,
        val bottomColor: Int,
        val topColor: Int,
        val max: Float,
        val dataUntil: Int = bottom.size,
        val reference: Float? = null,
        val referenceColor: Int = 0,
    ) : MiniChart

    /**
     * One bar per slot and [RangeSeries] from its low to its high value, the series drawn over each other.
     * Slots without any range draw a stub.
     */
    class RangeBars(val series: List<RangeSeries>, val min: Float, val max: Float) : MiniChart

    /**
     * Stretches of a night on [colors].size lanes, the first lane at the top, between [start] and [end].
     */
    class Hypnogram(
        val segments: List<HypnogramSegment>,
        val start: Long,
        val end: Long,
        val colors: IntArray,
    ) : MiniChart

    /**
     * A NaN y breaks the line. [reference] draws a dashed line across the chart at that y. [secondary] is a thinner
     * line drawn below the main one, [rangeBand] an area whose limits change along x, and [dots] marks every point.
     */
    class Line(
        val points: List<PointF>,
        val xMin: Float,
        val xMax: Float,
        val yMin: Float,
        val yMax: Float,
        val color: Int,
        val fill: Boolean = false,
        val bands: List<Band> = emptyList(),
        val reference: Float? = null,
        val referenceColor: Int = 0,
        val lineWidthDp: Float = 2f,
        val secondary: List<PointF> = emptyList(),
        val secondaryColor: Int = 0,
        val rangeBand: RangeBand? = null,
        val dots: Boolean = false,
    ) : MiniChart
}

data class Band(val from: Float, val to: Float, val color: Int)

/**
 * A range from [low] to [high] per slot. A NaN limit leaves the slot empty.
 */
class RangeSeries(val low: FloatArray, val high: FloatArray, val color: Int)

/**
 * An area from [low] to [high] at each [x]. A NaN limit breaks the area.
 */
class RangeBand(val x: FloatArray, val low: FloatArray, val high: FloatArray, val color: Int)

data class HypnogramSegment(val start: Long, val end: Long, val lane: Int)
