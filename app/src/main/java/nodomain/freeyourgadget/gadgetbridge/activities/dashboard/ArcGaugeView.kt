package nodomain.freeyourgadget.gadgetbridge.activities.dashboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import nodomain.freeyourgadget.gadgetbridge.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A 240° arc gauge, open at the bottom, with its single child (the value) centered inside the arc.
 */
class ArcGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    var maxGaugeWidth: Int = (DEFAULT_WIDTH_DP * resources.displayMetrics.density).roundToInt()
        set(value) {
            field = value
            requestLayout()
        }

    private val trackColor = MaterialColors.getColor(
        this, R.attr.gauge_track, ContextCompat.getColor(context, R.color.gauge_line_color)
    )
    private val density = resources.displayMetrics.density
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val oval = RectF()

    private var colors = IntArray(0)
    private var sections: List<Section> = emptyList()
    private var marker: Float = -1f
    private var markerColor = 0

    init {
        setWillNotDraw(false)
    }

    /**
     * A single arc from the start up to [fraction], or only the track when [fraction] is negative.
     */
    fun setProgress(color: Int, fraction: Float) {
        colors = intArrayOf(color)
        sections = if (fraction > 0) listOf(Section(0, 0f, min(fraction, 1f), roundStart = true, roundEnd = true)) else emptyList()
        marker = -1f
        invalidate()
    }

    /**
     * Consecutive [segments] (fractions of the sweep) in [colors], with a marker at [value] unless it is negative.
     */
    fun setZones(colors: IntArray, segments: FloatArray, value: Float, gapBetweenSegments: Boolean = true, markerColor: Int? = null) {
        this.colors = colors
        sections = sections(segments, if (gapBetweenSegments) SEGMENT_GAP else 0f)
        marker = if (value >= 0) min(value, 1f) else -1f
        this.markerColor = markerColor ?: zoneAt(segments, marker)?.let { colors[it] }
            ?: MaterialColors.getColor(this, R.attr.textColorPrimary, 0)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec)
        val width = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) maxGaugeWidth else min(available, maxGaugeWidth)
        val height = (width * VIEW_HEIGHT / VIEW_WIDTH).roundToInt()
        val childSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST)
        for (i in 0 until childCount) {
            getChildAt(i).measure(childSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST))
        }
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val width = right - left
        val centerY = width * VALUE_CENTER
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val childLeft = (width - child.measuredWidth) / 2
            val childTop = (centerY - child.measuredHeight / 2f).roundToInt()
            child.layout(childLeft, childTop, childLeft + child.measuredWidth, childTop + child.measuredHeight)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val scale = width / VIEW_WIDTH
        val radius = RADIUS * scale
        val centerX = CENTER_X * scale
        val centerY = CENTER_Y * scale
        oval.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        strokePaint.strokeWidth = STROKE * scale

        val layer = if (marker >= 0) canvas.saveLayer(null, null) else -1
        drawSection(canvas, 0f, 1f, trackColor, roundStart = true, roundEnd = true)
        for (section in sections) {
            drawSection(canvas, section.start, section.end, colors[section.index], section.roundStart, section.roundEnd)
        }
        if (marker >= 0) {
            val (x, y) = pointAt(marker)
            canvas.drawCircle(x, y, MARKER_CUTOUT_DP * density, clearPaint)
            fillPaint.color = markerColor
            canvas.drawCircle(x, y, MARKER_RADIUS_DP * density, fillPaint)
            canvas.restoreToCount(layer)
        }
    }

    private fun drawSection(canvas: Canvas, start: Float, end: Float, color: Int, roundStart: Boolean, roundEnd: Boolean) {
        if (end <= start) return
        strokePaint.color = color
        canvas.drawArc(oval, START_DEGREES + SWEEP_DEGREES * start, SWEEP_DEGREES * (end - start), false, strokePaint)
        fillPaint.color = color
        val capRadius = strokePaint.strokeWidth / 2
        if (roundStart) drawCap(canvas, start, capRadius, startCap = true)
        if (roundEnd) drawCap(canvas, end, capRadius, startCap = false)
    }

    private fun drawCap(canvas: Canvas, at: Float, capRadius: Float, startCap: Boolean) {
        val degrees = START_DEGREES + SWEEP_DEGREES * at
        val (x, y) = pointAt(at)
        canvas.drawArc(x - capRadius, y - capRadius, x + capRadius, y + capRadius, if (startCap) degrees - 180 else degrees, 180f, true, fillPaint)
    }

    private fun pointAt(fraction: Float): Pair<Float, Float> {
        val angle = Math.toRadians((START_DEGREES + SWEEP_DEGREES * fraction).toDouble())
        val radius = oval.width() / 2
        return oval.centerX() + radius * cos(angle).toFloat() to oval.centerY() + radius * sin(angle).toFloat()
    }

    data class Section(val index: Int, val start: Float, val end: Float, val roundStart: Boolean, val roundEnd: Boolean)

    companion object {
        private const val DEFAULT_WIDTH_DP = 118f
        private const val VIEW_WIDTH = 100f
        private const val VIEW_HEIGHT = 72f
        private const val CENTER_X = 50f
        private const val CENTER_Y = 46f
        private const val RADIUS = 40f
        private const val STROKE = 8f
        private const val VALUE_CENTER = 0.47f
        private const val START_DEGREES = 150f
        private const val SWEEP_DEGREES = 240f
        private const val MARKER_RADIUS_DP = 5f
        private const val MARKER_CUTOUT_DP = 7.5f
        const val SEGMENT_GAP = 0.022f

        fun zoneAt(segments: FloatArray, value: Float): Int? {
            if (value < 0) return null
            var start = 0f
            for (i in segments.indices) {
                val end = start + segments[i]
                if (segments[i] > 0 && value >= start && value <= end) return i
                start = end
            }
            return null
        }

        /**
         * The drawn part of each non-empty segment, with [gap] between neighbours and rounded outer ends.
         */
        fun sections(segments: FloatArray, gap: Float): List<Section> {
            val first = segments.indexOfFirst { it > 0 }
            val last = segments.indexOfLast { it > 0 }
            val result = mutableListOf<Section>()
            var start = 0f
            for (i in segments.indices) {
                val end = start + segments[i]
                if (segments[i] > 0) {
                    // A sum just under 1 is a rounding error, so the last segment still reaches the end
                    val reachedEnd = if (i == last && end >= 1 - gap) 1f else end
                    result += Section(
                        index = i,
                        start = if (i == first) start else start + gap / 2,
                        end = if (i == last) min(reachedEnd, 1f) else end - gap / 2,
                        roundStart = i == first,
                        roundEnd = i == last,
                    )
                }
                start = end
            }
            return result
        }
    }
}
