package nodomain.freeyourgadget.gadgetbridge.activities.dashboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A day as two rings, midnight to noon inside and noon to midnight outside, or as one 24h ring.
 * The rings go clockwise from 12 o'clock.
 */
class DayClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class Kind(val thick: Boolean) {
        ACTIVITY(true),
        EXERCISE(true),
        LIGHT_SLEEP(true),
        DEEP_SLEEP(true),
        REM_SLEEP(true),
        WORN(false),
        NOT_WORN(false),
    }

    /**
     * A stretch of [kind] between seconds [start] and [end] after midnight.
     */
    data class Segment(val start: Int, val end: Int, val kind: Kind)

    private var segments: List<Segment> = emptyList()
    private var yesterday: List<Segment> = emptyList()
    private var now: Int? = null
    private var dimYesterday = true
    private var showMarker = true
    private var showLabels = false

    /**
     * The size of the clock when there is room for it.
     */
    var maxSizeDp: Float = SIZE_DP
        set(value) {
            field = value
            requestLayout()
        }

    /**
     * Whether the day is one 24h ring instead of two 12h rings.
     */
    var mode24h: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /**
     * Whether midnight is at the bottom of the 24h ring.
     */
    var upsideDown: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /**
     * The size of the hour labels when the clock is at [maxSizeDp].
     */
    var labelSp: Float = LABEL_SP
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val oval = RectF()

    private val futureColor = colorOf(context, null)
    private val innerLabelColor = GBApplication.getSecondaryTextColor(context)
    private val outerLabelColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant)
    private val markerColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)
    private val markerRingColor = MaterialColors.getColor(this, R.attr.stat_tile_bg, Color.BLACK)

    /**
     * Shows [segments], with the time after [now] (seconds after midnight) as future; null for a past day. The
     * [yesterday] stretches are drawn in the future, dimmed when [dimYesterday] is set.
     */
    fun setDay(segments: List<Segment>, now: Int?, yesterday: List<Segment>, dimYesterday: Boolean, showMarker: Boolean) {
        this.segments = segments
        this.now = now
        this.yesterday = yesterday
        this.dimYesterday = dimYesterday
        this.showMarker = showMarker
        showLabels = true
        invalidate()
    }

    /**
     * Only the base rings, while the day is loading.
     */
    fun setLoading() {
        segments = emptyList()
        yesterday = emptyList()
        now = 0
        showLabels = false
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxSize = (maxSizeDp * density).roundToInt()
        var size = min(MeasureSpec.getSize(widthMeasureSpec), maxSize)
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            size = min(size, MeasureSpec.getSize(heightMeasureSpec))
        }
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0) return
        val scale = width / CANVAS
        val end = now ?: DAY

        // Base rings: past time without data as not worn, the rest as future
        drawSpan(canvas, 0, end, colorOf(context, Kind.NOT_WORN), THIN, scale)
        drawSpan(canvas, end, DAY, futureColor, THIN, scale)
        drawSegments(canvas, yesterday, if (dimYesterday) YESTERDAY_ALPHA else OPAQUE, scale)
        drawSegments(canvas, segments, OPAQUE, scale)

        if (showLabels) {
            val fontSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, labelSp, resources.displayMetrics) * width / (maxSizeDp * density)
            labelPaint.textSize = fontSize
            if (mode24h) {
                labelPaint.color = outerLabelColor
                labelPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                for (hour in 0 until 24 step 3) {
                    drawLabel(canvas, hour, DAY_LABEL_RADIUS, scale, fontSize)
                }
            } else {
                labelPaint.color = innerLabelColor
                labelPaint.typeface = Typeface.DEFAULT
                for (hour in intArrayOf(0, 3, 6, 9)) {
                    drawLabel(canvas, hour, INNER_LABEL_RADIUS, scale, fontSize)
                }
                labelPaint.color = outerLabelColor
                labelPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                for (hour in intArrayOf(12, 15, 18, 21)) {
                    drawLabel(canvas, hour, OUTER_LABEL_RADIUS, scale, fontSize)
                }
            }
        }

        val current = now
        if (showLabels && showMarker && current != null && current < DAY) {
            val radius = if (current < HALF_DAY && !mode24h) INNER_RADIUS else OUTER_RADIUS
            val angle = Math.toRadians(angleOf(current).toDouble())
            val x = (CENTER + radius * cos(angle).toFloat()) * scale
            val y = (CENTER + radius * sin(angle).toFloat()) * scale
            fillPaint.color = markerRingColor
            canvas.drawCircle(x, y, (MARKER_RADIUS + MARKER_STROKE) * scale, fillPaint)
            fillPaint.color = markerColor
            canvas.drawCircle(x, y, MARKER_RADIUS * scale, fillPaint)
        }
    }

    private fun drawSegments(canvas: Canvas, segments: List<Segment>, alpha: Int, scale: Float) {
        for (segment in segments.filter { !it.kind.thick }) {
            drawSpan(canvas, segment.start, segment.end, colorOf(context, segment.kind, alpha), THIN, scale)
        }
        for (segment in segments.filter { it.kind.thick }) {
            drawSpan(canvas, segment.start, segment.end, colorOf(context, segment.kind, alpha), THICK, scale)
        }
    }

    /**
     * Draws [start]..[end] on the inner ring up to noon and on the outer ring after, or on the outer ring in
     * [mode24h].
     */
    private fun drawSpan(canvas: Canvas, start: Int, end: Int, color: Int, stroke: Float, scale: Float) {
        if (end <= start) return
        arcPaint.color = color
        arcPaint.strokeWidth = stroke * scale
        if (mode24h) {
            drawArc(canvas, start, end, OUTER_RADIUS, scale)
            return
        }
        if (start < HALF_DAY) {
            drawArc(canvas, start, min(end, HALF_DAY), INNER_RADIUS, scale)
        }
        if (end > HALF_DAY) {
            drawArc(canvas, maxOf(start, HALF_DAY), end, OUTER_RADIUS, scale)
        }
    }

    private fun drawArc(canvas: Canvas, start: Int, end: Int, radius: Float, scale: Float) {
        if (end <= start) return
        oval.set((CENTER - radius) * scale, (CENTER - radius) * scale, (CENTER + radius) * scale, (CENTER + radius) * scale)
        canvas.drawArc(oval, angleOf(start), (end - start) * FULL_TURN / turnSeconds(), false, arcPaint)
    }

    private fun drawLabel(canvas: Canvas, hour: Int, radius: Float, scale: Float, fontSize: Float) {
        val angle = Math.toRadians(angleOf(hour * HOUR).toDouble())
        val x = (CENTER + radius * cos(angle).toFloat()) * scale
        val y = (CENTER + radius * sin(angle).toFloat()) * scale + LABEL_BASELINE * fontSize
        canvas.drawText(hour.toString(), x, y, labelPaint)
    }

    private fun turnSeconds() = if (mode24h) DAY else HALF_DAY

    /**
     * Clock angle of [seconds] after midnight, 12 o'clock being -90º.
     */
    private fun angleOf(seconds: Int): Float {
        val angle = (seconds % turnSeconds()) * FULL_TURN / turnSeconds() - FULL_TURN / 4
        return if (mode24h && upsideDown) angle + FULL_TURN / 2 else angle
    }

    companion object {
        private const val SIZE_DP = 264f
        private const val CANVAS = 300f
        private const val CENTER = 150f
        private const val INNER_RADIUS = 74f
        private const val OUTER_RADIUS = 108f
        private const val INNER_LABEL_RADIUS = 50f
        private const val OUTER_LABEL_RADIUS = 132f
        private const val DAY_LABEL_RADIUS = 82f
        private const val THIN = 8f
        private const val THICK = 16f
        private const val MARKER_RADIUS = 5f
        private const val MARKER_STROKE = 2f
        private const val LABEL_SP = 11f
        private const val LABEL_BASELINE = 0.36f
        private const val FULL_TURN = 360f
        const val HOUR = 3600
        const val HALF_DAY = 12 * HOUR
        const val DAY = 24 * HOUR

        /**
         * The colour of [kind]; null is the future.
         */
        /**
         * The colour of [kind], with its alpha scaled by [alpha] out of [OPAQUE].
         */
        fun colorOf(context: Context, kind: Kind?, alpha: Int): Int {
            val color = colorOf(context, kind)
            return ColorUtils.setAlphaComponent(color, Color.alpha(color) * alpha / OPAQUE)
        }

        fun colorOf(context: Context, kind: Kind?): Int {
            val track = MaterialColors.getColor(context, R.attr.gauge_track, ContextCompat.getColor(context, R.color.gauge_line_color))
            return when (kind) {
                Kind.ACTIVITY -> Color.rgb(0x4A, 0xDE, 0x80)
                Kind.EXERCISE -> Color.rgb(0xFB, 0x92, 0x3C)
                Kind.LIGHT_SLEEP -> Color.rgb(0x38, 0xBD, 0xF8)
                Kind.DEEP_SLEEP -> Color.rgb(0x25, 0x63, 0xEB)
                Kind.REM_SLEEP -> Color.rgb(0xE8, 0x79, 0xF9)
                Kind.WORN -> ColorUtils.blendARGB(track, GBApplication.getSecondaryTextColor(context), WORN_BLEND)
                Kind.NOT_WORN -> track
                null -> ColorUtils.setAlphaComponent(track, FUTURE_ALPHA)
            }
        }

        private const val WORN_BLEND = 0.35f
        private const val FUTURE_ALPHA = 110
        const val YESTERDAY_ALPHA = 64
        const val OPAQUE = 255
    }
}
