package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.PaiSample
import nodomain.freeyourgadget.gadgetbridge.widgets.GaugeSpec
import nodomain.freeyourgadget.gadgetbridge.widgets.LegendRow
import nodomain.freeyourgadget.gadgetbridge.widgets.MiniChart
import nodomain.freeyourgadget.gadgetbridge.widgets.Stat
import nodomain.freeyourgadget.gadgetbridge.widgets.StatusLine
import nodomain.freeyourgadget.gadgetbridge.widgets.TileAxis
import nodomain.freeyourgadget.gadgetbridge.widgets.TileModel
import nodomain.freeyourgadget.gadgetbridge.widgets.TimedValue
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetHistory
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetVariant
import org.slf4j.LoggerFactory
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/**
 * Current PAI (Personal Activity Intelligence) score.
 * <p>
 * When the total meets or exceeds the device target, the arc is drawn full, split into the
 * carry-over portion (total minus today) and today's contribution. Otherwise, the arc is only
 * partially filled to reflect the fraction of the goal completed.
 */
object PaiWidget : MetricWidget<PaiWidget.Data>() {
    private val LOG = LoggerFactory.getLogger(PaiWidget::class.java)

    private val COLOR_LOW = Color.rgb(0x5B, 0xB5, 0x34)
    private val COLOR_MODERATE = Color.rgb(0xF5, 0xA6, 0x23)
    private val COLOR_HIGH = Color.rgb(0xD6, 0x3A, 0x3A)

    private const val DAYS = 7
    private const val SCALE_ABOVE = 1.1f

    override val id = "pai"
    override val label = R.string.menuitem_pai
    override val icon = R.drawable.ic_run_circle
    override val chartTab = "pai"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsPai(device)

    override fun labelFor(variant: WidgetVariant, config: WidgetConfig): Int = nameOf(config.resolveDevices(this))

    override fun displayName(context: Context): String =
        namesOf(GBApplication.app().deviceManager.devices)
            .map { context.getString(it) }
            .distinct()
            .sorted()
            .joinToString(" / ")
            .ifEmpty { context.getString(label) }

    /**
     * The distinct names of the metric across the [devices] that support it.
     */
    internal fun namesOf(devices: List<GBDevice>): List<Int> =
        devices.filter { isSupportedBy(it) }.map { it.deviceCoordinator.paiName }.distinct()

    /**
     * The devices' own name for the metric, such as Intensity Minutes, or PAI when they disagree.
     */
    internal fun nameOf(devices: List<GBDevice>): Int =
        namesOf(devices).singleOrNull() ?: label

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        // Bounded by timeFrom/timeTo (not getLatestSample, which has no lower bound) so a day
        // with no data doesn't bleed back to a previous day's score.
        val windowStartMs = scope.query.timeFrom * 1000L
        val windowEndMs = scope.query.timeTo * 1000L

        val data = Data()
        data.hasTime = scope.devices.any { it.deviceCoordinator.supportsPaiTime(it) }
        data.hasLow = scope.devices.any { it.deviceCoordinator.supportsPaiLow(it) }
        var latestTimestamp = Long.MIN_VALUE

        try {
            scope.db { db ->
                for (dev in scope.devices) {
                    val coordinator = dev.deviceCoordinator
                    val provider = coordinator.getPaiSampleProvider(dev, db.daoSession)
                    if (provider == null) {
                        LOG.warn("Device {} returned a null PAI sample provider - skipping", dev)
                        continue
                    }

                    val samples: List<PaiSample> = provider.getAllSamples(windowStartMs, windowEndMs)
                    if (samples.isEmpty()) continue

                    val sample = samples.last()
                    if (sample.timestamp > latestTimestamp) {
                        latestTimestamp = sample.timestamp
                        data.total = sample.paiTotal.roundToInt()
                        data.today = sample.paiToday.roundToInt()
                        data.target = coordinator.paiTarget
                        data.low = sample.paiLow.roundToInt()
                        data.moderate = sample.paiModerate.roundToInt()
                        data.high = sample.paiHigh.roundToInt()
                        data.minutesLow = sample.timeLow
                        data.minutesModerate = sample.timeModerate
                        data.minutesHigh = sample.timeHigh
                    }
                }
            }
        } catch (e: Exception) {
            LOG.error("Could not get PAI sample for dashboard widget", e)
        }

        if (config.isTall || config.isWide || config.isLargeStyle) {
            val (totals, increases) = week(scope)
            data.days = totals
            data.dayIncreases = increases
            data.day = scope.day
        }
        return data
    }

    override fun previewData(): Data = Data(
        total = 86,
        today = 12,
        target = 100,
        low = 3,
        moderate = 6,
        high = 3,
        minutesLow = 38,
        minutesModerate = 22,
        minutesHigh = 6,
        hasTime = true,
        hasLow = true,
        days = floatArrayOf(64f, 70f, 69f, 75f, 72f, 74f, 86f),
        dayIncreases = floatArrayOf(8f, 6f, 2f, 9f, 0f, 2f, 12f),
        // Sunday, so that the bars of the whole week have data
        day = LocalDate.of(2026, 10, 18),
    )

    /**
     * The weekly total and the day's increase at the end of each day of the selected day's week, Monday to Sunday.
     */
    private suspend fun week(scope: WidgetDataScope): Pair<FloatArray, FloatArray> {
        val from = weekStart(scope.day).atStartOfDay(scope.zone).toEpochSecond() * 1000L
        val samples = try {
            scope.db { db ->
                scope.devices.flatMap { dev ->
                    dev.deviceCoordinator.getPaiSampleProvider(dev, db.daoSession)
                        ?.getAllSamples(from, scope.query.timeTo * 1000L)
                        .orEmpty()
                }
            }
        } catch (e: Exception) {
            LOG.error("Could not get PAI history", e)
            emptyList()
        }
        val sunday = weekStart(scope.day).plusDays(DAYS - 1L)
        fun daily(value: (PaiSample) -> Float) =
            WidgetHistory.dailyLatest(samples.map { TimedValue(it.timestamp / 1000L, value(it)) }, sunday, DAYS, scope.zone)
        return daily { it.paiTotal } to daily { it.paiToday }
    }

    private fun weekStart(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    override fun model(context: Context, config: WidgetConfig, data: Data): TileModel {
        if (data.target <= 0) {
            return emptyModel(context)
        }

        val colorWeekly = ContextCompat.getColor(context, R.color.chart_pai_weekly)
        val colorToday = ContextCompat.getColor(context, R.color.chart_pai_today)
        val colors = intArrayOf(colorWeekly, colorToday)

        val segments = if (data.total >= data.target) {
            val todayFraction = if (data.total > 0) data.today / data.total.toFloat() else 0f
            floatArrayOf(1f - todayFraction, todayFraction)
        } else {
            floatArrayOf((data.total - data.today) / data.target.toFloat(), data.today / data.target.toFloat())
        }
        val rest = (1f - segments.sum()).coerceAtLeast(0f)

        val composition = MiniChart.Composition(
            intArrayOf(colorWeekly, colorToday, MaterialColors.getColor(context, R.attr.gauge_track, colorWeekly)),
            floatArrayOf(segments[0], segments[1], rest),
        )
        val model = TileModel(
            value = data.total.toString(),
            gauge = GaugeSpec.Zones(colors, segments, gapBetweenSegments = false),
            status = StatusLine.Text(context.getString(R.string.widget_status_increase_today, data.today)),
            chart = composition,
        )
        val goalColor = GBApplication.getSecondaryTextColor(context)
        val bars = data.days?.let {
            dayBars(it, data.dayIncreases ?: FloatArray(it.size), data.target, colorWeekly, colorToday, goalColor, data.day.dayOfWeek.value)
        }
        val axis = if (bars != null) TileAxis.weekdays(weekStart(data.day).plusDays(DAYS - 1L), DAYS) else emptyList()
        return when {
            config.isTall -> model.copy(
                chart = null,
                largeChart = bars,
                axis = axis,
            )
            config.isLargeStyle -> model.copy(
                chart = null,
                valueSuffix = context.getString(R.string.widget_value_of_goal, data.target.toString()),
                miniStats = intensityLegend(context, data).map { Stat(it.value, it.name) },
                largeChart = bars,
                axis = axis,
            )
            config.isWide -> model.copy(
                chart = bars,
                largeChart = bars,
                axis = axis,
            )
            else -> model
        }
    }

    /**
     * Each day's weekly total split into what came before and the day's [increases], with a goal line at [target]
     * on the same scale. Days from [dataUntil] on are still to come.
     */
    internal fun dayBars(
        totals: FloatArray,
        increases: FloatArray,
        target: Int,
        earlierColor: Int,
        increaseColor: Int,
        goalColor: Int,
        dataUntil: Int = totals.size,
    ): MiniChart.StackedBars {
        val increase = FloatArray(totals.size) { if (totals[it].isNaN()) Float.NaN else increases[it].coerceIn(0f, totals[it]) }
        return MiniChart.StackedBars(
            FloatArray(totals.size) { totals[it] - increase[it] },
            increase,
            earlierColor,
            increaseColor,
            maxOf(totals.filter { !it.isNaN() }.maxOrNull() ?: 0f, target.toFloat()) * SCALE_ABOVE,
            dataUntil,
            reference = target.toFloat(),
            referenceColor = goalColor,
        )
    }

    /**
     * Low (when the device tracks it), moderate and high intensity, as minutes or else as the PAI they earned.
     */
    internal fun intensityLegend(context: Context, data: Data): List<LegendRow> {
        val text = { pai: Int, minutes: Int ->
            if (data.hasTime) context.getString(R.string.num_min, minutes) else context.getString(R.string.pai_plus_num, pai)
        }
        return listOfNotNull(
            LegendRow(COLOR_LOW, context.getString(R.string.sony_speak_to_chat_sensitivity_low), text(data.low, data.minutesLow))
                .takeIf { data.hasLow },
            LegendRow(COLOR_MODERATE, context.getString(R.string.stress_moderate), text(data.moderate, data.minutesModerate)),
            LegendRow(COLOR_HIGH, context.getString(R.string.stress_high), text(data.high, data.minutesHigh)),
        )
    }

    /**
     * Rolling 7-day [total], today's contribution ([today]), and the device's [target] (typically 100). [low],
     * [moderate] and [high] are the PAI earned per intensity today, and the minutes fields the time spent in each.
     * [days] holds the weekly totals of [day]'s week, Monday to Sunday, and [dayIncreases] each day's increase,
     * for the tall, wide and large styles.
     */
    class Data(
        var total: Int = 0,
        var today: Int = 0,
        var target: Int = 0,
        var low: Int = 0,
        var moderate: Int = 0,
        var high: Int = 0,
        var minutesLow: Int = 0,
        var minutesModerate: Int = 0,
        var minutesHigh: Int = 0,
        var hasTime: Boolean = false,
        var hasLow: Boolean = false,
        var days: FloatArray? = null,
        var dayIncreases: FloatArray? = null,
        var day: LocalDate = LocalDate.now(),
    )
}
