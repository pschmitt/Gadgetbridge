package nodomain.freeyourgadget.gadgetbridge.devices.huami;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.devices.DefaultSleepSessionProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.FallbackSleepSessionProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.FixedSampleDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.entities.HuamiExtendedActivitySample;
import nodomain.freeyourgadget.gadgetbridge.entities.HuamiSleepSessionSample;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;
import nodomain.freeyourgadget.gadgetbridge.model.SleepSession;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

import static org.junit.Assert.assertEquals;

public class HuamiExtendedSampleProviderTest extends TestBase {
    private static final int DAY = (int) Instant.parse("2023-11-15T00:00:00Z").getEpochSecond();
    private GBDevice device;
    private HuamiExtendedSampleProvider activityProvider;
    private DeviceCoordinator coordinator;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        device = new GBDevice("00:00:00:00:20:19", "Test", null, null, DeviceType.TEST) {
            @NonNull
            @Override
            public DeviceCoordinator getDeviceCoordinator() {
                return coordinator;
            }
        };
        activityProvider = new HuamiExtendedSampleProvider(device, daoSession);
        coordinator = new FixedSampleDeviceCoordinator(activityProvider);
        final long deviceId = DBHelper.getDevice(device, daoSession).getId();
        final long userId = DBHelper.getUser(daoSession).getId();
        for (int minute = 0; minute < 360; minute++) {
            final boolean sleep = minute >= 60 && minute < 120 || minute >= 240 && minute < 258;
            daoSession.getHuamiExtendedActivitySampleDao().insert(new HuamiExtendedActivitySample(
                    DAY + minute * 60, deviceId, userId, 32, sleep ? 0 : 1,
                    sleep ? HuamiExtendedSampleProvider.TYPE_SLEEP : 0, 60, 0, 0, 0, 0));
        }
    }

    private void seedNativeSession() {
        final ByteBuffer data = ByteBuffer.allocate(594).order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(0, DAY + 8 * 3600);
        data.putInt(4, DAY + 86400);
        data.put(0x54, (byte) 3);
        data.position(0x56);
        for (int[] stage : new int[][] {{60, 79, 4}, {80, 99, 5}, {100, 119, 8}}) {
            data.putShort((short) stage[0]).putShort((short) stage[1]).put((byte) stage[2]);
        }
        daoSession.getHuamiSleepSessionSampleDao().insert(new HuamiSleepSessionSample(
                (DAY + 8 * 3600) * 1000L, DBHelper.getDevice(device, daoSession).getId(),
                DBHelper.getUser(daoSession).getId(), data.array()));
    }

    @Test
    public void clearsSleepKindOutsideNativeSessionWithoutChangingIntensity() {
        seedNativeSession();
        final List<HuamiExtendedActivitySample> samples = activityProvider.getAllActivitySamples(DAY, DAY + 6 * 3600);
        final HuamiExtendedActivitySample outside = samples.stream()
                .filter(s -> s.getTimestamp() == DAY + 4 * 3600).findFirst().orElseThrow();
        assertEquals(ActivityKind.UNKNOWN, outside.getKind());
        assertEquals(32, outside.getRawIntensity());
        final HuamiExtendedActivitySample inside = samples.stream()
                .filter(s -> s.getTimestamp() == DAY + 90 * 60).findFirst().orElseThrow();
        assertEquals(ActivityKind.DEEP_SLEEP, inside.getKind());
    }

    @Test
    public void fallbackDoesNotAddSpuriousLightSleepOutsideNativeSession() {
        seedNativeSession();
        final List<SleepSession> sessions = new FallbackSleepSessionProvider(
                new HuamiSleepSessionProvider(device, daoSession),
                new DefaultSleepSessionProvider(device, daoSession)).getSleepSessions(DAY, DAY + 6 * 3600);
        assertEquals(1, sessions.size());
        assertEquals(60 * 60L, sessions.get(0).getTotalSleepDuration());
        assertEquals(20 * 60L, sessions.get(0).getLightSleepDuration());
        assertEquals(20 * 60L, sessions.get(0).getDeepSleepDuration());
        assertEquals(20 * 60L, sessions.get(0).getRemSleepDuration());
    }

    @Test
    public void retainsActivityBasedSleepWhenNoNativeSessionIsAvailable() {
        final List<SleepSession> sessions = new FallbackSleepSessionProvider(
                new HuamiSleepSessionProvider(device, daoSession),
                new DefaultSleepSessionProvider(device, daoSession)).getSleepSessions(DAY, DAY + 6 * 3600);
        assertEquals(2, sessions.size());
        assertEquals(60 * 60L, sessions.get(0).getTotalSleepDuration());
        assertEquals(18 * 60L, sessions.get(1).getLightSleepDuration());
    }
}
