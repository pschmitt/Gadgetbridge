package nodomain.freeyourgadget.gadgetbridge.database.schema;

import android.database.sqlite.SQLiteDatabase;

import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.database.DBUpdateScript;
import nodomain.freeyourgadget.gadgetbridge.entities.CyclingSampleDao;

public class GadgetbridgeUpdate_156 implements DBUpdateScript {
    @Override
    public void upgradeSchema(final SQLiteDatabase db) {
        DBHelper.addColumnIfNotExist(CyclingSampleDao.TABLENAME, CyclingSampleDao.Properties.Cadence.columnName, "INTEGER", db);
    }

    @Override
    public void downgradeSchema(final SQLiteDatabase db) {
    }
}
