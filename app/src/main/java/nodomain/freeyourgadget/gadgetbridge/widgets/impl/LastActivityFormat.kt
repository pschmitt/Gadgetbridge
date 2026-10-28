package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Groups, stat choices and value formatting of the Last activity widget.
 */
object LastActivityFormat {
    enum class Group { RUNNING, CYCLING, CARDIO }

    enum class StatKind { DISTANCE, DURATION, PACE, SPEED, CALORIES, AVG_HR }

    sealed interface Day {
        data object Today : Day
        data object Yesterday : Day
        data class Text(val text: String) : Day
    }

    private const val METERS_PER_MILE = 1609.344

    fun group(kind: ActivityKind): Group {
        val name = kind.name
        return when {
            name.contains("MOTOR") -> Group.CARDIO
            name.contains("CYCL") || name.contains("BIKE") || name == "SPINNING" -> Group.CYCLING
            name.contains("RUN") || name.contains("WALK") || name.contains("HIK") || name.contains("TREADMILL") ->
                Group.RUNNING
            else -> Group.CARDIO
        }
    }

    /**
     * The group whose stats are shown: Cardio when a distance sport has no distance.
     */
    fun statGroup(kind: ActivityKind, distanceMeters: Float?): Group {
        val group = group(kind)
        return if (group != Group.CARDIO && (distanceMeters == null || distanceMeters <= 0f)) Group.CARDIO else group
    }

    fun stats(group: Group): List<StatKind> = when (group) {
        Group.RUNNING -> listOf(StatKind.DISTANCE, StatKind.DURATION, StatKind.PACE)
        Group.CYCLING -> listOf(StatKind.DISTANCE, StatKind.DURATION, StatKind.SPEED)
        Group.CARDIO -> listOf(StatKind.DURATION, StatKind.CALORIES, StatKind.AVG_HR)
    }

    fun distance(meters: Float, imperial: Boolean, locale: Locale): String {
        val value = if (imperial) meters / METERS_PER_MILE else meters / 1000.0
        val pattern = if (value < 10) "0.00" else "0.0"
        return DecimalFormat(pattern, DecimalFormatSymbols(locale)).format(value)
    }

    fun duration(seconds: Long): String {
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.ROOT, "%d:%02d", m, s)
        }
    }

    fun pace(seconds: Long, meters: Float, imperial: Boolean): String {
        val units = if (imperial) meters / METERS_PER_MILE else meters / 1000.0
        val perUnit = (seconds / units).roundToLong()
        return String.format(Locale.ROOT, "%d:%02d", perUnit / 60, perUnit % 60)
    }

    fun speed(seconds: Long, meters: Float, imperial: Boolean, locale: Locale): String {
        val units = if (imperial) meters / METERS_PER_MILE else meters / 1000.0
        return DecimalFormat("0.0", DecimalFormatSymbols(locale)).format(units / (seconds / 3600.0))
    }

    fun day(date: LocalDate, today: LocalDate, locale: Locale): Day {
        val daysAgo = ChronoUnit.DAYS.between(date, today)
        return when {
            daysAgo == 0L -> Day.Today
            daysAgo == 1L -> Day.Yesterday
            daysAgo in 2..6 -> Day.Text(DateTimeFormatter.ofPattern("EEEE", locale).format(date))
            date.year == today.year -> Day.Text(DateTimeFormatter.ofPattern("EEE, MMM d", locale).format(date))
            else -> Day.Text(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", locale).format(date))
        }
    }
}
