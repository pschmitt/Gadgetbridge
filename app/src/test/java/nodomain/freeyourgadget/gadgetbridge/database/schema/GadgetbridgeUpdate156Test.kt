package nodomain.freeyourgadget.gadgetbridge.database.schema

import android.database.sqlite.SQLiteDatabase
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class GadgetbridgeUpdate156Test {

    private lateinit var db: SQLiteDatabase

    @Before
    fun createSchema155Table() {
        db = SQLiteDatabase.create(null)
        // CYCLING_SAMPLE as schemas 152 to 155 created it, before the CADENCE column
        db.execSQL(
            "CREATE TABLE \"CYCLING_SAMPLE\" (\"TIMESTAMP\" INTEGER NOT NULL, \"DEVICE_ID\" INTEGER NOT NULL, " +
                "\"USER_ID\" INTEGER NOT NULL, \"REVOLUTION_COUNT\" INTEGER, \"DISTANCE\" REAL, \"SPEED\" REAL, " +
                "PRIMARY KEY (\"TIMESTAMP\", \"DEVICE_ID\") ON CONFLICT REPLACE) WITHOUT ROWID"
        )
        db.execSQL("INSERT INTO CYCLING_SAMPLE VALUES (1000, 1, 1, 42, 88.0, 5.5)")
    }

    @After
    fun close() {
        db.close()
    }

    @Test
    fun addsCadenceColumn_keepsExistingRows() {
        assertFalse(DBHelper.existsColumn("CYCLING_SAMPLE", "CADENCE", db))

        GadgetbridgeUpdate_156().upgradeSchema(db)

        assertTrue(DBHelper.existsColumn("CYCLING_SAMPLE", "CADENCE", db))
        db.rawQuery("SELECT REVOLUTION_COUNT, CADENCE FROM CYCLING_SAMPLE", null).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(42, c.getInt(0))
            assertTrue("existing samples have no cadence", c.isNull(1))
        }
    }

    @Test
    fun runningTwice_isHarmless() {
        GadgetbridgeUpdate_156().upgradeSchema(db)
        GadgetbridgeUpdate_156().upgradeSchema(db)

        assertTrue(DBHelper.existsColumn("CYCLING_SAMPLE", "CADENCE", db))
    }
}
