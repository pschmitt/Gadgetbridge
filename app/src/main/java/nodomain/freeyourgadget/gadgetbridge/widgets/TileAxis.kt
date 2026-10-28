package nodomain.freeyourgadget.gadgetbridge.widgets

import android.text.format.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * Labels under the tall and wide tiles' charts.
 */
object TileAxis {
    private const val DAYS_PER_WEEK = 7L

    /**
     * Hours of the day from midnight to midnight, the first and last at the chart's edges.
     */
    val HOURS = listOf("00", "06", "12", "18", "24")

    /**
     * Narrow weekday names of the [days] days ending on [lastDay], oldest first.
     */
    fun weekdays(lastDay: LocalDate, days: Int, locale: Locale = Locale.getDefault()): List<String> =
        (days - 1 downTo 0).map { lastDay.minusDays(it.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, locale) }

    /**
     * Short month names at the start, middle and end of the [weeks] weeks ending on [lastDay].
     */
    fun months(lastDay: LocalDate, weeks: Int, locale: Locale = Locale.getDefault()): List<String> {
        val first = lastDay.minusDays(weeks * DAYS_PER_WEEK - 1)
        val middle = first.plusDays((weeks * DAYS_PER_WEEK) / 2)
        return listOf(first, middle, lastDay).map { it.month.getDisplayName(TextStyle.SHORT, locale) }
    }

    /**
     * The first and last day, like "Sep 12" and "Oct 11".
     */
    fun dateRange(first: LocalDate, last: LocalDate, locale: Locale = Locale.getDefault()): List<String> {
        val format = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale)
        return listOf(first.format(format), last.format(format))
    }

    /**
     * The first day of each of the [weeks] weeks ending on [lastDay], like "Sep 14".
     */
    fun weekStarts(lastDay: LocalDate, weeks: Int, locale: Locale = Locale.getDefault()): List<String> {
        val format = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale)
        return (weeks - 1 downTo 0).map { lastDay.minusDays(it * DAYS_PER_WEEK + DAYS_PER_WEEK - 1).format(format) }
    }

    /**
     * The local times of two epoch seconds, like "23:10" and "06:45".
     */
    fun timeRange(start: Long, end: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): List<String> {
        val format = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        return listOf(start, end).map { Instant.ofEpochSecond(it).atZone(zone).format(format) }
    }
}
