package nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.github.mikephil.charting.animation.ChartAnimator
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.dataprovider.LineDataProvider
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.github.mikephil.charting.renderer.LineChartRenderer
import com.github.mikephil.charting.utils.Utils
import com.github.mikephil.charting.utils.ViewPortHandler

/**
 * One span of a [nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.SeriesStyle.Span]: its first and last
 * entry are the span's edges.
 */
internal class SpanDataSet(entries: List<Entry<Float>>, label: String) : LineDataSet<Float>(entries, label)

/**
 * Draws each [SpanDataSet] as a bar of fixed height between its edges, and other line data sets as usual.
 */
internal class SpanLineRenderer(
    chart: LineDataProvider,
    animator: ChartAnimator,
    viewPortHandler: ViewPortHandler,
) : LineChartRenderer(chart, animator, viewPortHandler) {
    private val bar = RectF()
    private val buffer = FloatArray(4)

    /**
     * Draws the spans after the other data sets, widest first, so a narrow span widened to [MIN_WIDTH_DP] stays on top.
     */
    override fun drawData(c: Canvas) {
        super.drawData(c)
        val spans = chart.lineData?.dataSets?.filterIsInstance<SpanDataSet>() ?: return
        for (span in spans.filter { it.isVisible && it.entryCount >= 2 }.sortedByDescending { it.xMax - it.xMin }) {
            drawSpan(c, span)
        }
    }

    override fun drawDataSet(c: Canvas, dataSet: ILineDataSet<*>) {
        if (dataSet !is SpanDataSet) {
            super.drawDataSet(c, dataSet)
        }
    }

    private fun drawSpan(c: Canvas, dataSet: SpanDataSet) {
        val first = dataSet.getEntryForIndex(0)
        val last = dataSet.getEntryForIndex(dataSet.entryCount - 1)
        buffer[0] = first.x
        buffer[1] = first.y * animator.phaseY
        buffer[2] = last.x
        buffer[3] = last.y * animator.phaseY
        chart.getTransformer(dataSet.axisDependency).pointValuesToPixel(buffer)

        val half = Utils.convertDpToPixel(HEIGHT_DP) / 2f
        val gap = minOf(Utils.convertDpToPixel(GAP_DP), (buffer[2] - buffer[0]) * MAX_GAP_SHARE)
        bar.set(buffer[0] + gap, buffer[1] - half, buffer[2] - gap, buffer[1] + half)
        val minWidth = Utils.convertDpToPixel(MIN_WIDTH_DP)
        if (bar.width() < minWidth) {
            val centerX = bar.centerX()
            bar.left = centerX - minWidth / 2f
            bar.right = centerX + minWidth / 2f
        }
        renderPaint.style = Paint.Style.FILL
        renderPaint.color = dataSet.color
        c.drawRect(bar, renderPaint)
    }

    private companion object {
        const val HEIGHT_DP = 40f
        const val GAP_DP = 1f
        const val MAX_GAP_SHARE = 0.15f
        const val MIN_WIDTH_DP = 2f
    }
}
