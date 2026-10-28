package nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.ScrollView
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.widget.NestedScrollView
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis.XAxisPosition
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.components.YAxis.AxisDependency
import com.github.mikephil.charting.formatter.IAxisValueFormatter
import com.github.mikephil.charting.utils.Utils
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSide
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartTheme
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartValueFormat
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.DURATION_LABEL_SPACINGS_SECONDS
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.SeriesStyle
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.XMarkerSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.fixedLabelValues
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.labelFor
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.timeLabelValues
import java.lang.ref.WeakReference
import java.util.TimeZone
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Renders a [ChartSpec]. Tap or drag sideways to select an x; with a click listener, a tap clicks instead.
 * With [zoomable], pinch zooms the x axis, a drag pans it once zoomed in, and a long press then drag selects.
 */
class GbChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : CombinedChart(context, attrs, defStyle) {
    private val theme = ChartTheme.from(context)
    private val xAxisLabels = FixedValuesXAxisRenderer(viewPortHandler, xAxis, getTransformer(AxisDependency.LEFT))
    private val tooltip = ChartTooltipPainter(context, theme)
    private val accessibility = ChartSlotAccessibilityHelper(this)
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stagesRenderer = StagesRenderer()
    private val markerLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerLines = mutableListOf<Pair<XMarkerSpec, LimitLine>>()
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onLongPress(e: MotionEvent) {
            if (!selectionEnabled || !zoomable || viewPortHandler.isFullyZoomedOut) return
            val x = targetAt(e.x, e.y) ?: return
            startScrub()
            select(x)
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            // View.onTouchEvent(), through super.onTouchEvent(), already clicks a view with a click listener
            if (hasOnClickListeners()) return false
            performClick()
            if (!selectionEnabled) return false
            val x = targetAt(e.x, e.y)
            if (x == null) select(null) else toggle(x)
            return true
        }
    })
    private var spec: ChartSpec? = null
    private var xOrigin = 0.0
    private var labelledDays: AxisSpec? = null
    private var barLayout: BarLayout? = null
    private var selection: ChartSelection? = null
    private var targets = DoubleArray(0)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var scrubStartX = 0f
    private var scrubStartY = 0f
    private var scrubbing = false

    var selectionContent: ((Double) -> ChartSelection)? = null

    /**
     * Whether taps and drags select an x and show the tooltip.
     */
    var selectionEnabled = true

    var zoomable = false
        set(value) {
            field = value
            isDragEnabled = value
            isScaleXEnabled = value
            if (!value) fitScreen()
        }

    var selectedX: Double? = null
        private set

    init {
        renderer = RoundedCandleRenderer.Combined(this, animator, viewPortHandler).apply {
            drawBehindData = { canvas -> drawStages(canvas) }
        }
        isClipDataToContentEnabled = false
        rendererXAxis = xAxisLabels
        description.isEnabled = false
        legend.isEnabled = false
        isDragEnabled = false
        isScaleXEnabled = false
        isScaleYEnabled = false
        isPinchZoomEnabled = false
        isDoubleTapToZoomEnabled = false
        isHighlightPerTapEnabled = false
        isHighlightPerDragEnabled = false
        isNoDataIconEnabled = false
        noDataTextColor = theme.secondaryTextColor
        drawOrder = listOf(CombinedChart.DrawOrder.BAR, CombinedChart.DrawOrder.CANDLE, CombinedChart.DrawOrder.LINE)

        val gridColor = ColorUtils.setAlphaComponent(theme.secondaryTextColor, GRID_ALPHA)
        xAxis.position = XAxisPosition.BOTTOM
        xAxis.textColor = theme.textColor
        xAxis.gridColor = gridColor
        xAxis.axisLineColor = theme.secondaryTextColor
        xAxis.isDrawLimitLinesBehindDataEnabled = true
        for (axis in listOf(axisLeft, axisRight)) {
            axis.textColor = theme.textColor
            axis.gridColor = gridColor
            axis.isDrawAxisLineEnabled = false
        }
        axisRight.isDrawGridLinesEnabled = false

        ViewCompat.setAccessibilityDelegate(this, accessibility)
    }

    fun setSpec(spec: ChartSpec) {
        if (spec.isEmpty) {
            showMessage(context.getString(R.string.no_data))
            return
        }
        this.spec = spec
        xOrigin = ChartDataBuilder.xOrigin(spec)
        targets = ChartSlots.targets(spec)
        clearSelection()
        configureXAxis(spec.xAxis)
        configureYAxis(axisLeft, spec.yAxis)
        configureYAxis(axisRight, spec.endYAxis)
        configureLimitLines(spec)
        barLayout = barLayoutFor(spec, 0f)
        data = ChartDataBuilder.build(spec, barLayout!!, BAR_CORNER_DP, xOrigin)
        updateBarLayout(spec)
        accessibility.invalidateRoot()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        spec?.let { updateBarLayout(it) }
    }

    /**
     * Rebuilds the data when the bar width changes. The axis range and the content width are known only after the data is set.
     */
    private fun updateBarLayout(spec: ChartSpec) {
        if (xAxis.axisRange <= 0f) return
        val layout = barLayoutFor(spec, viewPortHandler.contentWidth / xAxis.axisRange)
        if (layout == barLayout) return
        barLayout = layout
        data = ChartDataBuilder.build(spec, layout, BAR_CORNER_DP, xOrigin)
    }

    fun showMessage(text: String) {
        spec = null
        barLayout = null
        targets = DoubleArray(0)
        clearSelection()
        noDataText = text
        clear()
        accessibility.invalidateRoot()
    }

    internal fun slots(): IntRange? = spec?.let { ChartSlots.of(it.xAxis) }

    internal fun hasSlotBounds() = data != null && viewPortHandler.contentWidth > 0f

    private fun targetAt(x: Float, y: Float): Double? {
        if (!hasSlotBounds() || !viewPortHandler.isInBounds(x, y)) return null
        return ChartSlots.nearest(getValuesByTouchPoint(x, y, AxisDependency.LEFT).x + xOrigin, targets)
    }

    internal fun slotAt(x: Float, y: Float): Int? = if (slots() == null) null else targetAt(x, y)?.toInt()

    internal fun slotBounds(slot: Int): Rect {
        val left = pixelX(slot - 0.5)
        val right = pixelX(slot + 0.5)
        return Rect(
            maxOf(left, viewPortHandler.contentLeft).toInt(),
            viewPortHandler.contentTop.toInt(),
            minOf(right, viewPortHandler.contentRight).toInt(),
            viewPortHandler.contentBottom.toInt(),
        )
    }

    internal fun selectionAt(x: Double): ChartSelection? = selectionContent?.invoke(x)

    internal fun toggle(x: Double) = select(ChartSlots.toggle(selectedX, x))

    /**
     * Dismiss the marker on taps that reach outside this chart. Several charts can share one [container].
     */
    fun dismissSelectionOnTapOutside(container: View) {
        val listener = tapOutsideListeners.getOrPut(container) { TapOutsideListener(container, touchSlop) }
        listener.add(this)
    }

    /**
     * Clears the selection of its charts on a tap that reaches [container] itself.
     */
    @SuppressLint("ClickableViewAccessibility")
    private class TapOutsideListener(container: View, private val touchSlop: Int) : View.OnTouchListener {
        private val charts = mutableListOf<WeakReference<GbChartView>>()
        private val handlesOwnTouches = container is ScrollView || container is NestedScrollView || container.isClickable
        private var downX = 0f
        private var downY = 0f

        init {
            container.setOnTouchListener(this)
        }

        fun add(chart: GbChartView) {
            charts.removeAll { it.get() == null || it.get() === chart }
            charts += WeakReference(chart)
        }

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    return !handlesOwnTouches
                }
                MotionEvent.ACTION_UP -> {
                    if (abs(event.x - downX) <= touchSlop && abs(event.y - downY) <= touchSlop) {
                        charts.forEach { it.get()?.select(null) }
                    }
                }
            }
            return false
        }
    }

    private fun select(selected: Double?) {
        if (selected == selectedX) return
        selectedX = selected
        selection = selected?.let { selectionAt(it) }
        resetXLimitLines()
        if (selected != null) {
            xAxis.addLimitLine(guide(selected))
            performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    HapticFeedbackConstants.TEXT_HANDLE_MOVE
                } else {
                    HapticFeedbackConstants.CLOCK_TICK
                }
            )
            selection?.let { announceForAccessibility(it.description) }
        }
        accessibility.invalidateRoot()
        invalidate()
    }

    private fun clearSelection() {
        selectedX = null
        selection = null
        resetXLimitLines()
    }

    /**
     * Leaves only the [ChartSpec.xMarkers] on the x axis, which also carries the selection guide.
     */
    private fun resetXLimitLines() {
        xAxis.removeAllLimitLines()
        markerLines.clear()
        for (marker in spec?.xMarkers.orEmpty()) {
            val line = LimitLine((marker.value - xOrigin).toFloat(), marker.label.orEmpty()).apply {
                lineColor = marker.color
                lineWidth = X_MARKER_WIDTH_DP
                textColor = theme.textColor
                textSize = X_MARKER_TEXT_SP
                labelPosition = labelPosition(marker.labelAtBottom, marker.labelBeforeLine)
            }
            markerLines += marker to line
            xAxis.addLimitLine(line)
        }
    }

    /**
     * Moves or hides the x marker labels so that none overlaps another at the current size and zoom.
     */
    private fun layoutMarkerLabels() {
        if (markerLines.isEmpty() || viewPortHandler.contentWidth <= 0f) return
        // LimitLine keeps its text size, line width and offset in dp; the renderer converts them when it draws
        val labels = markerLines.map { (marker, line) ->
            markerLabelPaint.textSize = Utils.convertDpToPixel(line.textSize)
            XMarkerLabelLayout.Label(
                x = pixelX(marker.value),
                width = markerLabelPaint.measureText(marker.label.orEmpty()) +
                        Utils.convertDpToPixel(line.lineWidth) + Utils.convertDpToPixel(line.xOffset),
                atBottom = marker.labelAtBottom,
                beforeLine = marker.labelBeforeLine,
            )
        }
        val placements = XMarkerLabelLayout.place(labels, viewPortHandler.contentLeft, viewPortHandler.contentRight)
        markerLines.zip(placements) { (marker, line), placement ->
            line.label = if (placement == null) "" else marker.label.orEmpty()
            if (placement != null) line.labelPosition = labelPosition(placement.atBottom, placement.beforeLine)
        }
    }

    private fun labelPosition(atBottom: Boolean, beforeLine: Boolean) = when {
        beforeLine && atBottom -> LimitLine.LimitLabelPosition.LEFT_BOTTOM
        beforeLine -> LimitLine.LimitLabelPosition.LEFT_TOP
        atBottom -> LimitLine.LimitLabelPosition.RIGHT_BOTTOM
        else -> LimitLine.LimitLabelPosition.RIGHT_TOP
    }

    private fun guide(x: Double) = LimitLine((x - xOrigin).toFloat()).apply {
        lineColor = GUIDE_COLOR
        lineWidth = GUIDE_WIDTH_DP
        val dash = Utils.convertDpToPixel(GUIDE_DASH_DP)
        enableDashedLine(dash, dash, 0f)
    }

    /**
     * The chart drawn dark on white with its series legend, e.g. for a PDF export.
     */
    fun toLightBitmap(): Bitmap {
        val previousBackground = background
        val axes = listOf(xAxis, axisLeft, axisRight)
        val textColors = axes.map { it.textColor }
        select(null)
        background = ColorDrawable(Color.WHITE)
        axes.forEach { it.textColor = Color.BLACK }
        legend.isEnabled = true
        legend.textColor = Color.BLACK
        notifyDataSetChanged()
        val bitmap = toBitmap()
        background = previousBackground
        axes.zip(textColors).forEach { (axis, color) -> axis.textColor = color }
        legend.isEnabled = false
        notifyDataSetChanged()
        invalidate()
        return bitmap
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val handled = gestures.onTouchEvent(event)
        if (event.pointerCount > 1) {
            endScrub()
        } else {
            scrub(event)
        }
        if (zoomable && scrubbing) {
            return true
        }
        return super.onTouchEvent(event) || handled
    }

    private fun startScrub() {
        scrubbing = true
        // The cancel makes MPAndroidChart call enableScroll(), so disallow the parent afterwards
        if (zoomable) {
            val cancel = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            super.onTouchEvent(cancel)
            cancel.recycle()
        }
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    private fun endScrub() {
        if (scrubbing) {
            scrubbing = false
            parent?.requestDisallowInterceptTouchEvent(false)
        }
    }

    /**
     * Horizontal drags move the selection, vertical ones go to the scrolling parent.
     */
    private fun scrub(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scrubStartX = event.x
                scrubStartY = event.y
                scrubbing = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scrubbing) {
                    if (!selectionEnabled || (zoomable && !viewPortHandler.isFullyZoomedOut)) return
                    val dx = abs(event.x - scrubStartX)
                    val dy = abs(event.y - scrubStartY)
                    if (dx <= touchSlop || dx <= dy || targetAt(scrubStartX, scrubStartY) == null) return
                    startScrub()
                }
                val x = event.x.coerceIn(viewPortHandler.contentLeft, viewPortHandler.contentRight)
                targetAt(x, viewPortHandler.contentCenter.y)?.let { select(it) }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endScrub()
        }
    }

    override fun performClick(): Boolean = super.performClick()

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        accessibility.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        accessibility.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        accessibility.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    }

    override fun onDraw(canvas: Canvas) {
        val spec = spec
        if (spec != null) {
            timeLabelsFor(spec.xAxis)?.let { labels -> xAxisLabels.values = labels.map { it - xOrigin } }
            updateDayLabels(spec.xAxis)
        }
        layoutMarkerLabels()
        super.onDraw(canvas)

        val selected = selectedX ?: return
        if (spec == null || data == null) return
        drawSelectedPoints(canvas, spec, selected)
        selection?.let { drawTooltip(canvas, it, selected) }
    }

    private fun drawStages(canvas: Canvas) {
        val spec = spec ?: return
        for (series in spec.series.filter { it.style is SeriesStyle.Stages }) {
            val trans = getTransformer(if (series.axis == AxisSide.END) AxisDependency.RIGHT else AxisDependency.LEFT)
            stagesRenderer.draw(canvas, series, xOrigin, trans, viewPortHandler)
        }
    }

    private fun timeLabelsFor(spec: AxisSpec): List<Double>? {
        val duration = spec.format == ChartValueFormat.DURATION_SECONDS
        if (spec.format != ChartValueFormat.TIME_OF_DAY && spec.format != ChartValueFormat.DATE && !duration) return null
        val min = maxOf(spec.minimum ?: return null, floor(lowestVisibleX + xOrigin))
        val max = minOf(spec.maximum ?: return null, ceil(highestVisibleX + xOrigin))
        val labelSpace = maxOf(xAxis.labelWidth.toFloat(), Utils.convertDpToPixel(MIN_TIME_LABEL_WIDTH_DP)) +
            Utils.convertDpToPixel(TIME_LABEL_GAP_DP)
        val maxLabels = (viewPortHandler.contentWidth / labelSpace).toInt()
        if (duration) {
            return timeLabelValues(min, max, maxLabels, spacings = DURATION_LABEL_SPACINGS_SECONDS)
        }
        val timeZone = TimeZone.getDefault()
        return timeLabelValues(min, max, maxLabels, zoneOffsetSeconds = { timeZone.getOffset(it.toLong() * 1000L) / 1000 })
    }

    private fun drawSelectedPoints(canvas: Canvas, spec: ChartSpec, selected: Double) {
        for (series in spec.series.filter { it.selectable }) {
            val style = series.style as? SeriesStyle.Line ?: continue
            val point = series.points.firstOrNull { it.x == selected }?.takeIf { it.y > 0.0 } ?: continue
            val axis = if (series.axis == AxisSide.END) AxisDependency.RIGHT else AxisDependency.LEFT
            val pixel = getPixelForValues((point.x - xOrigin).toFloat(), point.y.toFloat(), axis)
            val x = pixel.x.toFloat()
            val y = pixel.y.toFloat()

            val dotRadius = Utils.convertDpToPixel(SELECTED_DOT_DP / 2f)
            val gapRadius = dotRadius + Utils.convertDpToPixel(SELECTED_GAP_DP)
            pointPaint.color = style.color
            canvas.drawCircle(x, y, gapRadius + Utils.convertDpToPixel(SELECTED_RING_DP), pointPaint)
            pointPaint.color = theme.markerDotGapColor
            canvas.drawCircle(x, y, gapRadius, pointPaint)
            pointPaint.color = style.color
            canvas.drawCircle(x, y, dotRadius, pointPaint)
        }
    }

    private fun drawTooltip(canvas: Canvas, selection: ChartSelection, selected: Double) {
        val guideX = pixelX(selected)
        val left = ChartSlots.tooltipLeft(
            guideX = guideX,
            centerX = viewPortHandler.contentCenter.x,
            width = tooltip.width(selection),
            gap = Utils.convertDpToPixel(TOOLTIP_GAP_DP),
            minLeft = paddingLeft.toFloat(),
            maxRight = (width - paddingRight).toFloat(),
        )
        tooltip.draw(canvas, selection, left, viewPortHandler.contentTop)
    }

    private fun pixelX(x: Double): Float = getPixelForValues((x - xOrigin).toFloat(), 0f, AxisDependency.LEFT).x.toFloat()

    private fun barLayoutFor(spec: ChartSpec, pxPerX: Float) = BarLayout.of(
        pxPerX = pxPerX,
        pxPerDp = Utils.convertDpToPixel(1f),
        barCount = targets.size,
        grouped = ChartDataBuilder.columnGroups(spec).size > 1,
    )

    private fun configureXAxis(spec: AxisSpec) {
        val padding = if (fixedLabelValues(spec) != null) PERIOD_X_PADDING else 0.0
        labelledDays = null
        setXLabels(spec)
        spec.minimum?.let { xAxis.axisMinimum = (it - padding - xOrigin).toFloat() } ?: xAxis.resetAxisMinimum()
        spec.maximum?.let { xAxis.axisMaximum = (it + padding - xOrigin).toFloat() } ?: xAxis.resetAxisMaximum()
    }

    /**
     * Sets the x labels and their format for the range of [axis].
     */
    private fun setXLabels(axis: AxisSpec, dayNames: Boolean = true) {
        val origin = xOrigin
        val label = axis.labeler ?: labelFor(axis, dayNames)
        xAxisLabels.values = fixedLabelValues(axis)?.map { it - origin }
        xAxis.valueFormatter = formatterFor { label(it + origin) }
    }

    /**
     * Sets the labels of an [ChartValueFormat.EPOCH_DAY] axis for the days in view.
     */
    private fun updateDayLabels(axis: AxisSpec) {
        if (axis.format != ChartValueFormat.EPOCH_DAY) return
        val minimum = axis.minimum ?: return
        val maximum = axis.maximum ?: return
        val visible = axis.copy(
            minimum = maxOf(minimum, ceil(lowestVisibleX + xOrigin)),
            maximum = minOf(maximum, floor(highestVisibleX + xOrigin)),
        )
        if (visible == labelledDays) return
        labelledDays = visible
        setXLabels(visible, dayNames = visible == axis)
    }

    private fun configureLimitLines(spec: ChartSpec) {
        axisLeft.removeAllLimitLines()
        axisRight.removeAllLimitLines()
        for (limit in spec.limitLines) {
            val axis = if (limit.axis == AxisSide.END) axisRight else axisLeft
            axis.addLimitLine(LimitLine(limit.value.toFloat()).apply {
                lineColor = limit.color
                lineWidth = LIMIT_LINE_WIDTH_DP
                if (limit.dashed) {
                    enableDashedLine(
                        Utils.convertDpToPixel(LIMIT_LINE_DASH_DP),
                        Utils.convertDpToPixel(LIMIT_LINE_GAP_DP),
                        0f,
                    )
                }
            })
        }
    }

    private fun configureYAxis(axis: YAxis, spec: AxisSpec?) {
        axis.isEnabled = spec != null
        if (spec == null) {
            return
        }
        val label = spec.labeler ?: labelFor(spec.format)
        val unit = spec.unit
        axis.isDrawLabelsEnabled = spec.showLabels
        axis.valueFormatter = formatterFor { value -> if (unit == null) label(value) else "${label(value)} $unit" }
        axis.isGranularityEnabled = spec.format == ChartValueFormat.INTEGER
        spec.minimum?.let { axis.axisMinimum = it.toFloat() } ?: axis.resetAxisMinimum()
        spec.maximum?.let { axis.axisMaximum = it.toFloat() } ?: axis.resetAxisMaximum()
    }

    private fun formatterFor(label: (Double) -> String) = IAxisValueFormatter { value, _ -> label(value.toDouble()) }

    private companion object {
        private val tapOutsideListeners = WeakHashMap<View, TapOutsideListener>()

        const val BAR_CORNER_DP = 4f
        const val PERIOD_X_PADDING = 0.5
        const val GRID_ALPHA = 0x40
        const val GUIDE_WIDTH_DP = 1f
        const val GUIDE_DASH_DP = 3f
        const val TOOLTIP_GAP_DP = 10f
        const val SELECTED_DOT_DP = 8f
        const val SELECTED_GAP_DP = 1.5f
        const val SELECTED_RING_DP = 1.5f
        const val LIMIT_LINE_WIDTH_DP = 1.5f
        const val LIMIT_LINE_DASH_DP = 6f
        const val LIMIT_LINE_GAP_DP = 4f
        const val X_MARKER_WIDTH_DP = 1f
        const val X_MARKER_TEXT_SP = 9f
        const val MIN_TIME_LABEL_WIDTH_DP = 32f
        const val TIME_LABEL_GAP_DP = 16f
        val GUIDE_COLOR = Color.parseColor("#8F8F8F")
    }
}
