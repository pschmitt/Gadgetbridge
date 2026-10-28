package nodomain.freeyourgadget.gadgetbridge.database;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.DeviceDataTables;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericHeartRateSample;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericHeartRateSampleDao;
import nodomain.freeyourgadget.gadgetbridge.entities.HuaweiWorkoutDataSample;
import nodomain.freeyourgadget.gadgetbridge.entities.HuaweiWorkoutSummarySample;
import nodomain.freeyourgadget.gadgetbridge.entities.HuaweiWorkoutSummarySampleDao;
import nodomain.freeyourgadget.gadgetbridge.entities.User;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

public class DeviceDataTablesTest extends TestBase {
    @Test
    public void testDeleteKeepsOtherDevice() {
        final User user = DBHelper.getUser(daoSession);
        final Device deleted = DBHelper.getDevice(createDummyGDevice("AA:BB:CC:DD:EE:01"), daoSession);
        final Device kept = DBHelper.getDevice(createDummyGDevice("AA:BB:CC:DD:EE:02"), daoSession);
        insertDeviceData(deleted, user);
        insertDeviceData(kept, user);

        DeviceDataTables.delete(daoSession.getDatabase(), Objects.requireNonNull(deleted.getId()), null);
        daoSession.clear();

        assertDeviceData(deleted, 0);
        assertDeviceData(kept, 1);
        assertEquals(1, daoSession.getHuaweiWorkoutDataSampleDao().count());
    }

    @Test
    public void testDeleteOrphans() {
        final User user = DBHelper.getUser(daoSession);
        final Device orphaned = DBHelper.getDevice(createDummyGDevice("AA:BB:CC:DD:EE:01"), daoSession);
        final Device kept = DBHelper.getDevice(createDummyGDevice("AA:BB:CC:DD:EE:02"), daoSession);
        insertDeviceData(orphaned, user);
        insertDeviceData(kept, user);
        daoSession.getDeviceDao().delete(orphaned);

        DeviceDataTables.deleteOrphans(daoSession.getDatabase(), null);
        daoSession.clear();

        assertDeviceData(orphaned, 0);
        assertDeviceData(kept, 1);
        assertEquals(1, daoSession.getHuaweiWorkoutDataSampleDao().count());
    }

    @Test
    public void testQueryReturnsOnlyDevice() throws IOException {
        final User user = DBHelper.getUser(daoSession);
        final Device exported = DBHelper.getDevice(createDummyGDevice("AA:BB:CC:DD:EE:01"), daoSession);
        final Device other = DBHelper.getDevice(createDummyGDevice("AA:BB:CC:DD:EE:02"), daoSession);
        insertDeviceData(exported, user);
        insertDeviceData(other, user);

        final Map<String, Integer> rowCounts = new HashMap<>();
        DeviceDataTables.query(
            daoSession.getDatabase(),
            Objects.requireNonNull(exported.getId()),
            (table, cursor) -> rowCounts.put(table, cursor.getCount()),
            null
        );

        assertEquals(Integer.valueOf(1), rowCounts.get("DEVICE"));
        assertEquals(Integer.valueOf(1), rowCounts.get("GENERIC_HEART_RATE_SAMPLE"));
        assertEquals(Integer.valueOf(1), rowCounts.get("HUAWEI_WORKOUT_SUMMARY_SAMPLE"));
        assertEquals(Integer.valueOf(1), rowCounts.get("HUAWEI_WORKOUT_DATA_SAMPLE"));
    }

    private void insertDeviceData(final Device device, final User user) {
        final GenericHeartRateSample heartRateSample = new GenericHeartRateSample();
        heartRateSample.setTimestamp(1000L);
        heartRateSample.setDeviceId(Objects.requireNonNull(device.getId()));
        heartRateSample.setUserId(Objects.requireNonNull(user.getId()));
        heartRateSample.setHeartRate(60);
        daoSession.getGenericHeartRateSampleDao().insert(heartRateSample);

        final HuaweiWorkoutSummarySample summary = new HuaweiWorkoutSummarySample();
        summary.setDeviceId(device.getId());
        summary.setUserId(user.getId());
        final long workoutId = daoSession.getHuaweiWorkoutSummarySampleDao().insert(summary);

        daoSession.getHuaweiWorkoutDataSampleDao().insert(new HuaweiWorkoutDataSample(workoutId, 1000));
    }

    private void assertDeviceData(final Device device, final long expected) {
        assertEquals(expected, daoSession.getGenericHeartRateSampleDao().queryBuilder()
            .where(GenericHeartRateSampleDao.Properties.DeviceId.eq(device.getId()))
            .count());

        assertEquals(expected, daoSession.getHuaweiWorkoutSummarySampleDao().queryBuilder()
            .where(HuaweiWorkoutSummarySampleDao.Properties.DeviceId.eq(device.getId()))
            .count());
    }
}
