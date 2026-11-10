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
import android.graphics.Path
import android.util.AttributeSet
import nodomain.freeyourgadget.gadgetbridge.devices.soundpeats.SoundpeatsCustomEqStore
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import nodomain.freeyourgadget.gadgetbridge.R
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Graphic equalizer: one vertical bar per band, the gains joined by a line with draggable points.
 *
 * Dragging anywhere over a band changes that band's gain, which is the interaction every equalizer
 * app uses.
 */
class SoundpeatsEqualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    companion object {
        private const val dB_MIN = -12f
        private const val dB_MAX = 12f
        private const val GRID_STEP_DB = 6f
    }

    /** Called while dragging and on release. */
    var onGainsChanged: ((List<Double>, Boolean) -> Unit)? = null


    private var gains = MutableList(SoundpeatsCustomEqStore.EDIT_BANDS.size) { 0.0 }
    private var draggingBand = -1

    private val density = resources.displayMetrics.density
    private val labelTextSize = 10f * density
    private val axisWidth = 30f * density
    private val paddingBottom = 18f * density
    private val paddingTop = 8f * density

    private val trackColor = MaterialColors.getColor(
        this, R.attr.gauge_track, ContextCompat.getColor(context, R.color.gauge_line_color)
    )
    private val accentColor = MaterialColors.getColor(
        this, androidx.appcompat.R.attr.colorPrimary,
        ContextCompat.getColor(context, R.color.accent)
    )

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = trackColor
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = trackColor
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.FILL
        alpha = 90
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val curvePath = Path()
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.FILL
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = trackColor
        textSize = labelTextSize
        textAlign = Paint.Align.RIGHT
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MaterialColors.getColor(
            this@SoundpeatsEqualizerView,
            R.attr.textColorPrimary,
            trackColor,
        )
        textSize = labelTextSize
        textAlign = Paint.Align.CENTER
    }

    fun setGains(values: List<Double>) {
        gains = MutableList(gains.size) { values.getOrElse(it) { 0.0 } }
        invalidate()
    }

    fun getGains(): List<Double> = gains.toList()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = (180f * density).roundToInt() + paddingBottom.roundToInt() + paddingTop.roundToInt()
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(height, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val left = paddingLeft + axisWidth
        val right = width - paddingRight - 8f * density
        val top = paddingTop
        val bottom = height - paddingBottom
        val plotHeight = bottom - top
        val cellWidth = (right - left) / gains.size
        val centerY = top + plotHeight / 2f

        // horizontal dB grid with its axis labels
        var db = dB_MIN
        while (db <= dB_MAX) {
            val y = yForDb(db, top, plotHeight)
            if (db == 0f) {
                canvas.drawLine(left, y, right, y, baselinePaint)
            } else {
                canvas.drawLine(left, y, right, y, gridPaint)
            }
            canvas.drawText(
                if (db > 0f) "+${db.toInt()}" else db.toInt().toString(),
                paddingLeft + axisWidth - 6f * density,
                y + labelTextSize / 3f,
                axisPaint,
            )
            db += GRID_STEP_DB
        }

        // bars
        val barWidth = cellWidth * 0.42f
        gains.forEachIndexed { index, gain ->
            val x = left + cellWidth * (index + 0.5f)
            val y = yForDb(gain.toFloat(), top, plotHeight)
            canvas.drawRect(
                x - barWidth / 2f,
                minOf(y, centerY),
                x + barWidth / 2f,
                maxOf(y, centerY),
                barPaint,
            )
        }

        // curve + points
        curvePath.reset()
        gains.forEachIndexed { index, gain ->
            val x = left + cellWidth * (index + 0.5f)
            val y = yForDb(gain.toFloat(), top, plotHeight)
            if (index == 0) curvePath.moveTo(x, y) else curvePath.lineTo(x, y)
        }
        canvas.drawPath(curvePath, linePaint)
        gains.forEachIndexed { index, gain ->
            val x = left + cellWidth * (index + 0.5f)
            val y = yForDb(gain.toFloat(), top, plotHeight)
            canvas.drawCircle(x, y, 4.5f * density, pointPaint)
        }

        // band labels, every other one so sixteen of them still fit
        SoundpeatsCustomEqStore.EDIT_BANDS.forEachIndexed { index, frequency ->
            if (index % 2 == 0) {
                val x = left + cellWidth * (index + 0.5f)
                canvas.drawText(SoundpeatsCustomEqStore.bandLabel(frequency), x, height - 5f * density, labelPaint)
            }
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val left = paddingLeft + axisWidth
        val right = width - paddingRight - 8f * density
        val top = paddingTop
        val plotHeight = (height - paddingBottom) - top
        val cellWidth = (right - left) / gains.size

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                // dragging a point must not scroll the screen
                parent?.requestDisallowInterceptTouchEvent(true)
                val index = (((event.x - left) / cellWidth).toInt()).coerceIn(0, gains.size - 1)
                if (abs(event.x - (left + cellWidth * (index + 0.5f))) > cellWidth) {
                    return false
                }
                draggingBand = index
                gains[index] = dbForY(event.y, top, plotHeight).toDouble()
                invalidate()
                onGainsChanged?.invoke(gains.toList(), false)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (draggingBand >= 0) {
                    draggingBand = -1
                    parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                onGainsChanged?.invoke(gains.toList(), true)
                    performClick()
                }
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    private fun yForDb(db: Float, top: Float, plotHeight: Float): Float {
        val ratio = (dB_MAX - db) / (dB_MAX - dB_MIN)
        return top + plotHeight * ratio
    }

    private fun dbForY(y: Float, top: Float, plotHeight: Float): Float {
        val ratio = ((y - top) / plotHeight).coerceIn(0f, 1f)
        return dB_MAX - ratio * (dB_MAX - dB_MIN)
    }
}
