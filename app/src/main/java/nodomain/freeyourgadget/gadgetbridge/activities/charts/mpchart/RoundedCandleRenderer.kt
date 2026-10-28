package nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.github.mikephil.charting.animation.ChartAnimator
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.interfaces.dataprovider.CandleDataProvider
import com.github.mikephil.charting.interfaces.datasets.ICandleDataSet
import com.github.mikephil.charting.renderer.CandleStickChartRenderer
import com.github.mikephil.charting.renderer.CombinedChartRenderer
import com.github.mikephil.charting.utils.Utils
import com.github.mikephil.charting.utils.ViewPortHandler

/**
 * Draws each candle as a pill from low to high, with fully rounded ends. A pill is at most
 * [MAX_WIDTH_DP] wide, and never shorter than it is wide.
 */
internal class RoundedCandleRenderer(
    chart: CandleDataProvider,
    animator: ChartAnimator,
    viewPortHandler: ViewPortHandler,
) : CandleStickChartRenderer(chart, animator, viewPortHandler) {
    private val pill = RectF()
    private val buffer = FloatArray(4)

    override fun drawDataSet(c: Canvas, dataSet: ICandleDataSet<*>) {
        if (dataSet.entryCount < 1) return
        val trans = chart.getTransformer(dataSet.axisDependency)
        val phaseY = animator.phaseY
        val maxWidth = Utils.convertDpToPixel(MAX_WIDTH_DP)
        renderPaint.style = Paint.Style.FILL
        xBounds.set(chart, dataSet)

        for (index in xBounds.min..xBounds.min + xBounds.range) {
            val e = dataSet.getEntryForIndex(index)
            buffer[0] = e.x - 0.5f + dataSet.barSpace
            buffer[1] = e.high * phaseY
            buffer[2] = e.x + 0.5f - dataSet.barSpace
            buffer[3] = e.low * phaseY
            trans.pointValuesToPixel(buffer)

            pill.set(buffer[0], buffer[1], buffer[2], buffer[3])
            pill.sort()
            val radius = minOf(pill.width(), maxWidth) / 2f
            val centerX = pill.centerX()
            pill.left = centerX - radius
            pill.right = centerX + radius
            if (pill.height() < pill.width()) {
                val centerY = pill.centerY()
                pill.top = centerY - radius
                pill.bottom = centerY + radius
            }
            renderPaint.color = dataSet.getColor(index)
            c.drawRoundRect(pill, radius, radius, renderPaint)
        }
    }

    private companion object {
        const val MAX_WIDTH_DP = 8f
    }

    class Combined(
        chart: CombinedChart,
        animator: ChartAnimator,
        viewPortHandler: ViewPortHandler,
    ) : CombinedChartRenderer(chart, animator, viewPortHandler) {
        private val clip = RectF()

        var drawBehindData: ((Canvas) -> Unit)? = null

        /**
         * Clips the data to the content width, and to the content height plus half a line width, so that a line
         * on the axis minimum or maximum is fully drawn.
         */
        override fun drawData(c: Canvas) {
            val margin = Utils.convertDpToPixel(ChartDataBuilder.LINE_WIDTH_DP / 2f)
            clip.set(viewPortHandler.contentRect)
            clip.inset(0f, -margin)
            val restoreCount = c.save()
            c.clipRect(clip)
            drawBehindData?.invoke(c)
            super.drawData(c)
            c.restoreToCount(restoreCount)
        }

        override fun createRenderers() {
            super.createRenderers()
            val chart = chart.get() ?: return
            for (i in subRenderers.indices) {
                if (subRenderers[i] is CandleStickChartRenderer) {
                    subRenderers[i] = RoundedCandleRenderer(chart, animator, viewPortHandler)
                }
            }
        }
    }
}
