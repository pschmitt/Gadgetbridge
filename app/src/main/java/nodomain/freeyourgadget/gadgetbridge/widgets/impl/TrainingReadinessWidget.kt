package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import androidx.core.content.ContextCompat
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.charts.StressFragment
import nodomain.freeyourgadget.gadgetbridge.activities.charts.TrainingReadinessReport
import nodomain.freeyourgadget.gadgetbridge.devices.GenericMetricSampleProvider
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.HrvSummarySample
import nodomain.freeyourgadget.gadgetbridge.model.MetricSample
import nodomain.freeyourgadget.gadgetbridge.widgets.GaugeSpec
import nodomain.freeyourgadget.gadgetbridge.widgets.LegendRow
import nodomain.freeyourgadget.gadgetbridge.widgets.MiniChart
import nodomain.freeyourgadget.gadgetbridge.widgets.StatusLine
import nodomain.freeyourgadget.gadgetbridge.widgets.TileAxis
import nodomain.freeyourgadget.gadgetbridge.widgets.TileModel
import nodomain.freeyourgadget.gadgetbridge.widgets.TimedValue
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetHistory
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetVariant
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The training readiness score and the factors behind it.
 */
object TrainingReadinessWidget : MetricWidget<TrainingReadinessWidget.Data>() {
    private val LOG = LoggerFactory.getLogger(TrainingReadinessWidget::class.java)

    private const val DAYS = 7
    private const val STALE_MILLIS = 12 * 60 * 60 * 1000L

    override val id = "trainingreadiness"
    override val label = R.string.metric_garmin_training_readiness
    override val icon = R.drawable.ic_widget_training_readiness
    override val chartTab = "trainingreadiness"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsTrainingReadiness(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        val until = scope.query.timeTo * 1000L
        val from = scope.dayStartMinusDays(DAYS - 1) * 1000L
        var report: TrainingReadinessReport? = null
        val history = mutableListOf<TimedValue>()
        try {
            scope.db { db ->
                for (dev in scope.devices) {
                    val candidate = TrainingReadinessReport.load(db, dev, until)
                    if (candidate.timestamp != null && candidate.timestamp > (report?.timestamp ?: Long.MIN_VALUE)) {
                        report = candidate
                    }
                    if (config.needsHistory) {
                        GenericMetricSampleProvider.getMetricSamples(db, dev, MetricSample.Metric.GARMIN_TRAINING_READINESS, from, until)
                            .mapTo(history) { TimedValue(it.timestamp / 1000L, it.metricScore.toFloat()) }
                    }
                }
            }
        } catch (e: Exception) {
            LOG.error("Could not get training readiness", e)
        }
        // a past day only shows the score from that day
        val shown = report?.takeIf { scope.isToday || (it.timestamp ?: 0L) >= scope.dayStart * 1000L }
        return Data(
            report = shown,
            isToday = scope.isToday,
            history = if (config.needsHistory) WidgetHistory.dailyLatest(history, scope.day, DAYS, scope.zone) else null,
            day = scope.day,
        )
    }

    override fun previewData() = Data(
        report = TrainingReadinessReport(
            score = 78,
            timestamp = LocalDate.now().atTime(7, 12).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            hrvStatus = HrvSummarySample.Status.BALANCED,
            hrvWeeklyAverage = 52,
            acuteLoad = 420,
            chronicLoad = 382,
            sleepScore = 84,
            recoveryMinutes = 300,
            sleepHistoryAvg = 81,
            stressHistoryAvg = 28,
            stressRanges = intArrayOf(0, 40, 60, 80),
        ),
        history = floatArrayOf(64f, 71f, 58f, 82f, 76f, 69f, 78f),
    )

    override fun model(context: Context, config: WidgetConfig, data: Data): TileModel {
        val zoneColors = TrainingReadinessReport.ZONE_COLOR_RES.map { ContextCompat.getColor(context, it) }.toIntArray()
        val report = data.report
        val score = report?.score
        val bars = data.history?.let { history ->
            MiniChart.Bars(
                history,
                IntArray(history.size) { if (history[it].isNaN()) 0 else zoneColors[TrainingReadinessReport.zoneIndexForScore(history[it].toInt())] },
                0f,
                100f,
                highlightLast = true,
            )
        }
        val axis = if (bars != null) TileAxis.weekdays(data.day, DAYS) else emptyList()
        if (report == null || score == null) {
            val empty = emptyModel(context, GaugeSpec.Zones(zoneColors, TrainingReadinessReport.ZONE_SEGMENTS))
            return when {
                config.isTall || config.isWide -> empty.copy(largeChart = bars, axis = axis)
                config.variant.id == WidgetVariant.NUMBER.id -> empty.copy(chart = bars)
                else -> empty
            }
        }
        val zone = TrainingReadinessReport.zoneIndexForScore(score)
        val advice = context.getString(TrainingReadinessReport.ZONE_DESC_RES[zone])
        val factors = factors(context, report)
        val model = TileModel(
            value = score.toString(),
            gauge = GaugeSpec.Zones(zoneColors, TrainingReadinessReport.ZONE_SEGMENTS, score / 100f, markerColor = zoneColors[zone]),
            status = StatusLine.Chip(zoneColors[zone], context.getString(TrainingReadinessReport.ZONE_NAME_RES[zone])),
            contentDescription = describe(context, config, score, zone, advice, factors),
            valueNote = report.timestamp?.takeIf { config.variant.id == WidgetVariant.NUMBER.id || config.isWide }
                ?.let { context.getString(R.string.widget_status_at_time, measuredAt(it, data.day)) },
        )
        return when {
            config.isTall -> model.copy(largeChart = bars, axis = axis)
            config.isLargeStyle -> model.copy(
                gaugeBesideLegend = true,
                notes = listOfNotNull(advice, if (data.isToday) report.timestamp?.let { updatedText(context, it, System.currentTimeMillis()) } else null),
                legend = factors,
            )
            config.isWide -> model.copy(largeChart = bars, axis = axis)
            else -> model.copy(chart = bars)
        }
    }

    /**
     * HRV status, acute load, sleep, recovery, sleep over 3 nights and stress over 3 days, skipping missing ones.
     */
    internal fun factors(context: Context, report: TrainingReadinessReport): List<LegendRow> {
        val neutral = GBApplication.getSecondaryTextColor(context)
        fun color(res: Int) = ContextCompat.getColor(context, res)
        fun sleepColor(score: Int) = color(
            when {
                score >= 90 -> R.color.training_readiness_high
                score >= 80 -> R.color.training_readiness_moderate
                score >= 60 -> R.color.training_readiness_low
                else -> R.color.training_readiness_poor
            }
        )
        val loadColors = TrainingLoadWidget.Colors(context)
        return listOfNotNull(
            report.hrvWeeklyAverage?.let {
                val res = when (report.hrvStatus) {
                    HrvSummarySample.Status.BALANCED -> R.color.hrv_status_balanced
                    HrvSummarySample.Status.UNBALANCED -> R.color.hrv_status_unbalanced
                    HrvSummarySample.Status.LOW -> R.color.hrv_status_low
                    HrvSummarySample.Status.POOR -> R.color.hrv_status_poor
                    else -> null
                }
                LegendRow(res?.let(::color) ?: neutral, context.getString(R.string.pref_header_hrv_status), context.getString(R.string.hrv_status_unit, it), square = true)
            },
            report.acuteLoad?.let { acute ->
                val status = TrainingLoadWidget.statusFor(acute, report.chronicLoad ?: 0)
                LegendRow(status?.let { loadColors.of(it) } ?: neutral, context.getString(R.string.training_acute_load), acute.toString(), square = true)
            },
            report.sleepScore?.let {
                LegendRow(sleepColor(it), context.getString(R.string.menuitem_sleep), it.toString(), square = true)
            },
            report.recoveryMinutes?.let {
                LegendRow(
                    if (it == 0) loadColors.optimal else neutral,
                    context.getString(R.string.recoveryTime),
                    "${it / 60}${context.getString(R.string.unit_hours)}",
                    square = true,
                )
            },
            report.sleepHistoryAvg?.let {
                LegendRow(sleepColor(it), context.getString(R.string.training_readiness_factor_sleep_3_nights), it.toString(), square = true)
            },
            report.stressHistoryAvg?.let {
                LegendRow(
                    StressFragment.StressType.fromStress(it, report.stressRanges).getColor(context),
                    context.getString(R.string.training_readiness_factor_stress_3_days),
                    it.toString(),
                    square = true,
                )
            },
        )
    }

    /**
     * The time of [millis], with the date when it is not on [day].
     */
    private fun measuredAt(millis: Long, day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String {
        val time = Instant.ofEpochMilli(millis).atZone(zone)
        val style = if (time.toLocalDate() == day) {
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        } else {
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        }
        return time.format(style)
    }

    /**
     * "Updated 09:58", or "Updated yesterday 09:58" once the score is more than 12 hours old.
     */
    internal fun updatedText(context: Context, millis: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val time = Instant.ofEpochMilli(millis).atZone(zone)
        val formatted = time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when {
            now - millis <= STALE_MILLIS -> context.getString(R.string.widget_updated_at, formatted)
            time.toLocalDate() == today.minusDays(1) -> context.getString(R.string.widget_updated_yesterday_at, formatted)
            else -> context.getString(
                R.string.widget_updated_at,
                time.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)),
            )
        }
    }

    private fun describe(context: Context, config: WidgetConfig, score: Int, zone: Int, advice: String, factors: List<LegendRow>): String {
        val head = listOf(
            config.title(context, label),
            score.toString(),
            context.getString(TrainingReadinessReport.ZONE_NAME_RES[zone]),
            advice,
        ).joinToString(", ")
        val tail = factors.joinToString(", ") { "${it.name} ${it.value}" }
        return if (tail.isEmpty()) head else context.getString(R.string.widget_overview_description, head, tail)
    }

    /**
     * The latest [report] up to the selected day, null without a score. [history] holds the scores of the seven
     * days ending on the selected day, for every style but the gauge.
     */
    class Data(
        val report: TrainingReadinessReport? = null,
        val isToday: Boolean = true,
        val history: FloatArray? = null,
        val day: LocalDate = LocalDate.now(),
    )
}
