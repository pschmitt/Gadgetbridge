package nodomain.freeyourgadget.gadgetbridge.model;

import org.junit.Before;
import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.devices.miband.MiBandSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.MiBandActivitySample;
import nodomain.freeyourgadget.gadgetbridge.entities.User;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

import static org.junit.Assert.assertEquals;

public class DailyTotalsTest extends TestBase {
    private final ZonedDateTime day = ZonedDateTime.of(2026, 1, 15, 10, 0, 0, 0, ZoneId.of("Europe/London"));
    private GBDevice device;

    @Before
    public void insertSamples() {
        device = new GBDevice("00:11:22:33:44:55", "Test", null, null, DeviceType.MIBAND);
        final Device dbDevice = DBHelper.getDevice(device, daoSession);
        final User user = DBHelper.getUser(daoSession);
        final MiBandSampleProvider provider = new MiBandSampleProvider(device, daoSession);
        final ZonedDateTime yesterday = day.minusDays(1).withHour(0);
        final List<MiBandActivitySample> samples = new ArrayList<>();
        for (ZonedDateTime time = yesterday.withHour(9); !time.isAfter(day); time = time.plusMinutes(1)) {
            final boolean sleeping = inRange(time, yesterday.withHour(11), 20)
                    || inRange(time, yesterday.withHour(13), 109)
                    || inRange(time, yesterday.withHour(23), 462);
            final MiBandActivitySample sample = new MiBandActivitySample();
            sample.setTimestamp((int) time.toEpochSecond());
            sample.setDeviceId(dbDevice.getId());
            sample.setUserId(user.getId());
            sample.setRawKind(sleeping ? MiBandSampleProvider.TYPE_LIGHT_SLEEP : MiBandSampleProvider.TYPE_ACTIVITY);
            sample.setSteps(sleeping ? 0 : 1);
            samples.add(sample);
        }
        provider.addGBActivitySamples(samples);
    }

    @Test
    public void defaultRangeExcludesPreviousAfternoonSleep() {
        GBApplication.getPrefs().getPreferences().edit().remove("chart_sleep_range_mode").apply();
        assertEquals(462, totals().getSleep());
    }

    @Test
    public void noonRangeIncludesPreviousAfternoonSleep() {
        setRange("12:00");
        final DailyTotals noon = totals();
        assertEquals(571, noon.getSleep());
        setRange("18:00");
        assertEquals(noon.getSteps(), totals().getSteps());
    }

    @Test
    public void rollingRangeEndsAtSelectedTime() {
        setRange("24h");
        assertEquals(591, totals().getSleep());
    }

    private DailyTotals totals() {
        return DailyTotals.getDailyTotalsForDevice(device, GregorianCalendar.from(day), dbHandler);
    }

    private void setRange(String range) {
        GBApplication.getPrefs().getPreferences().edit().putString("chart_sleep_range_mode", range).apply();
    }

    private boolean inRange(ZonedDateTime time, ZonedDateTime start, int minutes) {
        return !time.isBefore(start) && time.isBefore(start.plusMinutes(minutes));
    }
}
