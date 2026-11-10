/*  Copyright (C) 2026 Pedro

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.activities.soundpeats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import nodomain.freeyourgadget.gadgetbridge.R
import kotlin.math.roundToInt

/**
 * Vertical slider used by the equalizer editor, with 0 dB in the middle and the value filled from
 * the centre outwards, like the sliders of any other equalizer.
 */
class SoundpeatsBandSlider @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    var minDb: Float = -12f
        set(value) {
            field = value
            invalidate()
        }

    var maxDb: Float = 12f
        set(value) {
            field = value
            invalidate()
        }

    var value: Float = 0f
        set(value) {
            field = value.coerceIn(minDb, maxDb)
            invalidate()
        }

    /** Called while dragging, and once more with `released = true` when the finger lifts. */
    var onValueChanged: ((Float, Boolean) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val trackWidth = 6f * density
    private val thumbRadius = 7f * density
    private val inset = 10f * density

    private val trackColor = MaterialColors.getColor(
        this, R.attr.gauge_track, ContextCompat.getColor(context, R.color.gauge_line_color)
    )
    private val accentColor = MaterialColors.getColor(
        this, androidx.appcompat.R.attr.colorPrimary, ContextCompat.getColor(context, R.color.accent)
    )

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = trackColor
        style = Paint.Style.FILL
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.FILL
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.FILL
    }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = trackColor
        strokeWidth = 1f * density
        style = Paint.Style.STROKE
    }

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = (28f * density).roundToInt() + paddingLeft + paddingRight
        val height = MeasureSpec.getSize(heightMeasureSpec).takeIf { it > 0 }
            ?: (180f * density).roundToInt()
        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val centerX = width / 2f
        val top = paddingTop + inset
        val bottom = height - paddingBottom - inset
        val zeroY = top + (bottom - top) / 2f

        canvas.drawRoundRect(
            centerX - trackWidth / 2f,
            top,
            centerX + trackWidth / 2f,
            bottom,
            trackWidth / 2f,
            trackWidth / 2f,
            trackPaint,
        )
        canvas.drawLine(centerX - trackWidth, zeroY, centerX + trackWidth, zeroY, centerPaint)

        val thumbY = yForValue(value, top, bottom)
        canvas.drawRoundRect(
            centerX - trackWidth / 2f,
            minOf(thumbY, zeroY),
            centerX + trackWidth / 2f,
            maxOf(thumbY, zeroY),
            trackWidth / 2f,
            trackWidth / 2f,
            fillPaint,
        )
        canvas.drawCircle(centerX, thumbY, thumbRadius, thumbPaint)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val top = paddingTop + inset
        val bottom = height - paddingBottom - inset

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                // the slider owns the gesture: the scrolling parents must not move underneath
                parent?.requestDisallowInterceptTouchEvent(true)
                value = valueForY(event.y, top, bottom)
                onValueChanged?.invoke(value, false)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                onValueChanged?.invoke(value, true)
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    private fun yForValue(value: Float, top: Float, bottom: Float): Float {
        val ratio = (maxDb - value) / (maxDb - minDb)
        return top + (bottom - top) * ratio
    }

    private fun valueForY(y: Float, top: Float, bottom: Float): Float {
        val ratio = ((y - top) / (bottom - top)).coerceIn(0f, 1f)
        val raw = maxDb - ratio * (maxDb - minDb)
        return (raw * 2f).roundToInt() / 2f
    }
}
