package nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.github.mikephil.charting.utils.Transformer
import com.github.mikephil.charting.utils.Utils
import com.github.mikephil.charting.utils.ViewPortHandler
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.SeriesStyle

/**
 * Draws a [SeriesStyle.Stages] series as rounded bars per row, joined by gradient connectors.
 */
internal class StagesRenderer {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val connectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val buffer = FloatArray(4)
    private val gradientMatrix = Matrix()
    private val gradients = HashMap<Pair<Int, Int>, LinearGradient>()
    private var cachedPoints: List<ChartPoint>? = null
    private var cachedRuns: List<Run> = emptyList()

    internal data class Run(val start: Double, val end: Double, val row: Int)

    fun draw(c: Canvas, series: ChartSeries, xOrigin: Double, trans: Transformer, viewPort: ViewPortHandler) {
        val style = series.style as SeriesStyle.Stages
        if (cachedPoints !== series.points) {
            cachedRuns = runs(series.points, style.colors.size)
            cachedPoints = series.points
        }
        val runs = cachedRuns
        val radius = Utils.convertDpToPixel(CORNER_RADIUS_DP)
        connectionPaint.strokeWidth = Utils.convertDpToPixel(CONNECTION_WIDTH_DP)

        for (i in runs.indices) {
            val run = runs[i]
            if (run.row < 0) continue
            val left = pixelX(trans, run.start - xOrigin)
            val right = pixelX(trans, run.end - xOrigin)
            if (right < viewPort.contentLeft || left > viewPort.contentRight) continue
            val (top, bottom) = rowPixels(trans, run.row)

            fillPaint.color = style.colors[run.row]
            drawBar(c, left, top, right, bottom, radius, cornerFlags(runs, i))

            val previous = runs.getOrNull(i - 1)?.takeIf { it.row >= 0 } ?: continue
            val (previousTop, _) = rowPixels(trans, previous.row)
            val barHeight = bottom - top
            val connectionTop = minOf(previousTop, top) + barHeight
            val connectionBottom = maxOf(previousTop, top)
            val upper = if (run.row > previous.row) run.row else previous.row
            val lower = if (run.row > previous.row) previous.row else run.row
            val gradient = gradients.getOrPut(style.colors[upper] to style.colors[lower]) {
                LinearGradient(0f, 0f, 0f, 1f, style.colors[upper], style.colors[lower], Shader.TileMode.CLAMP)
            }
            gradientMatrix.setScale(1f, connectionBottom - connectionTop)
            gradientMatrix.postTranslate(left, connectionTop)
            gradient.setLocalMatrix(gradientMatrix)
            connectionPaint.shader = gradient
            c.drawLine(left, connectionTop, left, connectionBottom, connectionPaint)
            connectionPaint.shader = null
        }
    }

    private fun pixelX(trans: Transformer, x: Double): Float {
        buffer[0] = x.toFloat()
        buffer[1] = 0f
        trans.pointValuesToPixel(buffer)
        return buffer[0]
    }

    private fun rowPixels(trans: Transformer, row: Int): Pair<Float, Float> {
        buffer[0] = 0f
        buffer[1] = row + BAR_HEIGHT / 2f
        buffer[2] = 0f
        buffer[3] = row - BAR_HEIGHT / 2f
        trans.pointValuesToPixel(buffer)
        return buffer[1] to buffer[3]
    }

    private fun drawBar(c: Canvas, left: Float, top: Float, right: Float, bottom: Float, radius: Float, corners: Int) {
        val centerX = (left + right) / 2f
        val wide = left + radius < centerX
        path.reset()
        if (wide) {
            path.moveTo(left + radius, top)
            path.lineTo(right - radius, top)
        } else {
            path.moveTo(centerX, top)
        }
        path.quadTo(right, top, right, if (corners has TOP_RIGHT) top - radius else top + radius)
        path.lineTo(right, if (corners has BOTTOM_RIGHT) bottom + radius else bottom - radius)
        if (wide) {
            path.quadTo(right, bottom, right - radius, bottom)
            path.lineTo(left + radius, bottom)
        } else {
            path.quadTo(right, bottom, centerX, bottom)
        }
        path.quadTo(left, bottom, left, if (corners has BOTTOM_LEFT) bottom + radius else bottom - radius)
        path.lineTo(left, if (corners has TOP_LEFT) top - radius else top + radius)
        if (wide) {
            path.quadTo(left, top, left + radius, top)
        } else {
            path.quadTo(left, top, centerX, top)
        }
        path.close()
        c.drawPath(path, fillPaint)
    }

    private infix fun Int.has(flag: Int) = this and flag == flag

    companion object {
        /**
         * Height of a bar in rows.
         */
        const val BAR_HEIGHT = 0.5f
        private const val CORNER_RADIUS_DP = 4f
        private const val CONNECTION_WIDTH_DP = 1f

        const val TOP_LEFT = 1
        const val TOP_RIGHT = 2
        const val BOTTOM_LEFT = 4
        const val BOTTOM_RIGHT = 8

        /**
         * Consecutive points on the same row, each lasting until the next run. Rows outside [rowCount] become -1.
         */
        fun runs(points: List<ChartPoint>, rowCount: Int): List<Run> {
            val runs = mutableListOf<Run>()
            for (i in points.indices) {
                val row = points[i].y.toInt().takeIf { it.toDouble() == points[i].y && it in 0 until rowCount } ?: -1
                val end = points.getOrNull(i + 1)?.x ?: points[i].x
                val last = runs.lastOrNull()
                if (last != null && last.row == row) {
                    runs[runs.size - 1] = last.copy(end = end)
                } else {
                    runs += Run(points[i].x, end, row)
                }
            }
            return runs
        }

        /**
         * Corners that flare out towards the neighbouring runs, so the bars join their connectors.
         */
        fun cornerFlags(runs: List<Run>, index: Int): Int {
            val row = runs[index].row
            var flags = 0
            runs.getOrNull(index - 1)?.row?.takeIf { it >= 0 }
                ?.let { flags = flags or if (row < it) TOP_LEFT else BOTTOM_LEFT }
            runs.getOrNull(index + 1)?.row?.takeIf { it >= 0 }
                ?.let { flags = flags or if (row < it) TOP_RIGHT else BOTTOM_RIGHT }
            return flags
        }
    }
}
