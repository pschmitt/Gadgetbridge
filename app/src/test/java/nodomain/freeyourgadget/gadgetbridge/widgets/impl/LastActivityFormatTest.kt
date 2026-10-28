package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.LastActivityFormat.Day
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.LastActivityFormat.Group
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.LastActivityFormat.StatKind
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class LastActivityFormatTest {
    private val locale = Locale.US

    @Test
    fun groups() {
        assertEquals(Group.RUNNING, LastActivityFormat.group(ActivityKind.RUNNING))
        assertEquals(Group.RUNNING, LastActivityFormat.group(ActivityKind.TREADMILL))
        assertEquals(Group.RUNNING, LastActivityFormat.group(ActivityKind.WALKING))
        assertEquals(Group.RUNNING, LastActivityFormat.group(ActivityKind.HIKING))
        assertEquals(Group.RUNNING, LastActivityFormat.group(ActivityKind.TRAIL_RUN))
        assertEquals(Group.CYCLING, LastActivityFormat.group(ActivityKind.CYCLING))
        assertEquals(Group.CYCLING, LastActivityFormat.group(ActivityKind.INDOOR_CYCLING))
        assertEquals(Group.CYCLING, LastActivityFormat.group(ActivityKind.E_BIKE))
        assertEquals(Group.CYCLING, LastActivityFormat.group(ActivityKind.MOUNTAIN_BIKE))
        assertEquals(Group.CYCLING, LastActivityFormat.group(ActivityKind.SPINNING))
        assertEquals(Group.CARDIO, LastActivityFormat.group(ActivityKind.MOTORCYCLING))
        assertEquals(Group.CARDIO, LastActivityFormat.group(ActivityKind.YOGA))
        assertEquals(Group.CARDIO, LastActivityFormat.group(ActivityKind.UNKNOWN))
    }

    @Test
    fun distanceSportWithoutDistanceUsesCardioStats() {
        assertEquals(Group.RUNNING, LastActivityFormat.statGroup(ActivityKind.TREADMILL, 5210f))
        assertEquals(Group.CARDIO, LastActivityFormat.statGroup(ActivityKind.TREADMILL, null))
        assertEquals(Group.CARDIO, LastActivityFormat.statGroup(ActivityKind.INDOOR_CYCLING, 0f))
    }

    @Test
    fun stats() {
        assertEquals(
            listOf(StatKind.DISTANCE, StatKind.DURATION, StatKind.PACE),
            LastActivityFormat.stats(Group.RUNNING)
        )
        assertEquals(
            listOf(StatKind.DISTANCE, StatKind.DURATION, StatKind.SPEED),
            LastActivityFormat.stats(Group.CYCLING)
        )
        assertEquals(
            listOf(StatKind.DURATION, StatKind.CALORIES, StatKind.AVG_HR),
            LastActivityFormat.stats(Group.CARDIO)
        )
    }

    @Test
    fun distance() {
        assertEquals("5.21", LastActivityFormat.distance(5210f, false, locale))
        assertEquals("24.6", LastActivityFormat.distance(24600f, false, locale))
        assertEquals("3.24", LastActivityFormat.distance(5210f, true, locale))
        assertEquals("5,21", LastActivityFormat.distance(5210f, false, Locale.GERMANY))
    }

    @Test
    fun duration() {
        assertEquals("28:14", LastActivityFormat.duration(28 * 60 + 14L))
        assertEquals("0:45", LastActivityFormat.duration(45L))
        assertEquals("1:02:18", LastActivityFormat.duration(3600 + 2 * 60 + 18L))
    }

    @Test
    fun pace() {
        assertEquals("5:25", LastActivityFormat.pace(28 * 60 + 14L, 5210f, false))
        assertEquals("8:43", LastActivityFormat.pace(28 * 60 + 14L, 5210f, true))
    }

    @Test
    fun speed() {
        assertEquals("23.7", LastActivityFormat.speed(3600L, 23700f, false, locale))
        assertEquals("14.7", LastActivityFormat.speed(3600L, 23700f, true, locale))
    }

    @Test
    fun day() {
        val today = LocalDate.of(2026, 10, 13)
        assertEquals(Day.Today, LastActivityFormat.day(today, today, locale))
        assertEquals(Day.Yesterday, LastActivityFormat.day(today.minusDays(1), today, locale))
        assertEquals(Day.Text("Wednesday"), LastActivityFormat.day(today.minusDays(6), today, locale))
        assertEquals(Day.Text("Tue, Oct 6"), LastActivityFormat.day(today.minusDays(7), today, locale))
        assertEquals(Day.Text("Wed, Dec 31, 2025"), LastActivityFormat.day(LocalDate.of(2025, 12, 31), today, locale))
    }
}
