package nodomain.freeyourgadget.gadgetbridge.database.schema;

import static nodomain.freeyourgadget.gadgetbridge.entities.GarminSleepStatsSampleDao.Properties.*;

import android.database.sqlite.SQLiteDatabase;

import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.database.DBUpdateScript;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminSleepStatsSampleDao;

public class GadgetbridgeUpdate_154 implements DBUpdateScript {
    @Override
    public void upgradeSchema(final SQLiteDatabase db) {
        final String GarminSleepStats = GarminSleepStatsSampleDao.TABLENAME;
        DBHelper.addColumnIfNotExist(GarminSleepStats, AverageStressDuringSleep.columnName, "FLOAT", db);
        DBHelper.addColumnIfNotExist(GarminSleepStats, AwakeTimeScore.columnName, "INTEGER", db);
        DBHelper.addColumnIfNotExist(GarminSleepStats, AwakeningsCount.columnName, "INTEGER", db);
        DBHelper.addColumnIfNotExist(GarminSleepStats, AwakeningsCountScore.columnName, "INTEGER", db);
        DBHelper.addColumnIfNotExist(GarminSleepStats, CombinedAwakeScore.columnName, "INTEGER", db);
        DBHelper.addColumnIfNotExist(GarminSleepStats, DeepSleepScore.columnName, "INTEGER", db);
        DBHelper.addColumnIfNotExist(GarminSleepStats, InterruptionsScore.columnName, "INTEGER", db);
        DBHelper.addColumnIfNotExist(GarminSleepStats, LightSleepScore.columnName, "INTEGER", db);
        DBHelper.addColumnIfNotExist(GarminSleepStats, RemSleepScore.columnName, "INTEGER", db);
        DBHelper.addColumnIfNotExist(GarminSleepStats, SleepDurationScore.columnName, "INTEGER", db);
        DBHelper.addColumnIfNotExist(GarminSleepStats, SleepQualityScore.columnName, "INTEGER", db);
        DBHelper.addColumnIfNotExist(GarminSleepStats, SleepRecoveryScore.columnName, "INTEGER", db);
        DBHelper.addColumnIfNotExist(GarminSleepStats, SleepRestlessnessScore.columnName, "INTEGER", db);
    }

    @Override
    public void downgradeSchema(final SQLiteDatabase db) {
    }
}
