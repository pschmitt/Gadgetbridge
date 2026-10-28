package nodomain.freeyourgadget.gadgetbridge.activities.dashboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.android.material.color.MaterialColors
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.DayClockView.Kind
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A day as one band from midnight on the left to midnight on the right, with the same stretches as
 * [DayClockView].
 */
class DayTimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var segments: List<DayClockView.Segment> = emptyList()
    private var yesterday: List<DayClockView.Segment> = emptyList()
    private var now: Int? = null
    private var dimYesterday = true
    private var showMarker = true
    private var rowDp = ROW_DP
    private var baseDp = BASE_DP

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val futureColor = DayClockView.colorOf(context, null)
    private val tickColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline)
    private val markerColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface)

    /**
     * Shows [segments], with the time after [now] (seconds after midnight) as future; null for a past day.
     * The [yesterday] stretches are drawn in the future, dimmed when [dimYesterday] is set.
     */
    fun setDay(
        segments: List<DayClockView.Segment>,
        now: Int?,
        yesterday: List<DayClockView.Segment>,
        dimYesterday: Boolean,
        showMarker: Boolean,
    ) {
        this.segments = segments
        this.now = now
        this.yesterday = yesterday
        this.dimYesterday = dimYesterday
        this.showMarker = showMarker
        invalidate()
    }

    /**
     * The height of the sleep and activity band and of the worn band within it. The view grows to fit them.
     */
    fun setBands(rowDp: Float, baseDp: Float) {
        if (this.rowDp == rowDp && this.baseDp == baseDp) return
        this.rowDp = rowDp
        this.baseDp = baseDp
        layoutParams?.let { it.height = ((MARKER_OVERHANG_DP + rowDp + TICK_BOTTOM_GAP_DP) * density).roundToInt() }
        requestLayout()
        invalidate()
    }

    fun setLoading() {
        segments = emptyList()
        yesterday = emptyList()
        now = 0
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0) return
        val top = MARKER_OVERHANG_DP * density
        val rowHeight = rowDp * density
        val baseTop = top + (rowHeight - baseDp * density) / 2
        val baseBottom = baseTop + baseDp * density
        val end = now ?: DayClockView.DAY

        fun x(seconds: Int) = seconds.toFloat() / DayClockView.DAY * width
        fun band(start: Int, stop: Int, color: Int, bandTop: Float, bandBottom: Float, minWidth: Float = 0f) {
            if (stop <= start) return
            paint.color = color
            canvas.drawRect(x(start), bandTop, max(x(stop), x(start) + minWidth), bandBottom, paint)
        }

        band(0, end, DayClockView.colorOf(context, Kind.NOT_WORN), baseTop, baseBottom)
        band(end, DayClockView.DAY, futureColor, baseTop, baseBottom)
        fun bands(segments: List<DayClockView.Segment>, alpha: Int) {
            for (segment in segments.filter { !it.kind.thick }) {
                band(segment.start, segment.end, DayClockView.colorOf(context, segment.kind, alpha), baseTop, baseBottom)
            }
            // Higher priority kinds draw last
            for (kinds in THICK_ORDER) {
                for (segment in segments.filter { it.kind in kinds }) {
                    band(segment.start, segment.end, DayClockView.colorOf(context, segment.kind, alpha), top, top + rowHeight, MIN_SEGMENT_DP * density)
                }
            }
        }
        bands(yesterday, if (dimYesterday) DayClockView.YESTERDAY_ALPHA else DayClockView.OPAQUE)
        bands(segments, DayClockView.OPAQUE)

        linePaint.color = tickColor
        linePaint.strokeWidth = TICK_DP * density
        for (hour in intArrayOf(6, 12, 18)) {
            val tickX = x(hour * DayClockView.HOUR)
            canvas.drawLine(tickX, top + rowHeight + TICK_TOP_GAP_DP * density, tickX, top + rowHeight + TICK_BOTTOM_GAP_DP * density, linePaint)
        }

        val current = now
        if (showMarker && current != null && current < DayClockView.DAY) {
            linePaint.color = markerColor
            linePaint.strokeWidth = MARKER_DP * density
            val markerX = x(current)
            canvas.drawLine(markerX, top - MARKER_OVERHANG_DP * density, markerX, top + rowHeight + MARKER_BOTTOM_GAP_DP * density, linePaint)
        }
    }

    companion object {
        const val ROW_DP = 20f
        const val BASE_DP = 8f
        private const val MIN_SEGMENT_DP = 1.5f
        private const val TICK_DP = 1f
        private const val TICK_TOP_GAP_DP = 2f
        private const val TICK_BOTTOM_GAP_DP = 5f
        private const val MARKER_DP = 2f
        private const val MARKER_OVERHANG_DP = 3f
        private const val MARKER_BOTTOM_GAP_DP = 3f

        private val THICK_ORDER = listOf(
            setOf(Kind.LIGHT_SLEEP, Kind.DEEP_SLEEP, Kind.REM_SLEEP),
            setOf(Kind.ACTIVITY),
            setOf(Kind.EXERCISE),
        )
    }
}
