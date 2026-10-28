package nodomain.freeyourgadget.gadgetbridge.activities.dashboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.widgets.MiniChart
import kotlin.math.max
import kotlin.math.min

/**
 * Draws a dashboard tile's [MiniChart]: a thin bar, small bars or a small line.
 */
class MiniChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var chart: MiniChart? = null
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val trackColor = MaterialColors.getColor(
        this, R.attr.gauge_track, ContextCompat.getColor(context, R.color.gauge_line_color)
    )
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = LINE_WIDTH_DP * density
    }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val referencePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = REFERENCE_WIDTH_DP * density
        pathEffect = DashPathEffect(floatArrayOf(REFERENCE_DASH_DP * density, REFERENCE_DASH_DP * density), 0f)
    }
    private val rect = RectF()
    private val linePath = Path()
    private val areaPath = Path()
    private val clipPath = Path()

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        when (val chart = chart) {
            null -> Unit
            is MiniChart.Progress -> drawProgress(canvas, chart)
            is MiniChart.Composition -> drawComposition(canvas, chart.colors, chart.values)
            is MiniChart.ZoneBar -> drawZoneBar(canvas, chart)
            is MiniChart.Bars -> drawBars(canvas, chart)
            is MiniChart.Line -> drawLine(canvas, chart)
            is MiniChart.StackedBars -> drawStackedBars(canvas, chart)
            is MiniChart.RangeBars -> drawRangeBars(canvas, chart)
            is MiniChart.Hypnogram -> drawHypnogram(canvas, chart)
        }
    }

    private fun drawStackedBars(canvas: Canvas, chart: MiniChart.StackedBars) {
        val count = chart.bottom.size
        if (count == 0) return
        val chartHeight = height.toFloat()
        val slot = width.toFloat() / count
        val barWidth = slot * BAR_WIDTH_FRACTION
        val radius = BAR_RADIUS_DP * density
        val gap = STACK_GAP_DP * density
        for (i in 0 until count) {
            val left = i * slot + (slot - barWidth) / 2
            val bottom = chart.bottom[i].takeUnless { it.isNaN() }?.coerceAtLeast(0f) ?: 0f
            val top = chart.top.getOrNull(i)?.takeUnless { it.isNaN() }?.coerceAtLeast(0f) ?: 0f
            val total = bottom + top
            if (i >= chart.dataUntil || total <= 0f) {
                drawStub(canvas, left, barWidth, chartHeight)
                continue
            }
            val totalHeight = barHeight(total, 0f, chart.max, chartHeight, MIN_BAR_DP * density)
            val stackGap = if (bottom > 0f && top > 0f) gap else 0f
            val bottomHeight = (totalHeight - stackGap).coerceAtLeast(0f) * bottom / total
            if (bottomHeight > 0f) {
                fillPaint.color = chart.bottomColor
                rect.set(left, chartHeight - bottomHeight, left + barWidth, chartHeight)
                canvas.drawRoundRect(rect, radius, radius, fillPaint)
            }
            val topStart = chartHeight - bottomHeight - stackGap
            val topHeight = if (top > 0f) max(totalHeight - bottomHeight - stackGap, MIN_BAR_DP * density) else 0f
            if (topHeight > 0f) {
                fillPaint.color = chart.topColor
                rect.set(left, topStart - topHeight, left + barWidth, topStart)
                canvas.drawRoundRect(rect, radius, radius, fillPaint)
            }
        }

        chart.reference?.takeIf { it in 0f..chart.max }?.let {
            referencePaint.color = chart.referenceColor
            val y = valueY(it, 0f, chart.max, chartHeight)
            canvas.drawLine(0f, y, width.toFloat(), y, referencePaint)
        }
    }

    private fun drawRangeBars(canvas: Canvas, chart: MiniChart.RangeBars) {
        val count = chart.series.maxOfOrNull { it.low.size } ?: 0
        if (count == 0) return
        val chartHeight = height.toFloat()
        val slot = width.toFloat() / count
        val barWidth = slot * BAR_WIDTH_FRACTION
        val radius = BAR_RADIUS_DP * density
        val minHeight = MIN_BAR_DP * density
        for (i in 0 until count) {
            val left = i * slot + (slot - barWidth) / 2
            var drawn = false
            for (series in chart.series) {
                val low = series.low.getOrNull(i) ?: Float.NaN
                val high = series.high.getOrNull(i) ?: Float.NaN
                if (low.isNaN() || high.isNaN()) continue
                var top = valueY(high, chart.min, chart.max, chartHeight)
                var bottom = valueY(low, chart.min, chart.max, chartHeight)
                if (bottom - top < minHeight) {
                    val center = (top + bottom) / 2
                    top = center - minHeight / 2
                    bottom = center + minHeight / 2
                }
                fillPaint.color = series.color
                rect.set(left, top, left + barWidth, bottom)
                canvas.drawRoundRect(rect, radius, radius, fillPaint)
                drawn = true
            }
            if (!drawn) {
                drawStub(canvas, left, barWidth, chartHeight)
            }
        }
    }

    private fun drawHypnogram(canvas: Canvas, chart: MiniChart.Hypnogram) {
        val lanes = chart.colors.size
        if (lanes == 0 || chart.end <= chart.start) return
        val laneHeight = height.toFloat() / lanes
        val barHeight = laneHeight * LANE_FILL
        val radius = BAR_RADIUS_DP * density
        val gap = SEGMENT_GAP_DP * density
        for (segment in chart.segments) {
            if (segment.lane !in 0 until lanes) continue
            val left = timeX(segment.start, chart.start, chart.end, width.toFloat())
            val right = timeX(segment.end, chart.start, chart.end, width.toFloat()) - gap
            if (right <= left) continue
            val top = segment.lane * laneHeight + (laneHeight - barHeight) / 2
            fillPaint.color = chart.colors[segment.lane]
            rect.set(left, top, right, top + barHeight)
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
        }
    }

    private fun drawStub(canvas: Canvas, left: Float, barWidth: Float, chartHeight: Float) {
        fillPaint.color = trackColor
        val stub = STUB_HEIGHT_DP * density
        rect.set(left, chartHeight - stub, left + barWidth, chartHeight)
        canvas.drawRoundRect(rect, stub / 2, stub / 2, fillPaint)
    }

    private fun barTop() = (height - BAR_HEIGHT_DP * density) / 2

    private fun drawProgress(canvas: Canvas, chart: MiniChart.Progress) {
        val top = barTop()
        val barHeight = BAR_HEIGHT_DP * density
        val radius = barHeight / 2
        fillPaint.color = trackColor
        rect.set(0f, top, width.toFloat(), top + barHeight)
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        if (chart.fraction > 0) {
            fillPaint.color = chart.color
            rect.right = max(barHeight, width * min(chart.fraction, 1f))
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
        }
    }

    private fun drawComposition(canvas: Canvas, colors: IntArray, values: FloatArray) {
        val top = barTop()
        val barHeight = BAR_HEIGHT_DP * density
        val radius = barHeight / 2
        val widths = compositionWidths(values, width.toFloat(), GAP_DP * density, MIN_SEGMENT_DP * density)
        if (widths.all { it == 0f }) {
            fillPaint.color = trackColor
            rect.set(0f, top, width.toFloat(), top + barHeight)
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
            return
        }
        clipPath.reset()
        rect.set(0f, top, width.toFloat(), top + barHeight)
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.save()
        canvas.clipPath(clipPath)
        var x = 0f
        for (i in widths.indices) {
            if (widths[i] == 0f) continue
            fillPaint.color = colors[min(i, colors.size - 1)]
            canvas.drawRect(x, top, x + widths[i], top + barHeight, fillPaint)
            x += widths[i] + GAP_DP * density
        }
        canvas.restore()
    }

    private fun drawZoneBar(canvas: Canvas, chart: MiniChart.ZoneBar) {
        if (chart.marker < 0) {
            drawComposition(canvas, chart.colors, chart.segments)
            return
        }
        val inset = min(MARKER_RADIUS_DP * density, width / 2f)
        val x = (width * min(chart.marker, 1f)).coerceIn(inset, width - inset)
        val y = height / 2f
        val layer = canvas.saveLayer(null, null)
        drawComposition(canvas, chart.colors, chart.segments)
        canvas.drawCircle(x, y, (MARKER_RADIUS_DP + MARKER_RING_DP) * density, clearPaint)
        canvas.restoreToCount(layer)
        fillPaint.color = ArcGaugeView.zoneAt(chart.segments, min(chart.marker, 1f))?.let { chart.colors[it] } ?: trackColor
        canvas.drawCircle(x, y, MARKER_RADIUS_DP * density, fillPaint)
    }

    private fun drawBars(canvas: Canvas, chart: MiniChart.Bars) {
        val count = chart.values.size
        if (count == 0) return
        val chartHeight = height.toFloat()
        val slot = width.toFloat() / count
        val barWidth = slot * BAR_WIDTH_FRACTION
        val radius = BAR_RADIUS_DP * density

        chart.band?.let { band ->
            clipBand(band.from, band.to, chart.min, chart.max)?.let { (from, to) ->
                fillPaint.color = band.color
                canvas.drawRect(0f, valueY(to, chart.min, chart.max, chartHeight), width.toFloat(), valueY(from, chart.min, chart.max, chartHeight), fillPaint)
            }
        }

        for (i in 0 until count) {
            val left = i * slot + (slot - barWidth) / 2
            val value = chart.values[i]
            if (i >= chart.dataUntil || value.isNaN() || value <= 0f) {
                drawStub(canvas, left, barWidth, chartHeight)
                continue
            }
            val barHeight = barHeight(value, chart.min, chart.max, chartHeight, MIN_BAR_DP * density)
            val color = chart.colors[if (chart.colors.size == 1) 0 else min(i, chart.colors.size - 1)]
            fillPaint.color = if (chart.highlightLast && i != count - 1) ColorUtils.setAlphaComponent(color, DIMMED_ALPHA) else color
            rect.set(left, chartHeight - barHeight, left + barWidth, chartHeight)
            canvas.drawRoundRect(rect, radius, radius, fillPaint)
        }
    }

    private fun drawLine(canvas: Canvas, chart: MiniChart.Line) {
        val inset = (DOT_RADIUS_DP + DOT_RING_DP) * density
        val left = inset
        val right = width - inset
        val top = inset
        val bottom = height - inset
        fun xOf(x: Float) = if (chart.xMax > chart.xMin) left + (x - chart.xMin) / (chart.xMax - chart.xMin) * (right - left) else (left + right) / 2
        fun yOf(y: Float) = if (chart.yMax > chart.yMin) bottom - (y - chart.yMin) / (chart.yMax - chart.yMin) * (bottom - top) else (top + bottom) / 2

        val layer = canvas.saveLayer(null, null)
        for (band in chart.bands) {
            val (from, to) = clipBand(band.from, band.to, chart.yMin, chart.yMax) ?: continue
            fillPaint.color = band.color
            canvas.drawRect(0f, yOf(to), width.toFloat(), yOf(from), fillPaint)
        }
        chart.reference?.takeIf { it in chart.yMin..chart.yMax }?.let {
            referencePaint.color = chart.referenceColor
            canvas.drawLine(0f, yOf(it), width.toFloat(), yOf(it), referencePaint)
        }
        chart.rangeBand?.let { band ->
            fillPaint.color = band.color
            var start = 0
            while (start < band.x.size) {
                var end = start
                while (end < band.x.size && !band.low[end].isNaN() && !band.high[end].isNaN()) end++
                if (end > start) {
                    areaPath.reset()
                    for (i in start until end) {
                        val y = yOf(band.high[i].coerceIn(chart.yMin, chart.yMax))
                        if (i == start) areaPath.moveTo(xOf(band.x[i]), y) else areaPath.lineTo(xOf(band.x[i]), y)
                    }
                    for (i in end - 1 downTo start) {
                        areaPath.lineTo(xOf(band.x[i]), yOf(band.low[i].coerceIn(chart.yMin, chart.yMax)))
                    }
                    areaPath.close()
                    canvas.drawPath(areaPath, fillPaint)
                }
                start = end + 1
            }
        }
        if (chart.secondary.isNotEmpty()) {
            linePath.reset()
            var down = false
            for (point in chart.secondary) {
                if (point.y.isNaN()) {
                    down = false
                    continue
                }
                val x = xOf(point.x)
                val y = yOf(point.y.coerceIn(chart.yMin, chart.yMax))
                if (down) linePath.lineTo(x, y) else linePath.moveTo(x, y)
                down = true
            }
            linePaint.color = chart.secondaryColor
            linePaint.strokeWidth = SECONDARY_LINE_WIDTH_DP * density
            canvas.drawPath(linePath, linePaint)
        }

        linePath.reset()
        areaPath.reset()
        var penDown = false
        var lastX = 0f
        var lastY = 0f
        var hasLast = false
        for (point in chart.points) {
            if (point.y.isNaN()) {
                if (penDown) closeArea(lastX, height.toFloat())
                penDown = false
                continue
            }
            val x = xOf(point.x)
            val y = yOf(point.y.coerceIn(chart.yMin, chart.yMax))
            if (penDown) {
                linePath.lineTo(x, y)
                areaPath.lineTo(x, y)
            } else {
                linePath.moveTo(x, y)
                areaPath.moveTo(x, height.toFloat())
                areaPath.lineTo(x, y)
                penDown = true
            }
            lastX = x
            lastY = y
            hasLast = true
        }
        if (penDown) closeArea(lastX, height.toFloat())

        if (chart.fill) {
            fillPaint.color = ColorUtils.setAlphaComponent(chart.color, FILL_ALPHA)
            canvas.drawPath(areaPath, fillPaint)
        }
        linePaint.color = chart.color
        linePaint.strokeWidth = chart.lineWidthDp * density
        canvas.drawPath(linePath, linePaint)
        if (chart.dots) {
            fillPaint.color = chart.color
            val known = chart.points.filter { !it.y.isNaN() }
            for (point in known.dropLast(1)) {
                canvas.drawCircle(xOf(point.x), yOf(point.y.coerceIn(chart.yMin, chart.yMax)), SMALL_DOT_RADIUS_DP * density, fillPaint)
            }
        }
        if (hasLast) {
            canvas.drawCircle(lastX, lastY, (DOT_RADIUS_DP + DOT_RING_DP) * density, clearPaint)
        }
        canvas.restoreToCount(layer)
        if (hasLast) {
            fillPaint.color = chart.color
            canvas.drawCircle(lastX, lastY, DOT_RADIUS_DP * density, fillPaint)
        }
    }

    private fun closeArea(x: Float, bottom: Float) {
        areaPath.lineTo(x, bottom)
        areaPath.close()
    }

    private fun valueY(value: Float, low: Float, high: Float, height: Float): Float =
        height - ((value - low) / (high - low)).coerceIn(0f, 1f) * height

    companion object {
        private const val BAR_HEIGHT_DP = 6f
        private const val GAP_DP = 2f
        private const val MIN_SEGMENT_DP = 3f
        private const val MARKER_RADIUS_DP = 4f
        private const val MARKER_RING_DP = 1.5f
        private const val BAR_WIDTH_FRACTION = 0.7f
        private const val BAR_RADIUS_DP = 1.5f
        private const val STUB_HEIGHT_DP = 1.5f
        private const val MIN_BAR_DP = 2f
        private const val LINE_WIDTH_DP = 2f
        private const val DOT_RADIUS_DP = 2.5f
        private const val DOT_RING_DP = 1.5f
        private const val SMALL_DOT_RADIUS_DP = 1.6f
        private const val SECONDARY_LINE_WIDTH_DP = 1.5f
        private const val DIMMED_ALPHA = 115
        private const val FILL_ALPHA = 31
        private const val STACK_GAP_DP = 1f
        private const val REFERENCE_WIDTH_DP = 1f
        private const val REFERENCE_DASH_DP = 3f
        private const val SEGMENT_GAP_DP = 0.6f
        private const val LANE_FILL = 0.7f

        /**
         * Horizontal position of [time] on a [start]..[end] axis [width] wide, clamped to it.
         */
        fun timeX(time: Long, start: Long, end: Long, width: Float): Float =
            ((time - start).toFloat() / (end - start)).coerceIn(0f, 1f) * width

        /**
         * Segment widths for [values] over [width]: shares of the room left after the gaps, each non-zero one at
         * least [minWidth], zero for empty values.
         */
        fun compositionWidths(values: FloatArray, width: Float, gap: Float, minWidth: Float): FloatArray {
            val result = FloatArray(values.size)
            var nonZero = 0
            var total = 0f
            for (value in values) {
                if (value > 0f) {
                    nonZero++
                    total += value
                }
            }
            if (nonZero == 0) return result
            val available = width - gap * (nonZero - 1)
            var smallCount = 0
            var smallTotal = 0f
            for (value in values) {
                if (value > 0f && value / total * available < minWidth) {
                    smallCount++
                    smallTotal += value
                }
            }
            val rest = available - smallCount * minWidth
            val restTotal = total - smallTotal
            for (i in values.indices) {
                val value = values[i]
                if (value <= 0f) continue
                result[i] = if (value / total * available < minWidth || restTotal <= 0f) minWidth else value / restTotal * rest
            }
            return result
        }

        /**
         * Height of a bar for [value] on a [low]..[high] scale, at least [minHeight].
         */
        fun barHeight(value: Float, low: Float, high: Float, height: Float, minHeight: Float): Float {
            if (high <= low) return minHeight
            return max(minHeight, ((value - low) / (high - low)).coerceIn(0f, 1f) * height)
        }

        /**
         * The part of [from]..[to] inside [low]..[high], or null if none of it is.
         */
        fun clipBand(from: Float, to: Float, low: Float, high: Float): Pair<Float, Float>? {
            val clippedFrom = max(from, low)
            val clippedTo = min(to, high)
            return if (clippedTo > clippedFrom) clippedFrom to clippedTo else null
        }
    }
}
