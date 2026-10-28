package nodomain.freeyourgadget.gadgetbridge.devices;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.TimeZone;

import nodomain.freeyourgadget.gadgetbridge.database.CsvTableImporter;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;
import nodomain.freeyourgadget.gadgetbridge.model.SleepSession;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;
import nodomain.freeyourgadget.gadgetbridge.util.SleepRangeUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests after #6647 - compares the sleep sessions of real device data with the values that the daily sleep
 * chart displayed before the sleep session unification.
 */
public class SleepSessionProviderRegressionTest extends TestBase {
    private static final ZoneId ZONE = ZoneId.of("Europe/London");
    private static final long TOLERANCE_MINUTES = 1;

    @Test
    public void amazfitGtr4_2022_11_27() {
        assertNight(DeviceType.AMAZFITGTR4, "amazfit-gtr4-2022-11-27", "03:56", "11:19", 78, 220, 144, 2);
    }

    @Test
    public void amazfitGtr4_2024_04_11() {
        assertNight(DeviceType.AMAZFITGTR4, "amazfit-gtr4-2024-04-11", "00:59", "08:14", 19, 306, 96, 15);
    }

    @Test
    public void cmfWatchPro_2024_01_27() {
        assertNight(DeviceType.NOTHING_CMF_WATCH_PRO, "cmf-2024-01-27", "00:52", "08:15", 120, 229, 94, 0);
    }

    @Test
    public void garminVenu3_2026_10_17() {
        assertNight(DeviceType.GARMIN_VENU_3, "garmin-2026-10-17", "23:51", "08:50", 99, 290, 150, 1);
    }

    /**
     * The chart on master displayed 23:40 - 08:20, with 310 min deep, 190 min light and 30 min REM.
     * These values do not match the stage samples.
     */
    @Test
    public void gloryFit_2025_07_06() {
        assertNight(DeviceType.OUKITEL_BT103, "gloryfit-2025-07-06", "23:39", "08:29", 283, 210, 33, 4);
    }

    @Test
    public void miBand8_2024_01_16() {
        assertNight(DeviceType.MIBAND8, "miband8-2024-01-16", "03:30", "11:53", 133, 280, 77, 14);
    }

    @Test
    public void colmiR02_2024_08_23() {
        assertNight(DeviceType.COLMI_R02, "yawell-2024-08-23", "00:18", "09:07", 112, 406, 0, 12);
    }

    /**
     * Loads the data set and checks the sessions against the expected times and stage durations.
     * All durations are in minutes.
     */
    private void assertNight(final DeviceType type,
                             final String dataSet,
                             final String expectedStart,
                             final String expectedEnd,
                             final long deep,
                             final long light,
                             final long rem,
                             final long awake) {
        final GBDevice device = new GBDevice("00:00:00:00:00:01", dataSet, null, null, type);
        CsvTableImporter.importDirectory(daoSession, device, "/sleep-sessions/" + dataSet);

        final LocalDate day = LocalDate.parse(dataSet.substring(dataSet.length() - "yyyy-MM-dd".length()));
        final Calendar anchor = new GregorianCalendar(TimeZone.getTimeZone(ZONE));
        anchor.setTimeInMillis(day.atTime(LocalTime.MAX).atZone(ZONE).toInstant().toEpochMilli());
        final int[] range = SleepRangeUtils.getSleepRange(anchor, SleepRangeUtils.SleepRangeMode.EVENING);

        final List<SleepSession> sessions = device.getDeviceCoordinator()
            .getSleepSessionProvider(device, daoSession)
            .getSleepSessions(range[0], range[1]);
        assertFalse("No sleep sessions for " + dataSet, sessions.isEmpty());

        long deepSeconds = 0;
        long lightSeconds = 0;
        long remSeconds = 0;
        long awakeSeconds = 0;
        long totalSeconds = 0;
        for (final SleepSession session : sessions) {
            deepSeconds += session.getDeepSleepDuration();
            lightSeconds += session.getLightSleepDuration();
            remSeconds += session.getRemSleepDuration();
            awakeSeconds += session.getAwakeSleepDuration();
            totalSeconds += session.getTotalSleepDuration();
        }

        assertTime("start", expectedStart, sessions.get(0).getStartTime());
        assertTime("end", expectedEnd, sessions.get(sessions.size() - 1).getEndTime());
        assertMinutes("deep", deep, deepSeconds);
        assertMinutes("light", light, lightSeconds);
        assertMinutes("rem", rem, remSeconds);
        assertMinutes("awake", awake, awakeSeconds);
        assertMinutes("total", deep + light + rem, totalSeconds);
    }

    private static void assertTime(final String label, final String expected, final long actualMillis) {
        final LocalTime actual = Instant.ofEpochMilli(actualMillis).atZone(ZONE).toLocalTime();
        final long difference = Math.abs(Duration.between(LocalTime.parse(expected), actual).toMinutes());
        // Times just before and after midnight are close, also when the difference is almost one day
        final long minutes = Math.min(difference, Duration.ofDays(1).toMinutes() - difference);
        assertTrue(label + ": expected " + expected + ", actual " + actual, minutes <= TOLERANCE_MINUTES);
    }

    private static void assertMinutes(final String label, final long expectedMinutes, final long actualSeconds) {
        assertEquals(label + " (actual " + actualSeconds + "s)", expectedMinutes, actualSeconds / 60.0, TOLERANCE_MINUTES);
    }
}
