package nodomain.freeyourgadget.gadgetbridge.activities.dashboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.core.graphics.ColorUtils
import androidx.core.text.toSpannable
import androidx.core.view.doOnLayout
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.widgets.GaugeSpec
import nodomain.freeyourgadget.gadgetbridge.widgets.LegendRow
import nodomain.freeyourgadget.gadgetbridge.widgets.MiniChart
import nodomain.freeyourgadget.gadgetbridge.widgets.Stat
import nodomain.freeyourgadget.gadgetbridge.widgets.StatusLine
import nodomain.freeyourgadget.gadgetbridge.widgets.TileAxis
import nodomain.freeyourgadget.gadgetbridge.widgets.TileModel
import kotlin.math.roundToInt

/**
 * Draws a [TileModel] into a tile layout of one style.
 */
interface TileRenderer {
    @get:LayoutRes
    val layout: Int

    fun render(view: View, title: String, @DrawableRes icon: Int, model: TileModel)

    fun renderLoading(view: View, title: String, @DrawableRes icon: Int)
}

object GaugeTileRenderer : TileRenderer {
    override val layout = R.layout.dashboard_widget_gauge_tile

    override fun render(view: View, title: String, icon: Int, model: TileModel) =
        renderGauge(view, title, icon, model)

    override fun renderLoading(view: View, title: String, icon: Int) = renderGaugeLoading(view, title, icon)
}

object NumberTileRenderer : TileRenderer {
    override val layout = R.layout.dashboard_widget_number_tile

    override fun render(view: View, title: String, icon: Int, model: TileModel) {
        renderHeader(view, title, icon)
        renderValue(view, model)
        renderStatus(view, model.status, null)
        keepStatusSpace(view)
        renderChart(view.findViewById(R.id.tile_chart), model.chart, SMALL_CHART_HEIGHT_DP)
    }

    override fun renderLoading(view: View, title: String, icon: Int) {
        renderHeader(view, title, icon)
        renderValueLoading(view)
        view.findViewById<View>(R.id.tile_status).visibility = View.INVISIBLE
        view.findViewById<View>(R.id.tile_chart).visibility = View.GONE
    }

    /**
     * Keeps the value at the same height in every widget, with or without a status line.
     */
    private fun keepStatusSpace(view: View) {
        val status = view.findViewById<View>(R.id.tile_status)
        if (status.visibility == View.GONE) {
            view.findViewById<TextView>(R.id.tile_status_text).text = " "
            status.visibility = View.INVISIBLE
        }
    }

    private const val SMALL_CHART_HEIGHT_DP = 22f
}

/**
 * The 1x1.5 tile: a gauge at the top and a detail block at the bottom.
 */
object TallTileRenderer : TileRenderer {
    override val layout = R.layout.dashboard_widget_tall_tile

    override fun render(view: View, title: String, icon: Int, model: TileModel) {
        renderGauge(view, title, icon, model)
        renderStatus(view, model.status, model.trailing)
        renderChart(view.findViewById(R.id.tile_chart), model.chart?.takeIf { isBar(it) }, 0f)
        renderChart(view.findViewById(R.id.tile_large_chart), model.largeChart, LARGE_CHART_HEIGHT_DP)
        renderAxis(view.findViewById(R.id.tile_axis), model.axis, model.largeChart != null)
        renderLegend(view.findViewById(R.id.tile_legend), model.legend)
    }

    override fun renderLoading(view: View, title: String, icon: Int) {
        renderGaugeLoading(view, title, icon)
        for (id in intArrayOf(R.id.tile_status, R.id.tile_chart, R.id.tile_large_chart, R.id.tile_axis, R.id.tile_legend)) {
            view.findViewById<View>(id).visibility = View.GONE
        }
    }

    private const val LARGE_CHART_HEIGHT_DP = 44f
}

/**
 * The 2x1 tile: the number tile's content on the left and a chart on the right.
 */
object WideTileRenderer : TileRenderer {
    override val layout = R.layout.dashboard_widget_wide_tile

    override fun render(view: View, title: String, icon: Int, model: TileModel) {
        renderHeader(view, title, icon)
        renderValue(view, model)
        renderStatus(view, model.status, null)
        // without a large chart, the right side shows the small one stretched
        val right = model.largeChart ?: model.chart
        val small = model.chart?.takeIf { isBar(it) && it !== right }
        renderChart(view.findViewById(R.id.tile_chart), small, 0f)
        renderChart(view.findViewById(R.id.tile_large_chart), right, LARGE_CHART_HEIGHT_DP)
        renderAxis(view.findViewById(R.id.tile_axis), model.axis, right != null)
        val note = view.findViewById<TextView>(R.id.tile_note)
        note.text = model.notes.firstOrNull()
        note.visibility = if (model.notes.isEmpty()) View.GONE else View.VISIBLE
        renderLegend(view.findViewById(R.id.tile_legend), model.legend)
    }

    override fun renderLoading(view: View, title: String, icon: Int) {
        renderHeader(view, title, icon)
        renderValueLoading(view)
        for (id in intArrayOf(R.id.tile_status, R.id.tile_chart, R.id.tile_large_chart, R.id.tile_axis, R.id.tile_note, R.id.tile_legend)) {
            view.findViewById<View>(id).visibility = View.GONE
        }
    }

    private const val LARGE_CHART_HEIGHT_DP = 78f
}

/**
 * The 2x1.5 tile: header, value row with mini stats, a chart and a stats row.
 */
object LargeTileRenderer : TileRenderer {
    override val layout = R.layout.dashboard_widget_large_tile

    override fun render(view: View, title: String, icon: Int, model: TileModel) {
        renderHeader(view, title, icon)
        val besideLegend = model.gaugeBesideLegend
        view.findViewById<View>(R.id.tile_gauge_row).visibility = if (besideLegend) View.VISIBLE else View.GONE
        view.findViewById<View>(R.id.tile_value_row).visibility = if (besideLegend) View.GONE else View.VISIBLE
        val sideInfo = besideLegend && model.notes.isNotEmpty()
        view.findViewById<View>(R.id.tile_side_info).visibility = if (sideInfo) View.VISIBLE else View.GONE
        if (sideInfo) {
            renderArc(view, model, GAUGE_VALUE_SIZE_SP, GAUGE_LONG_VALUE_SIZE_SP)
            renderSideInfo(view, model)
            renderLegend(view.findViewById(R.id.tile_side_legend), emptyList(), 1)
            renderLegend(view.findViewById(R.id.tile_legend), model.legend, LEGEND_COLUMNS)
        } else if (besideLegend) {
            renderArc(view, model, GAUGE_VALUE_SIZE_SP, GAUGE_LONG_VALUE_SIZE_SP)
            renderLegend(view.findViewById(R.id.tile_side_legend), model.legend, 1)
            renderLegend(view.findViewById(R.id.tile_legend), emptyList(), LEGEND_COLUMNS)
        } else {
            renderValue(view, model)
            val suffix = view.findViewById<TextView>(R.id.tile_value_suffix)
            suffix.text = model.valueSuffix
            suffix.visibility = if (model.valueSuffix == null) View.GONE else View.VISIBLE
            renderStats(view.findViewById(R.id.tile_mini_stats), model.miniStats, mini = true)
            renderStatus(view, model.status, model.trailing)
            renderLegend(view.findViewById(R.id.tile_legend), model.legend, LEGEND_COLUMNS)
        }
        renderChart(view.findViewById(R.id.tile_chart), model.chart?.takeIf { isBar(it) }, 0f)
        val large = model.largeChart
        val height = when (large) {
            is MiniChart.Line, is MiniChart.RangeBars -> if (model.stats.isEmpty()) LINE_HEIGHT_DP else BARS_HEIGHT_DP
            is MiniChart.Hypnogram -> HYPNOGRAM_HEIGHT_DP
            is MiniChart.StackedBars -> STACKED_HEIGHT_DP
            else -> BARS_HEIGHT_DP
        }
        renderChart(view.findViewById(R.id.tile_large_chart), large, height)
        renderAxis(view.findViewById(R.id.tile_axis), model.axis, large != null)
        view.findViewById<View>(R.id.tile_stats).visibility = if (model.stats.isEmpty()) View.GONE else View.VISIBLE
        renderStats(view.findViewById(R.id.tile_stats_row), model.stats, mini = false)
    }

    /**
     * The status chip and the first two [TileModel.notes] beside the gauge.
     */
    private fun renderSideInfo(view: View, model: TileModel) {
        val status = view.findViewById<View>(R.id.tile_side_status)
        val chip = model.status as? StatusLine.Chip
        status.visibility = if (chip == null) View.GONE else View.VISIBLE
        if (chip != null) {
            view.findViewById<View>(R.id.tile_side_status_dot).backgroundTintList = ColorStateList.valueOf(chip.color)
            view.findViewById<TextView>(R.id.tile_side_status_text).text = chip.text
        }
        view.findViewById<TextView>(R.id.tile_side_note).text = model.notes.firstOrNull()
        val subnote = view.findViewById<TextView>(R.id.tile_side_subnote)
        subnote.text = model.notes.getOrNull(1)
        subnote.visibility = if (model.notes.size > 1) View.VISIBLE else View.GONE
    }

    override fun renderLoading(view: View, title: String, icon: Int) {
        renderHeader(view, title, icon)
        view.findViewById<View>(R.id.tile_value_row).visibility = View.VISIBLE
        view.findViewById<TextView>(R.id.tile_value).text = ""
        for (id in intArrayOf(
            R.id.tile_value_suffix, R.id.tile_mini_stats, R.id.tile_status, R.id.tile_gauge_row, R.id.tile_chart,
            R.id.tile_legend, R.id.tile_large_chart, R.id.tile_axis, R.id.tile_stats,
        )) {
            view.findViewById<View>(id).visibility = View.GONE
        }
    }

    private const val LEGEND_COLUMNS = 2
    private const val BARS_HEIGHT_DP = 44f
    private const val STACKED_HEIGHT_DP = 70f
    private const val HYPNOGRAM_HEIGHT_DP = 60f
    private const val LINE_HEIGHT_DP = 80f
}

private const val GAUGE_VALUE_SIZE_SP = 26f
private const val GAUGE_LONG_VALUE_SIZE_SP = 24f
private const val GAUGE_WIDTH_DP = 104f
private const val HEADER_TEXT_SIZE_SP = 13f
private const val HEADER_MIN_TEXT_SIZE_SP = 12f
private const val BAR_HEIGHT_DP = 6f
private const val ZONE_BAR_HEIGHT_DP = 10f
private const val GAUGE_LONG_VALUE_LENGTH = 5

private fun isBar(chart: MiniChart) =
    chart is MiniChart.Progress || chart is MiniChart.Composition || chart is MiniChart.ZoneBar

private fun renderGauge(view: View, title: String, icon: Int, model: TileModel) {
    renderHeader(view, title, icon)
    sizeGauge(view)
    renderArc(view, model, GAUGE_VALUE_SIZE_SP, GAUGE_LONG_VALUE_SIZE_SP)
}

private fun sizeGauge(view: View) {
    view.findViewById<ArcGaugeView>(R.id.gauge_bar).maxGaugeWidth =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, GAUGE_WIDTH_DP, view.resources.displayMetrics).roundToInt()
}

private fun renderArc(view: View, model: TileModel, valueSizeSp: Float, longValueSizeSp: Float) {
    val value = view.findViewById<TextView>(R.id.gauge_value)
    value.text = model.value
    value.setTextColor(valueColor(value, model.valueDimmed))
    val numberLength = value.text.takeWhile { !it.isLetter() && it != '%' }.trim().length
    value.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (numberLength >= GAUGE_LONG_VALUE_LENGTH) longValueSizeSp else valueSizeSp)
    shrinkUnits(value)
    val gauge = view.findViewById<ArcGaugeView>(R.id.gauge_bar)
    when (val spec = model.gauge) {
        is GaugeSpec.Progress -> gauge.setProgress(spec.color, spec.fraction)
        is GaugeSpec.Zones -> gauge.setZones(spec.colors, spec.segments, spec.marker, spec.gapBetweenSegments, spec.markerColor)
    }
}

private fun renderGaugeLoading(view: View, title: String, icon: Int) {
    renderHeader(view, title, icon)
    sizeGauge(view)
    view.findViewById<TextView>(R.id.gauge_value).text = ""
    view.findViewById<ArcGaugeView>(R.id.gauge_bar).setProgress(0, -1f)
}

/**
 * Shows the icon and the full widget name, shrunk from 13 to 12sp rather than cut when it is too long.
 */
internal fun renderHeader(view: View, title: String, icon: Int) {
    val label = view.findViewById<TextView>(R.id.tile_label)
    label.ellipsize = null
    label.text = title
    label.setTextSize(TypedValue.COMPLEX_UNIT_SP, HEADER_TEXT_SIZE_SP)
    label.doOnLayout {
        if (label.paint.measureText(title) > label.width - label.paddingStart - label.paddingEnd) {
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, HEADER_MIN_TEXT_SIZE_SP)
        }
    }
    view.findViewById<ImageView>(R.id.tile_icon).setImageResource(icon)
}

private fun renderValue(view: View, model: TileModel) {
    val value = view.findViewById<TextView>(R.id.tile_value)
    value.visibility = View.VISIBLE
    value.text = model.value
    value.setTextColor(valueColor(value, model.valueDimmed))
    shrinkUnits(value)
    model.valueNote?.let { appendNote(value, it) }
    val placeholder: View? = view.findViewById(R.id.tile_placeholder)
    placeholder?.visibility = View.GONE
}

private fun valueColor(view: TextView, dimmed: Boolean): Int = if (dimmed) {
    GBApplication.getSecondaryTextColor(view.context)
} else {
    MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface)
}

private fun renderValueLoading(view: View) {
    view.findViewById<View>(R.id.tile_value).visibility = View.GONE
    view.findViewById<View>(R.id.tile_placeholder).visibility = View.VISIBLE
}

private fun renderStatus(view: View, line: StatusLine?, trailing: CharSequence?) {
    val status = view.findViewById<View>(R.id.tile_status)
    val dot = view.findViewById<View>(R.id.tile_status_dot)
    val statusText = view.findViewById<TextView>(R.id.tile_status_text)
    val trailingView: TextView? = view.findViewById(R.id.tile_trailing)
    trailingView?.text = trailing
    trailingView?.visibility = if (trailing == null) View.GONE else View.VISIBLE
    when (line) {
        null -> status.visibility = if (trailing == null) View.GONE else View.VISIBLE
        is StatusLine.Text -> {
            status.visibility = View.VISIBLE
            dot.visibility = View.GONE
            statusText.text = line.text
            statusText.setTextColor(GBApplication.getSecondaryTextColor(statusText.context))
        }
        is StatusLine.Chip -> {
            status.visibility = View.VISIBLE
            dot.visibility = View.VISIBLE
            dot.backgroundTintList = ColorStateList.valueOf(line.color)
            statusText.text = line.text
            statusText.setTextColor(MaterialColors.getColor(statusText, com.google.android.material.R.attr.colorOnSurface))
        }
    }
    if (line == null) {
        dot.visibility = View.GONE
        statusText.text = null
    }
}

/**
 * Shows [chart] in [view], with the bar kinds at their fixed height and the others at [heightDp].
 */
private fun renderChart(view: MiniChartView, chart: MiniChart?, heightDp: Float) {
    if (chart == null) {
        view.visibility = View.GONE
        return
    }
    view.visibility = View.VISIBLE
    val dp = when (chart) {
        is MiniChart.Progress, is MiniChart.Composition -> BAR_HEIGHT_DP
        is MiniChart.ZoneBar -> ZONE_BAR_HEIGHT_DP
        else -> heightDp
    }
    val height = (dp * view.resources.displayMetrics.density).roundToInt()
    if (view.layoutParams.height != height) {
        view.layoutParams.height = height
        view.requestLayout()
    }
    view.chart = chart
}

/**
 * Spreads [labels] from end to end under a chart.
 */
internal fun renderAxis(container: LinearLayout, labels: List<String>, hasChart: Boolean) {
    if (!hasChart || labels.isEmpty()) {
        container.visibility = View.GONE
        return
    }
    container.visibility = View.VISIBLE
    while (container.childCount > labels.size) container.removeViewAt(container.childCount - 1)
    while (container.childCount < labels.size) {
        LayoutInflater.from(container.context).inflate(R.layout.dashboard_widget_axis_label, container, true)
    }
    val atEdges = labels == TileAxis.HOURS
    for (i in labels.indices) {
        val label = container.getChildAt(i) as TextView
        label.text = labels[i]
        val edge = i == 0 || i == labels.size - 1
        (label.layoutParams as LinearLayout.LayoutParams).weight = if (atEdges && edge) 0.5f else 1f
        label.gravity = when {
            labels.size == 1 -> Gravity.CENTER_HORIZONTAL
            i == 0 -> Gravity.START
            i == labels.size - 1 -> Gravity.END
            else -> Gravity.CENTER_HORIZONTAL
        }
    }
}

/**
 * Lays [rows] out in [columns] columns.
 */
private fun renderLegend(container: LinearLayout, rows: List<LegendRow>, columns: Int = 1) {
    container.removeAllViews()
    if (rows.isEmpty()) {
        container.visibility = View.GONE
        return
    }
    container.visibility = View.VISIBLE
    val inflater = LayoutInflater.from(container.context)
    val gap = (LEGEND_COLUMN_GAP_DP * container.resources.displayMetrics.density).roundToInt()
    for (line in rows.chunked(columns)) {
        val lineView = LinearLayout(container.context)
        lineView.orientation = LinearLayout.HORIZONTAL
        container.addView(lineView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        for (column in 0 until columns) {
            val item = inflater.inflate(R.layout.dashboard_widget_legend_row, lineView, false)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            params.marginStart = if (column > 0) gap else 0
            lineView.addView(item, params)
            val row = line.getOrNull(column)
            if (row == null) {
                item.visibility = View.INVISIBLE
                continue
            }
            val swatch = item.findViewById<View>(R.id.legend_dot)
            if (row.square) {
                swatch.setBackgroundResource(R.drawable.dashboard_legend_swatch)
                val size = (SWATCH_SIZE_DP * container.resources.displayMetrics.density).roundToInt()
                swatch.layoutParams.width = size
                swatch.layoutParams.height = size
            }
            swatch.backgroundTintList = ColorStateList.valueOf(row.color)
            item.findViewById<TextView>(R.id.legend_name).text = row.name
            item.findViewById<TextView>(R.id.legend_value).text = row.value
        }
    }
}

/**
 * The inline [mini] figures next to the value, or the equal columns of the stats row.
 */
private fun renderStats(container: LinearLayout, stats: List<Stat>, mini: Boolean) {
    container.removeAllViews()
    container.visibility = if (stats.isEmpty()) View.GONE else View.VISIBLE
    val inflater = LayoutInflater.from(container.context)
    val gap = (MINI_STAT_GAP_DP * container.resources.displayMetrics.density).roundToInt()
    for (stat in stats) {
        val item = inflater.inflate(R.layout.dashboard_widget_stat, container, false)
        val value = item.findViewById<TextView>(R.id.stat_value)
        value.text = stat.value
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (mini) MINI_STAT_SIZE_SP else STAT_SIZE_SP)
        item.findViewById<TextView>(R.id.stat_label).text = stat.label
        val params = if (mini) {
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = gap
            }
        } else {
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        container.addView(item, params)
    }
}

private const val LEGEND_COLUMN_GAP_DP = 12f
private const val SWATCH_SIZE_DP = 8f
private const val MINI_STAT_GAP_DP = 12f
private const val MINI_STAT_SIZE_SP = 14f
private const val STAT_SIZE_SP = 16f

/**
 * Wrapping legend of square swatches, [rowGapDp] apart. Swatches darker than the track get a thin border so they
 * stay visible.
 */
internal fun renderFlowLegend(container: ViewGroup, items: List<Pair<Int, CharSequence>>, rowGapDp: Float) {
    container.removeAllViews()
    container.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    val inflater = LayoutInflater.from(container.context)
    val density = container.resources.displayMetrics.density
    val border = MaterialColors.getColor(container, com.google.android.material.R.attr.colorOutline)
    for ((color, label) in items) {
        val item = inflater.inflate(R.layout.dashboard_widget_swatch_item, container, false)
        val swatch = GradientDrawable()
        swatch.setColor(color)
        swatch.cornerRadius = SWATCH_RADIUS_DP * density
        if (ColorUtils.calculateLuminance(color) < DARK_SWATCH_LUMINANCE) {
            swatch.setStroke(density.roundToInt().coerceAtLeast(1), border)
        }
        item.findViewById<View>(R.id.swatch).background = swatch
        item.findViewById<TextView>(R.id.swatch_label).text = label
        (item.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
            val vertical = (rowGapDp / 2 * density).roundToInt()
            val horizontal = (LEGEND_ITEM_GAP_DP / 2 * density).roundToInt()
            it.setMargins(horizontal, vertical, horizontal, vertical)
        }
        container.addView(item)
    }
}

/**
 * Shows the info button when the tile hides its legend, opening [items] in a dialog titled [title]. The button sits
 * in the bottom corner, or in the header on tiles without a free corner.
 */
internal fun renderLegendButton(view: View, title: String, items: List<Pair<Int, CharSequence>>) {
    val button = view.findViewById<View?>(R.id.tile_info_corner) ?: view.findViewById(R.id.tile_info)
    if (items.isEmpty()) {
        button.visibility = View.GONE
        button.setOnClickListener(null)
        return
    }
    button.visibility = View.VISIBLE
    button.setOnClickListener { showLegendDialog(it.context, title, items) }
}

private fun showLegendDialog(context: Context, title: String, items: List<Pair<Int, CharSequence>>) {
    val density = context.resources.displayMetrics.density
    val list = LinearLayout(context)
    list.orientation = LinearLayout.VERTICAL
    val padding = (DIALOG_PADDING_DP * density).roundToInt()
    list.setPadding(padding, (DIALOG_ROW_GAP_DP * density).roundToInt(), padding, 0)
    renderFlowLegend(list, items, DIALOG_ROW_GAP_DP)
    for (i in 0 until list.childCount) {
        val item = list.getChildAt(i)
        item.minimumHeight = (DIALOG_ROW_HEIGHT_DP * density).roundToInt()
        val swatch = item.findViewById<View>(R.id.swatch)
        swatch.layoutParams.width = (DIALOG_SWATCH_DP * density).roundToInt()
        swatch.layoutParams.height = (DIALOG_SWATCH_DP * density).roundToInt()
        (swatch.layoutParams as ViewGroup.MarginLayoutParams).marginEnd = (DIALOG_SWATCH_GAP_DP * density).roundToInt()
        (swatch.background as? GradientDrawable)?.cornerRadius = DIALOG_SWATCH_RADIUS_DP * density
        val label = item.findViewById<TextView>(R.id.swatch_label)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, DIALOG_LABEL_SP)
        label.setTextColor(MaterialColors.getColor(label, com.google.android.material.R.attr.colorOnSurface))
    }
    MaterialAlertDialogBuilder(context)
        .setTitle(title)
        .setView(list)
        .setPositiveButton(android.R.string.ok, null)
        .show()
}

private const val DIALOG_PADDING_DP = 24f
private const val DIALOG_ROW_GAP_DP = 2f
private const val DIALOG_ROW_HEIGHT_DP = 32f
private const val DIALOG_SWATCH_DP = 12f
private const val DIALOG_SWATCH_RADIUS_DP = 3f
private const val DIALOG_SWATCH_GAP_DP = 12f
private const val DIALOG_LABEL_SP = 14f

private const val SWATCH_RADIUS_DP = 2f
private const val LEGEND_ITEM_GAP_DP = 12f

// The luminance of #262626
private const val DARK_SWATCH_LUMINANCE = 0.019

private val UNIT_REGEX = Regex("[\\p{L}%]+(?:(?:\\s+|/)[\\p{L}%]+)*")
private const val UNIT_SIZE = 0.5f
private const val NOTE_SIZE_SP = 12

/**
 * [note] after the value, in the note size and colour.
 */
private fun appendNote(value: TextView, note: CharSequence) {
    val text = SpannableStringBuilder(value.text).append(' ')
    val start = text.length
    text.append(note)
    text.setSpan(AbsoluteSizeSpan(NOTE_SIZE_SP, true), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    text.setSpan(TypefaceSpan("sans-serif"), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    text.setSpan(
        ForegroundColorSpan(MaterialColors.getColor(value, com.google.android.material.R.attr.colorOnSurfaceVariant)),
        start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
    )
    value.text = text
}

/**
 * Draws the unit part of a value, like "kcal" or "%", smaller and in the secondary colour.
 */
private fun shrinkUnits(value: TextView) {
    val text = value.text
    if (text is Spanned || text.none { it.isDigit() }) {
        return
    }
    val spannable = text.toSpannable()
    val unitColor = MaterialColors.getColor(value, com.google.android.material.R.attr.colorOnSurfaceVariant)
    for (match in UNIT_REGEX.findAll(text)) {
        spannable.setSpan(RelativeSizeSpan(UNIT_SIZE), match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(ForegroundColorSpan(unitColor), match.range.first, match.range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
    value.text = spannable
}
