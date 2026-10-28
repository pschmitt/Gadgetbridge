package nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart

import android.graphics.Canvas
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.renderer.XAxisRenderer
import com.github.mikephil.charting.utils.MPPointF
import com.github.mikephil.charting.utils.Transformer
import com.github.mikephil.charting.utils.Utils
import com.github.mikephil.charting.utils.ViewPortHandler

/**
 * Draws x labels and grid lines only at [values], when set.
 */
internal class FixedValuesXAxisRenderer(
    viewPortHandler: ViewPortHandler,
    xAxis: XAxis,
    transformer: Transformer?,
) : XAxisRenderer(viewPortHandler, xAxis, transformer) {
    var values: List<Double>? = null

    override fun drawLabels(c: Canvas, pos: Float, anchor: MPPointF) {
        val fixed = values ?: return super.drawLabels(c, pos, anchor)
        val positions = pixelPositions(fixed)
        for (i in fixed.indices) {
            val x = positions[i * 2]
            if (viewPortHandler.isInBoundsX(x)) {
                val label = xAxis.valueFormatter.getFormattedValue(fixed[i].toFloat(), xAxis)
                val halfWidth = Utils.calcTextWidth(paintAxisLabels, label) / 2f
                val inside = x.coerceIn(halfWidth, maxOf(halfWidth, viewPortHandler.chartWidth - halfWidth))
                drawLabel(c, label, inside, pos, anchor, xAxis.labelRotationAngle)
            }
        }
    }

    override fun renderGridLines(c: Canvas) {
        val fixed = values ?: return super.renderGridLines(c)
        if (!xAxis.isDrawGridLinesEnabled || !xAxis.isEnabled) return

        val clipRestoreCount = c.save()
        c.clipRect(gridClippingRect)
        setupGridPaint()
        val positions = pixelPositions(fixed)
        for (i in fixed.indices) {
            drawGridLine(c, positions[i * 2], positions[i * 2 + 1], renderGridLinesPath)
        }
        c.restoreToCount(clipRestoreCount)
    }

    private fun pixelPositions(fixed: List<Double>): FloatArray {
        val positions = FloatArray(fixed.size * 2) { fixed[it / 2].toFloat() }
        transformer?.pointValuesToPixel(positions)
        return positions
    }
}
