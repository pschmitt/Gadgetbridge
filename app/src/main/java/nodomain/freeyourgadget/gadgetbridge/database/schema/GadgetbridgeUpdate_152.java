package nodomain.freeyourgadget.gadgetbridge.database.schema;

import android.database.sqlite.SQLiteDatabase;

import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.database.DBUpdateScript;
import nodomain.freeyourgadget.gadgetbridge.entities.WorkoutTemplateStepDao;

public class GadgetbridgeUpdate_152 implements DBUpdateScript {
    @Override
    public void upgradeSchema(final SQLiteDatabase db) {
        if (!DBHelper.existsColumn(WorkoutTemplateStepDao.TABLENAME, WorkoutTemplateStepDao.Properties.DurationPlus.columnName, db)) {
            final String statement = "ALTER TABLE " + WorkoutTemplateStepDao.TABLENAME + " ADD COLUMN \""
                + WorkoutTemplateStepDao.Properties.DurationPlus.columnName + "\" INTEGER;";
            db.execSQL(statement);
        }
    }

    @Override
    public void downgradeSchema(final SQLiteDatabase db) {
    }
}
