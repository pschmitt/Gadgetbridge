package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.charts.StressFragment
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/**
 * Shared data model for the stress widgets. [hourly] holds hourly averages for every style but the gauge, and
 * [quarters] 15-minute averages for the wide and large styles; [hoursStarted] is how many hours have begun.
 * [latestSeconds] is the time of the latest sample, when [value] is a recent value of today.
 */
data class StressData(
    val value: Int,
    val ranges: IntArray,
    val totalTime: IntArray,
    val hourly: FloatArray? = null,
    val hoursStarted: Int = WidgetHistory.HOURS_PER_DAY,
    val quarters: FloatArray? = null,
    val latestSeconds: Long? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StressData) return false

        if (value != other.value) return false
        if (!ranges.contentEquals(other.ranges)) return false
        if (!totalTime.contentEquals(other.totalTime)) return false
        if (!hourly.contentEquals(other.hourly)) return false
        if (hoursStarted != other.hoursStarted) return false
        if (!quarters.contentEquals(other.quarters)) return false
        if (latestSeconds != other.latestSeconds) return false

        return true
    }

    override fun hashCode(): Int {
        var result = value
        result = 31 * result + ranges.contentHashCode()
        result = 31 * result + totalTime.contentHashCode()
        result = 31 * result + hourly.contentHashCode()
        result = 31 * result + hoursStarted
        result = 31 * result + quarters.contentHashCode()
        result = 31 * result + (latestSeconds?.hashCode() ?: 0)
        return result
    }
}

private val LOG = LoggerFactory.getLogger("StressData")
private const val QUARTER_SECONDS = 15 * 60L
private const val QUARTERS_PER_HOUR = 4
private const val QUARTERS_PER_DAY = 24 * QUARTERS_PER_HOUR
private const val STALE_SECONDS = 15 * 60L

/**
 * The colour of the gauge marker when the value is stale.
 */
internal val STRESS_STALE_COLOR = Color.rgb(0x8F, 0x8F, 0x8F)

/**
 * Computes the stress for [scope]'s devices/period, plus a per-type time breakdown, the hourly averages
 * when [hourly] is set and the 15-minute ones when [quarters] is set. The value is the average of the period when
 * [windowSeconds] is null, else the average of the samples in the [windowSeconds] before the latest sample, or the
 * latest sample when it is 0. Returns null if no device in [scope] has any stress samples for the period.
 */
suspend fun computeStress(
    scope: WidgetDataScope,
    hourly: Boolean = false,
    quarters: Boolean = false,
    windowSeconds: Long? = null,
): StressData? {
    var stressDevice: GBDevice? = null
    var averageStress = -1.0
    var latestMillis = 0L
    var hourlySamples: List<TimedValue> = emptyList()

    val totalTime = IntArray(StressFragment.StressType.entries.size)

    try {
        for (dev in scope.devices) {
            val samples = scope.db { db ->
                dev.deviceCoordinator.getStressSampleProvider(dev, db.daoSession)?.getAllSamples(
                    scope.query.timeFrom * 1000L,
                    scope.query.timeTo * 1000L
                )
            }
            if (samples.isNullOrEmpty()) {
                continue
            }
            val valid = samples.filter { it.stress > 0 }
            if (valid.isEmpty()) {
                continue
            }

            stressDevice = dev
            val stressRanges = dev.deviceCoordinator.stressRanges
            for (sample in samples) {
                val stressType = StressFragment.StressType.fromStress(sample.stress, stressRanges)
                if (stressType != StressFragment.StressType.UNKNOWN) {
                    totalTime[stressType.ordinal - 1] += 60
                }
            }
            latestMillis = valid.maxOf { it.timestamp }
            val averaged = when (windowSeconds) {
                null -> valid
                0L -> valid.filter { it.timestamp == latestMillis }
                else -> valid.filter { latestMillis - it.timestamp < windowSeconds * 1000L }
            }
            averageStress = averaged.sumOf { it.stress }.toDouble() / averaged.size
            if (hourly || quarters) {
                hourlySamples = valid.map { TimedValue(it.timestamp / 1000L, it.stress.toFloat()) }
            }
        }
    } catch (e: Exception) {
        LOG.error("Could not compute stress", e)
    }

    val device = stressDevice ?: return null
    return StressData(
        value = averageStress.roundToInt(),
        ranges = device.deviceCoordinator.stressRanges,
        totalTime = totalTime,
        hourly = if (hourly) WidgetHistory.hourlyAverages(hourlySamples, scope.dayStart) else null,
        hoursStarted = scope.hoursStarted,
        quarters = if (quarters) {
            WidgetHistory.bucketAverages(hourlySamples, scope.dayStart, QUARTER_SECONDS, QUARTERS_PER_DAY)
        } else {
            null
        },
        latestSeconds = if (windowSeconds != null && scope.isToday) latestMillis / 1000L else null,
    )
}

/**
 * Whether the latest sample of a recent value is stale (more than [STALE_SECONDS] old).
 */
internal fun isStressStale(data: StressData): Boolean {
    val latest = data.latestSeconds ?: return false
    return System.currentTimeMillis() / 1000L - latest > STALE_SECONDS
}

/**
 * The zone of the stress value, as a chip.
 */
internal fun stressStatus(context: Context, data: StressData): StatusLine.Chip {
    val type = StressFragment.StressType.fromStress(data.value, data.ranges)
    return StatusLine.Chip(type.getColor(context), type.getLabel(context))
}

/**
 * Hourly averages, each bar coloured by its zone, or null when they weren't loaded.
 */
internal fun stressHourlyBars(context: Context, data: StressData): MiniChart.Bars? {
    val hourly = data.hourly ?: return null
    val colors = IntArray(hourly.size) { hour ->
        if (hourly[hour].isNaN()) 0 else StressFragment.StressType.fromStress(hourly[hour].roundToInt(), data.ranges)
            .getColor(context)
    }
    return MiniChart.Bars(hourly, colors, 0f, 100f, dataUntil = data.hoursStarted)
}

/**
 * Stubs for a day without stress data.
 */
internal fun emptyStressBars(): MiniChart.Bars =
    MiniChart.Bars(FloatArray(WidgetHistory.HOURS_PER_DAY) { Float.NaN }, intArrayOf(0), 0f, 100f)

/**
 * 15-minute averages, each bar coloured by its zone, or null when they weren't loaded.
 */
internal fun stressQuarterBars(context: Context, data: StressData): MiniChart.Bars? {
    val quarters = data.quarters ?: return null
    val colors = IntArray(quarters.size) { quarter ->
        if (quarters[quarter].isNaN()) 0 else StressFragment.StressType.fromStress(
            quarters[quarter].roundToInt(),
            data.ranges
        ).getColor(context)
    }
    return MiniChart.Bars(quarters, colors, 0f, 100f, dataUntil = data.hoursStarted * QUARTERS_PER_HOUR)
}

/**
 * Time in each zone as shares and as a legend, for the large style.
 */
internal fun stressTimeInZones(
    context: Context,
    data: StressData,
    colors: IntArray
): Pair<MiniChart.Composition, List<LegendRow>> {
    val labels =
        intArrayOf(R.string.stress_relaxed, R.string.stress_mild, R.string.stress_moderate, R.string.stress_high)
    val minutes = FloatArray(colors.size) { data.totalTime[it] / 60f }
    val legend = colors.indices.map { zone ->
        LegendRow(
            colors[zone],
            context.getString(labels[zone]),
            context.getString(
                R.string.widget_duration_hours_minutes,
                minutes[zone].toInt() / 60,
                minutes[zone].toInt() % 60
            ),
        )
    }
    return MiniChart.Composition(colors, minutes) to legend
}

/**
 * The zone colours relaxed, mild, moderate and high.
 */
internal fun stressZoneColors(context: Context) = intArrayOf(
    ContextCompat.getColor(context, R.color.chart_stress_relaxed),
    ContextCompat.getColor(context, R.color.chart_stress_mild),
    ContextCompat.getColor(context, R.color.chart_stress_moderate),
    ContextCompat.getColor(context, R.color.chart_stress_high),
)

/**
 * The parts of a stress tile that depend on the style: the zone chip and 15-minute bars, or the time in zones beside
 * the gauge in the large style.
 */
internal fun stressTile(
    context: Context,
    config: WidgetConfig,
    data: StressData,
    value: CharSequence,
    gauge: GaugeSpec
): TileModel {
    val quarters = stressQuarterBars(context, data)
    val latest = data.latestSeconds
    val valueNote =
        if (latest != null && isStressStale(data) && (config.variant.id == WidgetVariant.NUMBER.id || config.isWide)) {
            val measuredAt = Instant.ofEpochSecond(latest).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
            context.getString(R.string.widget_status_at_time, measuredAt)
        } else {
            null
        }
    if (config.isLargeStyle) {
        return TileModel(
            value = value,
            gauge = gauge,
            status = stressStatus(context, data),
            legend = stressTimeInZones(context, data, stressZoneColors(context)).second,
            largeChart = quarters,
            axis = if (quarters != null) TileAxis.HOURS else emptyList(),
            gaugeBesideLegend = true,
        )
    }
    return TileModel(
        value = value,
        gauge = gauge,
        valueNote = valueNote,
        status = stressStatus(context, data),
        chart = stressHourlyBars(context, data),
        largeChart = quarters,
        axis = if (quarters != null) TileAxis.HOURS else emptyList(),
    )
}
