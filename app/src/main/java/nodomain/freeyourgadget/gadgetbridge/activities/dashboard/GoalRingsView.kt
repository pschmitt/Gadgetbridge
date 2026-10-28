package nodomain.freeyourgadget.gadgetbridge.activities.dashboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import nodomain.freeyourgadget.gadgetbridge.R
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Concentric goal rings, the first goal outermost, each filled clockwise from 12 o'clock.
 */
class GoalRingsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /**
     * A goal's progress: [fraction] of it reached, drawn in [color]. A negative fraction draws only the track.
     */
    data class Ring(val color: Int, val fraction: Float)

    private var rings: List<Ring> = emptyList()

    var maxSizeDp: Float = SIZE_DP
        set(value) {
            field = value
            requestLayout()
        }

    private val density = resources.displayMetrics.density
    private val trackColor = MaterialColors.getColor(this, R.attr.gauge_track, ContextCompat.getColor(context, R.color.gauge_line_color))
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val oval = RectF()

    fun setRings(rings: List<Ring>) {
        this.rings = rings.take(RADII.size)
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
        paint.strokeWidth = STROKE * scale
        for ((index, ring) in rings.withIndex()) {
            val radius = RADII[index]
            val center = CENTER * scale
            paint.color = trackColor
            canvas.drawCircle(center, center, radius * scale, paint)
            if (ring.fraction <= 0f) continue
            paint.color = ring.color
            if (ring.fraction >= 1f) {
                canvas.drawCircle(center, center, radius * scale, paint)
            } else {
                oval.set((CENTER - radius) * scale, (CENTER - radius) * scale, (CENTER + radius) * scale, (CENTER + radius) * scale)
                canvas.drawArc(oval, START_ANGLE, FULL_TURN * ring.fraction, false, paint)
            }
        }
    }

    companion object {
        private const val SIZE_DP = 288f
        private const val CANVAS = 240f
        private const val CENTER = 120f
        private const val STROKE = 16f
        private const val START_ANGLE = -90f
        private const val FULL_TURN = 360f
        private val RADII = floatArrayOf(104f, 84f, 64f, 44f)
    }
}
