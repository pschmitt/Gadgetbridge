package nodomain.freeyourgadget.gadgetbridge.activities.charts.spec

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

private const val DAY_LABEL_STEP = 5

fun labelFor(format: ChartValueFormat): (Double) -> String = when (format) {
    ChartValueFormat.TIME_OF_DAY -> timeLabeler("HH:mm")
    ChartValueFormat.DATE -> timeLabeler("MMM d")
    ChartValueFormat.DURATION_SECONDS -> ::durationLabel
    ChartValueFormat.DECIMAL -> decimalLabeler("0.#")
    ChartValueFormat.ONE_DECIMAL -> decimalLabeler("0.0")
    ChartValueFormat.INTEGER -> decimalLabeler("0")
    ChartValueFormat.DAY_OF_WEEK -> wholeLabeler(1..7) { dayOfWeekLabel(it) }
    ChartValueFormat.DAY_OF_MONTH -> wholeLabeler(1..31, ::dayOfMonthLabel)
    ChartValueFormat.MONTH_OF_YEAR -> wholeLabeler(1..12) { monthOfYearLabel(it) }
    ChartValueFormat.EPOCH_DAY -> wholeLabeler(Int.MIN_VALUE..Int.MAX_VALUE) { dayOfMonthLabel(epochDay(it).dayOfMonth.toDouble()) }
    ChartValueFormat.DURATION_MINUTES -> { value -> minutesLabel(value) }
    ChartValueFormat.BAR_INDEX -> decimalLabeler("0")
}

/**
 * Uses day names for an [ChartValueFormat.EPOCH_DAY] axis of a week or less, when [dayNames] is true.
 */
fun labelFor(axis: AxisSpec, dayNames: Boolean = true): (Double) -> String {
    if (axis.format == ChartValueFormat.EPOCH_DAY && dayNames && isWeekOrLess(axis)) {
        return wholeLabeler(Int.MIN_VALUE..Int.MAX_VALUE) { dayOfWeekLabel(epochDay(it).dayOfWeek.value.toDouble()) }
    }
    if (axis.format == ChartValueFormat.EPOCH_DAY && isOverTwoMonths(axis)) {
        return wholeLabeler(Int.MIN_VALUE..Int.MAX_VALUE) {
            epochDay(it).month.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.getDefault())
        }
    }
    return labelFor(axis.format)
}

private fun epochDay(value: Double): LocalDate = LocalDate.ofEpochDay(Math.round(value))

private fun isWeekOrLess(axis: AxisSpec): Boolean {
    val min = axis.minimum ?: return false
    val max = axis.maximum ?: return false
    return max - min < DAYS_PER_WEEK
}

private fun isOverTwoMonths(axis: AxisSpec): Boolean {
    val min = axis.minimum ?: return false
    val max = axis.maximum ?: return false
    return max - min >= DAYS_IN_TWO_MONTHS
}

private const val DAYS_PER_WEEK = 7
private const val DAYS_IN_TWO_MONTHS = 62

private fun wholeLabeler(valid: IntRange, label: (Double) -> String): (Double) -> String = { value ->
    val whole = Math.round(value).toInt()
    if (value == whole.toDouble() && whole in valid) label(value) else ""
}

/**
 * Formats an epoch-second timestamp using [SimpleDateFormat] [pattern].
 */
fun timeLabeler(pattern: String): (Double) -> String {
    val format = SimpleDateFormat(pattern, Locale.getDefault())
    return { value -> format.format(value.toLong() * 1000L) }
}

private fun decimalLabeler(pattern: String): (Double) -> String {
    val format = DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.getDefault()))
    return { value -> format.format(value) }
}

/**
 * A duration in seconds, as `H:MM:SS` (or `M:SS` under an hour).
 */
fun durationLabel(value: Double): String {
    val totalSeconds = value.toLong()
    val hours = TimeUnit.SECONDS.toHours(totalSeconds)
    val minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

fun minutesLabel(value: Double): String {
    val minutes = Math.round(value)
    return String.format(Locale.getDefault(), "%d:%02d", minutes / 60, minutes % 60)
}

/**
 * Short name of an ISO day of the week (1 = Monday), e.g. "Mon".
 */
fun dayOfWeekLabel(value: Double, locale: Locale = Locale.getDefault()): String =
    DayOfWeek.of(Math.round(value).toInt()).getDisplayName(TextStyle.SHORT, locale)

/**
 * The day of the month as a number. Which days get a label is up to [dayOfMonthValues].
 */
fun dayOfMonthLabel(value: Double): String = Math.round(value).toString()

/**
 * Narrow name of a month of the year (1 = January), e.g. "J".
 */
fun monthOfYearLabel(value: Double, locale: Locale = Locale.getDefault()): String =
    Month.of(Math.round(value).toInt()).getDisplayName(TextStyle.NARROW_STANDALONE, locale)

/**
 * The days of a month of [lastDay] days that get a label: the first, then every fifth.
 */
fun dayOfMonthValues(lastDay: Int): List<Double> =
    (listOf(1) + (DAY_LABEL_STEP..lastDay step DAY_LABEL_STEP)).map { it.toDouble() }

/**
 * 2 to 7 labels between [min] and [max] (epoch seconds), on round local times. [zoneOffsetSeconds] gives the
 * offset from UTC at an epoch second.
 */
fun timeLabelValues(
    min: Double,
    max: Double,
    maxLabels: Int,
    zoneOffsetSeconds: (Double) -> Int = { 0 },
    spacings: List<Double> = TIME_LABEL_SPACINGS_SECONDS,
): List<Double> {
    val range = max - min
    if (range <= 0.0) return emptyList()
    val count = maxLabels.coerceIn(MIN_TIME_LABELS, MAX_TIME_LABELS)
    val minimumSpacing = range / count
    val spacing = spacings.firstOrNull { it >= minimumSpacing }
        ?: (ceil(minimumSpacing / SECONDS_PER_DAY) * SECONDS_PER_DAY)
    val minOffset = zoneOffsetSeconds(min)
    val firstLocal = ceil((min + minOffset) / spacing) * spacing
    // The offset can change inside the range, so each local time is converted with the offset at that time
    return generateSequence(0) { it + 1 }
        .map { firstLocal + it * spacing }
        .map { local -> local - zoneOffsetSeconds(local - minOffset) }
        .takeWhile { it <= max }
        .filter { it >= min }
        .distinct()
        .toList()
}

private const val MIN_TIME_LABELS = 2
private const val MAX_TIME_LABELS = 7
private const val SECONDS_PER_DAY = 24 * 60 * 60.0
private val TIME_LABEL_SPACINGS_SECONDS = listOf(5, 10, 15, 30, 60, 120, 180, 240, 360, 720, 1440).map { it * 60.0 }

/**
 * Label spacings for a duration axis, which can be zoomed down to seconds.
 */
val DURATION_LABEL_SPACINGS_SECONDS = listOf(5.0, 10.0, 15.0, 30.0, 60.0, 120.0) + TIME_LABEL_SPACINGS_SECONDS

/**
 * Label positions for period axes, or null to let the chart choose.
 */
fun fixedLabelValues(xAxis: AxisSpec): List<Double>? {
    val min = xAxis.minimum ?: return null
    val max = xAxis.maximum ?: return null
    return when (xAxis.format) {
        ChartValueFormat.DAY_OF_MONTH -> dayOfMonthValues(max.toInt())
        ChartValueFormat.DAY_OF_WEEK, ChartValueFormat.MONTH_OF_YEAR ->
            (min.toInt()..max.toInt()).map { it.toDouble() }
        ChartValueFormat.EPOCH_DAY -> (min.toLong()..max.toLong())
            .filter {
                val dayOfMonth = LocalDate.ofEpochDay(it).dayOfMonth
                when {
                    isWeekOrLess(xAxis) -> true
                    isOverTwoMonths(xAxis) -> dayOfMonth == 1
                    else -> isLabelledDayOfMonth(dayOfMonth)
                }
            }
            .map { it.toDouble() }
        ChartValueFormat.BAR_INDEX -> {
            val step = ceil((max - min + 1) / MAX_BAR_INDEX_LABELS).toInt().coerceAtLeast(1)
            (min.toInt()..max.toInt() step step).map { it.toDouble() }
        }
        else -> null
    }
}

private const val MAX_BAR_INDEX_LABELS = 6.0

/**
 * Skips the 30th, which would collide with the next month's 1st.
 */
private fun isLabelledDayOfMonth(day: Int) = day == 1 || (day % DAY_LABEL_STEP == 0 && day < LAST_LABELLED_DAY_LIMIT)

private const val LAST_LABELLED_DAY_LIMIT = 30
