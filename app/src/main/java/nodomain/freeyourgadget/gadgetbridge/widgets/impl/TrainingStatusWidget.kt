package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import com.google.android.material.color.MaterialColors
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.charts.TrainingStatTiles
import nodomain.freeyourgadget.gadgetbridge.activities.charts.TrainingStatusReport
import nodomain.freeyourgadget.gadgetbridge.activities.charts.trainingstatus.StatusRun
import nodomain.freeyourgadget.gadgetbridge.activities.charts.trainingstatus.TrainingStatusChartData
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.MiniChartView
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.renderAxis
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.renderHeader
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.renderStatsRow
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.HrvSummarySample
import nodomain.freeyourgadget.gadgetbridge.model.TrainingStatus
import nodomain.freeyourgadget.gadgetbridge.widgets.GBWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.MiniChart
import nodomain.freeyourgadget.gadgetbridge.widgets.Stat
import nodomain.freeyourgadget.gadgetbridge.widgets.TileAxis
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetActions
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetVariant
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The training status of the selected day and of the 4 weeks up to it.
 */
object TrainingStatusWidget : GBWidget<TrainingStatusWidget.Data> {
    private val LOG = LoggerFactory.getLogger(TrainingStatusWidget::class.java)

    private const val CHART_TAB = "trainingstatus"
    private const val DAYS = 28
    private const val PAST_ALPHA = 0.55f

    private class Style(val iconDp: Int, val nameSp: Float, val barDp: Int, val barTopDp: Int)

    private val GAUGE = Style(iconDp = 32, nameSp = 20f, barDp = 0, barTopDp = 0)
    private val NUMBER = Style(iconDp = 0, nameSp = 20f, barDp = 6, barTopDp = 10)
    private val TALL = Style(iconDp = 32, nameSp = 20f, barDp = 10, barTopDp = 20)
    private val WIDE = Style(iconDp = 24, nameSp = 22f, barDp = 8, barTopDp = 0)
    private val LARGE = Style(iconDp = 28, nameSp = 24f, barDp = 10, barTopDp = 16)

    private fun styleOf(variant: WidgetVariant): Style = when (variant.id) {
        WidgetVariant.NUMBER.id -> NUMBER
        WidgetVariant.TALL.id -> TALL
        WidgetVariant.WIDE.id -> WIDE
        WidgetVariant.METRIC_LARGE.id -> LARGE
        else -> GAUGE
    }

    override val id = CHART_TAB
    override val label = R.string.metric_garmin_training_status
    override val icon = R.drawable.ic_widget_training_status
    override val variants = listOf(
        WidgetVariant.GAUGE,
        WidgetVariant.NUMBER,
        WidgetVariant.TALL,
        WidgetVariant.WIDE,
        WidgetVariant.METRIC_LARGE,
    )

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsTrainingStatus(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        val reports = try {
            scope.db { db -> scope.devices.map { TrainingStatusReport.load(db, it, scope.day.toEpochDay(), DAYS) } }
        } catch (e: Exception) {
            LOG.error("Could not get training status", e)
            emptyList()
        }
        val report = reports.firstOrNull { it.status != null }
            ?: reports.firstOrNull { candidate -> candidate.days.any { it != null } }
            ?: reports.firstOrNull()
        return Data(report, scope.day)
    }

    override fun previewData(): Data {
        val today = LocalDate.now()
        val todayDay = today.toEpochDay()
        val days = Array<Int?>(DAYS) {
            when {
                it < 6 -> TrainingStatus.MAINTAINING.value
                it < 22 -> TrainingStatus.PRODUCTIVE.value
                else -> TrainingStatus.RECOVERY.value
            }
        }
        return Data(
            TrainingStatusReport(
                days = days,
                day = todayDay,
                today = todayDay,
                run = StatusRun(TrainingStatus.RECOVERY.value, todayDay - 5, todayDay),
                acuteLoad = 420,
                chronicLoad = 382,
                vo2Max = 46.2f,
                hrvStatus = HrvSummarySample.Status.BALANCED,
                hrvWeeklyAverage = 52,
            ),
            today,
        )
    }

    override fun createView(inflater: LayoutInflater, parent: ViewGroup, variant: WidgetVariant): View {
        val full = variant.id == WidgetVariant.WIDE.id || variant.id == WidgetVariant.METRIC_LARGE.id
        val view = inflater.inflate(
            if (full) R.layout.dashboard_widget_training_status_wide else R.layout.dashboard_widget_training_status,
            parent,
            false,
        )
        val density = view.resources.displayMetrics.density
        val style = styleOf(variant)

        val iconView = view.findViewById<ImageView>(R.id.training_status_icon)
        if (style.iconDp == 0) {
            iconView.visibility = View.GONE
        } else {
            iconView.layoutParams.width = (style.iconDp * density).roundToInt()
            iconView.layoutParams.height = (style.iconDp * density).roundToInt()
        }
        view.findViewById<TextView>(R.id.training_status_name).setTextSize(TypedValue.COMPLEX_UNIT_SP, style.nameSp)

        val bar = view.findViewById<View>(R.id.training_status_bar)
        if (style.barDp == 0) {
            bar.visibility = View.GONE
            view.findViewById<View>(R.id.training_status_axis).visibility = View.GONE
        } else {
            val params = bar.layoutParams as LinearLayout.LayoutParams
            params.height = (style.barDp * density).roundToInt()
            params.topMargin = (style.barTopDp * density).roundToInt()
        }

        if (variant.id == WidgetVariant.NUMBER.id) {
            view.findViewById<LinearLayout>(R.id.training_status_summary).gravity = Gravity.BOTTOM or Gravity.START
        }
        return view
    }

    override fun bindLoading(view: View, variant: WidgetVariant, config: WidgetConfig) {
        renderHeader(view, config.title(view.context, label), icon)
        view.findViewById<ImageView>(R.id.training_status_icon).setImageDrawable(null)
        view.findViewById<TextView>(R.id.training_status_name).text = null
        view.findViewById<View>(R.id.training_status_range).visibility = View.GONE
        view.findViewById<MiniChartView>(R.id.training_status_bar).chart = null
        view.findViewById<LinearLayout>(R.id.training_status_axis).removeAllViews()
        if (variant.id == WidgetVariant.METRIC_LARGE.id) {
            renderStatsRow(view, emptyList())
        }
        view.contentDescription = null
    }

    override fun bind(view: View, variant: WidgetVariant, config: WidgetConfig, data: Data) {
        val context = view.context
        val title = config.title(context, label)
        renderHeader(view, title, icon)

        val report = data.report
        val days = report?.days ?: arrayOfNulls<Int>(DAYS)
        val status = days.last()
        val color = if (status != null) TrainingStatus.color(context, status) else GBApplication.getSecondaryTextColor(context)
        val name = if (status != null) TrainingStatus.label(context, status) else context.getString(R.string.no_data)
        val range = report?.rangeText(context)

        val iconView = view.findViewById<ImageView>(R.id.training_status_icon)
        iconView.setImageResource(if (status != null) TrainingStatus.icon(status) else R.drawable.ic_help_outline)
        iconView.imageTintList = ColorStateList.valueOf(color)
        val nameView = view.findViewById<TextView>(R.id.training_status_name)
        nameView.text = name
        nameView.setTextColor(color)
        val rangeView = view.findViewById<TextView>(R.id.training_status_range)
        rangeView.text = range
        rangeView.visibility = if (range == null || variant.id == WidgetVariant.GAUGE.id) View.GONE else View.VISIBLE

        if (variant.id != WidgetVariant.GAUGE.id) {
            renderBar(view, days, data.day)
        }
        if (variant.id == WidgetVariant.METRIC_LARGE.id) {
            renderStatsRow(view, report?.let { stats(context, it) }.orEmpty())
        }
        view.contentDescription = describe(context, title, name, range, days)
    }

    private fun renderBar(view: View, days: Array<Int?>, day: LocalDate) {
        val context = view.context
        val track = MaterialColors.getColor(view, R.attr.gauge_track)
        val spans = TrainingStatusChartData.spans(days)
        val colors = IntArray(spans.size) { index ->
            val status = spans[index].status
            when {
                status == null -> track
                index == spans.lastIndex -> TrainingStatus.color(context, status)
                else -> ColorUtils.setAlphaComponent(TrainingStatus.color(context, status), (PAST_ALPHA * 255).roundToInt())
            }
        }
        val weights = FloatArray(spans.size) { spans[it].days.toFloat() }
        view.findViewById<MiniChartView>(R.id.training_status_bar).chart = MiniChart.Timeline(colors, weights)

        val axis = view.findViewById<LinearLayout>(R.id.training_status_axis)
        renderAxis(axis, TileAxis.dateRange(day.minusDays(DAYS - 1L), day), true)
        val last = axis.getChildAt(axis.childCount - 1) as TextView
        last.setTextColor(MaterialColors.getColor(view, com.google.android.material.R.attr.colorOnSurface))
        last.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun stats(context: Context, report: TrainingStatusReport): List<Stat> {
        val load = report.acuteLoad?.takeIf { (report.chronicLoad ?: 0) > 0 }?.let {
            statusStat(TrainingStatTiles.acuteLoad(context, context.getString(R.string.training_status_load), it, report.chronicLoad))
        }
        val hrv = statusStat(TrainingStatTiles.hrv(context, report.hrvStatus, report.hrvWeeklyAverage))
        val vo2Max = report.vo2Max?.let {
            Stat(String.format(Locale.getDefault(), "%.1f", it), context.getString(R.string.menuitem_vo2_max))
        }
        return listOfNotNull(load, hrv, vo2Max)
    }

    private fun statusStat(tile: StatTileData): Stat? {
        val text = tile.subtext ?: return null
        val textColor = tile.subtextColor ?: return null
        return Stat(buildSpannedString { color(textColor) { append(text) } }, tile.label)
    }

    private fun describe(context: Context, title: String, name: String, range: String?, days: Array<Int?>): String {
        val history = TrainingStatusChartData.spans(days).mapNotNull { span ->
            span.status?.let {
                "${TrainingStatus.label(context, it)} " +
                    context.resources.getQuantityString(R.plurals.amount_of_days, span.days, span.days)
            }
        }
        return (listOfNotNull(title, name, range) + history).joinToString(", ")
    }

    override fun onClick(view: View, variant: WidgetVariant, config: WidgetConfig, timestamp: Int) {
        WidgetActions.openChart(view.context, config, this, CHART_TAB, label, "", timestamp)
    }

    /**
     * The [report] of the selected [day], null without a device.
     */
    class Data(val report: TrainingStatusReport?, val day: LocalDate)
}
