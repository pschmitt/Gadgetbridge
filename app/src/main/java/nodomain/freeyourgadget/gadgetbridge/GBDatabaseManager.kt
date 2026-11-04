package nodomain.freeyourgadget.gadgetbridge;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;

/**
 * Static access to the {@link DatabaseManager} of the current application, kept so that existing
 * callers keep working.
 *
 * @deprecated Use Hilt DI to inject {@link DatabaseManager} instead.
 */
@Deprecated
public class GBDatabaseManager {
    private GBDatabaseManager() {
    }

    private static DatabaseManager manager() {
        return GBApplication.app().getDatabaseManager();
    }

    public static void closeDatabase() {
        manager().closeDatabase();
    }

    /**
     * @param context ignored, the database always uses the application context
     */
    public static void setupDatabase(final Context context) {
        manager().setupDatabase();
    }

    /**
     * @see DatabaseManager#acquireWrite()
     */
    public static DBHandler acquireWrite() throws GBException {
        return manager().acquireWrite();
    }

    /**
     * @see DatabaseManager#acquireReadOnly()
     */
    public static DBHandler acquireReadOnly() throws GBException {
        return manager().acquireReadOnly();
    }

    /**
     * @param context ignored, the database always uses the application context
     * @see DatabaseManager#deleteActivityDatabase()
     */
    public static boolean deleteActivityDatabase(final Context context) {
        return manager().deleteActivityDatabase();
    }

    public static void exportDB(final File destFile) throws IOException {
        manager().exportDB(destFile);
    }

    public static void exportDB(final OutputStream dest) throws IOException {
        manager().exportDB(dest);
    }

    /**
     * @param context ignored, the database always uses the application context
     * @see DatabaseManager#deleteOldActivityDatabase()
     */
    public static boolean deleteOldActivityDatabase(final Context context) {
        return manager().deleteOldActivityDatabase();
    }

    public static void importDB(final File fromFile) throws IllegalStateException, IOException {
        manager().importDB(fromFile);
    }

    public static void importDB(final InputStream inputStream) throws IllegalStateException, IOException {
        manager().importDB(inputStream);
    }
}
