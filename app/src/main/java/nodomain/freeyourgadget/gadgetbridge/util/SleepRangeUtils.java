package nodomain.freeyourgadget.gadgetbridge.util;

import java.util.Calendar;
import java.util.GregorianCalendar;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;

public final class SleepRangeUtils {
    private static final String PREF_KEY = "chart_sleep_range_mode";
    private static final String DEFAULT_VALUE = "18:00";

    private SleepRangeUtils() {
    }

    public enum SleepRangeMode {
        ROLLING_24H,
        NOON,
        EVENING,
    }

    public static SleepRangeMode getMode() {
        final String value = GBApplication.getPrefs().getString(PREF_KEY, DEFAULT_VALUE);
        if ("24h".equals(value)) {
            return SleepRangeMode.ROLLING_24H;
        } else if ("12:00".equals(value)) {
            return SleepRangeMode.NOON;
        } else {
            return SleepRangeMode.EVENING;
        }
    }

    /**
     * Returns the sleep window (one local day for fixed ranges, 24 hours for rolling) {@code {from, to}} (epoch seconds, to exclusive) ending at
     * anchorTs, per the current {@code chart_sleep_range_mode} preference.
     */
    public static int[] getSleepRange(final int anchorTs) {
        return getSleepRange(anchorTs, getMode());
    }

    public static int[] getSleepRange(final int anchorTs, final SleepRangeMode mode) {
        final Calendar calendar = new GregorianCalendar();
        calendar.setTimeInMillis(anchorTs * 1000L);
        return getSleepRange(calendar, mode);
    }

    public static int[] getSleepRange(final Calendar anchor) {
        return getSleepRange(anchor, getMode());
    }

    public static int[] getSleepRange(final Calendar anchor, final SleepRangeMode mode) {
        final Calendar end = (Calendar) anchor.clone();
        if (mode != SleepRangeMode.ROLLING_24H) {
            end.set(Calendar.HOUR_OF_DAY, mode == SleepRangeMode.NOON ? 12 : 18);
            end.set(Calendar.MINUTE, 0);
            end.set(Calendar.SECOND, 0);
            end.set(Calendar.MILLISECOND, 0);
        }
        final int to = (int) (end.getTimeInMillis() / 1000L);
        if (mode == SleepRangeMode.ROLLING_24H) {
            return new int[]{to - 24 * 60 * 60, to};
        }
        final Calendar start = (Calendar) end.clone();
        start.add(Calendar.DATE, -1);
        return new int[]{(int) (start.getTimeInMillis() / 1000L), to};
    }
}
