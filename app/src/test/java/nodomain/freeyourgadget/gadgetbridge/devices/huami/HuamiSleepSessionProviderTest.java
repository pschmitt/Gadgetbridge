package nodomain.freeyourgadget.gadgetbridge.devices.huami;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.devices.CsvSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.DefaultSleepSessionProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.FallbackSleepSessionProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.FixedSampleDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.SleepSessionProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.HuamiSleepSessionSample;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;
import nodomain.freeyourgadget.gadgetbridge.model.SleepSession;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

import static org.junit.Assert.assertEquals;

public class HuamiSleepSessionProviderTest extends TestBase {
    private static final int MIDNIGHT = (int) Instant.parse("2023-11-15T00:00:00Z").getEpochSecond();
    private static final int START = MIDNIGHT - 2 * 3600;
    private static final int END = MIDNIGHT + 6 * 3600;
    private GBDevice device;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        final DeviceCoordinator coordinator = new FixedSampleDeviceCoordinator(
                new CsvSampleProvider(null, null, "/sleep-session-realistic-night.csv"));
        device = new GBDevice("00:00:00:00:20:01", "Test", null, null, DeviceType.TEST) {
            @NonNull
            @Override
            public DeviceCoordinator getDeviceCoordinator() {
                return coordinator;
            }
        };
        DBHelper.getDevice(device, daoSession);
    }

    private HuamiSleepSessionProvider provider() {
        return new HuamiSleepSessionProvider(device, daoSession);
    }

    private SleepSessionProvider providerWithFallback() {
        return new FallbackSleepSessionProvider(provider(), new DefaultSleepSessionProvider(device, daoSession));
    }

    private void seed(int recordTimestamp, int midnight, int[][] stages) {
        final ByteBuffer data = ByteBuffer.allocate(594).order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(0, recordTimestamp);
        data.putInt(4, midnight);
        data.put(0x54, (byte) stages.length);
        data.position(0x56);
        for (int[] stage : stages) {
            data.putShort((short) stage[0]);
            // Test ranges are half-open; Huami encodes the last minute inclusively.
            data.putShort((short) (stage[1] - 1));
            data.put((byte) stage[2]);
        }
        daoSession.getHuamiSleepSessionSampleDao().insertOrReplace(new HuamiSleepSessionSample(
                recordTimestamp * 1000L, DBHelper.getDevice(device, daoSession).getId(),
                DBHelper.getUser(daoSession).getId(), data.array()));
    }

    private void seedNight(int recordTimestamp) {
        seed(recordTimestamp, MIDNIGHT, new int[][] {
                {1320, 1380, 5}, {1380, 1680, 4}, {1680, 1770, 8}, {1770, 1800, 7}
        });
    }

    @Test
    public void returnsWholeSessionWhenRecordArrivesAfterQueryEnd() {
        seedNight(END + 3600);
        final List<SleepSession> sessions = provider().getSleepSessions(START, START + 60);
        assertEquals(1, sessions.size());
        final SleepSession night = sessions.get(0);
        assertEquals(START * 1000L, night.getStartTime());
        assertEquals(END * 1000L, night.getEndTime());
        assertEquals(3600, night.getDeepSleepDuration());
        assertEquals(5 * 3600, night.getLightSleepDuration());
        assertEquals(90 * 60, night.getRemSleepDuration());
        assertEquals(30 * 60, night.getAwakeSleepDuration());
        assertEquals(450 * 60, night.getTotalSleepDuration());
    }

    @Test
    public void findsSessionReportedTheFollowingDay() {
        seedNight(START + 27 * 3600);
        assertEquals(1, provider().getSleepSessions(START, START + 60).size());
    }

    @Test
    public void filtersByStartWithExclusiveUpperBound() {
        seedNight(END + 3600);
        assertEquals(0, provider().getSleepSessions(START + 60, END + 2 * 3600).size());
        assertEquals(0, provider().getSleepSessions(START - 3600, START).size());
    }

    @Test
    public void repeatedSyncKeepsOneSessionAndIdenticalBounds() {
        seedNight(END + 3600);
        final SleepSession first = provider().getSleepSessions(START, END + 2 * 3600).get(0);
        seedNight(END + 3600);
        final List<SleepSession> again = provider().getSleepSessions(START, END + 2 * 3600);
        assertEquals(1, again.size());
        assertEquals(first.getStartTime(), again.get(0).getStartTime());
        assertEquals(first.getEndTime(), again.get(0).getEndTime());
        assertEquals(first.getTotalSleepDuration(), again.get(0).getTotalSleepDuration());
    }

    @Test
    public void returnsNightAndNapInSleepOrder() {
        seedNight(END + 3600);
        seed(MIDNIGHT + 16 * 3600, MIDNIGHT + 86400, new int[][] {{14 * 60, 15 * 60, 4}});
        final List<SleepSession> sessions = provider().getSleepSessions(START, MIDNIGHT + 18 * 3600);
        assertEquals(2, sessions.size());
        assertEquals(START * 1000L, sessions.get(0).getStartTime());
        assertEquals((MIDNIGHT + 14 * 3600) * 1000L, sessions.get(1).getStartTime());
        assertEquals(3600, sessions.get(1).getTotalSleepDuration());
    }

    @Test
    public void fallsBackToActivitySamplesWhenNativeRecordsAreMissing() {
        final int start = 1705356000; // The existing realistic-night CSV fixture.
        final List<SleepSession> sessions = providerWithFallback().getSleepSessions(start - 60, start + 60);
        assertEquals(1, sessions.size());
        assertEquals(start * 1000L, sessions.get(0).getStartTime());
        assertEquals(1705376460_000L, sessions.get(0).getEndTime());
    }

    @Test
    public void fallbackNightIsAlsoReturnedInARangeWithNativeRecords() {
        seedNight(END + 3600);
        final int fallbackStart = 1705356000; // The existing realistic-night CSV fixture.
        final List<SleepSession> sessions = providerWithFallback().getSleepSessions(START, fallbackStart + 60);
        assertEquals(2, sessions.size());
        assertEquals(START * 1000L, sessions.get(0).getStartTime());
        assertEquals(fallbackStart * 1000L, sessions.get(1).getStartTime());
    }

    @Test
    public void laterNativeReportReplacesEarlierCopyOfTheSameNight() {
        seedNight(END + 3600);
        seed(END + 2 * 3600, MIDNIGHT, new int[][] {{1320, 1380, 5}, {1380, 1810, 4}});
        final List<SleepSession> sessions = provider().getSleepSessions(START, END + 3 * 3600);
        assertEquals(1, sessions.size());
        assertEquals(490 * 60, sessions.get(0).getTotalSleepDuration());
        assertEquals((END + 600) * 1000L, sessions.get(0).getEndTime());
    }

    @Test
    public void activityOverlayIncludesSleepThatStartedBeforeSampleWindow() {
        seedNight(END + 3600);
        final int timestamp = START + 60;
        daoSession.getHuamiExtendedActivitySampleDao().insertOrReplace(
                new nodomain.freeyourgadget.gadgetbridge.entities.HuamiExtendedActivitySample(
                        timestamp, DBHelper.getDevice(device, daoSession).getId(),
                        DBHelper.getUser(daoSession).getId(), 1, 0,
                        HuamiExtendedSampleProvider.TYPE_SLEEP, 70, 0, 0, 0, 0));
        final var samples = new HuamiExtendedSampleProvider(device, daoSession)
                .getAllActivitySamples(timestamp, timestamp + 60);
        final var sample = samples.stream().filter(it -> it.getTimestamp() == timestamp).findFirst().orElseThrow();
        assertEquals(ActivityKind.DEEP_SLEEP, sample.getKind());
    }

    @Test
    public void laterReportWithShiftedStartReplacesEarlierNight() {
        seedNight(END + 3600);
        seed(END + 2 * 3600, MIDNIGHT, new int[][] {{1321, 1800, 4}});
        final List<SleepSession> sessions = provider().getSleepSessions(START, END + 3 * 3600);
        assertEquals(1, sessions.size());
        assertEquals((START + 60) * 1000L, sessions.get(0).getStartTime());
        assertEquals(479 * 60, sessions.get(0).getTotalSleepDuration());
    }

    @Test
    public void revisionOutsideQueryWindowSuppressesStaleCopyInsideIt() {
        seed(END + 3600, MIDNIGHT, new int[][] {{1321, 1800, 4}});
        seedNight(END + 2 * 3600);
        assertEquals(0, provider().getSleepSessions(START + 60, END + 3 * 3600).size());
    }

    @Test
    public void adjacentSessionsRemainSeparate() {
        seed(END + 3600, MIDNIGHT, new int[][] {{1320, 1380, 4}});
        seed(END + 2 * 3600, MIDNIGHT, new int[][] {{1380, 1440, 4}});
        final List<SleepSession> sessions = provider().getSleepSessions(START, END + 3 * 3600);
        assertEquals(2, sessions.size());
        assertEquals(sessions.get(0).getEndTime(), sessions.get(1).getStartTime());
    }

    @Test
    public void singleMinuteStagesHaveTheirFullDuration() {
        seed(END + 3600, MIDNIGHT, new int[][] {
                {1320, 1321, 5}, {1321, 1322, 4}, {1322, 1323, 8}, {1323, 1324, 7}
        });
        final SleepSession session = provider().getSleepSessions(START, END + 2 * 3600).get(0);
        assertEquals(60, session.getDeepSleepDuration());
        assertEquals(60, session.getLightSleepDuration());
        assertEquals(60, session.getRemSleepDuration());
        assertEquals(60, session.getAwakeSleepDuration());
        assertEquals(180, session.getTotalSleepDuration());
        assertEquals((START + 4 * 60) * 1000L, session.getEndTime());
    }

    @Test
    public void newerReportsCanSplitAnOlderBroadSession() {
        seedNight(END + 3600);
        seed(END + 2 * 3600, MIDNIGHT, new int[][] {{1320, 1440, 4}});
        seed(END + 3 * 3600, MIDNIGHT, new int[][] {{1560, 1800, 4}});
        final List<SleepSession> sessions = provider().getSleepSessions(START, END + 4 * 3600);
        assertEquals(2, sessions.size());
        assertEquals(2 * 3600, sessions.get(0).getTotalSleepDuration());
        assertEquals(4 * 3600, sessions.get(1).getTotalSleepDuration());
    }

}
