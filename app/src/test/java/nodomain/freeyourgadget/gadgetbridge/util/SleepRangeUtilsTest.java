package nodomain.freeyourgadget.gadgetbridge.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;

import nodomain.freeyourgadget.gadgetbridge.util.SleepRangeUtils.SleepRangeMode;

public class SleepRangeUtilsTest {

    private static final int DAY_SECONDS = 24 * 60 * 60;

    @Test
    public void rolling24h_endsExactlyAtAnchor_notSnappedToNoon() {
        // Regression guard: "24h" used to silently fall back to noon in some callers.
        int anchor = anchorAt(2026, Calendar.JANUARY, 10, 7, 15);

        int[] range = SleepRangeUtils.getSleepRange(anchor, SleepRangeMode.ROLLING_24H);

        assertEquals(anchor, range[1]);
        assertEquals(anchor - DAY_SECONDS, range[0]);
    }

    @Test
    public void noon_snapsToNoonOnTheAnchorsOwnDate() {
        // Anchor is in the morning, before noon - the window still ends at noon on the SAME date,
        // not the previous day's noon.
        int anchor = anchorAt(2026, Calendar.JANUARY, 10, 7, 15);
        int expectedEnd = anchorAt(2026, Calendar.JANUARY, 10, 12, 0);

        int[] range = SleepRangeUtils.getSleepRange(anchor, SleepRangeMode.NOON);

        assertEquals(expectedEnd, range[1]);
        assertEquals(expectedEnd - DAY_SECONDS, range[0]);
    }

    @Test
    public void evening_snapsTo18_00OnTheAnchorsOwnDate() {
        int anchor = anchorAt(2026, Calendar.JANUARY, 10, 20, 45);
        int expectedEnd = anchorAt(2026, Calendar.JANUARY, 10, 18, 0);

        int[] range = SleepRangeUtils.getSleepRange(anchor, SleepRangeMode.EVENING);

        assertEquals(expectedEnd, range[1]);
        assertEquals(expectedEnd - DAY_SECONDS, range[0]);
    }

    @Test
    public void consecutiveDays_produceAdjacentWindows() {
        // Day D's window and day (D+1)'s window must share a boundary - no gap, no overlap - or a
        // session sitting exactly on the boundary could be double-counted or dropped.
        int day1 = anchorAt(2026, Calendar.JANUARY, 10, 12, 0);
        int day2 = anchorAt(2026, Calendar.JANUARY, 11, 12, 0);

        int[] range1 = SleepRangeUtils.getSleepRange(day1, SleepRangeMode.EVENING);
        int[] range2 = SleepRangeUtils.getSleepRange(day2, SleepRangeMode.EVENING);

        assertEquals(range1[1], range2[0]);
    }

    @Test
    public void everyModeReturnsAnExactly24HourWindow() {
        int anchor = anchorAt(2026, Calendar.JANUARY, 10, 9, 0);
        for (SleepRangeMode mode : SleepRangeMode.values()) {
            int[] range = SleepRangeUtils.getSleepRange(anchor, mode);
            assertEquals("mode " + mode, DAY_SECONDS, range[1] - range[0]);
        }
    }


    @Test
    public void fixedRangesStayAdjacentAcrossDaylightSavingChanges() {
        final java.util.TimeZone previous = java.util.TimeZone.getDefault();
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Australia/Melbourne"));
        try {
            for (int[] date : new int[][] {{Calendar.APRIL, 5}, {Calendar.OCTOBER, 4}}) {
                for (SleepRangeMode mode : new SleepRangeMode[] {SleepRangeMode.NOON, SleepRangeMode.EVENING}) {
                    final int before = anchorAt(2026, date[0], date[1] - 1, 20, 0);
                    final int after = anchorAt(2026, date[0], date[1], 20, 0);
                    final int[] first = SleepRangeUtils.getSleepRange(before, mode);
                    final int[] next = SleepRangeUtils.getSleepRange(after, mode);
                    assertEquals(mode + " " + date[0], first[1], next[0]);
                }
            }
        } finally {
            java.util.TimeZone.setDefault(previous);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static int anchorAt(int year, int month, int day, int hour, int minute) {
        final Calendar calendar = new GregorianCalendar(year, month, day, hour, minute, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return (int) (calendar.getTimeInMillis() / 1000L);
    }
}
